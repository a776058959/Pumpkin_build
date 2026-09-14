package com.pumpkin.server;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * 「液态玻璃」面板：在不换 UI 栈的前提下逼近 SukiSU Ultra 的观感。
 *
 * 由下往上叠四层（全部 Canvas 直绘；不碰 RenderNode、不录制、无嵌套绘制，
 * 避开「在 onDraw 里录 RenderNode 会启动即崩」这类坑）：
 *
 *   1. 半透明垂直渐变填充 —— 玻璃底（垫在 BlurBackdrop 的模糊层上面，配色不变）
 *   2. 倾斜光斑（可选）  —— 径向高光，随手机倾斜流动（数据由 TiltGlow 喂）
 *   3. 顶部光泽 + 底部反光 —— sheen / catch light，玻璃的「高光带」
 *   4. 渐变描边          —— 上亮下暗的边缘「折射」亮线
 *
 * 描边画在圆角矩形内缩 stroke/2 的路径上（GradientDrawable 的习惯做法），
 * 不会因越界被 View 裁掉半根线。
 */
public final class GlassPanelDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF boundsF = new RectF();
    private final Matrix matrix = new Matrix();

    private final float radius;
    private final float stroke;
    private final int fillTop;
    private final int fillBottom;
    private final int rimTop;
    private final int rimBottom;
    private final int sheenColor;
    private final int catchColor;
    private final int glossColor;
    private final boolean dynamic;

    /** 倾斜量 [-1,1]，TiltGlow 平滑后喂进来；非 dynamic 恒为 0。 */
    private float tiltX;
    private float tiltY;

    private int w = -1;
    private int h = -1;
    private int alpha = 255;

    private Shader fillShader;
    private Shader sheenShader;
    private Shader catchShader;
    private Shader rimShader;
    private Shader glossShader;

    public GlassPanelDrawable(float radiusPx, float strokePx,
                              int fillTop, int fillBottom,
                              int rimTop, int rimBottom,
                              int sheenColor, int catchColor, int glossColor,
                              boolean dynamic) {
        this.radius = radiusPx;
        this.stroke = strokePx;
        this.fillTop = fillTop;
        this.fillBottom = fillBottom;
        this.rimTop = rimTop;
        this.rimBottom = rimBottom;
        this.sheenColor = sheenColor;
        this.catchColor = catchColor;
        this.glossColor = glossColor;
        this.dynamic = dynamic;
    }

    /** TiltGlow 的回调（传感器事件在主线程）：x/y 是平滑后的倾斜量 [-1,1]。 */
    public void setTilt(float x, float y) {
        if (!dynamic) {
            return;
        }
        if (Math.abs(x - tiltX) + Math.abs(y - tiltY) < 0.004f) {
            return;
        }
        tiltX = x;
        tiltY = y;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        RectF b = boundsF;
        b.set(getBounds());
        if (b.isEmpty()) {
            return;
        }
        int bw = (int) b.width();
        int bh = (int) b.height();
        if (bw != w || bh != h) {
            w = bw;
            h = bh;
            rebuild(b);
        }

        paint.setAlpha(alpha);

        // 1) 玻璃底
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(fillShader);
        canvas.drawPath(path, paint);

        // 2~4) 光泽层全部裁剪在圆角内
        canvas.save();
        canvas.clipPath(path);

        if (dynamic && glossShader != null) {
            // 光斑中心随倾斜平移：手机往右倾，高光滑向右边
            matrix.setTranslate(tiltX * 0.38f * w, -tiltY * 0.30f * h);
            glossShader.setLocalMatrix(matrix);
            paint.setShader(glossShader);
            canvas.drawRect(b, paint);
        }

        paint.setShader(sheenShader);
        canvas.drawRect(b.left, b.top, b.right, b.top + h * 0.5f, paint);

        paint.setShader(catchShader);
        canvas.drawRect(b.left, b.bottom - h * 0.32f, b.right, b.bottom, paint);

        canvas.restore();

        // 5) 边缘「折射」亮线（上亮下暗的渐变描边）
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setShader(rimShader);
        canvas.drawPath(path, paint);

        paint.setShader(null);
    }

    /** 尺寸变化时重建路径与各渐变（平时一次都不重建，滚动/倾斜零分配）。 */
    private void rebuild(RectF b) {
        RectF inset = new RectF(b);
        inset.inset(stroke / 2f, stroke / 2f);
        path.reset();
        path.addRoundRect(inset, radius, radius, Path.Direction.CW);

        fillShader = new LinearGradient(0, b.top, 0, b.bottom,
                fillTop, fillBottom, Shader.TileMode.CLAMP);
        sheenShader = new LinearGradient(0, b.top, 0, b.top + Math.max(1f, h * 0.42f),
                sheenColor, 0x00000000, Shader.TileMode.CLAMP);
        catchShader = new LinearGradient(0, b.bottom - Math.max(1f, h * 0.30f), 0, b.bottom,
                0x00000000, catchColor, Shader.TileMode.CLAMP);
        rimShader = new LinearGradient(0, b.top, 0, b.bottom,
                rimTop, rimBottom, Shader.TileMode.CLAMP);
        if (dynamic) {
            glossShader = new RadialGradient(w * 0.5f, h * 0.40f,
                    Math.max(w, h) * 0.75f,
                    glossColor, 0x00000000, Shader.TileMode.CLAMP);
        } else {
            glossShader = null;
        }
    }

    /** 供悬浮栏的 elevation 阴影使用（不覆写的话自定义 Drawable 没有投影轮廓）。 */
    @Override
    public void getOutline(Outline outline) {
        android.graphics.Rect r = getBounds();
        if (r.isEmpty()) {
            outline.setEmpty();
            return;
        }
        outline.setRoundRect(r.left, r.top, r.right, r.bottom, radius);
    }

    @Override
    public void setAlpha(int a) {
        if (a != alpha) {
            alpha = a;
            invalidateSelf();
        }
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // 不支持：调色板是固定的
    }
}
