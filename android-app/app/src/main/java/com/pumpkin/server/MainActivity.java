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
import android.graphics.Insets;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.DecelerateInterpolator;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 轻壳主界面：底部悬浮玻璃导航栏 + 三个页面（运行 / 更新 / 设置）。
 * 壳本身不含服务端，联网从 Releases 下载、切换、回滚、清理版本。
 *
 * 界面已由 Java View 迁移到 Compose（见 ui 包），本类保留全部业务逻辑，
 * 并实现 {@link com.pumpkin.server.ui.PumpkinActions} 作为 Compose 的动作出口。
 */
public class MainActivity extends Activity implements com.pumpkin.server.ui.PumpkinActions {

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
    private View navIndicator;
    private ViewGroup navRow;
    private int currentPage = 0;
    private final TextView[] tabIcons = new TextView[3];
    private final TextView[] tabLabels = new TextView[3];
    private FrameLayout contentArea;
    private BlurBackdropView navBlur;
    private BlurBackdrop backdrop;
    /** 悬浮栏的液态玻璃背景：接收倾斜数据让高光流动。 */
    private GlassPanelDrawable navGlass;
    private TiltGlow tiltGlow;

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
    private TextView versionSelected;
    private TextView versionInstalled;
    private TextView versionHint;
    private ProgressBar progress;
    private Button installBtn;
    private Button checkBtn;
    private Button deleteTaskBtn;
    private TextView downloadHint;
    private TextView localCount;
    private View dotView;
    private Downloader downloader;
    private Downloader.Listener downloadListener;

    // 设置页
    private TextView modeValue;
    private EditText apiInput;
    private EditText repoInput;
    private EditText mirrorInput;

    private final List<UpdateClient.Release> available = new ArrayList<>();
    private UpdateClient.Release selected;
    private volatile boolean busy;
    private volatile boolean cancelRequested;

    /**
     * Compose UI 的可观察状态。
     *
     * 说明：这是「Java 业务 → Compose 界面」的唯一通道。业务方法本身一行没改，
     * 只是把原来 setText 的地方改成写这里，Compose 侧读状态自动重组。
     */
    private com.pumpkin.server.ui.PumpkinUiState state;

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
        installCrashHandler();
        applyEdgeToEdge();
        versions = new VersionManager(this);
        updates = new UpdateClient(this);
        downloader = new Downloader(this);
        state = new com.pumpkin.server.ui.PumpkinUiState();
        View root = buildUi();
        setContentView(root);
        applyInsets(root);
        switchPage(PAGE_RUN);
        requestNotificationPermissionIfNeeded();
        autoCheckForUpdate();
        ui.post(ticker);
    }

    /**
     * 崩溃兜底：把堆栈写到可直接取出的位置
     * （Android/data/com.pumpkin.server/files/last_crash.txt），
     * 否则这种「一打开就闪退」的问题没法定位。
     */
    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                try {
                    File dir = getExternalFilesDir(null);
                    if (dir == null) {
                        dir = getFilesDir();
                    }
                    File f = new File(dir, "last_crash.txt");
                    java.io.PrintWriter w = new java.io.PrintWriter(new java.io.FileWriter(f, false));
                    w.println("时间: " + new java.util.Date());
                    w.println("线程: " + thread.getName());
                    w.println("壳版本: " + versionName());
                    w.println("Android API: " + Build.VERSION.SDK_INT);
                    w.println("---- 堆栈 ----");
                    ex.printStackTrace(w);
                    w.close();
                } catch (Throwable ignored) {
                    // 写日志本身失败就算了
                }
                if (previous != null) {
                    previous.uncaughtException(thread, ex);
                }
            }
        });
    }

    /**
     * 沉浸式：让窗口内容铺到状态栏与导航栏下面，消除上下两条系统黑边。
     * 状态栏/导航栏设为透明，背景由我们自己的渐变负责。
     */
    private void applyEdgeToEdge() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        // 只用老的 setSystemUiVisibility：本应用 targetSdk=28，这套接口全程有效，
        // 且比 setDecorFitsSystemWindows / InsetsController 稳得多（新接口曾导致启动即崩）。
        // 不设 LIGHT_STATUS_BAR，即保持浅色图标，配深色背景。
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    /** 把系统栏占用的高度变成内边距，内容不会被状态栏或手势条挡住。 */
    private void applyInsets(final View root) {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top;
                int bottom;
                try {
                    // 一律使用旧接口，避免引用 API 30 才有的 Insets / WindowInsets.Type
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                } catch (Throwable t) {
                    // 任何异常都不要让界面崩掉，退化为无内边距
                    return insets;
                }
                // 只在值真正变化时改 padding：否则「改 padding → 重新分发 insets」会互相触发，
                // 在部分 ROM 上形成死循环导致启动即闪退。
                if (v.getPaddingTop() != top || v.getPaddingBottom() != bottom) {
                    v.setPadding(0, top, 0, bottom);
                }
                return insets;
            }
        });
        root.requestApplyInsets();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.removeCallbacks(ticker);
        ui.post(ticker);
        // 液态玻璃的高光随倾斜流动：回到前台才开传感器，退后台立刻注销
        if (tiltGlow == null && navGlass != null) {
            tiltGlow = TiltGlow.start(this, new TiltGlow.Listener() {
                @Override
                public void onTilt(float x, float y) {
                    if (navGlass != null) {
                        navGlass.setTilt(x, y);
                    }
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(ticker);
        if (tiltGlow != null) {
            tiltGlow.stop();
            tiltGlow = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelRequested = true;
        if (downloader != null && downloader.isRunning()) {
            downloader.pause();   // 退到后台时暂停，保留断点，下次可继续
        }
        if (backdrop != null) {
            backdrop.release();
        }
    }

    // ================================================================ 整体布局

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(UiKit.windowBackground(this));

        contentArea = new FrameLayout(this);
        // 关键：内容区**不设**底部内边距，让内容一直延伸到悬浮导航栏下面。
        // 否则导航栏背后是一片纯色留白，模糊纯色还是纯色，看着就跟不透明一样。
        contentArea.setBackground(UiKit.windowBackground(this));
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
        navBlur = new BlurBackdropView(this);
        // 关键：模糊层也必须圆角。它垫在悬浮栏后面，如果保持直角矩形，
        // 悬浮栏圆角「缺掉」的那四块就会露出方形模糊内容（看起来就是圆角坏了）。
        GradientDrawable blurShape = new GradientDrawable();
        blurShape.setColor(0x00000000);
        blurShape.setCornerRadius(UiKit.dp(this, 26));
        navBlur.setBackground(blurShape);
        navBlur.setClipToOutline(true);
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
        // 滚动过程中立刻刷新背后的毛玻璃，否则模糊层会滞留在上一帧内容上（观感就是"延迟"）。
        sv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override
            public void onScrollChange(View v, int scrollX, int scrollY,
                                       int oldScrollX, int oldScrollY) {
                if (backdrop != null) {
                    backdrop.refresh();
                }
            }
        });
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = UiKit.dp(this, 16);
        // 底部留出足够空间：内容可以滚到悬浮导航栏下面（这样才有东西可模糊），
        // 同时最后一项不会被挡住。
        col.setPadding(pad, UiKit.dp(this, 26), pad, UiKit.dp(this, 112));
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

        // 用哪个版本运行，属于「运行」的事，所以放在这一页
        Button switchBtn = UiKit.button(this, "选择运行版本", false);
        statusCard.addView(UiKit.buttonRow(this, switchBtn));
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
        switchBtn.setOnClickListener(v -> showSwitchDialog());
        sendBtn.setOnClickListener(v -> sendCommand());

        return sv;
    }

    // ---------------------------------------------------------------- 更新页

    private View buildUpdatePage() {
        ScrollView sv = pageContainer();
        LinearLayout col = columnOf(sv);

        col.addView(UiKit.header(this, "服务端版本", "检查 · 下载 · 清理"));

        // ==================== ① 版本信息 ====================
        LinearLayout infoCard = UiKit.card(this);
        infoCard.addView(UiKit.cardTitle(this, "当前版本"));

        versionCurrent = UiKit.value(this, "");
        versionCurrent.setTextSize(16);
        versionCurrent.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.topMargin = UiKit.dp(this, 10);
        versionCurrent.setLayoutParams(vp);
        infoCard.addView(versionCurrent);

        versionInstalled = UiKit.label(this, "");
        infoCard.addView(versionInstalled);

        versionSelected = UiKit.value(this, "");
        versionSelected.setTextSize(15);
        versionSelected.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = UiKit.dp(this, 12);
        versionSelected.setLayoutParams(slp);
        infoCard.addView(versionSelected);

        versionHint = UiKit.label(this, "");
        infoCard.addView(versionHint);

        checkBtn = UiKit.button(this, "检查更新", false);
        Button pickBtn = UiKit.button(this, "选择版本", false);
        infoCard.addView(UiKit.buttonRow(this, checkBtn, pickBtn));
        col.addView(infoCard);

        // ==================== ② 下载（独立一块） ====================
        LinearLayout dlCard = UiKit.card(this);
        dlCard.addView(UiKit.cardTitle(this, "下载"));

        downloadHint = UiKit.label(this,
                "点「下载」开始。下载中按钮会变成「暂停」，暂停后才能删除下载任务。");
        dlCard.addView(downloadHint);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setVisibility(View.GONE);
        progress.setProgressTintList(ColorStateList.valueOf(UiKit.ACCENT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 6));
        pp.topMargin = UiKit.dp(this, 12);
        progress.setLayoutParams(pp);
        dlCard.addView(progress);

        // 一个按钮承担三态：下载 → 暂停 → 继续
        installBtn = UiKit.button(this, "下载", true);
        installBtn.setEnabled(false);
        dlCard.addView(UiKit.buttonRow(this, installBtn));

        deleteTaskBtn = UiKit.button(this, "删除下载任务", false);
        deleteTaskBtn.setEnabled(false);
        dlCard.addView(UiKit.buttonRow(this, deleteTaskBtn));
        col.addView(dlCard);

        // ==================== ③ 本地版本（独立一块） ====================
        LinearLayout localCard = UiKit.card(this);
        localCard.addView(UiKit.cardTitle(this, "本地版本"));
        localCount = UiKit.label(this, "");
        localCard.addView(localCount);
        Button deleteBtn = UiKit.button(this, "删除已安装的版本", false);
        localCard.addView(UiKit.buttonRow(this, deleteBtn));
        col.addView(localCard);

        checkBtn.setOnClickListener(v -> doCheck());
        pickBtn.setOnClickListener(v -> showVersionPicker());
        installBtn.setOnClickListener(v -> onDownloadButtonClicked());
        deleteTaskBtn.setOnClickListener(v -> onDeleteTaskClicked());
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
                "普通模式开箱即用。若启动时报权限错误，切到 Root 模式再试。"));
        Button modeBtn = UiKit.button(this, "切换启动方式", false);
        modeCard.addView(UiKit.buttonRow(this, modeBtn));
        col.addView(modeCard);

        // 下载源
        LinearLayout srcCard = UiKit.card(this);
        srcCard.addView(UiKit.cardTitle(this, "下载源"));
        srcCard.addView(UiKit.label(this,
                "平时不用改。下载失败时会自动换源，这里可手动指定。"));

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
        Button cleanVerBtn = UiKit.button(this, "清服务端版本", false);
        Button cleanDataBtn = UiKit.button(this, "清游戏数据", false);
        cleanCard.addView(UiKit.buttonRow(this, cleanVerBtn, cleanDataBtn));
        Button cleanAllBtn = UiKit.button(this, "全部清空", false);
        cleanCard.addView(UiKit.buttonRow(this, cleanAllBtn));
        col.addView(cleanCard);

        // 关于
        LinearLayout aboutCard = UiKit.card(this);
        aboutCard.addView(UiKit.cardTitle(this, "关于"));
        aboutCard.addView(UiKit.value(this, "版本 " + versionName()));
        Button checkShellBtn = UiKit.button(this, "检查更新", false);
        aboutCard.addView(UiKit.buttonRow(this, checkShellBtn));
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
                selected = null;
                available.clear();
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
                selected = null;
                available.clear();
                toast("已全部清空");
                refresh();
            }
        }));
        battBtn2.setOnClickListener(v -> openBatterySettings());
        dirBtn.setOnClickListener(v -> copyWorkDir());
        checkShellBtn.setOnClickListener(v -> checkShellUpdate());

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
        FrameLayout nav = new FrameLayout(this);
        navGlass = UiKit.glassPanel(this, true, true);
        nav.setBackground(navGlass);
        nav.setElevation(UiKit.dp(this, 10));

        // 选中指示器：垫在三个 tab 下面，切换页面时平滑滑过去（而不是瞬间跳）
        navIndicator = new View(this);
        navIndicator.setBackground(UiKit.navPill(this, true));
        navIndicator.setElevation(UiKit.dp(this, 2));
        nav.addView(navIndicator, new FrameLayout.LayoutParams(
                UiKit.dp(this, 64), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        navRow = row;

        String[] icons = new String[]{"▶", "⤓", "⚙"};
        String[] labels = new String[]{"运行", "更新", "设置"};
        int px = UiKit.dp(this, 8);
        int py = UiKit.dp(this, 9);
        for (int i = 0; i < 3; i++) {
            final int index = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(px, py, px, py);

            TextView icon = new TextView(this);
            icon.setText(icons[i]);
            icon.setTextSize(18);
            icon.setGravity(Gravity.CENTER);

            // 图标右上角挂一个小红点：有新版本时才亮，平时隐藏，不占地方也不打扰
            FrameLayout iconBox = new FrameLayout(this);
            iconBox.addView(icon, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            if (i == 1) {
                dotView = new View(this);
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(0xFFFF5C6C);
                dot.setStroke(UiKit.dp(this, 1.5f), 0xCC141824);
                dotView.setBackground(dot);
                dotView.setVisibility(View.GONE);
                iconBox.addView(dotView, new FrameLayout.LayoutParams(
                        UiKit.dp(this, 9), UiKit.dp(this, 9), Gravity.TOP | Gravity.END));
            }
            tab.addView(iconBox, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(11.5f);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = UiKit.dp(this, 3);
            tab.addView(label, tlp);

            tabIcons[i] = icon;
            tabLabels[i] = label;

            tab.setOnClickListener(v -> switchPage(index));
            row.addView(tab, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        nav.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return nav;
    }

    /** 把选中指示器移到第 index 个 tab 上；宽高按 tab 实际尺寸算。 */
    private void moveIndicator(final int index, final boolean animate) {
        if (navIndicator == null || navRow == null) {
            return;
        }
        if (navRow.getWidth() <= 0) {
            navRow.post(new Runnable() {
                @Override
                public void run() {
                    moveIndicator(index, false);
                }
            });
            return;
        }
        float tabWidth = navRow.getWidth() / 3f;
        // 小胶囊，只罩住图标那一行（SukiSU / Material 3 的做法）：
        // 太宽会变成一块椭圆药丸，所以收紧到接近图标尺寸。
        int pillW = Math.max(UiKit.dp(this, 38),
                Math.min((int) (tabWidth * 0.56f), UiKit.dp(this, 52)));
        int pillH = UiKit.dp(this, 32);
        int topMargin = UiKit.dp(this, 6);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) navIndicator.getLayoutParams();
        if (lp.width != pillW || lp.height != pillH) {
            lp.width = pillW;
            lp.height = pillH;
            lp.topMargin = topMargin;
            navIndicator.setLayoutParams(lp);
        }
        float targetX = index * tabWidth + (tabWidth - pillW) / 2f;
        navIndicator.animate().cancel();
        if (animate) {
            navIndicator.animate()
                    .translationX(targetX)
                    .setDuration(260)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            navIndicator.setTranslationX(targetX);
        }
    }

    private void switchPage(int index) {
        pageRun.setVisibility(index == PAGE_RUN ? View.VISIBLE : View.GONE);
        pageUpdate.setVisibility(index == PAGE_UPDATE ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(index == PAGE_SETTINGS ? View.VISIBLE : View.GONE);
        for (int i = 0; i < 3; i++) {
            boolean active = (i == index);
            // 文字/图标：选中用白色加粗，未选中用暗色；指示器本身负责“选中块”
            tabIcons[i].setTextColor(active ? Color.WHITE : UiKit.TEXT_DIM);
            tabLabels[i].setTextColor(active ? Color.WHITE : UiKit.TEXT_DIM);
            tabLabels[i].setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            tabIcons[i].animate()
                    .scaleX(active ? 1.15f : 1f)
                    .scaleY(active ? 1.15f : 1f)
                    .setDuration(180)
                    .start();
        }
        moveIndicator(index, true);
        if (index == PAGE_UPDATE) {
            updateUpdateDot(false);   // 进过更新页就把提醒收起来
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
        versionCurrent.setText(cur == null ? "尚未安装服务端" : cur);
        if (versionSelected != null) {
            versionSelected.setText(selected == null
                    ? "未选择要下载的版本"
                    : "选中：" + selected.tag + "　" + fmtSize(selected.binarySize));
        }
        // 下载相关的按钮统一交给状态机刷新：这里如果再单独设一次，
        // 就会每秒把「暂停 / 继续」覆盖回「下载」，造成按钮来回跳。
        updateDownloadButtons();
        StringBuilder sb = new StringBuilder();
        sb.append("已安装 ").append(installed.size()).append(" 个版本");
        if (installed.size() > 1) {
            sb.append("（可回滚）");
        }
        sb.append("　占用 ").append(fmtSize(VersionManager.dirSize(versions.getVersionsDir())));
        sb.append("\n游戏数据 ").append(fmtSize(VersionManager.dirSize(ServerPaths.workDir(this))));
        versionInstalled.setText(sb.toString());
        if (localCount != null) {
            localCount.setText(installed.isEmpty()
                    ? "还没有安装任何版本"
                    : "已安装 " + installed.size() + " 个，可单独删除其中一个");
        }

        String text = server.tailLog();
        String display = text.isEmpty() ? "（还没有日志）\n启动服务器后这里会实时输出" : text;
        if (!display.contentEquals(logView.getText())) {
            logView.setTextColor(text.isEmpty() ? UiKit.TEXT_DIM : 0xFFCFD6E4);
            logView.setText(display);
            logScroll.post(new Runnable() {
                @Override
                public void run() {
                    logScroll.fullScroll(View.FOCUS_DOWN);
                }
            });
        }

        syncState();
    }

    // ================================================================ Compose 状态桥接

    /**
     * 把 Java 侧的业务状态推给 Compose 状态对象。
     *
     * 调用时机与 refresh() 一致（每秒一次 + 各动作后）。这里只读不写业务，
     * 所以即使 Compose 界面还没启用也不会有副作用。
     */
    private void syncState() {
        com.pumpkin.server.ui.PumpkinUiState s = state;
        if (s == null) {
            return;
        }
        PumpkinServer server = PumpkinServer.get();

        s.setRunning(server.isRunning());
        s.setHasExitCode(server.getExitCode() != Integer.MIN_VALUE);
        s.setStatusText(statusText.getText().toString());

        String lan = PumpkinServer.findLanIpv4();
        s.setAddrText(lan == null
                ? "未检测到局域网 IP（确认已连上 WiFi）"
                : "Java " + lan + ":25565　·　基岩 " + lan + ":19132");
        s.setDirText("数据目录 " + ServerPaths.workDir(this).getAbsolutePath());

        boolean rootMode = Prefs.getBool(this, "root_mode", false);
        s.setModeValue(rootMode
                ? "Root 模式（su，不改 SELinux）"
                : "普通模式（targetSdk 28 豁免）");

        String cur = versions.currentTag();
        List<VersionManager.Installed> installed = versions.listInstalled();
        s.setVersionCurrent(cur == null ? "尚未安装服务端" : cur);
        s.setVersionSelected(selected == null
                ? "未选择要下载的版本"
                : "选中：" + selected.tag + "　" + fmtSize(selected.binarySize));
        s.setVersionHint(versionHint.getText().toString());
        s.setVersionInstalled(versionInstalled.getText().toString());
        s.setLocalCount(localCount == null ? "" : localCount.getText().toString());

        s.getInstalledTags().clear();
        for (VersionManager.Installed v : installed) {
            s.getInstalledTags().add(v.tag);
        }

        // 日志整段同步：Compose 侧只做展示，不做增量 diff，避免两边状态不一致。
        s.setLogText(PumpkinServer.get().tailLog());

        // 下载相关三态
        Downloader.State st = downloader.getState();
        s.setInstallButtonText(installBtn.getText().toString());
        s.setInstallButtonEnabled(installBtn.isEnabled());
        s.setDeleteTaskEnabled(deleteTaskBtn.isEnabled());
        s.setCheckEnabled(checkBtn.isEnabled());
        s.setDownloadProgressVisible(progress.getVisibility() == View.VISIBLE);
        s.setDownloadProgress(progress.getProgress() / (float) progress.getMax());
        s.setDownloadHint(downloadHint.getText().toString());

        s.setShellVersion(versionName());
        s.setShowUpdateDot(dotView != null && dotView.getVisibility() == View.VISIBLE);

        // 设置页输入框：只在用户没在编辑时回填，否则会打断输入。
        if (!apiInput.hasFocus()) {
            s.setApiBase(apiInput.getText().toString());
        }
        if (!repoInput.hasFocus()) {
            s.setRepo(repoInput.getText().toString());
        }
        if (!mirrorInput.hasFocus()) {
            s.setMirror(mirrorInput.getText().toString());
        }
    }

    // ---- 供 Compose 调用的桥接方法（都只是转调原有私有方法，业务逻辑不变） ----

    /** 底部导航点了第 index 项。 */
    public void onNavItemSelected(int index) {
        if (index < 0 || index > PAGE_SETTINGS) {
            return;
        }
        if (index == PAGE_SETTINGS || index == PAGE_UPDATE || index == PAGE_RUN) {
            switchPage(index);
        }
    }

    public void startServerFromUi() {
        startServer();
    }

    public void stopServerFromUi() {
        stopServer();
    }

    public void openBatterySettingsFromUi() {
        openBatterySettings();
    }

    public void copyAddressFromUi() {
        copyAddress();
    }

    public void copyWorkDirFromUi() {
        copyWorkDir();
    }

    public void showSwitchDialogFromUi() {
        showSwitchDialog();
    }

    public void showModeDialogFromUi() {
        showModeDialog();
    }

    public void sendCommandFromUi(String cmd) {
        if (cmd == null || cmd.trim().isEmpty()) {
            return;
        }
        PumpkinServer.get().sendCommand(cmd);
        if (state != null) {
            state.setCommand("");
        }
        refresh();
    }

    public void doCheckFromUi() {
        doCheck();
    }

    public void showVersionPickerFromUi() {
        showVersionPicker();
    }

    public void onDownloadButtonClickedFromUi() {
        onDownloadButtonClicked();
    }

    public void onDeleteTaskClickedFromUi() {
        onDeleteTaskClicked();
    }

    public void showDeleteDialogFromUi() {
        showDeleteDialog(versions.listInstalled());
    }

    public void checkShellUpdateFromUi() {
        checkShellUpdate();
    }

    /** 保存下载源。参数来自 Compose 侧的输入框（原方法读 EditText，这里改为显式传参）。 */
    public void saveSourceFromUi(String api, String repo, String mirror) {
        Prefs.put(this, "api_base", api == null ? "" : api.trim());
        Prefs.put(this, "repo", repo == null ? "" : repo.trim());
        Prefs.put(this, "download_mirror", mirror == null ? "" : mirror.trim());
        toast("已保存下载源设置");
        refresh();
    }

    /**
     * 设置页点选某个加速源：写入镜像前缀并同步到输入框。
     *
     * @param prefix 加速前缀，空串表示直连。
     */
    public void applyMirrorFromUi(String prefix) {
        String p = prefix == null ? "" : prefix.trim();
        Prefs.put(this, "download_mirror", p);
        // 用户手动选了源，就把「上次成功记住的源」清掉，
        // 否则它会排在手动选择前面，造成「选了不生效」的错觉。
        Prefs.put(this, "good_prefix", "");
        if (mirrorInput != null) {
            mirrorInput.setText(p);
        }
        if (state != null) {
            state.setMirror(p);
        }
        toast(p.isEmpty() ? "已改为直连（不加速）" : "已选用加速源：" + p);
        refresh();
    }

    public void clearVersionsFromUi() {
        confirm("确定删除所有已下载的服务端版本吗？游戏数据会保留。", new Runnable() {
            @Override
            public void run() {
                if (PumpkinServer.get().isRunning()) {
                    toast("请先停止服务端");
                    return;
                }
                versions.clearAllVersions();
                selected = null;
                available.clear();
                toast("已清理服务端版本");
                refresh();
            }
        });
    }

    public void clearGameDataFromUi() {
        confirm("确定删除世界存档、配置和日志吗？此操作不可恢复。", new Runnable() {
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
        });
    }

    public void clearAllFromUi() {
        confirm("确定清空全部数据吗？包括所有服务端版本和世界存档，不可恢复。", new Runnable() {
            @Override
            public void run() {
                if (PumpkinServer.get().isRunning()) {
                    toast("请先停止服务端");
                    return;
                }
                versions.clearAllVersions();
                VersionManager.clearServerData(MainActivity.this);
                selected = null;
                available.clear();
                toast("已全部清空");
                refresh();
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

    /** 根据当前选中的版本刷新提示文案。 */
    private void updateVersionHint() {
        if (versionSelected != null) {
            versionSelected.setText(selected == null
                    ? "未选择要下载的版本"
                    : "选中：" + selected.tag + "　" + fmtSize(selected.binarySize));
        }
        if (selected == null) {
            versionHint.setText("点「检查更新」获取可用版本列表");
            return;
        }
        String cur = versions.currentTag();
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(available.size())
                .append(" 个可用版本，默认选最新的；点「选择版本」可换");
        if (cur != null && cur.equals(selected.tag)) {
            sb.append("\n（这就是当前运行的版本）");
        } else if (versions.isInstalled(selected.tag)) {
            sb.append("\n（该版本已下载，到「运行」页可切换启用）");
        }
        versionHint.setText(sb.toString());
    }

    /** 选择要下载的版本（默认最新）。 */
    private void showVersionPicker() {
        if (available.isEmpty()) {
            toast("先点「检查更新」");
            return;
        }
        final String cur = versions.currentTag();
        final String[] items = new String[available.size()];
        for (int i = 0; i < available.size(); i++) {
            UpdateClient.Release r = available.get(i);
            StringBuilder sb = new StringBuilder();
            sb.append(r.tag.equals(cur) ? "● " : "○ ").append(r.tag);
            if (r.binarySize > 0) {
                sb.append("　").append(fmtSize(r.binarySize));
            }
            if (versions.isInstalled(r.tag)) {
                sb.append("　[已下载]");
            }
            items[i] = sb.toString();
        }
        new AlertDialog.Builder(this)
                .setTitle("选择要下载的版本")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        selected = available.get(which);
                        updateVersionHint();
                        refresh();
                        toast("已选中 " + selected.tag);
                    }
                })
                .show();
    }

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
                            available.clear();
                            available.addAll(list);
                            selected = list.get(0);   // 默认选中最新
                            installBtn.setEnabled(true);
                            updateVersionHint();
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
                                    + "\n可在「设置」页指定下载源");
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
        if (selected == null) {
            doCheck();
            toast("先点「检查更新」，再用「选择版本」挑一个");
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
                                    doDownloadInstall(selected);
                                }
                            }, 1200);
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        doDownloadInstall(selected);
    }

    private void doDownloadInstall(final UpdateClient.Release release) {
        if (downloader.isRunning()) {
            toast("正在下载中");
            return;
        }
        busy = true;
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        downloadHint.setText("正在下载 " + release.tag + " …");

        downloadListener = new Downloader.Listener() {
            @Override
            public void onProgress(long done, long total) {
                if (total > 0) {
                    progress.setProgress((int) (done * 1000 / total));
                    downloadHint.setText("下载中 " + fmtSize(done) + " / " + fmtSize(total)
                            + "（" + (done * 100 / total) + "%）");
                } else {
                    downloadHint.setText("下载中 " + fmtSize(done));
                }
                updateDownloadButtons();
            }

            @Override
            public void onSourceFailed(String url, String reason) {
                String shortUrl = url.length() > 52 ? url.substring(0, 52) + "…" : url;
                downloadHint.setText("这个下载源不可用，正在自动换源…\n" + shortUrl);
            }

            @Override
            public void onFinished(File file) {
                try {
                    versions.install(release.tag, file);
                    finishBusy();
                    downloadHint.setText("已安装 " + release.tag + "，到「运行」页启动或切换");
                    toast("下载完成");
                    refresh();
                } catch (Exception e) {
                    finishBusy();
                    versionHint.setText("安装失败：" + e.getMessage());
                }
            }

            @Override
            public void onPaused(long done, long total) {
                busy = false;
                downloadHint.setText("已暂停 " + fmtSize(done)
                        + (total > 0 ? " / " + fmtSize(total) : "")
                        + "\n点「继续」接着下，或点「删除下载任务」丢弃");
                updateDownloadButtons();
            }

            @Override
            public void onFailed(String message) {
                finishBusy();
                downloadHint.setText("下载失败：" + message);
            }
        };
        downloader.start(release, downloadListener);
        updateDownloadButtons();
    }

    /** 下载按钮：一个按钮承担三态 —— 下载 → 暂停 → 继续。 */
    private void onDownloadButtonClicked() {
        Downloader.State st = downloader.getState();
        if (st == Downloader.State.RUNNING) {
            downloader.pause();
            downloadHint.setText("正在暂停…");
            return;
        }
        if (st == Downloader.State.PAUSED) {
            if (downloadListener == null) {
                return;
            }
            downloader.start(null, downloadListener);
            progress.setVisibility(View.VISIBLE);
            downloadHint.setText("正在继续下载…");
            updateDownloadButtons();
            return;
        }
        doInstallOrStop();
    }

    /** 删除下载任务：只有暂停状态下才允许，且会丢弃已下载的全部文件。 */
    private void onDeleteTaskClicked() {
        if (downloader.getState() != Downloader.State.PAUSED) {
            toast("请先暂停下载，再删除任务");
            return;
        }
        confirm("删除下载任务会丢弃已经下载的部分，确定吗？", new Runnable() {
            @Override
            public void run() {
                downloader.deleteTask();
                busy = false;
                progress.setProgress(0);
                progress.setVisibility(View.GONE);
                downloadHint.setText("下载任务已删除，已下载的文件已清理。");
                updateDownloadButtons();
                refresh();
            }
        });
    }

    /**
     * 界面按钮完全由 Downloader 的状态推导（单一状态源，别处不要再改这些按钮文字）：
     *   IDLE    → 「下载」，删除任务不可点
     *   RUNNING → 「暂停」，删除任务不可点（必须先暂停）
     *   PAUSED  → 「继续」，删除任务可点
     */
    private void updateDownloadButtons() {
        Downloader.State st = downloader.getState();
        if (st == Downloader.State.RUNNING) {
            installBtn.setText("暂停");
            installBtn.setEnabled(true);
        } else if (st == Downloader.State.PAUSED) {
            installBtn.setText("继续");
            installBtn.setEnabled(true);
        } else {
            installBtn.setText(selected != null && versions.isInstalled(selected.tag)
                    ? "重新下载" : "下载");
            installBtn.setEnabled(selected != null);
        }
        deleteTaskBtn.setEnabled(st == Downloader.State.PAUSED);
        checkBtn.setEnabled(st != Downloader.State.RUNNING);
    }

    private void finishBusy() {
        busy = false;
        progress.setVisibility(View.GONE);
        updateDownloadButtons();
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

    /** 启动时静默检查一次更新；有新版本就在「更新」角标上点个红点。 */
    private void autoCheckForUpdate() {
        updateUpdateDot(Prefs.getBool(this, "has_newer", false));
        long now = System.currentTimeMillis();
        long last = 0;
        try {
            last = Long.parseLong(Prefs.get(this, "last_auto_check", "0"));
        } catch (Exception ignored) {
            last = 0;
        }
        if (now - last < 1800000L) {
            // 半小时内不重复请求，避开 GitHub 的匿名限流
            return;
        }
        Prefs.put(this, "last_auto_check", String.valueOf(now));
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<UpdateClient.Release> list = updates.fetchReleases();
                    final boolean newer = !list.isEmpty() && !versions.isInstalled(list.get(0).tag);
                    Prefs.putBool(MainActivity.this, "has_newer", newer);
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (available.isEmpty()) {
                                available.addAll(list);
                                if (selected == null && !list.isEmpty()) {
                                    selected = list.get(0);
                                }
                                updateVersionHint();
                                refresh();
                            }
                            updateUpdateDot(newer);
                        }
                    });
                } catch (Exception ignored) {
                    // 静默失败，不打扰用户
                }
            }
        }, "auto-check").start();
    }

    private void updateUpdateDot(boolean show) {
        if (dotView != null) {
            dotView.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /** 检查本应用（南瓜坞）自身有没有新版本。 */
    private void checkShellUpdate() {
        toast("正在检查更新…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final UpdateClient.ShellAsset asset = updates.fetchShellAsset();
                    long installed = 0;
                    try {
                        installed = getPackageManager()
                                .getPackageInfo(getPackageName(), 0).lastUpdateTime;
                    } catch (Exception ignored) {
                        installed = 0;
                    }
                    final long installedTime = installed;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (asset == null || asset.downloadUrl == null) {
                                toast("没有找到壳的发布信息");
                                return;
                            }
                            if (asset.updatedAt > installedTime + 120000L) {
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("壳有新版本")
                                        .setMessage("服务器上的壳比你当前装的更新，要下载吗？")
                                        .setPositiveButton("下载", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d, int w) {
                                                openUrl(asset.downloadUrl);
                                            }
                                        })
                                        .setNegativeButton("以后", null)
                                        .show();
                            } else {
                                toast("壳已是最新");
                            }
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            toast("检查失败：" + e.getMessage());
                        }
                    });
                }
            }
        }, "shell-check").start();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("url", url));
            }
            toast("无法打开浏览器，链接已复制");
        }
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
