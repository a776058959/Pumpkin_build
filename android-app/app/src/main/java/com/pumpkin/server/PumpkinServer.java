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
 * 持有 Pumpkin 原生进程的状态与输出。
 *
 * APK 里的 libpumpkin.so 就是交叉编译出来的 aarch64-linux-android 可执行文件，
 * 安装时由系统解压到 nativeLibraryDir，该目录在 Android 10+ 上仍允许执行
 * （app 私有数据目录被 W^X 禁止执行，nativeLibraryDir 是例外）。
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

    /** 启动原生进程。工作目录设为 filesDir，这样 config/ world/ logs/ 都写在可写私有目录里。 */
    public synchronized void start(Context context) {
        if (running) {
            appendLine("[app] 服务器已在运行");
            return;
        }

        File bin = new File(context.getApplicationInfo().nativeLibraryDir, "libpumpkin.so");
        if (!bin.isFile()) {
            appendLine("[app] 找不到可执行文件: " + bin.getAbsolutePath());
            return;
        }

        // 优先用应用专属外部目录：插 USB / 系统文件管理器就能访问 Android/data/<包名>/files，
        // 方便改配置和放世界存档；不可用时退回内部私有目录。
        File external = context.getExternalFilesDir(null);
        workDir = (external != null) ? external : context.getFilesDir();
        if (workDir == null) {
            appendLine("[app] 找不到可写目录");
            return;
        }
        if (!workDir.isDirectory() && !workDir.mkdirs()) {
            appendLine("[app] 无法创建工作目录: " + workDir.getAbsolutePath());
            return;
        }

        appendLine("[app] 可执行文件: " + bin.getAbsolutePath());
        appendLine("[app] 工作目录: " + workDir.getAbsolutePath());

        try {
            ProcessBuilder pb = new ProcessBuilder(bin.getAbsolutePath());
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            // stdin 保持默认管道：服务端在非 TTY 下逐行读 stdin 当控制台命令，
            // App 用 sendCommand() 往这个管道写命令（/op、/stop、/save-all 等）
            process = pb.start();
            running = true;
            exitCode = Integer.MIN_VALUE;
            startedAt = System.currentTimeMillis();
            pumpOutput(process);
            appendLine("[app] 已启动");
        } catch (IOException e) {
            appendLine("[app] 启动失败: " + e);
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
            // 拿不到就返回 null，界面显示提示即可
        }
        return null;
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
}
