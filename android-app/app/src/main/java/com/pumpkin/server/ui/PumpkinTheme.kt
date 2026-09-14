// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 主题包装：把 miuix 的 MiuixTheme 收敛成项目里唯一的入口。
// 配色沿用原 Java UI 的深色观感（深灰底 + 蓝紫强调色）。

package com.pumpkin.server.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 供组件判断当前是否深色（液态玻璃的高光强度依赖它）。 */
val LocalPumpkinDark = compositionLocalOf { true }

/** 原 UiKit 里的强调色，保持视觉延续。 */
object PumpkinColors {
    /** 主强调色（原 UiKit.ACCENT 附近的蓝紫）。 */
    val Accent = Color(0xFF7C9CFF)

    /** 变体强调色，用于渐变。 */
    val AccentVariant = Color(0xFF5B7CFA)

    /** 运行中状态色。 */
    val Ok = Color(0xFF4ADE80)

    val Text = Color(0xFFE8ECF5)
    val TextDim = Color(0xFF8A93A6)
}

/**
 * 应用主题。默认深色（原 UI 即深色渐变），保持观感延续。
 *
 * 注意：不使用 ThemeController 的 Monet 动态色，因为原设计有固定的品牌蓝紫渐变，
 * 跟随壁纸取色会破坏一致性。
 */
@Composable
fun PumpkinTheme(
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = remember(dark) {
        if (dark) {
            darkColorScheme(
                primary = PumpkinColors.Accent,
                primaryVariant = PumpkinColors.AccentVariant,
            )
        } else {
            lightColorScheme(
                primary = PumpkinColors.Accent,
                primaryVariant = PumpkinColors.AccentVariant,
            )
        }
    }
    CompositionLocalProvider(LocalPumpkinDark provides dark) {
        MiuixTheme(colors = colors, content = content)
    }
}
