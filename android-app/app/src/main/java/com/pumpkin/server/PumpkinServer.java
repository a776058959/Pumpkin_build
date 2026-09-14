package com.pumpkin.server;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * Pumpkin 原生服务端进程的状态与输出。
 *
 * 二进制来自 {@link VersionManager}（内部私有目录，targetSdk=28 下可 execve），
 * 工作目录用 {@link ServerPaths#workDir}（外部目录，方便改配置和放存档）。
 */
public final class PumpkinServer {

    private static final PumpkinServer INSTANCE = new PumpkinServer();
    private static final int MAX_LOG_CHARS = 200_000;
    private static final int TAIL_CHARS = 60_000;

    public static PumpkinServer get() {
        return INSTANCE;
    }

    private final StringBuilder log = new StringBuilder();
    private Process process;
    private File workDir;
    private volatile boolean running;
    private volatile int exitCode = Integer.MIN_VALUE;
    private volatile long startedAt;

    private PumpkinServer() {
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized File getWorkDir() {
        return workDir;
    }

    public synchronized int getExitCode() {
        return exitCode;
    }

    public synchronized long getStartedAt() {
        return startedAt;
    }

    public synchronized String tailLog() {
        int len = log.length();
        if (len <= TAIL_CHARS) {
            return log.toString();
        }
        return log.substring(len - TAIL_CHARS);
    }

    public synchronized void clearLog() {
        log.setLength(0);
    }

    public synchronized void logLine(String s) {
        appendLine(s);
    }

    /** 启动当前选中的服务端版本。 */
    public synchronized void start(Context context) {
        if (running) {
            appendLine("[app] 服务器已在运行");
            return;
        }

        VersionManager vm = new VersionManager(context);
        File bin = vm.currentBinary();
        if (bin == null) {
            appendLine("[app] 还没有安装任何服务端版本");
            appendLine("[app] 请先在上方「服务端版本」里检查更新并下载");
            return;
        }
        final String tag = vm.currentTag();

        workDir = ServerPaths.workDir(context);
        if (!workDir.isDirectory() && !workDir.mkdirs()) {
            appendLine("[app] 无法创建工作目录: " + workDir.getAbsolutePath());
            return;
        }

        appendLine("[app] 版本: " + tag);
        appendLine("[app] 可执行文件: " + bin.getAbsolutePath());
        appendLine("[app] 工作目录: " + workDir.getAbsolutePath());

        try {
            boolean rootMode = Prefs.getBool(context, "root_mode", false);
            if (rootMode && RootHelper.available()) {
                appendLine("[app] 启动方式: Root（su 域，不改动 SELinux）");
                process = RootHelper.start(workDir.getAbsolutePath(), bin.getAbsolutePath());
            } else {
                if (rootMode) {
                    appendLine("[app] 未获得 root 授权，本次回退到普通模式");
                }
                ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath());
                pb.directory(workDir);
                pb.redirectErrorStream(true);
                process = pb.start();
            }
            running = true;
            exitCode = Integer.MIN_VALUE;
            startedAt = System.currentTimeMillis();
            pumpOutput(process);
            appendLine("[app] 已启动");
        } catch (IOException e) {
            appendLine("[app] 启动失败: " + e);
            appendLine("[app] 若是 'Permission denied'，说明该系统不允许从应用私有目录执行文件");
            running = false;
        }
    }

    private void pumpOutput(final Process p) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                InputStream in = p.getInputStream();
                byte[] buf = new byte[8192];
                try {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        append(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                } catch (IOException ignored) {
                    // 进程退出时流会关闭
                }
                int code;
                try {
                    code = p.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    code = -1;
                }
                synchronized (PumpkinServer.this) {
                    running = false;
                    exitCode = code;
                    log.append("\n[app] 进程已退出，退出码 ").append(code).append('\n');
                    trim();
                }
            }
        }, "pumpkin-stdout");
        t.setDaemon(true);
        t.start();
    }

    /** 往服务端 stdin 发一条控制台命令。 */
    public synchronized void sendCommand(String command) {
        if (command == null) {
            return;
        }
        String cmd = command.trim();
        if (cmd.isEmpty()) {
            return;
        }
        if (process == null || !running) {
            appendLine("[app] 服务器未在运行，无法发送命令");
            return;
        }
        try {
            OutputStream os = process.getOutputStream();
            os.write((cmd + "\n").getBytes(StandardCharsets.UTF_8));
            os.flush();
            appendLine("[app] > " + cmd);
        } catch (IOException e) {
            appendLine("[app] 发送命令失败: " + e);
        }
    }

    public synchronized void stop() {
        if (process == null || !running) {
            appendLine("[app] 服务器未在运行");
            return;
        }
        appendLine("[app] 正在停止服务器...");
        process.destroy();
        process = null;
    }

    private void appendLine(String s) {
        synchronized (this) {
            log.append(s).append('\n');
            trim();
        }
    }

    private void append(String s) {
        synchronized (this) {
            log.append(s);
            trim();
        }
    }

    private void trim() {
        int len = log.length();
        if (len > MAX_LOG_CHARS) {
            log.delete(0, len - MAX_LOG_CHARS);
        }
    }

    /** 找一个可用的局域网 IPv4，用于提示客户端该连哪个地址。 */
    public static String findLanIpv4() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) {
                    continue;
                }
                String name = ni.getName();
                if (name != null && (name.startsWith("rmnet") || name.startsWith("dummy")
                        || name.startsWith("p2p"))) {
                    continue;
                }
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address
                            && !addr.isLoopbackAddress()
                            && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
            // 拿不到就返回 null
        }
        return null;
    }
}
