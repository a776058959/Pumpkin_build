package com.pumpkin.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Root 模式支持。
 *
 * 关键点：**完全不动 SELinux**（保持 Enforcing，不 setenforce 0，不改策略文件），
 * 因此不会被那些检查 getenforce / SELinux 状态的 App 检测到。
 *
 * 做法是通过 su 启动服务端，让进程落在 magisk / su 域里——这些域本身不受
 * app_data_file 的 execute 限制，所以无论二进制放在哪里都能运行，
 * 也就不再依赖 targetSdk<=28 的豁免。
 *
 * 对未来的意义：如果 Android 某天收紧/取消旧 targetSdk 的豁免，
 * 普通模式会失效，而 root 用户仍然可以用这个模式。
 */
public final class RootHelper {

    private static Boolean cached;

    private RootHelper() {
    }

    /**
     * 检测 su 是否可用且已授权。注意：第一次调用会触发 Magisk 的授权弹窗，
     * 所以只在用户主动选择 Root 模式时调用，不要放在每秒刷新的逻辑里。
     */
    public static boolean available() {
        if (cached != null) {
            return cached;
        }
        Process p = null;
        boolean ok = false;
        try {
            p = new ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start();
            if (p.waitFor(8, TimeUnit.SECONDS)) {
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.contains("uid=0")) {
                        ok = true;
                    }
                }
            }
        } catch (Exception ignored) {
            ok = false;
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
        cached = ok;
        return ok;
    }

    public static void resetCache() {
        cached = null;
    }

    /** 以 root 身份启动二进制；stdin/stdout 保持管道，日志与命令照常工作。 */
    public static Process start(String workDir, String binary) throws IOException {
        String cmd = "cd " + quote(workDir) + " && exec " + quote(binary);
        ProcessBuilder pb = new ProcessBuilder("su", "-c", cmd);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private static String quote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
