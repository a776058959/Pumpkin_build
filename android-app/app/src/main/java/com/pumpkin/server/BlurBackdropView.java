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
 * Android 12+（API 31）：内容录进 RenderNode，由 GPU 加 RenderEffect 模糊，
 * 全分辨率、无缩放失真，原理与 miuix-blur 在 Android 上的做法一致。
 *
 * 关键：录制必须发生在 onDraw 里（也就是本帧绘制期间），
 * 不能在滚动回调里抢先绘制源视图——那样录到的是「屏幕还没画出来的状态」，
 * 滚动时边缘就会闪烁。
 *
 * 更低版本：回退到软件路径，显示一张预先模糊好的位图。
 */
public class BlurBackdropView extends View {

    /** 录制回调：在 onDraw 期间被调用，负责把源内容录进 node。 */
    public interface Recorder {
        void record(RenderNode node, int width, int height);
    }

    private RenderNode node;
    private Bitmap fallback;
    private Recorder recorder;
    private boolean dirty = true;
    private boolean effectApplied;

    public BlurBackdropView(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public boolean canUseRenderNode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public void setRecorder(Recorder recorder) {
        this.recorder = recorder;
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

    /** 请求在本帧重录并重绘（调用方负责节流）。 */
    public void markDirty() {
        dirty = true;
        invalidate();
    }

    public void setFallbackBitmap(Bitmap bmp) {
        this.fallback = bmp;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (canUseRenderNode()) {
            if (dirty && recorder != null && getWidth() > 0 && getHeight() > 0) {
                recorder.record(renderNode(), getWidth(), getHeight());
                dirty = false;
            }
            if (node != null) {
                canvas.drawRenderNode(node);
            }
        } else if (fallback != null && !fallback.isRecycled()) {
            canvas.drawBitmap(fallback, null, new Rect(0, 0, getWidth(), getHeight()), null);
        }
    }
}
