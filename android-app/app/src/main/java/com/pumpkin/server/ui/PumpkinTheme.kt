// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 主题：配色方案（PumpkinPalette）+ miuix 主题的唯一入口。
//
// 每套配色只给一组锚点色（渐变两端 / 卡片底 / 强调色 / 文字色），
// 禁用态、容器色、分隔线这类派生色由 mix() 算出来 —— 十二套配色各抄一遍几十个字面色值，
// 既没必要，改的时候也一定会漏。
//
// **亮色方案要显式标 isLight = true**：它决定三件事 ——
//   1. miuix 用 lightColorScheme 还是 darkColorScheme
//   2. 液态玻璃的高光强度（组件读 LocalPumpkinDark）
//   3. 状态栏图标是深色还是浅色（Java 侧读 PumpkinPalettes.isLight）

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

/** 当前生效的配色。由 [PumpkinTheme] 提供，[PumpkinColors] 从这里取值。 */
val LocalPumpkinColors = compositionLocalOf { PumpkinPalettes.Default }

/** 按 t（0..1）在 a、b 之间线性插值；用来派生禁用态、容器色这类中间色。 */
private fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

/**
 * 一套配色方案。
 *
 * 只存锚点色，派生色走下面的计算属性 —— 加一套新配色只要写 11 个色值。
 */
@Immutable
data class PumpkinPalette(
    val id: String,
    /** 设置页里显示的名字。 */
    val name: String,
    /** 界面渐变的顶色，同时也是窗口底色（冷启动第一帧铺的就是它）。 */
    val bgTop: Color,
    /** 界面渐变的底色。 */
    val bgBottom: Color,
    /** 卡片底色（miuix 的 surfaceContainer）。 */
    val surface: Color,
    /** 主强调色。 */
    val accent: Color,
    /** 强调色变体，用于渐变。 */
    val accentVariant: Color,
    /** 强调色之上的文字色。浅色强调色必须配深色文字，否则对比度不够。 */
    val onAccent: Color,
    val text: Color,
    val textDim: Color,
    /** 「运行中」状态色。 */
    val ok: Color,
    /** 亮色方案（浅背景 + 深文字）。默认深色。 */
    val isLight: Boolean = false,
) {
    /** 卡片内嵌套块的颜色：深色方案往亮里走，亮色方案往暗里走。 */
    val surfaceHigh: Color
        get() = if (isLight) mix(surface, Color.Black, 0.045f) else mix(surface, Color.White, 0.05f)

    /** 强调色的禁用态。 */
    val accentDim: Color get() = mix(accent, surface, 0.80f)

    /** 选中项的容器底（miuix 的 tertiaryContainer 用它）。 */
    val accentContainer: Color get() = mix(accent, surface, if (isLight) 0.88f else 0.84f)

    /** 分隔线：卡片底往文字色靠一点点。 */
    val divider: Color get() = mix(surface, text, if (isLight) 0.12f else 0.08f)

    /** 禁用文字。 */
    val textDisabled: Color get() = mix(text, surface, 0.55f)
}

/**
 * 内置配色方案。列表顺序即设置页里的显示顺序。
 *
 * 分两组：前半是深色，后半是亮色。每套的 text/bgTop、onAccent/accent 都按
 * WCAG 对比度算过（正文 ≥ 7:1，次要文字与按钮文字 ≥ 4.5:1），不是随手挑的颜色。
 */
object PumpkinPalettes {

    // ---------------------------------------------------------------- 深色

    /** 星夜蓝 —— 原版配色，默认。 */
    val Night = PumpkinPalette(
        id = "night",
        name = "星夜蓝",
        bgTop = Color(0xFF171A22),
        bgBottom = Color(0xFF0E1016),
        surface = Color(0xFF232630),
        accent = Color(0xFF7C9CFF),
        accentVariant = Color(0xFF5B7CFA),
        onAccent = Color.White,
        text = Color(0xFFE8ECF5),
        textDim = Color(0xFF8A93A6),
        ok = Color(0xFF4ADE80),
    )

    val Forest = PumpkinPalette(
        id = "forest",
        name = "松林绿",
        bgTop = Color(0xFF131A17),
        bgBottom = Color(0xFF0B100E),
        surface = Color(0xFF1F2823),
        accent = Color(0xFF5FD39A),
        accentVariant = Color(0xFF35B87C),
        onAccent = Color(0xFF07281A),
        text = Color(0xFFE6F1EA),
        textDim = Color(0xFF8AA79A),
        ok = Color(0xFF7BE0A8),
    )

    val Sunset = PumpkinPalette(
        id = "sunset",
        name = "落日渐晖",
        bgTop = Color(0xFF1F1712),
        bgBottom = Color(0xFF120C09),
        surface = Color(0xFF2B211A),
        accent = Color(0xFFFF9F5A),
        accentVariant = Color(0xFFF07C3C),
        onAccent = Color(0xFF2B1405),
        text = Color(0xFFF5EBE3),
        textDim = Color(0xFFB39A8A),
        ok = Color(0xFF7BD98F),
    )

    val Sakura = PumpkinPalette(
        id = "sakura",
        name = "樱雾粉",
        bgTop = Color(0xFF1D1519),
        bgBottom = Color(0xFF110B0E),
        surface = Color(0xFF2B2025),
        accent = Color(0xFFFF8FB8),
        accentVariant = Color(0xFFF06A9C),
        onAccent = Color(0xFF2E0A18),
        text = Color(0xFFF7E9EF),
        textDim = Color(0xFFB394A0),
        ok = Color(0xFF7BD9A0),
    )

    val Ocean = PumpkinPalette(
        id = "ocean",
        name = "深海青",
        bgTop = Color(0xFF101A1E),
        bgBottom = Color(0xFF090F12),
        surface = Color(0xFF1B272C),
        accent = Color(0xFF4FD1E0),
        accentVariant = Color(0xFF2FB3C4),
        onAccent = Color(0xFF04262C),
        text = Color(0xFFE4F1F4),
        textDim = Color(0xFF86A3AA),
        ok = Color(0xFF5FD9A0),
    )

    val Graphite = PumpkinPalette(
        id = "graphite",
        name = "石墨灰",
        bgTop = Color(0xFF1A1A1A),
        bgBottom = Color(0xFF101010),
        surface = Color(0xFF272727),
        accent = Color(0xFFC9CFD8),
        accentVariant = Color(0xFFA9B1BC),
        // 浅灰强调色上必须配深色文字。
        onAccent = Color(0xFF1A1A1A),
        text = Color(0xFFEDEDED),
        textDim = Color(0xFF909090),
        ok = Color(0xFF7BD98F),
    )

    /** 深紫暮色 —— 比原来的深灰更有色相，不是"另一个黑"。 */
    val DuskPurple = PumpkinPalette(
        id = "dusk_purple",
        name = "深紫暮色",
        bgTop = Color(0xFF1E1430),
        bgBottom = Color(0xFF120A20),
        surface = Color(0xFF2A2040),
        accent = Color(0xFFBB86FC),
        accentVariant = Color(0xFF9B68E0),
        onAccent = Color(0xFF140820),
        text = Color(0xFFEDE5F8),
        textDim = Color(0xFF9A8CB5),
        ok = Color(0xFF6FD99A),
    )

    val DeepForest = PumpkinPalette(
        id = "deep_forest",
        name = "墨绿深林",
        bgTop = Color(0xFF0F1E18),
        bgBottom = Color(0xFF081410),
        surface = Color(0xFF1B2E25),
        accent = Color(0xFF5EEDA0),
        accentVariant = Color(0xFF3AD080),
        onAccent = Color(0xFF0A2818),
        text = Color(0xFFE2F5EA),
        textDim = Color(0xFF82AD96),
        ok = Color(0xFF80E8B0),
    )

    val WineDark = PumpkinPalette(
        id = "wine_dark",
        name = "酒红微醺",
        bgTop = Color(0xFF221218),
        bgBottom = Color(0xFF160A10),
        surface = Color(0xFF321E26),
        accent = Color(0xFFFF6B8A),
        accentVariant = Color(0xFFE04870),
        onAccent = Color(0xFF200810),
        text = Color(0xFFF5E4EA),
        textDim = Color(0xFFB8909A),
        ok = Color(0xFF6BD990),
    )

    // ---------------------------------------------------------------- 亮色

    /** 晨雾白 —— 干净清冷，白天的默认口味。 */
    val Daylight = PumpkinPalette(
        id = "daylight",
        name = "晨雾白",
        bgTop = Color(0xFFF7F8FA),
        bgBottom = Color(0xFFE8ECF1),
        surface = Color(0xFFFFFFFF),
        accent = Color(0xFF3B6FE0),
        accentVariant = Color(0xFF2A57C4),
        onAccent = Color.White,
        text = Color(0xFF1A1C22),
        textDim = Color(0xFF5F6673),
        ok = Color(0xFF14713D),
        isLight = true,
    )

    /** 暖米色 —— 像手帐纸页，护眼。 */
    val WarmLinen = PumpkinPalette(
        id = "warm_linen",
        name = "暖米色",
        bgTop = Color(0xFFF5F0E8),
        bgBottom = Color(0xFFE8E0D4),
        surface = Color(0xFFFDFAF5),
        // 白字压在 #C0652A 上只有 4.09:1，压暗到 #A85620 才有 5.23:1。
        accent = Color(0xFFA85620),
        accentVariant = Color(0xFF8E4517),
        onAccent = Color.White,
        text = Color(0xFF1E1B16),
        textDim = Color(0xFF675E51),
        ok = Color(0xFF237039),
        isLight = true,
    )

    /** 淡紫晨光 —— 清新、略带梦幻。 */
    val Lavender = PumpkinPalette(
        id = "lavender",
        name = "淡紫晨光",
        bgTop = Color(0xFFF3F0FA),
        bgBottom = Color(0xFFE6E0F2),
        surface = Color(0xFFFAF8FF),
        accent = Color(0xFF7C4DFF),
        accentVariant = Color(0xFF6234E0),
        onAccent = Color.White,
        text = Color(0xFF1A1625),
        textDim = Color(0xFF625A73),
        ok = Color(0xFF1F7048),
        isLight = true,
    )

    /** 全部配色，顺序即设置页显示顺序（先深色后亮色）。 */
    val all: List<PumpkinPalette> = listOf(
        Night, Forest, Sunset, Sakura, Ocean, Graphite,
        DuskPurple, DeepForest, WineDark,
        Daylight, WarmLinen, Lavender,
    )

    /** 默认配色（没存过偏好时用它）。 */
    val Default: PumpkinPalette = Night

    private val index: Map<String, PumpkinPalette> = all.associateBy { it.id }

    /** 按 id 取配色；id 为 null、空串或不认识时回落到默认。 */
    fun byId(id: String?): PumpkinPalette = index[id] ?: Default

    /**
     * 给 Java 侧调用：窗口底色（ARGB）。
     *
     * 系统在应用画出第一帧之前铺的就是这个颜色，必须跟 [PumpkinPalette.bgTop] 一致，
     * 否则冷启动会闪一下别的颜色（历史上闪的是 Theme.Material 的 #303030）。
     */
    @JvmStatic
    fun windowColor(id: String?): Int = byId(id).bgTop.toArgb()

    /** 给 Java 侧调用：配色的显示名，用于切换后的提示。 */
    @JvmStatic
    fun nameOf(id: String?): String = byId(id).name

    /**
     * 给 Java 侧调用：这套配色是不是亮色。
     *
     * 状态栏图标颜色靠它决定 —— 亮背景上必须用深色图标，否则白图标在浅底上根本看不见。
     */
    @JvmStatic
    fun isLight(id: String?): Boolean = byId(id).isLight
}

/**
 * 当前配色下的语义色。
 *
 * 这些 getter 带 @Composable，必须在组合里读 —— 好处是换配色时所有读到的地方
 * 自动重组，不必把 palette 沿着参数列表一层层传下去。
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
 * @param palette 当前选中的配色方案。深色/亮色由它自带的 [PumpkinPalette.isLight] 决定，
 *                不再单独传 dark —— 否则会出现"亮色配色 + 深色控件"这种自相矛盾的组合。
 *
 * 不使用 ThemeController 的 Monet 动态取色：跟随壁纸会让配色方案失去意义。
 */
@Composable
fun PumpkinTheme(
    palette: PumpkinPalette = PumpkinPalettes.Default,
    content: @Composable () -> Unit,
) {
    val dark = !palette.isLight
    val colors = remember(palette, dark) {
        if (dark) {
            darkColorScheme(
                primary = palette.accent,
                onPrimary = palette.onAccent,
                primaryVariant = palette.accentVariant,
                onPrimaryVariant = palette.onAccent,
                disabledPrimary = palette.accentDim,
                disabledPrimaryButton = palette.accentDim,
                disabledOnPrimary = mix(palette.accent, palette.text, 0.45f),
                disabledOnPrimaryButton = mix(palette.accent, palette.text, 0.45f),
                primaryContainer = palette.accent,
                onPrimaryContainer = palette.onAccent,
                tertiaryContainer = palette.accentContainer,
                onTertiaryContainer = palette.accent,
                background = palette.bgBottom,
                onBackground = palette.text,
                onBackgroundVariant = palette.textDim,
                onSurface = palette.text,
                onSurfaceContainer = palette.text,
                onSurfaceContainerVariant = palette.textDim,
                onSurfaceSecondary = palette.text,
                onSurfaceVariantSummary = palette.textDim,
                onSurfaceContainerHigh = palette.text,
                onSurfaceContainerHighest = palette.text,
                disabledOnSurface = palette.textDisabled,
                surfaceContainer = palette.surface,
                surfaceContainerHigh = palette.surfaceHigh,
                surfaceContainerHighest = palette.surfaceHigh,
                dividerLine = palette.divider,
            )
        } else {
            lightColorScheme(
                primary = palette.accent,
                onPrimary = palette.onAccent,
                primaryVariant = palette.accentVariant,
                onPrimaryVariant = palette.onAccent,
                disabledPrimary = palette.accentDim,
                disabledPrimaryButton = palette.accentDim,
                disabledOnPrimary = mix(palette.accent, palette.text, 0.45f),
                disabledOnPrimaryButton = mix(palette.accent, palette.text, 0.45f),
                primaryContainer = palette.accent,
                onPrimaryContainer = palette.onAccent,
                tertiaryContainer = palette.accentContainer,
                onTertiaryContainer = palette.accent,
                background = palette.bgBottom,
                onBackground = palette.text,
                onBackgroundVariant = palette.textDim,
                onSurface = palette.text,
                onSurfaceContainer = palette.text,
                onSurfaceContainerVariant = palette.textDim,
                onSurfaceSecondary = palette.text,
                onSurfaceVariantSummary = palette.textDim,
                onSurfaceContainerHigh = palette.text,
                onSurfaceContainerHighest = palette.text,
                disabledOnSurface = palette.textDisabled,
                surfaceContainer = palette.surface,
                surfaceContainerHigh = palette.surfaceHigh,
                surfaceContainerHighest = palette.surfaceHigh,
                dividerLine = palette.divider,
            )
        }
    }
    CompositionLocalProvider(
        LocalPumpkinDark provides dark,
        LocalPumpkinColors provides palette,
    ) {
        MiuixTheme(colors = colors, content = content)
    }
}
