package com.pumpkin.server;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 服务端版本的安装 / 切换 / 备份 / 清理。
 *
 * 二进制必须放在**内部私有目录**（/data/data/<pkg>/files/versions/...）：
 * 外部存储是 noexec 挂载，即使降低 targetSdk 也无法执行；
 * 而内部私有目录在 targetSdk=28（untrusted_app_27 域）下允许 execve。
 */
public final class VersionManager {

    /** 除当前版本外，额外保留的历史版本数（用于回滚）。 */
    public static final int MAX_BACKUPS = 3;

    private static final String KEY_CURRENT = "current_tag";

    private final Context ctx;
    private final File versionsDir;

    public VersionManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.versionsDir = new File(this.ctx.getFilesDir(), "versions");
        if (!versionsDir.isDirectory()) {
            versionsDir.mkdirs();
        }
    }

    public File getVersionsDir() {
        return versionsDir;
    }

    public static final class Installed {
        public final String tag;
        public final File binary;
        public final long size;
        public final long installedAt;

        Installed(String tag, File binary) {
            this.tag = tag;
            this.binary = binary;
            this.size = binary.length();
            this.installedAt = binary.lastModified();
        }
    }

    public File dirFor(String tag) {
        return new File(versionsDir, sanitize(tag));
    }

    public File binaryFor(String tag) {
        return new File(dirFor(tag), "pumpkin");
    }

    public List<Installed> listInstalled() {
        List<Installed> out = new ArrayList<>();
        File[] dirs = versionsDir.listFiles();
        if (dirs == null) {
            return out;
        }
        for (File dir : dirs) {
            if (!dir.isDirectory()) {
                continue;
            }
            File bin = new File(dir, "pumpkin");
            if (bin.isFile() && bin.length() > 0) {
                out.add(new Installed(dir.getName(), bin));
            }
        }
        Collections.sort(out, new Comparator<Installed>() {
            @Override
            public int compare(Installed a, Installed b) {
                return Long.compare(b.installedAt, a.installedAt);
            }
        });
        return out;
    }

    public boolean isInstalled(String tag) {
        return binaryFor(tag).isFile();
    }

    public String currentTag() {
        String tag = Prefs.get(ctx, KEY_CURRENT, "");
        if (tag != null && !tag.isEmpty() && isInstalled(tag)) {
            return tag;
        }
        List<Installed> all = listInstalled();
        return all.isEmpty() ? null : all.get(0).tag;
    }

    public void setCurrent(String tag) {
        Prefs.put(ctx, KEY_CURRENT, tag == null ? "" : tag);
    }

    public File currentBinary() {
        String tag = currentTag();
        if (tag == null) {
            return null;
        }
        File bin = binaryFor(tag);
        return bin.isFile() ? bin : null;
    }

    public long currentSize() {
        File bin = currentBinary();
        return bin == null ? 0 : bin.length();
    }

    /** 删除某个已安装版本；如果删的是当前版本，当前指针会切到剩下的最新一个。 */
    public void delete(String tag) {
        deleteRecursive(dirFor(tag));
        if (tag != null && tag.equals(Prefs.get(ctx, KEY_CURRENT, ""))) {
            List<Installed> left = listInstalled();
            Prefs.put(ctx, KEY_CURRENT, left.isEmpty() ? "" : left.get(0).tag);
        }
    }

    /**
     * 安装新版本：把已下载好的文件移动到 versions/<tag>/pumpkin。
     * 返回该版本目录。
     */
    public File install(String tag, File downloaded) throws Exception {
        validateBinary(downloaded);
        File dir = dirFor(tag);
        deleteRecursive(dir);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            throw new Exception("无法创建版本目录: " + dir);
        }
        File dest = new File(dir, "pumpkin");
        if (!downloaded.renameTo(dest)) {
            copyFile(downloaded, dest);
            downloaded.delete();
        }
        if (!dest.setExecutable(true, false)) {
            // 内部私有目录下即使不显式设置通常也可执行，失败不致命
        }
        setCurrent(tag);
        prune(MAX_BACKUPS);
        return dir;
    }

    /** 只保留当前版本 + keep 个最新的历史版本，其余删除。 */
    public void prune(int keep) {
        List<Installed> all = listInstalled();
        String cur = currentTag();
        int keptBackups = 0;
        for (Installed v : all) {
            if (v.tag.equals(cur)) {
                continue;
            }
            keptBackups++;
            if (keptBackups > keep) {
                deleteRecursive(v.binary.getParentFile());
            }
        }
    }

    /** 一键清空：删除所有已安装版本。 */
    public void clearAllVersions() {
        File[] dirs = versionsDir.listFiles();
        if (dirs != null) {
            for (File dir : dirs) {
                deleteRecursive(dir);
            }
        }
        Prefs.put(ctx, KEY_CURRENT, "");
    }

    /** 一键清空：删除服务端工作目录（config/world/logs）。 */
    public static void clearServerData(Context ctx) {
        deleteRecursive(ServerPaths.workDir(ctx));
    }

    private static String sanitize(String tag) {
        if (tag == null) {
            return "unknown";
        }
        return tag.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** 校验下载到的确实是 aarch64-linux-android 可执行文件，避免把错误页面当成服务端装上。 */
    public static void validateBinary(File f) throws Exception {
        if (f == null || !f.isFile()) {
            throw new Exception("下载文件不存在");
        }
        long len = f.length();
        if (len < 1000000L) {
            throw new Exception("文件只有 " + len + " 字节，不是服务端二进制（可能下载到了错误页面）");
        }
        byte[] head = new byte[20];
        java.io.FileInputStream in = new java.io.FileInputStream(f);
        try {
            int read = in.read(head);
            if (read < 20) {
                throw new Exception("文件头不完整");
            }
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
        if (head[0] != 0x7F || head[1] != 'E' || head[2] != 'L' || head[3] != 'F') {
            throw new Exception("不是有效的可执行文件（ELF 校验失败），可能下载到了错误页面");
        }
        int machine = (head[18] & 0xFF) | ((head[19] & 0xFF) << 8);
        if (machine != 0xB7) {
            throw new Exception("架构不匹配：期望 arm64（0xB7），实际 0x" + Integer.toHexString(machine));
        }
    }

    public static void deleteRecursive(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File k : kids) {
                    deleteRecursive(k);
                }
            }
        }
        f.delete();
    }

    public static void copyFile(File src, File dst) throws Exception {
        java.io.InputStream in = new java.io.FileInputStream(src);
        java.io.OutputStream out = new java.io.FileOutputStream(dst);
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
            try {
                out.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static long dirSize(File dir) {
        if (dir == null || !dir.exists()) {
            return 0;
        }
        if (dir.isFile()) {
            return dir.length();
        }
        long total = 0;
        File[] kids = dir.listFiles();
        if (kids != null) {
            for (File k : kids) {
                total += dirSize(k);
            }
        }
        return total;
    }
}
