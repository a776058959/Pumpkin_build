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
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * GitHub Releases 客户端：列出可用服务端版本、下载原生二进制、查询壳自身的更新。
 *
 * 下载源策略：直连优先，失败自动回退到内置加速前缀；成功过的源会被记住，下次优先用它。
 * 支持断点续传（HTTP Range），用于「暂停后继续」。
 *
 * 不依赖任何第三方库（HttpURLConnection + org.json）。
 */
public final class UpdateClient {

    public static final String DEFAULT_API_BASE = "https://api.github.com";
    public static final String DEFAULT_REPO = "a776058959/Pumpkin_build";

    /** 内置的 GitHub 加速前缀，空串表示直连。按顺序尝试。 */
    private static final String[] BUILTIN_PREFIXES = new String[]{
            "",
            "https://ghfast.top/",
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
    };

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

    /** 壳（APK）自身的发布信息。 */
    public static final class ShellAsset {
        public String name = "";
        public String downloadUrl;
        public long size;
        public long updatedAt;
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

    /** 查最新 Release 里的壳 APK（用于检查壳自身有没有更新）。 */
    public ShellAsset fetchShellAsset() throws IOException {
        String url = apiBase(ctx) + "/repos/" + repo(ctx) + "/releases/latest";
        JSONObject rel;
        try {
            rel = new JSONObject(httpGet(url));
        } catch (JSONException e) {
            throw new IOException("解析失败: " + e.getMessage());
        }
        JSONArray assets = rel.optJSONArray("assets");
        if (assets == null) {
            return null;
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) {
                continue;
            }
            String nm = a.optString("name", "");
            if (nm.endsWith(".apk")) {
                ShellAsset s = new ShellAsset();
                s.name = nm;
                s.downloadUrl = a.optString("browser_download_url", null);
                s.size = a.optLong("size", 0);
                s.updatedAt = parseIso(a.optString("updated_at", ""));
                return s;
            }
        }
        return null;
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
        for (String p : BUILTIN_PREFIXES) {
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
        conn.setRequestProperty("User-Agent", "PumpkinServerShell/1.0 (Android)");
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
