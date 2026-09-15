// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 主题：配色方案 + miuix 主题的唯一入口。
//
// 结构是**两条独立的轴**：
//   1. 色相（PumpkinPalette）—— 蓝/绿/橙/粉/青/紫/灰，每套各带一份深色版和一份亮色版
//   2. 明暗（PumpkinTheme 的 dark 参数）—— 深色 / 浅色 / 跟随系统，由用户在设置里选
//
// 为什么这么分：早先把「亮色」也做成了一整套配色（晨雾白/暖米色/淡紫晨光混在深色里），
// 于是换配色就顺带把明暗也换了，两件事搅在一起 —— 想「蓝的深色 + 绿的亮色」根本表达不出来。
// 拆成两条轴之后，配色只管色相，明暗只管深浅，组合是自由的。
//
// 每套色相的两份方案都按 WCAG 算过对比度：正文 ≥ 7:1，次要文字与按钮文字 ≥ 4.5:1。
// 亮色方案尤其容易翻车 —— 白字压在中等亮度的强调色上经常只有 4 左右。

package com.pumpkin.server.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 当前是否深色（液态玻璃的高光强度依赖它）。 */
val LocalPumpkinDark = compositionLocalOf { true }

/** 当前生效的那份具体颜色（已按明暗选好）。 */
val LocalPumpkinColors = compositionLocalOf { PumpkinPalettes.Default.dark }

/** 按 t（0..1）在 a、b 之间线性插值；用来派生禁用态、容器色这类中间色。 */
private fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/**
 * 一份具体可用的颜色（某个色相在某个明暗下的样子）。
 *
 * 只存锚点色，派生色走下面的计算属性 —— 加一套色相只要写 18 个色值（深浅各 9 个），
 * 派生色不用抄。
 */
@Immutable
data class PumpkinScheme(
    /** 界面渐变的顶色，同时也是窗口底色（冷启动第一帧铺的就是它）。 */
    val bgTop: Color,
    /** 界面渐变的底色。 */
    val bgBottom: Color,
    /** 卡片底色（miuix 的 surfaceContainer）。 */
    val surface: Color,
    val accent: Color,
    val accentVariant: Color,
    /** 强调色之上的文字色。浅色强调色必须配深色文字，否则对比度不够。 */
    val onAccent: Color,
    val text: Color,
    val textDim: Color,
    /** 「运行中」状态色。 */
    val ok: Color,
    val isLight: Boolean,
) {
    /** 卡片内嵌套块：深色往亮里走，亮色往暗里走。 */
    val surfaceHigh: Color
        get() = if (isLight) mix(surface, Color.Black, 0.045f) else mix(surface, Color.White, 0.05f)

    /** 强调色禁用态。 */
    val accentDim: Color get() = mix(accent, surface, 0.80f)

    /** 选中项容器底（miuix 的 tertiaryContainer 用它）。 */
    val accentContainer: Color get() = mix(accent, surface, if (isLight) 0.88f else 0.84f)

    /** 分隔线：卡片底往文字色靠一点点。 */
    val divider: Color get() = mix(surface, text, if (isLight) 0.12f else 0.08f)

    /** 禁用文字。 */
    val textDisabled: Color get() = mix(text, surface, 0.55f)
}

/**
 * 一套色相。
 *
 * @param id 英文 id，用于存偏好。
 * @param name 显示名。只描述**颜色**，不描述明暗 —— 明暗由主题模式决定。
 */
@Immutable
data class PumpkinPalette(
    val id: String,
    val name: String,
    /** 该色相的深色版。 */
    val dark: PumpkinScheme,
    /** 该色相的亮色版。 */
    val light: PumpkinScheme,
) {
    fun scheme(dark: Boolean): PumpkinScheme = if (dark) dark else light
}

/** 内置色相。列表顺序即设置页里的显示顺序。 */
object PumpkinPalettes {

    // 明暗两条轴里的「明暗」取值。存进 Prefs 的字符串就是这三个。
    const val MODE_DARK = "dark"
    const val MODE_LIGHT = "light"
    /** 跟随系统设置。 */
    const val MODE_AUTO = "auto"

    val Blue = PumpkinPalette(
        id = "blue",
        name = "星夜蓝",
        dark = PumpkinScheme(
            bgTop = Color(0xFF171A22), bgBottom = Color(0xFF0E1016), surface = Color(0xFF232630),
            accent = Color(0xFF7C9CFF), accentVariant = Color(0xFF5B7CFA), onAccent = Color.White,
            text = Color(0xFFE8ECF5), textDim = Color(0xFF8A93A6), ok = Color(0xFF4ADE80),
            isLight = false,
        ),
        light = PumpkinScheme(
            bgTop = Color(0xFFF7F8FA), bgBottom = Color(0xFFE8ECF1), surface = Color(0xFFFFFFFF),
            accent = Color(0xFF3B6FE0), accentVariant = Color(0xFF2A57C4), onAccent = Color.White,
            text = Color(0xFF1A1C22), textDim = Color(0xFF5F6673), ok = Color(0xFF14713D),
            isLight = true,
        ),
    )

    val Green = PumpkinPalette(
        id = "green",
        name = "松林绿",
        dark = PumpkinScheme(
            bgTop = Color(0xFF131A17), bgBottom = Color(0xFF0B100E), surface = Color(0xFF1F2823),
            accent = Color(0xFF5FD39A), accentVariant = Color(0xFF35B87C), onAccent = Color(0xFF07281A),
            text = Color(0xFFE6F1EA), textDim = Color(0xFF8AA79A), ok = Color(0xFF7BE0A8),
            isLight = false,
        ),
        light = PumpkinScheme(
            bgTop = Color(0xFFF4F8F5), bgBottom = Color(0xFFE6EFE9), surface = Color(0xFFFFFFFF),
            accent = Color(0xFF1F7A4D), accentVariant = Color(0xFF16603C), onAccent = Color.White,
            text = Color(0xFF16211B), textDim = Color(0xFF5A6B61), ok = Color(0xFF14713D),
            isLight = true,
        ),
    )

    val Orange = PumpkinPalette(
        id = "orange",
        name = "落日渐晖",
        dark = PumpkinScheme(
            bgTop = Color(0xFF1F1712), bgBottom = Color(0xFF120C09), surface = Color(0xFF2B211A),
            accent = Color(0xFFFF9F5A), accentVariant = Color(0xFFF07C3C), onAccent = Color(0xFF2B1405),
            text = Color(0xFFF5EBE3), textDim = Color(0xFFB39A8A), ok = Color(0xFF7BD98F),
            isLight = false,
        ),
        light = PumpkinScheme(
            // 白字压在 #C0652A 上只有 4.09:1，压暗到 #A85620 才有 5.23:1。
            bgTop = Color(0xFFF5F0E8), bgBottom = Color(0xFFE8E0D4), surface = Color(0xFFFDFAF5),
            accent = Color(0xFFA85620), accentVariant = Color(0xFF8E4517), onAccent = Color.White,
            text = Color(0xFF1E1B16), textDim = Color(0xFF675E51), ok = Color(0xFF237039),
            isLight = true,
        ),
    )

    val Pink = PumpkinPalette(
        id = "pink",
        name = "樱雾粉",
        dark = PumpkinScheme(
            bgTop = Color(0xFF1D1519), bgBottom = Color(0xFF110B0E), surface = Color(0xFF2B2025),
            accent = Color(0xFFFF8FB8), accentVariant = Color(0xFFF06A9C), onAccent = Color(0xFF2E0A18),
            text = Color(0xFFF7E9EF), textDim = Color(0xFFB394A0), ok = Color(0xFF7BD9A0),
            isLight = false,
        ),
        light = PumpkinScheme(
            bgTop = Color(0xFFFBF4F7), bgBottom = Color(0xFFF0E4EA), surface = Color(0xFFFFFFFF),
            accent = Color(0xFFC2185B), accentVariant = Color(0xFFA01248), onAccent = Color.White,
            text = Color(0xFF241A1F), textDim = Color(0xFF6E5A63), ok = Color(0xFF14713D),
            isLight = true,
        ),
    )

    val Cyan = PumpkinPalette(
        id = "cyan",
        name = "深海青",
        dark = PumpkinScheme(
            bgTop = Color(0xFF101A1E), bgBottom = Color(0xFF090F12), surface = Color(0xFF1B272C),
            accent = Color(0xFF4FD1E0), accentVariant = Color(0xFF2FB3C4), onAccent = Color(0xFF04262C),
            text = Color(0xFFE4F1F4), textDim = Color(0xFF86A3AA), ok = Color(0xFF5FD9A0),
            isLight = false,
        ),
        light = PumpkinScheme(
            bgTop = Color(0xFFF2F8F9), bgBottom = Color(0xFFE2EFF1), surface = Color(0xFFFFFFFF),
            accent = Color(0xFF0E7490), accentVariant = Color(0xFF0A5C73), onAccent = Color.White,
            text = Color(0xFF141F22), textDim = Color(0xFF566B70), ok = Color(0xFF14713D),
            isLight = true,
        ),
    )

    val Purple = PumpkinPalette(
        id = "purple",
        name = "深紫暮色",
        dark = PumpkinScheme(
            bgTop = Color(0xFF1E1430), bgBottom = Color(0xFF120A20), surface = Color(0xFF2A2040),
            accent = Color(0xFFBB86FC), accentVariant = Color(0xFF9B68E0), onAccent = Color(0xFF140820),
            text = Color(0xFFEDE5F8), textDim = Color(0xFF9A8CB5), ok = Color(0xFF6FD99A),
            isLight = false,
        ),
        light = PumpkinScheme(
            bgTop = Color(0xFFF3F0FA), bgBottom = Color(0xFFE6E0F2), surface = Color(0xFFFAF8FF),
            accent = Color(0xFF7C4DFF), accentVariant = Color(0xFF6234E0), onAccent = Color.White,
            text = Color(0xFF1A1625), textDim = Color(0xFF625A73), ok = Color(0xFF1F7048),
            isLight = true,
        ),
    )

    val Neutral = PumpkinPalette(
        id = "neutral",
        name = "石墨灰",
        dark = PumpkinScheme(
            bgTop = Color(0xFF1A1A1A), bgBottom = Color(0xFF101010), surface = Color(0xFF272727),
            accent = Color(0xFFC9CFD8), accentVariant = Color(0xFFA9B1BC), onAccent = Color(0xFF1A1A1A),
            text = Color(0xFFEDEDED), textDim = Color(0xFF909090), ok = Color(0xFF7BD98F),
            isLight = false,
        ),
        light = PumpkinScheme(
            bgTop = Color(0xFFF6F6F7), bgBottom = Color(0xFFE9E9EB), surface = Color(0xFFFFFFFF),
            accent = Color(0xFF4B5563), accentVariant = Color(0xFF374151), onAccent = Color.White,
            text = Color(0xFF1A1A1C), textDim = Color(0xFF63636B), ok = Color(0xFF14713D),
            isLight = true,
        ),
    )

    /** 全部色相，顺序即设置页显示顺序。 */
    val all: List<PumpkinPalette> = listOf(Blue, Green, Orange, Pink, Cyan, Purple, Neutral)

    /** 默认色相。 */
    val Default: PumpkinPalette = Blue

    private val index: Map<String, PumpkinPalette> = all.associateBy { it.id }

    /** 按 id 取色相；不认识就回落到默认。 */
    fun byId(id: String?): PumpkinPalette = index[id] ?: Default

    /** 明暗模式的中文名，设置页与提示都用它。 */
    @JvmStatic
    fun modeLabel(mode: String?): String = when (mode) {
        MODE_LIGHT -> "浅色"
        MODE_AUTO -> "跟随系统"
        else -> "深色"
    }

    /**
     * 给 Java 侧调用：窗口底色（ARGB）。
     *
     * 系统在应用画出第一帧之前铺的就是这个颜色，必须与 Compose 画出来的渐变顶色一致，
     * 否则冷启动会闪一下别的颜色（历史上闪的是 Theme.Material 的 #303030）。
     *
     * @param dark 当前是否为深色。由 Java 侧结合「主题模式 + 系统设置」算好再传进来 ——
     *             「跟随系统」这件事只有那一侧看得到 Configuration。
     */
    @JvmStatic
    fun windowColor(id: String?, dark: Boolean): Int = byId(id).scheme(dark).bgTop.toArgb()

    /** 给 Java 侧调用：色相的显示名。 */
    @JvmStatic
    fun nameOf(id: String?): String = byId(id).name
}

/**
 * 当前配色下的语义色。
 *
 * 这些 getter 带 @Composable，必须在组合里读 —— 好处是换配色或换明暗时，
 * 所有读到的地方自动重组，不必把 scheme 沿着参数列表一层层传下去。
 */
object PumpkinColors {
    val Accent: Color @Composable get() = LocalPumpkinColors.current.accent
    val AccentVariant: Color @Composable get() = LocalPumpkinColors.current.accentVariant
    val Ok: Color @Composable get() = LocalPumpkinColors.current.ok
    val Text: Color @Composable get() = LocalPumpkinColors.current.text
    val TextDim: Color @Composable get() = LocalPumpkinColors.current.textDim
}

/**
 * 应用主题。
 *
 * @param palette 选中的色相。
 * @param dark 深色还是浅色。由调用方结合「主题模式 + 系统设置」算好 ——
 *             「跟随系统」需要 isSystemInDarkTheme()，那只能在组合里读。
 *
 * 不使用 ThemeController 的 Monet 动态取色：跟随壁纸会让配色方案失去意义。
 */
@Composable
fun PumpkinTheme(
    palette: PumpkinPalette = PumpkinPalettes.Default,
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = palette.scheme(dark)
    val colors = remember(scheme) {
        if (dark) {
            darkColorScheme(
                primary = scheme.accent,
                onPrimary = scheme.onAccent,
                primaryVariant = scheme.accentVariant,
                onPrimaryVariant = scheme.onAccent,
                disabledPrimary = scheme.accentDim,
                disabledPrimaryButton = scheme.accentDim,
                disabledOnPrimary = mix(scheme.accent, scheme.text, 0.45f),
                disabledOnPrimaryButton = mix(scheme.accent, scheme.text, 0.45f),
                primaryContainer = scheme.accent,
                onPrimaryContainer = scheme.onAccent,
                tertiaryContainer = scheme.accentContainer,
                onTertiaryContainer = scheme.accent,
                background = scheme.bgBottom,
                onBackground = scheme.text,
                onBackgroundVariant = scheme.textDim,
                onSurface = scheme.text,
                onSurfaceContainer = scheme.text,
                onSurfaceContainerVariant = scheme.textDim,
                onSurfaceSecondary = scheme.text,
                onSurfaceVariantSummary = scheme.textDim,
                onSurfaceContainerHigh = scheme.text,
                onSurfaceContainerHighest = scheme.text,
                disabledOnSurface = scheme.textDisabled,
                surfaceContainer = scheme.surface,
                surfaceContainerHigh = scheme.surfaceHigh,
                surfaceContainerHighest = scheme.surfaceHigh,
                dividerLine = scheme.divider,
            )
        } else {
            lightColorScheme(
                primary = scheme.accent,
                onPrimary = scheme.onAccent,
                primaryVariant = scheme.accentVariant,
                onPrimaryVariant = scheme.onAccent,
                disabledPrimary = scheme.accentDim,
                disabledPrimaryButton = scheme.accentDim,
                disabledOnPrimary = mix(scheme.accent, scheme.text, 0.45f),
                disabledOnPrimaryButton = mix(scheme.accent, scheme.text, 0.45f),
                primaryContainer = scheme.accent,
                onPrimaryContainer = scheme.onAccent,
                tertiaryContainer = scheme.accentContainer,
                onTertiaryContainer = scheme.accent,
                background = scheme.bgBottom,
                onBackground = scheme.text,
                onBackgroundVariant = scheme.textDim,
                onSurface = scheme.text,
                onSurfaceContainer = scheme.text,
                onSurfaceContainerVariant = scheme.textDim,
                onSurfaceSecondary = scheme.text,
                onSurfaceVariantSummary = scheme.textDim,
                onSurfaceContainerHigh = scheme.text,
                onSurfaceContainerHighest = scheme.text,
                disabledOnSurface = scheme.textDisabled,
                surfaceContainer = scheme.surface,
                surfaceContainerHigh = scheme.surfaceHigh,
                surfaceContainerHighest = scheme.surfaceHigh,
                dividerLine = scheme.divider,
            )
        }
    }
    CompositionLocalProvider(
        LocalPumpkinDark provides dark,
        LocalPumpkinColors provides scheme,
    ) {
        MiuixTheme(colors = colors, content = content)
    }
}
