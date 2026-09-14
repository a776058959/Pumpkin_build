package com.pumpkin.server;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;

/**
 * 服务端下载任务：进度回调、暂停、继续（断点续传）、删除任务。
 *
 * 暂停时保留未完成的 .part 文件，继续时用 HTTP Range 从断点接着下；
 * 换下载源后无法保证 Range 有效，会自动退回从头下。
 */
public final class Downloader {

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
    private volatile boolean pauseRequested;
    private volatile boolean running;
    private volatile long done;
    private volatile long total;

    public Downloader(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.client = new UpdateClient(this.ctx);
    }

    public boolean isRunning() {
        return running;
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

    /** 是否有已暂停、可继续或可删除的任务。 */
    public boolean hasTask() {
        return release != null && !running;
    }

    public void forget() {
        release = null;
        done = 0;
        total = 0;
    }

    /** 开始下载；若存在未完成的 .part 则自动续传。 */
    public void start(UpdateClient.Release r, final Listener listener) {
        if (running) {
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
        running = true;
        done = offset;
        total = target.binarySize;

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File f = client.download(target, new UpdateClient.Progress() {
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
                    running = false;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onFinished(f);
                        }
                    });
                } catch (final Exception e) {
                    running = false;
                    final boolean wasPaused = pauseRequested;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (wasPaused) {
                                listener.onPaused(done, total);
                            } else {
                                listener.onFailed(e.getMessage() == null ? "下载失败" : e.getMessage());
                            }
                        }
                    });
                }
            }
        }, "pumpkin-download");
        t.setDaemon(true);
        t.start();
    }

    /** 暂停（保留已下载部分）。 */
    public void pause() {
        pauseRequested = true;
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
