package com.pumpkin.server;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.view.View;

/**
 * 悬浮栏背后的「毛玻璃」。
 *
 * 把悬浮栏下方的内容区渲染成模糊图层，垫在悬浮栏后面，让悬浮栏能透出背后的内容纹理。
 *
 * 两条实现路径：
 *   Android 12+：内容录进 RenderNode（GPU 记录，全分辨率）+ RenderEffect 硬件模糊。
 *                这与 miuix-blur 在 Android 上的做法同源，不会因为缩放而糊成马赛克。
 *   更低版本 ：内容绘制到 1/5 缩小位图，再跑三次盒式模糊近似高斯。
 *
 * 不依赖任何第三方库。
 */
public final class BlurBackdrop {

    /** 软件回退路径的缩小倍数。 */
    private static final int DOWNSCALE = 5;
    /** 软件回退路径在缩小图上的模糊半径。 */
    private static final float FALLBACK_RADIUS = 14f;
    /** 硬件路径的模糊半径（真实像素，约 20dp）。 */
    private static final float NODE_RADIUS = 62f;

    private final View source;
    private final BlurBackdropView target;
    private final int[] sourceLoc = new int[2];
    private final int[] targetLoc = new int[2];

    private Bitmap bitmap;
    private long lastDraw;

    public BlurBackdrop(View source, BlurBackdropView target) {
        this.source = source;
        this.target = target;
    }

    /** 重新采样并模糊。调用方自行控制频率（当前是每秒刷新 + 200ms 节流）。 */
    public void refresh() {
        int w = target.getWidth();
        int h = target.getHeight();
        if (w <= 0 || h <= 0 || source.getWidth() <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDraw < 200) {
            return;
        }
        lastDraw = now;

        source.getLocationInWindow(sourceLoc);
        target.getLocationInWindow(targetLoc);
        int offsetX = targetLoc[0] - sourceLoc[0];
        int offsetY = targetLoc[1] - sourceLoc[1];

        try {
            if (target.canUseRenderNode()) {
                drawHardware(w, h, offsetX, offsetY);
            } else {
                drawSoftware(w, h, offsetX, offsetY);
            }
        } catch (Exception ignored) {
            // 录制/绘制失败（例如视图正在销毁）时跳过这一轮
        }
    }

    /** 硬件路径：RenderNode 记录内容 + GPU 模糊。 */
    private void drawHardware(int w, int h, int offsetX, int offsetY) {
        RenderNode node = target.renderNode();
        node.setPosition(0, 0, w, h);
        if (!target.isEffectApplied()) {
            node.setRenderEffect(RenderEffect.createBlurEffect(
                    NODE_RADIUS, NODE_RADIUS, Shader.TileMode.CLAMP));
            target.markEffectApplied();
        }
        Canvas canvas = node.beginRecording();
        canvas.translate(-offsetX, -offsetY);
        source.draw(canvas);
        node.endRecording();
        target.invalidate();
    }

    /** 软件回退路径：缩小位图 + 盒式模糊。 */
    private void drawSoftware(int w, int h, int offsetX, int offsetY) {
        int sw = Math.max(1, w / DOWNSCALE);
        int sh = Math.max(1, h / DOWNSCALE);
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
        boxBlur(bitmap, Math.round(FALLBACK_RADIUS / 2f), 3);
        target.setFallbackBitmap(bitmap);
    }

    public void release() {
        if (bitmap != null) {
            bitmap.recycle();
            bitmap = null;
        }
    }

    /** 三次盒式模糊近似高斯：只用于低版本的缩小图，开销很小。 */
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
