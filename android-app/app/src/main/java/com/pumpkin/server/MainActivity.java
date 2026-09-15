package com.pumpkin.server;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.activity.ComponentActivity;

import com.pumpkin.server.ui.PumpkinDialogItem;
import com.pumpkin.server.ui.PumpkinDialogs;

/**
 * 南瓜坞主界面：底部悬浮玻璃导航栏 + 三个页面（运行 / 更新 / 设置）。
 * 南瓜坞本身不含服务端，联网从 Releases 下载、切换、回滚、清理版本。
 *
 * 界面已由 Java View 迁移到 Compose（见 ui 包），本类保留全部业务逻辑，
 * 并实现 {@link com.pumpkin.server.ui.PumpkinActions} 作为 Compose 的动作出口。
 */
public class MainActivity extends ComponentActivity implements com.pumpkin.server.ui.PumpkinActions {

    private static final int REQ_NOTIFICATIONS = 1;
    private static final int PAGE_RUN = 0;
    private static final int PAGE_UPDATE = 1;
    private static final int PAGE_SETTINGS = 2;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private VersionManager versions;
    private UpdateClient updates;

    /**
     * Compose UI 的可观察状态。
     *
     * 这是「Java 业务 → Compose 界面」的唯一通道：原来 setText 的地方改成写这里，
     * Compose 侧读状态自动重组。界面本身全在 ui 包里，本类不再持有任何 View。
     */
    private com.pumpkin.server.ui.PumpkinUiState state;

    // 更新页
    private Downloader downloader;
    private Downloader.Listener downloadListener;

    private final List<UpdateClient.Release> available = new ArrayList<>();
    private UpdateClient.Release selected;
    private volatile boolean busy;
    private volatile boolean cancelRequested;

    // ---------------------------------------------------------------- 对话框挂起状态
    //
    // miuix 对话框是异步的（用户点了才回调），所以要弹出的那一刻先在这里存下「确定后干什么」。
    // 同一时刻只会有一个对话框，所以每个用途一个字段就够，不需要队列。

    /** confirm() 要执行的动作，点「确定」时跑。 */
    private Runnable pendingConfirm;

    /** 应用更新对话框里要下载的地址，点「下载」时打开。 */
    private String pendingAppUrl;

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
        installCrashHandler();
        applyEdgeToEdge();
        versions = new VersionManager(this);
        updates = new UpdateClient(this);
        downloader = new Downloader(this);
        state = new com.pumpkin.server.ui.PumpkinUiState();
        // 界面交给 Compose：三页 + 液态玻璃底栏都在 ui 包里。
        // setContent 必须由 Kotlin 侧调用（@Composable lambda 带 $composer 参数，Java 造不出来）。
        com.pumpkin.server.ui.PumpkinUiBridge.launchPumpkinUi(this, state, this);
        // insets 仍由这里统一处理（沿用原来那套 legacy systemUiVisibility + setPadding），
        // 不引入 API 30 的新接口 —— 新接口历史上会导致本应用启动即崩。
        View content = findViewById(android.R.id.content);
        if (content != null) {
            applyInsets(content);
        }
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
                    w.println("南瓜坞版本: " + versionName());
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
        // 倾斜高光由 miuix 的 rememberDeviceTilt 在 Compose 侧自理（见 LiquidGlassNavBar），
        // 这里不再需要自己开关传感器。
        refresh();
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
        if (downloader != null && downloader.isRunning()) {
            downloader.pause();   // 退到后台时暂停，保留断点，下次可继续
        }
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    // ================================================================ 状态刷新

    private void refresh() {
        com.pumpkin.server.ui.PumpkinUiState s = state;
        if (s == null) {
            return;
        }
        PumpkinServer server = PumpkinServer.get();
        boolean running = server.isRunning();

        s.setRunning(running);
        s.setHasExitCode(server.getExitCode() != Integer.MIN_VALUE);
        if (running) {
            long secs = Math.max(0, (System.currentTimeMillis() - server.getStartedAt()) / 1000);
            s.setStatusText("运行中 " + (secs / 60) + ":" + String.format("%02d", secs % 60));
        } else if (server.getExitCode() != Integer.MIN_VALUE) {
            s.setStatusText("已停止（退出码 " + server.getExitCode() + "）");
        } else {
            s.setStatusText("未运行");
        }

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
        s.setVersionInstalled(sb.toString());
        s.setLocalCount(installed.isEmpty()
                ? "还没有安装任何版本"
                : "已安装 " + installed.size() + " 个，可单独删除其中一个");

        s.getInstalledTags().clear();
        for (VersionManager.Installed v : installed) {
            s.getInstalledTags().add(v.tag);
        }

        // 日志整段同步：Compose 侧只做展示，不做增量 diff，避免两边状态不一致。
        s.setLogText(server.tailLog());

        s.setAppVersion(versionName());
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

    public void checkAppUpdateFromUi() {
        checkAppUpdate();
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

    // ================================================================ 对话框回调
    //
    // miuix 对话框只负责「画」，点完之后回到这里按 kind 分派。
    // kind 的含义见 PumpkinDialogs；新增对话框时这里要加一个 case。

    @Override
    public void onDialogItemFromUi(int kind, String tag) {
        state.dismissDialog();
        switch (kind) {
            case PumpkinDialogs.START_MODE:
                applyStartMode(tag);
                break;
            case PumpkinDialogs.PICK_DOWNLOAD:
                applyVersionSelection(tag);
                break;
            case PumpkinDialogs.PICK_RUN:
                applyRunVersion(tag);
                break;
            case PumpkinDialogs.PICK_DELETE:
                applyDeleteVersion(tag);
                break;
            default:
                // 确认型对话框不该产生 item 回调；列表型遇到未知 kind 说明有分支漏了。
                toast("未处理的对话框操作（kind=" + kind + "）");
                break;
        }
    }

    @Override
    public void onDialogPositiveFromUi(int kind) {
        state.dismissDialog();
        switch (kind) {
            case PumpkinDialogs.NEED_STOP:
                stopThenDownload();
                break;
            case PumpkinDialogs.APP_UPDATE:
                if (pendingAppUrl != null) {
                    openUrl(pendingAppUrl);
                    pendingAppUrl = null;
                }
                break;
            case PumpkinDialogs.CONFIRM:
                Runnable action = pendingConfirm;
                pendingConfirm = null;
                if (action != null) {
                    action.run();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onDialogDismissedFromUi(int kind) {
        state.dismissDialog();
        // 取消时把挂起状态清掉，否则下次弹同一个对话框会跑到上一次残留的动作上。
        switch (kind) {
            case PumpkinDialogs.CONFIRM:
                pendingConfirm = null;
                break;
            case PumpkinDialogs.APP_UPDATE:
                pendingAppUrl = null;
                break;
            default:
                break;
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
        if (state == null) {
            return;
        }
        String cmd = state.getCommand();
        if (cmd == null || cmd.trim().isEmpty()) {
            return;
        }
        PumpkinServer.get().sendCommand(cmd);
        state.setCommand("");
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

    /** 保存下载源（输入值来自 Compose 侧状态，原方法读 EditText）。 */
    private void saveSource() {
        if (state == null) {
            return;
        }
        Prefs.put(this, "api_base", state.getApiBase().trim());
        Prefs.put(this, "repo", state.getRepo().trim());
        Prefs.put(this, "download_mirror", state.getMirror().trim());
        toast("已保存下载源设置");
    }

    private void setCheckEnabled(boolean enabled) {
        if (state != null) {
            state.setCheckEnabled(enabled);
        }
    }

    private void setInstallEnabled(boolean enabled) {
        if (state != null) {
            state.setInstallButtonEnabled(enabled);
        }
    }

    private void showModeDialog() {
        // miuix 风格对话框：这里只负责填「内容」，点击回到 onDialogItemFromUi 按 kind 分派。
        // 原来用 AlertDialog.setItems 的写法整个删掉了 —— 系统原生样式与 miuix 主界面不搭。
        boolean rootMode = Prefs.getBool(this, "root_mode", false);
        List<PumpkinDialogItem> items = new ArrayList<>();
        items.add(new PumpkinDialogItem(
                "普通模式",
                "App 直接启动（依赖 targetSdk 28 的 SELinux 豁免）",
                !rootMode, true, "normal"));
        items.add(new PumpkinDialogItem(
                "Root 模式",
                "通过 su 启动（不动 SELinux，不会被检测到）",
                rootMode, true, "root"));
        state.showListDialog(
                PumpkinDialogs.START_MODE,
                "选择服务端启动方式",
                "普通模式开箱即用。Root 模式通过 su 域运行，不受「私有目录禁止执行」的限制，"
                        + "适合普通模式失效时使用（需要 Magisk 授权，首次会弹窗）。",
                items);
    }

    /** 启动方式对话框选中了一项。tag 为 "normal" 或 "root"。 */
    private void applyStartMode(String tag) {
        if ("normal".equals(tag)) {
            Prefs.putBool(this, "root_mode", false);
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

    // ================================================================ 版本管理

    /** 根据当前选中的版本刷新提示文案。 */
    private void updateVersionHint() {
        com.pumpkin.server.ui.PumpkinUiState s = state;
        if (s == null) {
            return;
        }
        s.setVersionSelected(selected == null
                ? "未选择要下载的版本"
                : "选中：" + selected.tag + "　" + fmtSize(selected.binarySize));
        if (selected == null) {
            s.setVersionHint("点「检查更新」获取可用版本列表");
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
        s.setVersionHint(sb.toString());
    }

    /** 选择要下载的版本（默认最新）。 */
    private void showVersionPicker() {
        if (available.isEmpty()) {
            toast("先点「检查更新」");
            return;
        }
        String cur = versions.currentTag();
        String selectedTag = selected == null ? null : selected.tag;
        List<PumpkinDialogItem> items = new ArrayList<>();
        for (UpdateClient.Release r : available) {
            // 语义分工要清楚，否则用户分不清勾代表什么：
            //   勾      = 「将要下载的这个」（也就是当前选择，点击会改它）
            //   副文案  = 客观状态（多大、是否已下载、是不是正在跑的版本）
            // 之前用「● 已运行 / ○ 未运行」前缀，把「已运行」和「已选择」混在一个符号里，
            // 用户看不出版本列表里的记号到底指哪个。
            StringBuilder sum = new StringBuilder();
            if (r.binarySize > 0) {
                sum.append(fmtSize(r.binarySize));
            }
            if (r.tag.equals(cur)) {
                appendWithSep(sum, "正在运行");
            }
            if (versions.isInstalled(r.tag)) {
                appendWithSep(sum, "已下载");
            }
            items.add(new PumpkinDialogItem(
                    r.tag,
                    sum.length() == 0 ? null : sum.toString(),
                    r.tag.equals(selectedTag),
                    true,
                    r.tag));
        }
        state.showListDialog(
                PumpkinDialogs.PICK_DOWNLOAD,
                "选择要下载的版本",
                null,
                items);
    }

    /** 往副文案里追加一段，自动补分隔符。 */
    private static void appendWithSep(StringBuilder sb, String part) {
        if (sb.length() > 0) {
            sb.append("　·　");
        }
        sb.append(part);
    }

    /** 下载版本列表里选了一项。 */
    private void applyVersionSelection(String tag) {
        for (UpdateClient.Release r : available) {
            if (r.tag.equals(tag)) {
                selected = r;
                updateVersionHint();
                refresh();
                toast("已选中 " + r.tag);
                return;
            }
        }
    }

    private void doCheck() {
        if (busy) {
            toast("正在忙，请稍候");
            return;
        }
        busy = true;
        setCheckEnabled(false);
        setVersionHint("正在检查 " + UpdateClient.repo(this) + " 的 Releases …");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<UpdateClient.Release> list = updates.fetchReleases();
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            busy = false;
                            setCheckEnabled(true);
                            if (list.isEmpty()) {
                                setVersionHint("没有找到带 Android 二进制的 Release");
                                setInstallEnabled(false);
                                return;
                            }
                            available.clear();
                            available.addAll(list);
                            selected = list.get(0);   // 默认选中最新
                            setInstallEnabled(true);
                            updateVersionHint();
                            refresh();
                        }
                    });
                } catch (final Exception e) {
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            busy = false;
                            setCheckEnabled(true);
                            setVersionHint("检查失败：" + e.getMessage()
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
            state.showConfirmDialog(
                    PumpkinDialogs.NEED_STOP,
                    "需要先停止服务端",
                    "更新会替换服务端程序文件，需要先停止正在运行的服务器。要现在停止并继续更新吗？",
                    "停止并更新",
                    "取消");
            return;
        }
        doDownloadInstall(selected);
    }

    /** 「需要先停止服务端」确认后：停服并延迟一点再开始下载。 */
    private void stopThenDownload() {
        stopServer();
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                doDownloadInstall(selected);
            }
        }, 1200);
    }

    private void doDownloadInstall(final UpdateClient.Release release) {
        if (downloader.isRunning()) {
            toast("正在下载中");
            return;
        }
        busy = true;
        setProgress(0f, true);
        setDownloadHint("正在下载 " + release.tag + " …");

        downloadListener = new Downloader.Listener() {
            @Override
            public void onProgress(long done, long total) {
                if (total > 0) {
                    setProgress(done / (float) total, true);
                    setDownloadHint("下载中 " + fmtSize(done) + " / " + fmtSize(total)
                            + "（" + (done * 100 / total) + "%）");
                } else {
                    setDownloadHint("下载中 " + fmtSize(done));
                }
                updateDownloadButtons();
            }

            @Override
            public void onSourceFailed(String url, String reason) {
                String shortUrl = url.length() > 52 ? url.substring(0, 52) + "…" : url;
                setDownloadHint("这个下载源不可用，正在自动换源…\n" + shortUrl);
            }

            @Override
            public void onFinished(File file) {
                try {
                    versions.install(release.tag, file);
                    finishBusy();
                    setDownloadHint("已安装 " + release.tag + "，到「运行」页启动或切换");
                    toast("下载完成");
                    refresh();
                } catch (Exception e) {
                    finishBusy();
                    setVersionHint("安装失败：" + e.getMessage());
                }
            }

            @Override
            public void onPaused(long done, long total) {
                busy = false;
                setDownloadHint("已暂停 " + fmtSize(done)
                        + (total > 0 ? " / " + fmtSize(total) : "")
                        + "\n点「继续」接着下，或点「删除下载任务」丢弃");
                updateDownloadButtons();
            }

            @Override
            public void onFailed(String message) {
                finishBusy();
                setDownloadHint("下载失败：" + message);
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
            setDownloadHint("正在暂停…");
            return;
        }
        if (st == Downloader.State.PAUSED) {
            if (downloadListener == null) {
                return;
            }
            downloader.start(null, downloadListener);
            setProgress(state == null ? 0f : state.getDownloadProgress(), true);
            setDownloadHint("正在继续下载…");
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
        // 文案控制在 miuix 对话框正文宽度内一行放得下，否则「吗？」会被挤成孤字行。
        confirm("已下载的部分会被丢弃，确定吗？", new Runnable() {
            @Override
            public void run() {
                downloader.deleteTask();
                busy = false;
                setProgress(0f, false);
                setDownloadHint("下载任务已删除，已下载的文件已清理。");
                updateDownloadButtons();
                refresh();
            }
        });
    }

    // ---- 状态写入小工具：避免每处都判空 ----

    private void setProgress(float value, boolean visible) {
        if (state != null) {
            state.setDownloadProgress(value);
            state.setDownloadProgressVisible(visible);
        }
    }

    private void setDownloadHint(String text) {
        if (state != null) {
            state.setDownloadHint(text);
        }
    }

    private void setVersionHint(String text) {
        if (state != null) {
            state.setVersionHint(text);
        }
    }


    /**
     * 界面按钮完全由 Downloader 的状态推导（单一状态源，别处不要再改这些按钮文字）：
     *   IDLE    → 「下载」，删除任务不可点
     *   RUNNING → 「暂停」，删除任务不可点（必须先暂停）
     *   PAUSED  → 「继续」，删除任务可点
     */
    private void updateDownloadButtons() {
        com.pumpkin.server.ui.PumpkinUiState s = state;
        Downloader.State st = downloader.getState();
        if (s == null) {
            return;
        }
        if (st == Downloader.State.RUNNING) {
            s.setInstallButtonText("暂停");
            s.setInstallButtonEnabled(true);
        } else if (st == Downloader.State.PAUSED) {
            s.setInstallButtonText("继续");
            s.setInstallButtonEnabled(true);
        } else {
            s.setInstallButtonText(selected != null && versions.isInstalled(selected.tag)
                    ? "重新下载" : "下载");
            s.setInstallButtonEnabled(selected != null);
        }
        s.setDeleteTaskEnabled(st == Downloader.State.PAUSED);
        s.setCheckEnabled(st != Downloader.State.RUNNING);
    }

    private void finishBusy() {
        busy = false;
        if (state != null) {
            state.setDownloadProgressVisible(false);
        }
        updateDownloadButtons();
    }

    private void showSwitchDialog() {
        final List<VersionManager.Installed> installed = versions.listInstalled();
        if (installed.isEmpty()) {
            toast("还没有安装任何版本");
            return;
        }
        final String cur = versions.currentTag();
        List<PumpkinDialogItem> items = new ArrayList<>();
        for (int i = 0; i < installed.size(); i++) {
            VersionManager.Installed v = installed.get(i);
            items.add(new PumpkinDialogItem(
                    v.tag,
                    fmtSize(v.size),
                    v.tag.equals(cur),
                    true,
                    v.tag));
        }
        state.showListDialog(
                PumpkinDialogs.PICK_RUN,
                "选择要运行的版本",
                PumpkinServer.get().isRunning() ? "服务端正在运行，切换前需要先停止。" : null,
                items);
    }

    /** 选择运行版本。tag = 目标版本。 */
    private void applyRunVersion(String tag) {
        if (tag.equals(versions.currentTag())) {
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

    private void showDeleteDialog(final List<VersionManager.Installed> installed) {
        if (installed == null || installed.isEmpty()) {
            toast("还没有安装任何版本");
            return;
        }
        List<PumpkinDialogItem> items = new ArrayList<>();
        String curTag = versions.currentTag();
        for (int i = 0; i < installed.size(); i++) {
            VersionManager.Installed v = installed.get(i);
            boolean isCur = v.tag.equals(curTag);
            // 正在使用的版本不允许删 —— 置灰而不是点了才报错，减少一次无用点击。
            items.add(new PumpkinDialogItem(
                    v.tag,
                    isCur ? "正在使用，不能删除" : fmtSize(v.size),
                    false,
                    !isCur,
                    v.tag));
        }
        state.showListDialog(
                PumpkinDialogs.PICK_DELETE,
                "删除哪个版本？",
                "删除后需要重新下载才能使用。",
                items);
    }

    /** 删除版本。tag = 目标版本。 */
    private void applyDeleteVersion(String tag) {
        if (tag.equals(versions.currentTag())) {
            toast("不能删除正在使用的版本");
            return;
        }
        versions.delete(tag);
        toast("已删除 " + tag);
        refresh();
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

    /** 切页：只改状态，Compose 侧据此显示对应页面并移动底栏指示器。 */
    private void switchPage(int index) {
        if (state == null || index < 0 || index > PAGE_SETTINGS) {
            return;
        }
        state.setPage(index);
        if (index == PAGE_UPDATE) {
            updateUpdateDot(false);   // 进过更新页就把提醒收起来
        }
        refresh();
    }

    /** 更新页小红点（原 dotView）。 */
    private void updateUpdateDot(boolean show) {
        if (state != null) {
            state.setShowUpdateDot(show);
        }
    }

    /** 检查本应用（南瓜坞）自身有没有新版本。 */
    private void checkAppUpdate() {
        toast("正在检查更新…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final UpdateClient.AppAsset asset = updates.fetchAppAsset();
                    // 用 versionCode 判断新旧，而不是安装时间：
                    // 安装时间会被「重装同一个包」刷新，根本分不出哪个更新。
                    int installedCode;
                    long installedTime;
                    try {
                        android.content.pm.PackageInfo pi = getPackageManager()
                                .getPackageInfo(getPackageName(), 0);
                        installedCode = pi.versionCode;
                        installedTime = pi.lastUpdateTime;
                    } catch (Exception e) {
                        installedCode = 0;
                        installedTime = 0;
                    }
                    final int installed = installedCode;
                    final long installedAt = installedTime;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (asset == null || asset.downloadUrl == null) {
                                toast("服务器上还没有发布新的南瓜坞版本");
                                return;
                            }
                            // 优先按 versionCode 比；tag 解析不出序号时退回时间戳（留 2 分钟余量）。
                            final boolean newer;
                            if (asset.versionCode > 0 && installed > 0) {
                                newer = asset.versionCode > installed;
                            } else {
                                newer = asset.updatedAt > installedAt + 120000L;
                            }
                            if (newer) {
                                pendingAppUrl = asset.downloadUrl;
                                state.showConfirmDialog(
                                        PumpkinDialogs.APP_UPDATE,
                                        "南瓜坞有新版本",
                                        "服务器上发布了新版南瓜坞："
                                                + (asset.tag.isEmpty() ? asset.name : asset.tag)
                                                + "\n当前已装：" + versionName()
                                                + "\n\n要现在下载吗？下载完点开安装包覆盖安装即可。",
                                        "下载",
                                        "以后");
                            } else {
                                toast("南瓜坞已是最新（" + versionName() + "）");
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
        }, "app-update-check").start();
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
        // 通用二次确认。这里存下要执行的动作，等用户在 miuix 对话框里点「确定」再跑
        // （见 onDialogPositiveFromUi 的 CONFIRM 分支）。
        // 同时只可能有一个对话框，所以单个字段就够，不需要队列。
        pendingConfirm = onYes;
        state.showConfirmDialog(PumpkinDialogs.CONFIRM, "请确认", message, "确定", "取消");
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
