package com.pumpkin.server;

import android.content.Context;

import java.io.File;

/** 统一的路径约定：二进制在内部私有目录（需要可执行），世界数据在外部目录（方便访问）。 */
public final class ServerPaths {

    private ServerPaths() {
    }

    /** 服务端工作目录：放 config/ world/ logs/，外部可访问，可通过 USB/文件管理器修改。 */
    public static File workDir(Context ctx) {
        File external = ctx.getExternalFilesDir(null);
        File base = (external != null) ? external : ctx.getFilesDir();
        return new File(base, "server");
    }

    public static File versionsDir(Context ctx) {
        return new File(ctx.getFilesDir(), "versions");
    }

    public static File downloadCacheDir(Context ctx) {
        File d = new File(ctx.getCacheDir(), "downloads");
        if (!d.isDirectory()) {
            d.mkdirs();
        }
        return d;
    }
}
