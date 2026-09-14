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
import java.util.List;

/**
 * GitHub Releases 客户端：列出可用服务端版本、下载原生二进制。
 *
 * 不需要任何第三方依赖（用 HttpURLConnection + org.json）。
 * API 地址可自定义（{@link Prefs} 里的 api_base），方便手机上直连 GitHub 不通时走镜像。
 */
public final class UpdateClient {

    public static final String DEFAULT_API_BASE = "https://api.github.com";
    public static final String DEFAULT_REPO = "a776058959/Pumpkin_build";

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

    /** 列出最近发布的服务端版本（只保留带 Android 二进制附件的）。 */
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

    /** 下载到 cache 目录，返回临时文件。 */
    public File download(Release release, Progress progress) throws IOException {
        if (release == null || release.binaryUrl == null) {
            throw new IOException("该版本没有可下载的 Android 二进制");
        }
        File tmp = new File(ServerPaths.downloadCacheDir(ctx), release.tag.replaceAll("[^A-Za-z0-9._-]", "_") + ".part");
        HttpURLConnection conn = open(release.binaryUrl);
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
