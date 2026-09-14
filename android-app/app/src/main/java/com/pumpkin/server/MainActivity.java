package com.pumpkin.server;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * 轻壳主界面：不内置服务端，联网从 Releases 下载/切换/回滚/清理版本。
 * 视觉风格：深色渐变 + 柔光玻璃卡片。
 */
public class MainActivity extends Activity {

    private static final int REQ_NOTIFICATIONS = 1;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private VersionManager versions;
    private UpdateClient updates;

    private TextView statusDot;
    private TextView statusText;
    private TextView addrText;
    private TextView dirText;

    private TextView versionCurrent;
    private TextView versionInstalled;
    private TextView versionHint;
    private ProgressBar progress;

    private Button installBtn;
    private Button checkBtn;
    private Button modeBtn;

    private TextView logView;
    private ScrollView logScroll;
    private EditText cmdInput;

    private UpdateClient.Release latest;
    private volatile boolean busy;
    private volatile boolean cancelRequested;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        versions = new VersionManager(this);
        updates = new UpdateClient(this);
        setContentView(buildUi());
        requestNotificationPermissionIfNeeded();
        ui.post(ticker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.removeCallbacks(ticker);
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(ticker);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelRequested = true;
    }

    // ---------------------------------------------------------------- UI

    private View buildUi() {
        ScrollView root = new ScrollView(this);
        root.setBackground(UiKit.windowBackground());
        root.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = UiKit.dp(this, 16);
        col.setPadding(pad, UiKit.dp(this, 26), pad, UiKit.dp(this, 26));
        root.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        col.addView(UiKit.header(this, "Pumpkin", "Rust 版 Minecraft 服务端 · 在线版本管理"));

        // ---------- 运行状态 ----------
        LinearLayout statusCard = UiKit.card(this);
        statusDot = new TextView(this);
        statusText = new TextView(this);
        statusCard.addView(UiKit.statusRow(this, statusDot, statusText));

        addrText = UiKit.label(this, "");
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = UiKit.dp(this, 10);
        addrText.setLayoutParams(ap);
        addrText.setTextColor(UiKit.TEXT);
        addrText.setTextSize(14);
        addrText.setTypeface(Typeface.MONOSPACE);
        statusCard.addView(addrText);

        dirText = UiKit.label(this, "");
        statusCard.addView(dirText);

        Button startBtn = UiKit.button(this, "启动", true);
        Button stopBtn = UiKit.button(this, "停止", false);
        statusCard.addView(UiKit.buttonRow(this, startBtn, stopBtn));

        Button battBtn = UiKit.button(this, "电池优化", false);
        Button copyBtn = UiKit.button(this, "复制地址", false);
        statusCard.addView(UiKit.buttonRow(this, battBtn, copyBtn));

        modeBtn = UiKit.button(this, "启动方式：普通", false);
        statusCard.addView(UiKit.buttonRow(this, modeBtn));
        col.addView(statusCard);

        // ---------- 版本管理 ----------
        LinearLayout verCard = UiKit.card(this);
        verCard.addView(UiKit.cardTitle(this, "服务端版本"));

        versionCurrent = UiKit.value(this, "");
        versionCurrent.setTextSize(15);
        versionCurrent.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.topMargin = UiKit.dp(this, 10);
        versionCurrent.setLayoutParams(vp);
        verCard.addView(versionCurrent);

        versionInstalled = UiKit.label(this, "");
        verCard.addView(versionInstalled);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setVisibility(View.GONE);
        progress.setProgressTintList(ColorStateList.valueOf(UiKit.ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 6));
        pp.topMargin = UiKit.dp(this, 12);
        progress.setLayoutParams(pp);
        verCard.addView(progress);

        versionHint = UiKit.label(this, "");
        verCard.addView(versionHint);

        checkBtn = UiKit.button(this, "检查更新", false);
        installBtn = UiKit.button(this, "下载最新版本", true);
        verCard.addView(UiKit.buttonRow(this, checkBtn, installBtn));

        Button switchBtn = UiKit.button(this, "切换版本", false);
        Button cleanBtn = UiKit.button(this, "清理数据", false);
        verCard.addView(UiKit.buttonRow(this, switchBtn, cleanBtn));
        col.addView(verCard);

        // ---------- 控制台 ----------
        LinearLayout consoleCard = UiKit.card(this);
        consoleCard.addView(UiKit.cardTitle(this, "控制台"));

        logScroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextSize(10.5f);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextColor(0xFFCFD6E4);
        logView.setTextIsSelectable(true);
        logScroll.addView(logView);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 240));
        lp.topMargin = UiKit.dp(this, 10);
        logScroll.setLayoutParams(lp);
        logScroll.setBackground(UiKit.glass(this, false));
        logScroll.setPadding(UiKit.dp(this, 10), UiKit.dp(this, 10),
                UiKit.dp(this, 10), UiKit.dp(this, 10));
        consoleCard.addView(logScroll);

        LinearLayout cmdRow = new LinearLayout(this);
        cmdRow.setOrientation(LinearLayout.HORIZONTAL);
        cmdRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = UiKit.dp(this, 12);
        cmdRow.setLayoutParams(cp);

        cmdInput = new EditText(this);
        cmdInput.setHint("控制台命令：list / op 玩家名 / stop");
        cmdInput.setHintTextColor(UiKit.TEXT_DIM);
        cmdInput.setTextColor(UiKit.TEXT);
        cmdInput.setTextSize(14);
        cmdInput.setSingleLine(true);
        cmdInput.setInputType(InputType.TYPE_CLASS_TEXT);
        cmdInput.setBackground(UiKit.glass(this, false));
        cmdInput.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 10),
                UiKit.dp(this, 14), UiKit.dp(this, 10));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ip.rightMargin = UiKit.dp(this, 8);
        cmdRow.addView(cmdInput, ip);

        Button sendBtn = UiKit.button(this, "发送", true);
        cmdRow.addView(sendBtn);
        consoleCard.addView(cmdRow);
        col.addView(consoleCard);

        // ---------- 事件 ----------
        startBtn.setOnClickListener(v -> startServer());
        stopBtn.setOnClickListener(v -> stopServer());
        battBtn.setOnClickListener(v -> openBatterySettings());
        copyBtn.setOnClickListener(v -> copyAddress());
        modeBtn.setOnClickListener(v -> showModeDialog());
        checkBtn.setOnClickListener(v -> doCheck());
        installBtn.setOnClickListener(v -> doInstallOrStop());
        switchBtn.setOnClickListener(v -> showSwitchDialog());
        cleanBtn.setOnClickListener(v -> showCleanDialog());
        sendBtn.setOnClickListener(v -> sendCommand());

        return root;
    }

    // ---------------------------------------------------------------- 状态刷新

    private void refresh() {
        PumpkinServer server = PumpkinServer.get();
        boolean running = server.isRunning();

        statusDot.setText("●");
        statusDot.setTextColor(running ? UiKit.OK : UiKit.TEXT_DIM);
        if (running) {
            long secs = Math.max(0, (System.currentTimeMillis() - server.getStartedAt()) / 1000);
            statusText.setText("运行中 " + (secs / 60) + ":" + String.format("%02d", secs % 60));
        } else if (server.getExitCode() != Integer.MIN_VALUE) {
            statusText.setText("已停止（退出码 " + server.getExitCode() + "）");
        } else {
            statusText.setText("未运行");
        }

        String lan = PumpkinServer.findLanIpv4();
        addrText.setText(lan == null
                ? "未检测到局域网 IP（确认已连上 WiFi）"
                : "Java " + lan + ":25565　·　基岩 " + lan + ":19132");
        dirText.setText("数据目录 " + ServerPaths.workDir(this).getAbsolutePath());
        modeBtn.setText(Prefs.getBool(this, "root_mode", false)
                ? "启动方式：Root（su，不改 SELinux）"
                : "启动方式：普通（targetSdk 28 豁免）");

        // 版本信息
        String cur = versions.currentTag();
        List<VersionManager.Installed> installed = versions.listInstalled();
        if (cur == null) {
            versionCurrent.setText("尚未安装服务端");
            installBtn.setText("下载最新版本");
        } else {
            versionCurrent.setText("当前版本  " + cur);
            installBtn.setText(latest != null && !latest.tag.equals(cur) ? "更新到 " + latest.tag : "重新下载当前版本");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("已安装 ").append(installed.size()).append(" 个版本");
        if (installed.size() > 1) {
            sb.append("（可回滚到旧版本）");
        }
        sb.append("　占用 ").append(fmtSize(VersionManager.dirSize(versions.getVersionsDir())));
        sb.append("\n游戏数据 ").append(fmtSize(VersionManager.dirSize(ServerPaths.workDir(this))));
        versionInstalled.setText(sb.toString());

        // 日志
        String text = server.tailLog();
        if (!text.contentEquals(logView.getText())) {
            logView.setText(text);
            logScroll.post(new Runnable() {
                @Override
                public void run() {
                    logScroll.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }

    private static String fmtSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / 1048576.0);
        }
        return String.format("%.2f GB", bytes / 1073741824.0);
    }

    // ---------------------------------------------------------------- 动作

    private void startServer() {
        requestNotificationPermissionIfNeeded();
        if (versions.currentTag() == null) {
            toast("还没有安装服务端，请先「检查更新」并下载");
            return;
        }
        Intent intent = new Intent(this, ServerService.class);
        intent.setAction(ServerService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopServer() {
        Intent intent = new Intent(this, ServerService.class);
        intent.setAction(ServerService.ACTION_STOP);
        startService(intent);
    }

    private void sendCommand() {
        String cmd = cmdInput.getText().toString();
        if (cmd.trim().isEmpty()) {
            return;
        }
        PumpkinServer.get().sendCommand(cmd);
        cmdInput.setText("");
        refresh();
    }

    private void copyAddress() {
        String lan = PumpkinServer.findLanIpv4();
        String text = lan == null ? "未连接网络" : lan + ":25565";
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("addr", text));
            toast("已复制 " + text);
        }
    }

    private void showModeDialog() {
        final String[] items = new String[]{
                "普通模式 — App 直接启动（依赖 targetSdk 28 的 SELinux 豁免）",
                "Root 模式 — 通过 su 启动（不动 SELinux，不会被检测到）"
        };
        new AlertDialog.Builder(this)
                .setTitle("选择服务端启动方式")
                .setMessage("普通模式开箱即用。Root 模式通过 su 域运行，不受「私有目录禁止执行」的限制，"
                        + "适合普通模式失效时使用（需要 Magisk 授权，首次会弹窗）。")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            Prefs.putBool(MainActivity.this, "root_mode", false);
                            toast("已切换到普通模式");
                            refresh();
                            return;
                        }
                        toast("正在检测 root 授权…");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final boolean ok = RootHelper.available();
                                ui.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (ok) {
                                            Prefs.putBool(MainActivity.this, "root_mode", true);
                                            toast("已切换到 Root 模式");
                                        } else {
                                            toast("未获得 root 权限（su 不可用或未授权）");
                                        }
                                        refresh();
                                    }
                                });
                            }
                        }, "root-check").start();
                    }
                })
                .show();
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception first) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception second) {
                toast("请手动到系统设置里把本应用设为「不受限制」");
            }
        }
    }

    // ---------------------------------------------------------------- 版本管理

    private void doCheck() {
        if (busy) {
            toast("正在忙，请稍候");
            return;
        }
        busy = true;
        checkBtn.setEnabled(false);
        versionHint.setText("正在检查 " + UpdateClient.repo(this) + " 的 Releases …");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<UpdateClient.Release> list = updates.fetchReleases();
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            busy = false;
                            checkBtn.setEnabled(true);
                            if (list.isEmpty()) {
                                versionHint.setText("没有找到带 Android 二进制的 Release");
                                installBtn.setEnabled(false);
                                return;
                            }
                            latest = list.get(0);
                            installBtn.setEnabled(true);
                            String cur = versions.currentTag();
                            StringBuilder sb = new StringBuilder();
                            sb.append("最新版本 ").append(latest.tag);
                            if (latest.binarySize > 0) {
                                sb.append("　").append(fmtSize(latest.binarySize));
                            }
                            if (cur != null && cur.equals(latest.tag)) {
                                sb.append("\n已是最新版本");
                            } else if (cur != null) {
                                sb.append("\n可更新（当前 ").append(cur).append("）");
                            }
                            if (list.size() > 1) {
                                sb.append("\n历史上还有 ").append(list.size() - 1).append(" 个版本可选，可用「手动选择版本」");
                            }
                            versionHint.setText(sb.toString());
                            refresh();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            busy = false;
                            checkBtn.setEnabled(true);
                            versionHint.setText("检查失败：" + e.getMessage()
                                    + "\n若手机访问 GitHub 不通，可在设置里改用镜像地址");
                        }
                    });
                }
            }
        }, "check-update").start();
    }

    /** 下载按钮：运行中先停下来（替换二进制时不能占用）。 */
    private void doInstallOrStop() {
        if (busy) {
            toast("正在忙，请稍候");
            return;
        }
        if (latest == null) {
            doCheck();
            toast("先检查更新，再点一次下载");
            return;
        }
        if (PumpkinServer.get().isRunning()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要先停止服务端")
                    .setMessage("更新会替换服务端程序文件，需要先停止正在运行的服务器。要现在停止并继续更新吗？")
                    .setPositiveButton("停止并更新", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int w) {
                            stopServer();
                            ui.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    doDownloadInstall(latest);
                                }
                            }, 1200);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        doDownloadInstall(latest);
    }

    private void doDownloadInstall(final UpdateClient.Release release) {
        busy = true;
        cancelRequested = false;
        installBtn.setEnabled(false);
        checkBtn.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        versionHint.setText("正在下载 " + release.tag + " …");

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File tmp = updates.download(release, new UpdateClient.Progress() {
                        @Override
                        public void onProgress(final long done, final long total) {
                            ui.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (total > 0) {
                                        progress.setProgress((int) (done * 1000 / total));
                                        versionHint.setText("下载中 " + fmtSize(done) + " / " + fmtSize(total));
                                    } else {
                                        versionHint.setText("下载中 " + fmtSize(done));
                                    }
                                }
                            });
                        }

                        @Override
                        public boolean isRunning() {
                            return !cancelRequested;
                        }
                    });
                    versions.install(release.tag, tmp);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            finishBusy();
                            versionHint.setText("已安装 " + release.tag + "，点「启动」即可运行");
                            toast("更新完成");
                            refresh();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            finishBusy();
                            versionHint.setText("下载/安装失败：" + e.getMessage());
                        }
                    });
                }
            }
        }, "download").start();
    }

    private void finishBusy() {
        busy = false;
        progress.setVisibility(View.GONE);
        installBtn.setEnabled(true);
        checkBtn.setEnabled(true);
    }

    private void showSwitchDialog() {
        final List<VersionManager.Installed> installed = versions.listInstalled();
        if (installed.isEmpty()) {
            toast("还没有安装任何版本");
            return;
        }
        final String cur = versions.currentTag();
        final String[] items = new String[installed.size()];
        for (int i = 0; i < installed.size(); i++) {
            VersionManager.Installed v = installed.get(i);
            items[i] = (v.tag.equals(cur) ? "● " : "○ ") + v.tag + "　" + fmtSize(v.size);
        }
        new AlertDialog.Builder(this)
                .setTitle("选择要运行的版本")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String tag = installed.get(which).tag;
                        if (tag.equals(cur)) {
                            toast("已经是当前版本");
                            return;
                        }
                        if (PumpkinServer.get().isRunning()) {
                            toast("请先停止服务端再切换版本");
                            return;
                        }
                        versions.setCurrent(tag);
                        toast("已切换到 " + tag);
                        refresh();
                    }
                })
                .setNeutralButton("删除某个版本", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        showDeleteDialog(installed);
                    }
                })
                .show();
    }

    private void showDeleteDialog(final List<VersionManager.Installed> installed) {
        final String[] items = new String[installed.size()];
        for (int i = 0; i < installed.size(); i++) {
            items[i] = installed.get(i).tag;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除哪个版本？")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String tag = installed.get(which).tag;
                        if (tag.equals(versions.currentTag())) {
                            toast("不能删除正在使用的版本");
                            return;
                        }
                        versions.delete(tag);
                        toast("已删除 " + tag);
                        refresh();
                    }
                })
                .show();
    }

    private void showCleanDialog() {
        final String[] options = new String[]{
                "清理服务端版本（保留游戏数据）",
                "清理游戏数据（世界、配置、日志）",
                "全部清理（版本 + 游戏数据）"
        };
        new AlertDialog.Builder(this)
                .setTitle("清理")
                .setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (PumpkinServer.get().isRunning()) {
                            toast("请先停止服务端");
                            return;
                        }
                        switch (which) {
                            case 0:
                                versions.clearAllVersions();
                                latest = null;
                                toast("已清理所有服务端版本");
                                refresh();
                                break;
                            case 1:
                                confirm("确定删除世界存档、配置和日志吗？此操作不可恢复。", new Runnable() {
                                    @Override
                                    public void run() {
                                        VersionManager.clearServerData(MainActivity.this);
                                        toast("已清理游戏数据");
                                        refresh();
                                    }
                                });
                                break;
                            default:
                                confirm("确定清空全部数据吗？包括所有已下载的服务端版本和世界存档，此操作不可恢复。",
                                        new Runnable() {
                                            @Override
                                            public void run() {
                                                versions.clearAllVersions();
                                                VersionManager.clearServerData(MainActivity.this);
                                                latest = null;
                                                toast("已清空全部数据");
                                                refresh();
                                            }
                                        });
                                break;
                        }
                    }
                })
                .show();
    }

    private void confirm(String message, final Runnable onYes) {
        new AlertDialog.Builder(this)
                .setTitle("请确认")
                .setMessage(message)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        onYes.run();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIFICATIONS);
            }
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
