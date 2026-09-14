package com.pumpkin.server;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 界面构件工厂：统一的「柔光玻璃」视觉语言。
 *
 * 深色渐变底 + 半透明圆角玻璃卡片 + 细描边高光 + 涟漪反馈。
 * 不使用任何第三方 UI 库，保证构建稳定、体积小。
 */
public final class UiKit {

    // 调色板
    public static final int BG_TOP = 0xFF0B0D12;
    public static final int BG_BOTTOM = 0xFF141824;
    public static final int GLASS = 0x1AFFFFFF;        // 玻璃卡片填充
    public static final int GLASS_STRONG = 0x26FFFFFF; // 强调玻璃
    public static final int STROKE = 0x33FFFFFF;       // 玻璃描边
    public static final int TEXT = 0xFFF2F4F8;
    public static final int TEXT_DIM = 0xFF9AA3B2;
    public static final int ACCENT = 0xFF5B8CFF;
    public static final int ACCENT_2 = 0xFF8A6BFF;
    public static final int OK = 0xFF3DDC97;
    public static final int WARN = 0xFFFFB020;
    public static final int DANGER = 0xFFFF5C6C;

    private UiKit() {
    }

    public static int dp(Context ctx, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                ctx.getResources().getDisplayMetrics());
    }

    /** 窗口背景：从上到下的深色渐变，带一点冷调。 */
    public static GradientDrawable windowBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{BG_TOP, BG_BOTTOM});
    }

    /** 玻璃卡片背景（半透明 + 描边 + 大圆角）。 */
    public static GradientDrawable glass(Context ctx, boolean strong) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{strong ? 0x2EFFFFFF : GLASS, strong ? 0x14FFFFFF : 0x0DFFFFFF});
        d.setCornerRadius(dp(ctx, 26));
        d.setStroke(dp(ctx, 1), STROKE);
        return d;
    }

    /** 主按钮背景。 */
    public static GradientDrawable accentButton(Context ctx, boolean primary) {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                primary ? new int[]{ACCENT, ACCENT_2} : new int[]{0x1FFFFFFF, 0x14FFFFFF});
        d.setCornerRadius(dp(ctx, 20));
        if (!primary) {
            d.setStroke(dp(ctx, 1), STROKE);
        }
        return d;
    }

    /** 给任意 View 套上涟漪反馈（按住有反馈，观感更「原生现代」）。 */
    public static void ripple(View v, float radiusDp, int color) {
        Context ctx = v.getContext();
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(color),
                v.getBackground(), null));
    }

    public static LinearLayout card(Context ctx) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(glass(ctx, false));
        box.setPadding(dp(ctx, 18), dp(ctx, 16), dp(ctx, 18), dp(ctx, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 14);
        box.setLayoutParams(lp);
        return box;
    }

    public static TextView cardTitle(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(TEXT);
        tv.setTextSize(16);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    public static TextView label(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(TEXT_DIM);
        tv.setTextSize(12.5f);
        tv.setLineSpacing(dp(ctx, 3), 1f);
        return tv;
    }

    public static TextView value(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(TEXT);
        tv.setTextSize(14);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setLineSpacing(dp(ctx, 3), 1f);
        return tv;
    }

    public static android.widget.Button button(Context ctx, String text, boolean primary) {
        android.widget.Button b = new android.widget.Button(ctx);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(primary ? Color.WHITE : TEXT);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(accentButton(ctx, primary));
        b.setPadding(dp(ctx, 18), dp(ctx, 10), dp(ctx, 18), dp(ctx, 10));
        b.setMinHeight(dp(ctx, 44));
        b.setStateListAnimator(null);
        b.setElevation(0);
        return b;
    }

    /** 一行按钮，等宽排列。 */
    public static LinearLayout buttonRow(Context ctx, View... views) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 12);
        row.setLayoutParams(lp);
        for (View v : views) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            p.rightMargin = dp(ctx, 8);
            row.addView(v, p);
        }
        if (row.getChildCount() > 0) {
            ((LinearLayout.LayoutParams) row.getChildAt(row.getChildCount() - 1).getLayoutParams())
                    .rightMargin = 0;
        }
        return row;
    }

    public static LinearLayout vspace(Context ctx, int dpHeight) {
        LinearLayout v = new LinearLayout(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, dpHeight)));
        return v;
    }

    /** 顶部大标题区。 */
    public static LinearLayout header(Context ctx, String title, String subtitle) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(ctx, 6), dp(ctx, 10), dp(ctx, 6), dp(ctx, 16));

        TextView t = new TextView(ctx);
        t.setText(title);
        t.setTextColor(TEXT);
        t.setTextSize(28);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(-0.02f);
        box.addView(t);

        TextView s = new TextView(ctx);
        s.setText(subtitle);
        s.setTextColor(TEXT_DIM);
        s.setTextSize(13);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(ctx, 4);
        s.setLayoutParams(sp);
        box.addView(s);
        return box;
    }

    /** 带彩色圆点的状态行。 */
    public static LinearLayout statusRow(Context ctx, TextView dot, TextView text) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        dot.setTextSize(18);
        dot.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp.rightMargin = UiKit.dp(ctx, 10);
        row.addView(dot, dp);

        text.setTextSize(17);
        text.setTextColor(TEXT);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(text);
        return row;
    }

    public static GradientDrawable chip(Context ctx, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor((color & 0x00FFFFFF) | 0x33000000);
        d.setCornerRadius(dp(ctx, 10));
        d.setStroke(dp(ctx, 1), (color & 0x00FFFFFF) | 0x66000000);
        return d;
    }
}
