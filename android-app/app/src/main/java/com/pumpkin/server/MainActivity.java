package com.pumpkin.server;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * 轻壳主界面：底部悬浮玻璃导航栏 + 三个页面（运行 / 更新 / 设置）。
 * 壳本身不含服务端，联网从 Releases 下载、切换、回滚、清理版本。
 */
public class MainActivity extends Activity {

    private static final int REQ_NOTIFICATIONS = 1;
    private static final int PAGE_RUN = 0;
    private static final int PAGE_UPDATE = 1;
    private static final int PAGE_SETTINGS = 2;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private VersionManager versions;
    private UpdateClient updates;

    // 页面容器与导航
    private View pageRun;
    private View pageUpdate;
    private View pageSettings;
    private final TextView[] tabIcons = new TextView[3];
    private final TextView[] tabLabels = new TextView[3];
    private FrameLayout contentArea;
    private ImageView navBlur;
    private BlurBackdrop backdrop;

    // 运行页
    private TextView statusDot;
    private TextView statusText;
    private TextView addrText;
    private TextView dirText;
    private TextView logView;
    private ScrollView logScroll;
    private EditText cmdInput;

    // 更新页
    private TextView versionCurrent;
    private TextView versionInstalled;
    private TextView versionHint;
    private ProgressBar progress;
    private Button installBtn;
    private Button checkBtn;

    // 设置页
    private TextView modeValue;
    private EditText apiInput;
    private EditText repoInput;
    private EditText mirrorInput;

    private UpdateClient.Release latest;
    private volatile boolean busy;
    private volatile boolean cancelRequested;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (backdrop != null) {
                backdrop.refresh();
            }
            ui.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        versions = new VersionManager(this);
        updates = new UpdateClient(this);
        setContentView(buildUi());
        switchPage(PAGE_RUN);
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
        if (backdrop != null) {
            backdrop.release();
        }
    }

    // ================================================================ 整体布局

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(UiKit.windowBackground());

        contentArea = new FrameLayout(this);
        contentArea.setPadding(0, 0, 0, UiKit.dp(this, 96));
        // 内容区自己也画一份同样的渐变底：截屏做毛玻璃时才不会是一片透明
        contentArea.setBackground(UiKit.windowBackground());
        root.addView(contentArea, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pageRun = buildRunPage();
        pageUpdate = buildUpdatePage();
        pageSettings = buildSettingsPage();
        contentArea.addView(pageRun);
        contentArea.addView(pageUpdate);
        contentArea.addView(pageSettings);

        // 毛玻璃层：与导航栏同位置同尺寸，显示「导航栏背后的模糊内容」。
        // 注意：没有图片的 ImageView 高度是 0，必须先给一个高度，等导航栏测量完再同步真实高度，
        // 否则 BlurBackdrop 会因为取不到尺寸而永远不绘制。
        navBlur = new ImageView(this);
        navBlur.setScaleType(ImageView.ScaleType.FIT_XY);
        FrameLayout.LayoutParams blurParams = navParams();
        blurParams.height = UiKit.dp(this, 76);
        root.addView(navBlur, blurParams);

        View nav = buildBottomNav();
        root.addView(nav, navParams());
        nav.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int h = bottom - top;
                if (h > 0 && navBlur.getHeight() != h) {
                    ViewGroup.LayoutParams p = navBlur.getLayoutParams();
                    p.height = h;
                    navBlur.setLayoutParams(p);
                }
            }
        });

        backdrop = new BlurBackdrop(contentArea, navBlur);

        return root;
    }

    private FrameLayout.LayoutParams navParams() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        p.setMargins(UiKit.dp(this, 18), 0, UiKit.dp(this, 18), UiKit.dp(this, 18));
        return p;
    }

    private ScrollView pageContainer() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = UiKit.dp(this, 16);
        col.setPadding(pad, UiKit.dp(this, 26), pad, UiKit.dp(this, 18));
        sv.addView(col, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sv.setTag(col);
        return sv;
    }

    private static LinearLayout columnOf(ScrollView sv) {
        return (LinearLayout) sv.getTag();
    }

    // ---------------------------------------------------------------- 运行页

    private View buildRunPage() {
        ScrollView sv = pageContainer();
        LinearLayout col = columnOf(sv);

        col.addView(UiKit.header(this, "Pumpkin", "Rust 版 Minecraft 服务端"));

        LinearLayout statusCard = UiKit.card(this);
        statusDot = new TextView(this);
        statusText = new TextView(this);
        statusCard.addView(UiKit.statusRow(this, statusDot, statusText));

        addrText = UiKit.value(this, "");
        addrText.setTextSize(15);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = UiKit.dp(this, 12);
        addrText.setLayoutParams(ap);
        statusCard.addView(addrText);

        dirText = UiKit.label(this, "");
        statusCard.addView(dirText);

        Button startBtn = UiKit.button(this, "启动", true);
        Button stopBtn = UiKit.button(this, "停止", false);
        statusCard.addView(UiKit.buttonRow(this, startBtn, stopBtn));

        Button battBtn = UiKit.button(this, "电池优化", false);
        Button copyBtn = UiKit.button(this, "复制地址", false);
        statusCard.addView(UiKit.buttonRow(this, battBtn, copyBtn));
        col.addView(statusCard);

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
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 260));
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
        cmdInput.setHint("命令：list / op 玩家名 / stop");
        styleInput(cmdInput);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ip.rightMargin = UiKit.dp(this, 8);
        cmdRow.addView(cmdInput, ip);

        Button sendBtn = UiKit.button(this, "发送", true);
        cmdRow.addView(sendBtn);
        consoleCard.addView(cmdRow);
        col.addView(consoleCard);

        startBtn.setOnClickListener(v -> startServer());
        stopBtn.setOnClickListener(v -> stopServer());
        battBtn.setOnClickListener(v -> openBatterySettings());
        copyBtn.setOnClickListener(v -> copyAddress());
        sendBtn.setOnClickListener(v -> sendCommand());

        return sv;
    }

    // ---------------------------------------------------------------- 更新页

    private View buildUpdatePage() {
        ScrollView sv = pageContainer();
        LinearLayout col = columnOf(sv);

        col.addView(UiKit.header(this, "服务端版本", "从 Releases 下载 / 切换 / 回滚"));

        LinearLayout verCard = UiKit.card(this);
        verCard.addView(UiKit.cardTitle(this, "当前版本"));

        versionCurrent = UiKit.value(this, "");
        versionCurrent.setTextSize(16);
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

        Button switchBtn = UiKit.button(this, "切换 / 回滚", false);
        Button deleteBtn = UiKit.button(this, "删除版本", false);
        verCard.addView(UiKit.buttonRow(this, switchBtn, deleteBtn));
        col.addView(verCard);

        LinearLayout tipsCard = UiKit.card(this);
        tipsCard.addView(UiKit.cardTitle(this, "说明"));
        tipsCard.addView(UiKit.label(this,
                "· 服务端程序保存在应用内部私有目录（那里才允许执行）\n"
                        + "· 游戏数据在外部目录，插 USB 或文件管理器即可修改\n"
                        + "· 最多保留 3 个历史版本用于回滚\n"
                        + "· 更新会替换程序文件，需先停止服务器\n"
                        + "· 下载源不通时到「设置」页改镜像"));
        col.addView(tipsCard);

        checkBtn.setOnClickListener(v -> doCheck());
        installBtn.setOnClickListener(v -> doInstallOrStop());
        switchBtn.setOnClickListener(v -> showSwitchDialog());
        deleteBtn.setOnClickListener(v -> showDeleteDialog(versions.listInstalled()));

        return sv;
    }

    // ---------------------------------------------------------------- 设置页

    private View buildSettingsPage() {
        ScrollView sv = pageContainer();
        LinearLayout col = columnOf(sv);

        col.addView(UiKit.header(this, "设置", "启动方式 / 下载源 / 数据清理"));

        // 启动方式
        LinearLayout modeCard = UiKit.card(this);
        modeCard.addView(UiKit.cardTitle(this, "服务端启动方式"));
        modeValue = UiKit.value(this, "");
        modeCard.addView(modeValue);
        modeCard.addView(UiKit.label(this,
                "普通模式：App 直接启动，依赖 targetSdk 28 的 SELinux 域豁免。\n"
                        + "Root 模式：通过 su 启动（进程落在 magisk/su 域），不改动 SELinux，"
                        + "不会被检测软件发现；普通模式若报 Permission denied 就用它。"));
        Button modeBtn = UiKit.button(this, "切换启动方式", false);
        modeCard.addView(UiKit.buttonRow(this, modeBtn));
        col.addView(modeCard);

        // 下载源
        LinearLayout srcCard = UiKit.card(this);
        srcCard.addView(UiKit.cardTitle(this, "下载源"));
        srcCard.addView(UiKit.label(this,
                "手机直连 GitHub 不通时改这里。API 地址需返回与 GitHub 相同的 JSON；"
                        + "镜像前缀会拼在 https://github.com/... 前面（例如 https://ghfast.top/）。"));

        apiInput = new EditText(this);
        apiInput.setHint("API 地址，如 https://api.github.com");
        apiInput.setText(UpdateClient.apiBase(this));
        styleInput(apiInput);
        srcCard.addView(apiInput);

        repoInput = new EditText(this);
        repoInput.setHint("仓库，如 owner/repo");
        repoInput.setText(UpdateClient.repo(this));
        styleInput(repoInput);
        srcCard.addView(repoInput);

        mirrorInput = new EditText(this);
        mirrorInput.setHint("下载镜像前缀（可留空）");
        mirrorInput.setText(Prefs.get(this, "download_mirror", ""));
        styleInput(mirrorInput);
        srcCard.addView(mirrorInput);

        Button saveSrcBtn = UiKit.button(this, "保存下载源", true);
        srcCard.addView(UiKit.buttonRow(this, saveSrcBtn));
        col.addView(srcCard);

        // 清理
        LinearLayout cleanCard = UiKit.card(this);
        cleanCard.addView(UiKit.cardTitle(this, "清理数据"));
        cleanCard.addView(UiKit.label(this,
                "可以分开清理：只清服务端程序、只清游戏数据（世界/配置/日志），或者全部清空。"));
        Button cleanVerBtn = UiKit.button(this, "清服务端版本", false);
        Button cleanDataBtn = UiKit.button(this, "清游戏数据", false);
        cleanCard.addView(UiKit.buttonRow(this, cleanVerBtn, cleanDataBtn));
        Button cleanAllBtn = UiKit.button(this, "全部清空", false);
        cleanCard.addView(UiKit.buttonRow(this, cleanAllBtn));
        col.addView(cleanCard);

        // 关于
        LinearLayout aboutCard = UiKit.card(this);
        aboutCard.addView(UiKit.cardTitle(this, "关于"));
        aboutCard.addView(UiKit.value(this,
                "壳版本 " + versionName() + "（不含服务端）\n"
                        + "服务端机器 " + UpdateClient.repo(this)));
        Button battBtn2 = UiKit.button(this, "电池优化设置", false);
        Button dirBtn = UiKit.button(this, "数据目录路径", false);
        aboutCard.addView(UiKit.buttonRow(this, battBtn2, dirBtn));
        col.addView(aboutCard);

        modeBtn.setOnClickListener(v -> showModeDialog());
        saveSrcBtn.setOnClickListener(v -> saveSource());
        cleanVerBtn.setOnClickListener(v -> confirm("确定删除所有已下载的服务端版本吗？游戏数据会保留。", new Runnable() {
            @Override
            public void run() {
                if (PumpkinServer.get().isRunning()) {
                    toast("请先停止服务端");
                    return;
                }
                versions.clearAllVersions();
                latest = null;
                toast("已清理服务端版本");
                refresh();
            }
        }));
        cleanDataBtn.setOnClickListener(v -> confirm("确定删除世界存档、配置和日志吗？此操作不可恢复。", new Runnable() {
            @Override
            public void run() {
                if (PumpkinServer.get().isRunning()) {
                    toast("请先停止服务端");
                    return;
                }
                VersionManager.clearServerData(MainActivity.this);
                toast("已清理游戏数据");
                refresh();
            }
        }));
        cleanAllBtn.setOnClickListener(v -> confirm("确定清空全部数据吗？包括所有服务端版本和世界存档，不可恢复。", new Runnable() {
            @Override
            public void run() {
                if (PumpkinServer.get().isRunning()) {
                    toast("请先停止服务端");
                    return;
                }
                versions.clearAllVersions();
                VersionManager.clearServerData(MainActivity.this);
                latest = null;
                toast("已全部清空");
                refresh();
            }
        }));
        battBtn2.setOnClickListener(v -> openBatterySettings());
        dirBtn.setOnClickListener(v -> copyWorkDir());

        return sv;
    }

    private void styleInput(EditText et) {
        et.setHintTextColor(UiKit.TEXT_DIM);
        et.setTextColor(UiKit.TEXT);
        et.setTextSize(13.5f);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setBackground(UiKit.glass(this, false));
        et.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 10),
                UiKit.dp(this, 14), UiKit.dp(this, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiKit.dp(this, 10);
        et.setLayoutParams(lp);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    // ---------------------------------------------------------------- 底部导航

    private View buildBottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackground(UiKit.glass(this, true));
        nav.setElevation(UiKit.dp(this, 10));
        int pv = UiKit.dp(this, 10);
        nav.setPadding(UiKit.dp(this, 6), pv, UiKit.dp(this, 6), pv);

        String[] icons = new String[]{"▶", "⤓", "⚙"};
        String[] labels = new String[]{"运行", "更新", "设置"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 6));

            TextView icon = new TextView(this);
            icon.setText(icons[i]);
            icon.setTextSize(18);
            icon.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            icon.setLayoutParams(ilp);
            tab.addView(icon);

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(11.5f);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = UiKit.dp(this, 3);
            label.setLayoutParams(tlp);
            tab.addView(label);

            tabIcons[i] = icon;
            tabLabels[i] = label;

            tab.setOnClickListener(v -> switchPage(index));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            nav.addView(tab, tp);
        }
        return nav;
    }

    private void switchPage(int index) {
        pageRun.setVisibility(index == PAGE_RUN ? View.VISIBLE : View.GONE);
        pageUpdate.setVisibility(index == PAGE_UPDATE ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(index == PAGE_SETTINGS ? View.VISIBLE : View.GONE);
        for (int i = 0; i < 3; i++) {
            boolean active = (i == index);
            tabIcons[i].setTextColor(active ? UiKit.ACCENT : UiKit.TEXT_DIM);
            tabLabels[i].setTextColor(active ? UiKit.ACCENT : UiKit.TEXT_DIM);
            tabLabels[i].setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
        if (index == PAGE_RUN) {
            refresh();
        }
    }

    // ================================================================ 状态刷新

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

        boolean rootMode = Prefs.getBool(this, "root_mode", false);
        if (modeValue != null) {
            modeValue.setText(rootMode ? "Root 模式（su，不改 SELinux）" : "普通模式（targetSdk 28 豁免）");
        }

        String cur = versions.currentTag();
        List<VersionManager.Installed> installed = versions.listInstalled();
        if (cur == null) {
            versionCurrent.setText("尚未安装服务端");
            installBtn.setText("下载最新版本");
        } else {
            versionCurrent.setText(cur);
            installBtn.setText(latest != null && !latest.tag.equals(cur)
                    ? "更新到 " + latest.tag : "重新下载当前版本");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("已安装 ").append(installed.size()).append(" 个版本");
        if (installed.size() > 1) {
            sb.append("（可回滚）");
        }
        sb.append("　占用 ").append(fmtSize(VersionManager.dirSize(versions.getVersionsDir())));
        sb.append("\n游戏数据 ").append(fmtSize(VersionManager.dirSize(ServerPaths.workDir(this))));
        versionInstalled.setText(sb.toString());

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

    // ================================================================ 运行页动作

    private void startServer() {
        requestNotificationPermissionIfNeeded();
        if (versions.currentTag() == null) {
            toast("还没有安装服务端，先到「更新」页下载");
            switchPage(PAGE_UPDATE);
            return;
        }
        Intent intent = new Intent(this, ServerService.class);
        intent.setAction(ServerService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        switchPage(PAGE_RUN);
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

    private void copyWorkDir() {
        String dir = ServerPaths.workDir(this).getAbsolutePath();
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("dir", dir));
            toast("已复制 " + dir);
        }
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

    // ================================================================ 设置页动作

    private void saveSource() {
        Prefs.put(this, "api_base", apiInput.getText().toString().trim());
        Prefs.put(this, "repo", repoInput.getText().toString().trim());
        Prefs.put(this, "download_mirror", mirrorInput.getText().toString().trim());
        toast("已保存下载源设置");
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

    // ================================================================ 版本管理

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
                            sb.append("最新 ").append(latest.tag);
                            if (latest.binarySize > 0) {
                                sb.append("　").append(fmtSize(latest.binarySize));
                            }
                            if (cur != null && cur.equals(latest.tag)) {
                                sb.append("\n已是最新版本");
                            } else if (cur != null) {
                                sb.append("\n可更新（当前 ").append(cur).append("）");
                            }
                            sb.append("\n共找到 ").append(list.size()).append(" 个可用版本");
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
                                    + "\n请到「设置」页确认下载源（手机直连 GitHub 常不通）");
                        }
                    });
                }
            }
        }, "check-update").start();
    }

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
                            versionHint.setText("已安装 " + release.tag + "，到「运行」页点启动即可");
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
                .show();
    }

    private void showDeleteDialog(final List<VersionManager.Installed> installed) {
        if (installed == null || installed.isEmpty()) {
            toast("还没有安装任何版本");
            return;
        }
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
