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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * GitHub Releases 客户端：列出可用服务端版本、下载原生二进制。
 *
 * 下载源策略：直连优先，失败自动回退到内置加速前缀；一旦某个源成功过就记住它，
 * 下次优先用它。用户也可以在设置里指定自己的 API 地址与镜像前缀。
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

    public interface Progress {
        /** total <= 0 表示长度未知。 */
        void onProgress(long done, long total);

        /** 返回 true 继续，false 取消。 */
        boolean isRunning();

        /** 当前下载源失败、准备换下一个时回调（可选实现）。 */
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
                    // 只要原生二进制（排除 .apk 和校验文件）
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

    /** 逐个尝试所有下载源（直连 → 上次成功的源 → 用户镜像 → 内置加速），任一成功即可。 */
    public File download(Release release, Progress progress) throws IOException {
        if (release == null || release.binaryUrl == null) {
            throw new IOException("该版本没有可下载的 Android 二进制");
        }
        String raw = release.binaryUrl;

        // 顺序：上次成功的源 → 用户自填镜像 → 内置列表（含直连）
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
        for (String prefix : prefixes) {
            String url = prefix.isEmpty() ? raw : prefix + raw;
            if (progress != null && !progress.isRunning()) {
                throw new IOException("已取消");
            }
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    File f = downloadFrom(url, release, progress);
                    Prefs.put(ctx, "good_prefix", prefix);
                    return f;
                } catch (IOException e) {
                    last = e;
                    if (progress != null) {
                        progress.onSourceFailed(url, e.getMessage() == null ? "失败" : e.getMessage());
                    }
                    if (progress != null && !progress.isRunning()) {
                        throw new IOException("已取消");
                    }
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

    private File downloadFrom(String url, Release release, Progress progress) throws IOException {
        File tmp = new File(ServerPaths.downloadCacheDir(ctx),
                release.tag.replaceAll("[^A-Za-z0-9._-]", "_") + ".part");
        HttpURLConnection conn = open(url);
        long total = conn.getContentLength();
        if (total <= 0) {
            total = release.binarySize;
        }
        InputStream in = conn.getInputStream();
        OutputStream out = new FileOutputStream(tmp);
        try {
            byte[] buf = new byte[65536];
            long done = 0;
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

    private String httpGet(String url) throws IOException {
        HttpURLConnection conn = open(url);
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

    private HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "PumpkinServerShell/1.0 (Android)");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + " " + conn.getResponseMessage() + " (" + url + ")");
        }
        return conn;
    }
}
