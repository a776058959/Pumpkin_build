package com.pumpkin.server;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub Releases 客户端：列出可用服务端版本、下载原生二进制、查询南瓜坞自身的更新。
 *
 * 下载源策略：直连优先，失败自动回退到内置加速前缀；成功过的源会被记住，下次优先用它。
 * 支持断点续传（HTTP Range），用于「暂停后继续」。
 *
 * 不依赖任何第三方库（HttpURLConnection + org.json）。
 */
public final class UpdateClient {

    public static final String DEFAULT_API_BASE = "https://api.github.com";
    public static final String DEFAULT_REPO = "a776058959/Pumpkin_build";

    /**
     * 内置的 GitHub 加速前缀，空串表示直连。按顺序尝试。
     *
     * 定义在 {@link MirrorOption} 里，与设置页的「加速源」选项共用同一份清单，
     * 避免两处各写一份、改一处漏一处（曾经就是硬编码在这里的）。
     */
    private static String[] builtinPrefixes() {
        return MirrorOption.builtinPrefixes();
    }

    /** 一个可下载的服务端版本。 */
    public static final class Release {
        public String tag = "";
        public String name = "";
        public String publishedAt = "";
        public String binaryUrl;
        public long binarySize;
        public String body = "";

        @Override
        public String toString() {
            return tag;
        }
    }

    /** 南瓜坞（APK）自身的发布信息。 */
    public static final class AppAsset {
        public String name = "";
        /** 所在 release 的 tag，如 Custom-20260915-0244。 */
        public String tag = "";
        public String downloadUrl;
        public long size;
        public long updatedAt;
        /**
         * 从 tag 里解析出的版本序号（Custom-YYYYMMDD-HHMM → 可比较的 long），
         * 解析不出来时为 0。仅用于「哪个更新」的判断，不参与安装校验。
         */
        public long versionCode;
    }

    /**
     * 把构建 tag 解析成可比较的版本序号。
     *
     * 公式与构建脚本（android-app/app/build.gradle.kts）**必须保持一致**：
     *     versionCode = 自 1970-01-01 起的天数 * 10000 + HHMM
     * 例：Custom-20260915-0244 → epochDay(2026-09-15) * 10000 + 0244。
     *
     * 为什么不用 YYYYMMDDHHMM 直接当 versionCode：那个数（约 2026 亿）超出
     * Android versionCode 的 int 上限（21.47 亿）。用天数换算后约 2 亿，长期够用。
     *
     * 解析不出来时返回 0，调用方会退回按时间戳判断。
     */
    static long parseVersionCodeFromTag(String tag) {
        if (tag == null) {
            return 0;
        }
        Matcher m = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})-(\\d{2})(\\d{2})").matcher(tag);
        if (!m.find()) {
            return 0;
        }
        try {
            int y = Integer.parseInt(m.group(1));
            int mo = Integer.parseInt(m.group(2));
            int d = Integer.parseInt(m.group(3));
            int hhmm = Integer.parseInt(m.group(4) + m.group(5));
            long epochDay = LocalDate.of(y, mo, d).toEpochDay();
            return epochDay * 10000L + hhmm;
        } catch (Exception e) {
            return 0;
        }
    }

    public interface Progress {
        /** total <= 0 表示长度未知。 */
        void onProgress(long done, long total);

        /** 返回 true 继续，false 停止（暂停或取消）。 */
        boolean isRunning();

        /** 当前下载源失败、准备换下一个时回调。 */
        default void onSourceFailed(String url, String reason) {
        }
    }

    private final Context ctx;

    public UpdateClient(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static String apiBase(Context ctx) {
        String v = Prefs.get(ctx, "api_base", "");
        return (v == null || v.trim().isEmpty()) ? DEFAULT_API_BASE : v.trim();
    }

    public static String repo(Context ctx) {
        String v = Prefs.get(ctx, "repo", "");
        return (v == null || v.trim().isEmpty()) ? DEFAULT_REPO : v.trim();
    }

    /** 列出最近发布的服务端版本（只保留带 Android 二进制的）。 */
    public List<Release> fetchReleases() throws IOException {
        List<Release> out = new ArrayList<>();
        String url = apiBase(ctx) + "/repos/" + repo(ctx) + "/releases?per_page=30";
        JSONArray arr;
        try {
            arr = new JSONArray(httpGet(url));
        } catch (JSONException e) {
            throw new IOException("解析 Releases 列表失败: " + e.getMessage());
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject rel = arr.optJSONObject(i);
            if (rel == null) {
                continue;
            }
            Release r = new Release();
            r.tag = rel.optString("tag_name", "");
            r.name = rel.optString("name", r.tag);
            r.publishedAt = rel.optString("published_at", "");
            r.body = rel.optString("body", "");
            JSONArray assets = rel.optJSONArray("assets");
            if (assets != null) {
                for (int j = 0; j < assets.length(); j++) {
                    JSONObject a = assets.optJSONObject(j);
                    if (a == null) {
                        continue;
                    }
                    String nm = a.optString("name", "");
                    if (nm.startsWith("pumpkin-android-arm64-") && !nm.endsWith(".apk")
                            && !nm.endsWith(".sha256")) {
                        r.binaryUrl = a.optString("browser_download_url", null);
                        r.binarySize = a.optLong("size", 0);
                        break;
                    }
                }
            }
            if (r.binaryUrl != null) {
                out.add(r);
            }
        }
        return out;
    }

    /** 查最新 Release 里的南瓜坞 APK（用于检查南瓜坞自身有没有更新）。 */
    /**
     * 找「南瓜坞」自己（APK）的发布信息。
     *
     * 为什么不能只查 /releases/latest：
     *   本仓库的 Release 是「上游服务端构建」和「南瓜坞 APK」共用一个发布流的，
     *   而 latest 只会返回**最新那一个** release —— 它经常只带服务端二进制、不带 APK
     *   （服务端构建比南瓜坞频繁得多）。早先只查 latest 的写法在那种情况下直接返回 null，
     *   用户侧表现为「没有找到南瓜坞的发布信息」，于是永远检查不到南瓜坞的更新。
     *
     * 现在改为：拉最近若干个 release，取其中**最新的、确实带 .apk 附件**的那个。
     */
    public AppAsset fetchAppAsset() throws IOException {
        // releases?per_page=N 已经按发布时间倒序返回，第一个带 apk 的就是我们要的。
        String url = apiBase(ctx) + "/repos/" + repo(ctx) + "/releases?per_page=20";
        JSONArray rels;
        try {
            rels = new JSONArray(httpGet(url));
        } catch (JSONException e) {
            throw new IOException("解析失败: " + e.getMessage());
        }
        AppAsset best = null;
        for (int i = 0; i < rels.length(); i++) {
            JSONObject rel = rels.optJSONObject(i);
            if (rel == null || rel.optBoolean("draft", false)) {
                continue;
            }
            JSONArray assets = rel.optJSONArray("assets");
            if (assets == null) {
                continue;
            }
            for (int j = 0; j < assets.length(); j++) {
                JSONObject a = assets.optJSONObject(j);
                if (a == null) {
                    continue;
                }
                String nm = a.optString("name", "");
                if (!nm.endsWith(".apk")) {
                    continue;
                }
                AppAsset s = new AppAsset();
                s.name = nm;
                s.tag = rel.optString("tag_name", "");
                s.downloadUrl = a.optString("browser_download_url", null);
                s.size = a.optLong("size", 0);
                // updated_at 表示「这个 apk 附件最后一次被替换的时间」，
                // 比 release 的 published_at 更贴近南瓜坞的真实新旧（重新上传 apk 不会改 published_at）。
                s.updatedAt = parseIso(a.optString("updated_at", ""));
                s.versionCode = parseVersionCodeFromTag(s.tag);
                // 选「最新」的判据：优先比 tag 解析出的版本号，都是 0（老式 tag）时再比时间戳。
                boolean better;
                if (best == null) {
                    better = true;
                } else if (s.versionCode > 0 && best.versionCode > 0) {
                    better = s.versionCode > best.versionCode;
                } else if (s.versionCode > 0) {
                    better = true;      // 能解析出版本号的优先于解析不出的
                } else if (best.versionCode > 0) {
                    better = false;
                } else {
                    better = s.updatedAt > best.updatedAt;
                }
                if (better) {
                    best = s;
                }
            }
        }
        return best;
    }

    private static long parseIso(String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0;
        }
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(iso);
            return d == null ? 0 : d.getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 下载（从头发起）。 */
    public File download(Release release, Progress progress) throws IOException {
        return download(release, progress, 0L);
    }

    /**
     * 逐个尝试所有下载源，任一成功即可。
     *
     * @param startOffset 已下载字节数，大于 0 时尝试断点续传（服务端不支持则自动重下）。
     */
    public File download(Release release, Progress progress, long startOffset) throws IOException {
        if (release == null || release.binaryUrl == null) {
            throw new IOException("该版本没有可下载的 Android 二进制");
        }
        String raw = release.binaryUrl;

        LinkedHashSet<String> prefixes = new LinkedHashSet<>();
        String remembered = Prefs.get(ctx, "good_prefix", "");
        if (remembered != null && !remembered.isEmpty()) {
            prefixes.add(remembered);
        }
        String userMirror = Prefs.get(ctx, "download_mirror", "");
        if (userMirror != null && !userMirror.trim().isEmpty()) {
            prefixes.add(userMirror.trim());
        }
        for (String p : builtinPrefixes()) {
            prefixes.add(p);
        }

        IOException last = null;
        long offset = startOffset;
        for (String prefix : prefixes) {
            String url = prefix.isEmpty() ? raw : prefix + raw;
            if (progress != null && !progress.isRunning()) {
                throw new IOException("已暂停");
            }
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    File f = downloadFrom(url, release, progress, offset);
                    Prefs.put(ctx, "good_prefix", prefix);
                    return f;
                } catch (IOException e) {
                    last = e;
                    if (progress != null) {
                        progress.onSourceFailed(url, e.getMessage() == null ? "失败" : e.getMessage());
                    }
                    if (progress != null && !progress.isRunning()) {
                        throw new IOException("已暂停");
                    }
                    // 换源后无法保证 Range 有效，退回从头下载
                    offset = 0;
                    if (attempt == 1) {
                        try {
                            Thread.sleep(800L);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }
        throw (last != null) ? last : new IOException("所有下载源都失败了（可在设置里换镜像）");
    }

    private File downloadFrom(String url, Release release, Progress progress, long startOffset)
            throws IOException {
        File tmp = partFile(release);
        HttpURLConnection conn = openWithRange(url, startOffset);
        int code = conn.getResponseCode();
        boolean resumed = startOffset > 0 && code == 206;
        long total = resumed ? startOffset + conn.getContentLength() : conn.getContentLength();
        if (total <= 0) {
            total = release.binarySize;
        }

        InputStream in = conn.getInputStream();
        OutputStream out = new FileOutputStream(tmp, resumed);
        long done = resumed ? startOffset : 0;
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (progress != null) {
                    progress.onProgress(done, total);
                    if (!progress.isRunning()) {
                        throw new IOException("已暂停");
                    }
                }
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
        if (tmp.length() <= 0) {
            throw new IOException("下载内容为空");
        }
        return tmp;
    }

    /** 未完成的下载文件（暂停后保留，用于续传或删除）。 */
    public File partFile(Release release) {
        String name = release == null || release.tag == null
                ? "download" : release.tag.replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(ServerPaths.downloadCacheDir(ctx), name + ".part");
    }

    // ---------------------------------------------------------------- App 自身的更新包

    /**
     * App 更新包的落盘位置。
     *
     * 放外部私有目录（Android/data/&lt;包名&gt;/files/update/）：体积十几 MB，不占内部空间，
     * 而且用户可以在文件管理器里看到它、出问题时手动装。取不到外部目录就退回内部目录 ——
     * 不能因为存储位置拿不到就让更新功能失效。
     */
    public File appApkFile() {
        File dir = ctx.getExternalFilesDir(null);
        if (dir == null) {
            dir = ctx.getFilesDir();
        }
        File updateDir = new File(dir, "update");
        if (!updateDir.exists() && !updateDir.mkdirs() && !updateDir.exists()) {
            // 真的建不出来就退回内部目录再试一次
            updateDir = new File(ctx.getFilesDir(), "update");
            if (!updateDir.exists()) {
                updateDir.mkdirs();
            }
        }
        return new File(updateDir, "pumpkin-app.apk");
    }

    /**
     * 下载 App 自身的更新包。
     *
     * 走与服务端下载同一套多源回退（上次成功的源 → 用户镜像 → 内置镜像 → 直连）：
     * GitHub 直连在国内经常不通，只试一次就报失败等于把功能废掉。
     *
     * 不做断点续传：包只有十几 MB，换源重下比维护 Range 状态简单，也不容易出错。
     */
    public File downloadAppApk(String url, Progress progress) throws IOException {
        if (url == null || url.trim().isEmpty()) {
            throw new IOException("没有可下载的地址");
        }
        String raw = url.trim();

        // 源顺序：用户为「App 更新」单独选的源 → 官方直连 → 通用镜像前缀 → 内置加速源。
        //
        // 官方直连排在前面是有意的。以前沿用服务端那套「上次成功的源优先」，
        // 结果从某个慢镜像下十几 MB 的包会卡很久（实测卡在 3.3MB/13.7MB 几乎不动）。
        // App 更新包是一次性小下载，先直连最快；连不上会自动往下换源。
        LinkedHashSet<String> prefixes = new LinkedHashSet<>();
        String appSource = Prefs.get(ctx, "app_update_mirror", "");
        if (appSource != null && !appSource.trim().isEmpty()) {
            prefixes.add(appSource.trim());
        }
        prefixes.add("");                                   // 官方直连
        String userMirror = Prefs.get(ctx, "download_mirror", "");
        if (userMirror != null && !userMirror.trim().isEmpty()) {
            prefixes.add(userMirror.trim());
        }
        for (String p : builtinPrefixes()) {
            prefixes.add(p);
        }

        IOException last = null;
        for (String prefix : prefixes) {
            if (progress != null && !progress.isRunning()) {
                throw new IOException("已取消");
            }
            String full = prefix.isEmpty() ? raw : prefix + raw;
            try {
                // 刻意不写 good_prefix：那是「服务端二进制」记住的源，
                // 拿 App 更新包的结果去覆盖它，会把服务端下载的优先级带偏。
                return downloadFileTo(full, appApkFile(), progress);
            } catch (IOException e) {
                last = e;
                if (progress != null) {
                    progress.onSourceFailed(full, e.getMessage() == null ? "失败" : e.getMessage());
                }
            }
        }
        throw (last != null) ? last : new IOException("所有下载源都失败了（可在设置里换镜像）");
    }

    /**
     * 把一个 URL 流式写到目标文件。先写 {@code .part} 再改名，
     * 避免中途失败留下半个包被后续逻辑当成完整的。
     *
     * APK 不额外做内容校验（服务端那份要验 ELF 头）：写进来的东西最终由系统安装器校验签名，
     * 包不对它会直接拒绝安装，不需要我们再判断一次。
     */
    private File downloadFileTo(String url, File dest, Progress progress) throws IOException {
        File tmp = new File(dest.getAbsolutePath() + ".part");
        HttpURLConnection conn = openWithRange(url, 0);
        long total = conn.getContentLength();

        InputStream in = conn.getInputStream();
        OutputStream out = new FileOutputStream(tmp, false);
        long done = 0;
        IOException failure = null;
        try {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                done += n;
                if (progress != null) {
                    progress.onProgress(done, total);
                    if (!progress.isRunning()) {
                        throw new IOException("已取消");
                    }
                }
            }
            out.flush();
        } catch (IOException e) {
            failure = e;
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

        // 失败或取消时把半截文件删掉：App 更新包不做断点续传，
        // 留着它只会白占十几 MB，而且会让下次下载多一次无用写入。
        // 放在关流之后删，避免在文件还打开着的时候 unlink。
        if (failure != null) {
            tmp.delete();
            throw failure;
        }

        if (tmp.length() <= 0) {
            tmp.delete();
            throw new IOException("下载内容为空");
        }
        if (dest.exists() && !dest.delete()) {
            tmp.delete();
            throw new IOException("无法覆盖旧的安装包");
        }
        if (!tmp.renameTo(dest)) {
            tmp.delete();
            throw new IOException("无法写入安装包");
        }
        return dest;
    }

    private String httpGet(String url) throws IOException {
        HttpURLConnection conn = openWithRange(url, 0);
        InputStream in = conn.getInputStream();
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), "UTF-8");
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
            }
        }
    }

    private HttpURLConnection openWithRange(String url, long startOffset) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Pumpkin-App/1.0 (Android)");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        if (startOffset > 0) {
            conn.setRequestProperty("Range", "bytes=" + startOffset + "-");
        }
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " " + conn.getResponseMessage() + " (" + url + ")");
        }
        return conn;
    }
}
