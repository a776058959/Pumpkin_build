package com.pumpkin.server;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

/**
 * 服务端下载任务。
 *
 * 只有三种状态，并且是**唯一的状态源**——界面按钮必须完全由它推导，
 * 不要在别处（例如每秒刷新的 refresh）另设判断，否则两边会互相覆盖。
 *
 *   IDLE    没有任务（未开始 / 已完成 / 已失败 / 已删除）
 *   RUNNING 正在下载
 *   PAUSED  已暂停，磁盘上保留了 .part，可以继续或删除
 *
 * 暂停时保留未完成文件，继续时用 HTTP Range 从断点接着下。
 */
public final class Downloader {

    public enum State { IDLE, RUNNING, PAUSED }

    public interface Listener {
        void onProgress(long done, long total);

        void onSourceFailed(String url, String reason);

        void onFinished(File file);

        void onPaused(long done, long total);

        void onFailed(String message);
    }

    private final Context ctx;
    private final UpdateClient client;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private UpdateClient.Release release;
    private volatile State state = State.IDLE;
    private volatile boolean pauseRequested;
    private volatile long done;
    private volatile long total;

    public Downloader(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.client = new UpdateClient(this.ctx);
    }

    public State getState() {
        return state;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public boolean isPaused() {
        return state == State.PAUSED;
    }

    /** 只有「已暂停」才算有一个可继续 / 可删除的任务。 */
    public boolean hasTask() {
        return state == State.PAUSED;
    }

    public UpdateClient.Release getRelease() {
        return release;
    }

    public long getDone() {
        return done;
    }

    public long getTotal() {
        return total;
    }

    public File partFile() {
        return release == null ? null : client.partFile(release);
    }

    public void forget() {
        release = null;
        done = 0;
        total = 0;
        state = State.IDLE;
    }

    /** 开始下载；若存在未完成的 .part 会自动续传。 */
    public void start(UpdateClient.Release r, final Listener listener) {
        if (state == State.RUNNING) {
            return;
        }
        if (r != null) {
            release = r;
        }
        if (release == null) {
            listener.onFailed("没有选中的版本");
            return;
        }
        final UpdateClient.Release target = release;
        final File part = client.partFile(target);
        final long offset = part.isFile() ? part.length() : 0L;

        pauseRequested = false;
        state = State.RUNNING;
        done = offset;
        total = target.binarySize;

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final File f = client.download(target, new UpdateClient.Progress() {
                        @Override
                        public void onProgress(final long d, final long tot) {
                            done = d;
                            total = tot;
                            ui.post(new Runnable() {
                                @Override
                                public void run() {
                                    listener.onProgress(d, tot);
                                }
                            });
                        }

                        @Override
                        public boolean isRunning() {
                            return !pauseRequested;
                        }

                        @Override
                        public void onSourceFailed(final String url, final String reason) {
                            ui.post(new Runnable() {
                                @Override
                                public void run() {
                                    listener.onSourceFailed(url, reason);
                                }
                            });
                        }
                    }, offset);
                    // 下载完成：任务结束，回到 IDLE
                    state = State.IDLE;
                    release = null;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onFinished(f);
                        }
                    });
                } catch (final Exception e) {
                    if (pauseRequested) {
                        // 暂停：保留 .part，进入 PAUSED
                        state = State.PAUSED;
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onPaused(done, total);
                            }
                        });
                    } else {
                        // 真失败：任务作废
                        state = State.IDLE;
                        release = null;
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onFailed(e.getMessage() == null ? "下载失败" : e.getMessage());
                            }
                        });
                    }
                }
            }
        }, "pumpkin-download");
        t.setDaemon(true);
        t.start();
    }

    /** 暂停（保留已下载部分）。 */
    public void pause() {
        if (state == State.RUNNING) {
            pauseRequested = true;
        }
    }

    /** 删除任务：停止下载，并把已经下载的文件全部清理掉。 */
    public void deleteTask() {
        pauseRequested = true;
        File part = partFile();
        if (part != null && part.isFile()) {
            part.delete();
        }
        // 顺手清空下载缓存目录：之前中断留下的残留也一并清掉
        File dir = ServerPaths.downloadCacheDir(ctx);
        File[] leftovers = dir.listFiles();
        if (leftovers != null) {
            for (File f : leftovers) {
                f.delete();
            }
        }
        forget();
    }
}
