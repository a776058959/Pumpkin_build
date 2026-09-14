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
 * 把悬浮栏下方的内容区渲染成模糊图层，垫在悬浮栏后面，让悬浮栏透出背后的内容纹理。
 *
 * 两条实现路径：
 *   Android 12+：内容录进 RenderNode（GPU 记录，全分辨率）+ RenderEffect 硬件模糊。
 *                录制被推迟到 BlurBackdropView.onDraw 里执行，与主绘制同帧，
 *                这样滚动时不会因为「录到尚未画出的状态」而闪烁。
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
    /** 重录节流：太小会浪费 GPU，太大则滚动时模糊跟不上。 */
    private static final long MIN_INTERVAL_MS = 80;

    private final View source;
    private final BlurBackdropView target;
    private final int[] sourceLoc = new int[2];
    private final int[] targetLoc = new int[2];

    private Bitmap bitmap;
    private long lastDraw;

    /** 把录制安排在帧边界执行：不在 onDraw 里嵌套录制（那会崩），也不立即录制（那样会闪）。 */
    private final Runnable recordTask = new Runnable() {
        @Override
        public void run() {
            if (!target.canUseRenderNode()) {
                return;
            }
            int w = target.getWidth();
            int h = target.getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            try {
                recordInto(target.renderNode(), w, h);
                target.invalidate();
            } catch (Throwable ignored) {
                // 录制失败不致命，下一轮再试
            }
        }
    };

    public BlurBackdrop(View source, BlurBackdropView target) {
        this.source = source;
        this.target = target;
    }

    /** 请求刷新（带节流）。滚动回调、页面切换、定时器都可以调它。 */
    public void refresh() {
        int w = target.getWidth();
        int h = target.getHeight();
        if (w <= 0 || h <= 0 || source.getWidth() <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastDraw < MIN_INTERVAL_MS) {
            return;
        }
        lastDraw = now;

        try {
            if (target.canUseRenderNode()) {
                target.removeCallbacks(recordTask);
                target.postOnAnimation(recordTask);
            } else {
                drawSoftware(w, h);
            }
        } catch (Throwable ignored) {
            // 视图正在销毁等情况下跳过这一轮
        }
    }

    /** 在 onDraw 期间执行：把源内容录进 RenderNode。 */
    private void recordInto(RenderNode node, int w, int h) {
        source.getLocationInWindow(sourceLoc);
        target.getLocationInWindow(targetLoc);
        int offsetX = targetLoc[0] - sourceLoc[0];
        int offsetY = targetLoc[1] - sourceLoc[1];

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
    }

    /** 软件回退路径：缩小位图 + 盒式模糊（位图是静态的，不需要同帧约束）。 */
    private void drawSoftware(int w, int h) {
        source.getLocationInWindow(sourceLoc);
        target.getLocationInWindow(targetLoc);
        int offsetX = targetLoc[0] - sourceLoc[0];
        int offsetY = targetLoc[1] - sourceLoc[1];

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
