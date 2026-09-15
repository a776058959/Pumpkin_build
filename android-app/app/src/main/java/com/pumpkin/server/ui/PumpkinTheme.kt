// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 主题：配色方案（PumpkinPalette）+ miuix 主题的唯一入口。
//
// 以前强调色是三个写死的常量，换配色就得改代码。现在做成「配色方案」：
// 每套方案只给一组锚点色（渐变两端 / 卡片底 / 强调色 / 文字色），
// 禁用态、容器色这类派生色由 mix() 算出来 —— 六套配色各抄一遍几十个字面色值，
// 既没必要，改的时候也一定会漏。

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

/** 供组件判断当前是否深色（液态玻璃的高光强度依赖它）。 */
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
 * 只存锚点色，派生色走下面的计算属性 —— 这样加一套新配色只要写 10 个色值。
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
) {
    /** 比卡片再亮一档，用于卡片内嵌套的块。 */
    val surfaceHigh: Color get() = mix(surface, Color.White, 0.05f)

    /** 强调色的禁用态 / 容器底色。 */
    val accentDim: Color get() = mix(accent, surface, 0.78f)

    /** 选中项的容器底（miuix 的 tertiaryContainer 用它）。 */
    val accentContainer: Color get() = mix(accent, surface, 0.84f)

    /** 分隔线：卡片底往文字色靠一点点。 */
    val divider: Color get() = mix(surface, text, 0.08f)
}

/** 内置配色方案。列表顺序即设置页里的显示顺序。 */
object PumpkinPalettes {

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

    /** 全部配色，顺序即设置页显示顺序。 */
    val all: List<PumpkinPalette> = listOf(Night, Forest, Sunset, Sakura, Ocean, Graphite)

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
 * @param palette 当前选中的配色方案。
 * @param dark 是否深色。界面只做深色，留着这个开关是为了液态玻璃的高光强度判断
 *             （组件通过 [LocalPumpkinDark] 读它）。
 *
 * 不使用 ThemeController 的 Monet 动态取色：跟随壁纸会让配色方案失去意义。
 */
@Composable
fun PumpkinTheme(
    palette: PumpkinPalette = PumpkinPalettes.Default,
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
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
                surfaceContainer = palette.surface,
                surfaceContainerHigh = palette.surfaceHigh,
                surfaceContainerHighest = palette.surfaceHigh,
                dividerLine = palette.divider,
            )
        } else {
            lightColorScheme(
                primary = palette.accent,
                primaryVariant = palette.accentVariant,
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
