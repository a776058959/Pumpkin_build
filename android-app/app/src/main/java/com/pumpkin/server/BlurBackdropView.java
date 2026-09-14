package com.pumpkin.server;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.os.Build;
import android.view.View;

/**
 * 绘制「毛玻璃内容」的 View。
 *
 * Android 12+（API 31）：走硬件路径——内容录进 RenderNode，由 GPU 加 RenderEffect 模糊，
 * 全分辨率、无缩放失真，和 miuix-blur 在 Android 上的做法是同一个原理。
 *
 * 更低版本：回退到软件路径，显示一张预先模糊好的位图。
 */
public class BlurBackdropView extends View {

    private RenderNode node;
    private Bitmap fallback;
    private boolean effectApplied;

    public BlurBackdropView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public boolean canUseRenderNode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public RenderNode renderNode() {
        if (node == null) {
            node = new RenderNode("pumpkin-backdrop");
        }
        return node;
    }

    public boolean isEffectApplied() {
        return effectApplied;
    }

    public void markEffectApplied() {
        effectApplied = true;
    }

    public void setFallbackBitmap(Bitmap bmp) {
        this.fallback = bmp;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (canUseRenderNode() && node != null) {
            canvas.drawRenderNode(node);
        } else if (fallback != null && !fallback.isRecycled()) {
            canvas.drawBitmap(fallback, null,
                    new Rect(0, 0, getWidth(), getHeight()), null);
        }
    }
}
