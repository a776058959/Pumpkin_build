package com.pumpkin.server;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;

/**
 * 悬浮栏背后的「毛玻璃」。
 *
 * 思路：把悬浮栏下方的内容区绘制成一张缩小位图 → 模糊 → 作为悬浮栏的背景图，
 * 于是悬浮栏就能「透出」它背后的内容（真正的背景模糊，而不是单纯半透明）。
 *
 * 模糊实现分两条路：
 *   Android 12+ (API 31)：用硬件 RenderEffect 模糊 ImageView 自身内容，开销极低；
 *   更低版本：在缩小后的位图上跑三次盒式模糊近似高斯，小图下也很快。
 *
 * 不依赖任何第三方库。
 */
public final class BlurBackdrop {

    /** 缩小倍数：越大越快越糊。 */
    private static final int DOWNSCALE = 5;
    /** 模糊半径（缩小图上的像素）。 */
    private static final float BLUR_RADIUS = 14f;

    private final View source;
    private final ImageView target;
    private final boolean hardwareBlur;

    private Bitmap bitmap;
    private int[] sourceLoc = new int[2];
    private int[] targetLoc = new int[2];
    private long lastDraw;

    public BlurBackdrop(View source, ImageView target) {
        this.source = source;
        this.target = target;
        this.hardwareBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
        if (hardwareBlur) {
            target.setRenderEffect(
                    RenderEffect.createBlurEffect(BLUR_RADIUS, BLUR_RADIUS, Shader.TileMode.CLAMP));
        }
    }

    /** 重新采样并模糊。调用方自行控制频率（例如每 1~2 秒或页面切换时）。 */
    public void refresh() {
        int w = target.getWidth();
        int h = target.getHeight();
        if (w <= 0 || h <= 0 || source.getWidth() <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDraw < 220) {
            return; // 节流，避免每帧截屏
        }
        lastDraw = now;

        source.getLocationInWindow(sourceLoc);
        target.getLocationInWindow(targetLoc);
        int offsetX = targetLoc[0] - sourceLoc[0];
        int offsetY = targetLoc[1] - sourceLoc[1];

        int sw = Math.max(1, w / DOWNSCALE);
        int sh = Math.max(1, h / DOWNSCALE);

        try {
            if (bitmap == null || bitmap.getWidth() != sw || bitmap.getHeight() != sh) {
                if (bitmap != null) {
                    bitmap.recycle();
                }
                bitmap = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            }
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(0x00000000);
            canvas.scale(1f / DOWNSCALE, 1f / DOWNSCALE);
            canvas.translate(-offsetX, -offsetY);
            source.draw(canvas);

            if (!hardwareBlur) {
                boxBlur(bitmap, Math.round(BLUR_RADIUS / 2f), 3);
            }
            target.setImageBitmap(bitmap);
            target.invalidate();
        } catch (Exception ignored) {
            // 截屏失败（例如视图正在销毁）时静默跳过，下一轮再试
        }
    }

    public void release() {
        target.setImageBitmap(null);
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
    }

    /** 三次盒式模糊近似高斯：对缩小后的图来说开销很小。 */
    private static void boxBlur(Bitmap bmp, int radius, int rounds) {
        if (radius < 1) {
            return;
        }
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] src = new int[w * h];
        bmp.getPixels(src, 0, w, 0, 0, w, h);
        int[] tmp = new int[w * h];

        for (int r = 0; r < rounds; r++) {
            blurHorizontal(src, tmp, w, h, radius);
            blurVertical(tmp, src, w, h, radius);
        }
        bmp.setPixels(src, 0, w, 0, 0, w, h);
    }

    private static void blurHorizontal(int[] in, int[] out, int w, int h, int radius) {
        int div = radius * 2 + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int rSum = 0;
            int gSum = 0;
            int bSum = 0;
            int aSum = 0;
            for (int i = -radius; i <= radius; i++) {
                int px = in[row + clamp(i, 0, w - 1)];
                aSum += (px >>> 24) & 0xFF;
                rSum += (px >> 16) & 0xFF;
                gSum += (px >> 8) & 0xFF;
                bSum += px & 0xFF;
            }
            for (int x = 0; x < w; x++) {
                out[row + x] = ((aSum / div) << 24) | ((rSum / div) << 16)
                        | ((gSum / div) << 8) | (bSum / div);
                int add = in[row + clamp(x + radius + 1, 0, w - 1)];
                int sub = in[row + clamp(x - radius, 0, w - 1)];
                aSum += ((add >>> 24) & 0xFF) - ((sub >>> 24) & 0xFF);
                rSum += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                gSum += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                bSum += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    private static void blurVertical(int[] in, int[] out, int w, int h, int radius) {
        int div = radius * 2 + 1;
        for (int x = 0; x < w; x++) {
            int rSum = 0;
            int gSum = 0;
            int bSum = 0;
            int aSum = 0;
            for (int i = -radius; i <= radius; i++) {
                int px = in[clamp(i, 0, h - 1) * w + x];
                aSum += (px >>> 24) & 0xFF;
                rSum += (px >> 16) & 0xFF;
                gSum += (px >> 8) & 0xFF;
                bSum += px & 0xFF;
            }
            for (int y = 0; y < h; y++) {
                out[y * w + x] = ((aSum / div) << 24) | ((rSum / div) << 16)
                        | ((gSum / div) << 8) | (bSum / div);
                int add = in[clamp(y + radius + 1, 0, h - 1) * w + x];
                int sub = in[clamp(y - radius, 0, h - 1) * w + x];
                aSum += ((add >>> 24) & 0xFF) - ((sub >>> 24) & 0xFF);
                rSum += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                gSum += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                bSum += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
