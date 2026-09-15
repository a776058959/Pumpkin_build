// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 顶层 Compose 壳：一块铺满屏幕的内容背板（负责被底栏模糊采样）+ 三页内容 + 液态玻璃底栏。
//
// 层级自下而上：
//   1. 渐变背景（Box 铺满）
//   2. 内容层：用 layerBackdrop(contentBackdrop) 把三页画面录进背板 —— 底栏的模糊就是采这里
//   3. 底栏 LiquidGlassNavBar（悬浮在内容之上，采样 contentBackdrop）
//
// 只依赖 PumpkinUiState / PumpkinActions，不引用 MainActivity（原因见 PumpkinActions.kt）。

package com.pumpkin.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.pumpkin.server.ui.pages.RunPage
import com.pumpkin.server.ui.pages.SettingsPage
import com.pumpkin.server.ui.pages.UpdatePage
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Settings

/**
 * 应用根组件。
 *
 * @param state 共享状态（Java 侧写，Compose 侧读）。
 * @param actions 界面动作（Java 侧实现）。
 */
@Composable
fun PumpkinApp(
    state: PumpkinUiState,
    actions: PumpkinActions,
) {
    PumpkinTheme(dark = true) {
        // 底栏模糊的采样源：必须包住「所有会出现在底栏背后的内容」。
        val contentBackdrop = rememberLayerBackdrop()

        val dark = LocalPumpkinDark.current
        val bgTop = if (dark) Color(0xFF171A22) else Color(0xFFF2F4F9)
        val bgBottom = if (dark) Color(0xFF0E1016) else Color(0xFFE6EAF3)

        Box(modifier = Modifier.fillMaxSize()) {
            // ---------- 渐变背景（最底层，不进背板：玻璃底下也有底色可透） ----------
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(bgTop, bgBottom))),
            )

            // ---------- 内容层：录进背板，供底栏模糊采样 ----------
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop),
            ) {
                when (state.page) {
                    0 -> RunPage(state = state, actions = actions)
                    1 -> UpdatePage(state = state, actions = actions)
                    else -> SettingsPage(state = state, actions = actions)
                }
            }

            // ---------- 液态玻璃底栏（悬浮在最上层） ----------
            val items = remember {
                listOf(
                    NavigationItem("运行", MiuixIcons.Play),
                    NavigationItem("更新", MiuixIcons.Download),
                    NavigationItem("设置", MiuixIcons.Settings),
                )
            }

            LiquidGlassNavBar(
                items = items,
                selectedIndex = state.page,
                onItemClick = { idx -> actions.onNavItemSelected(idx) },
                backdrop = contentBackdrop,
                modifier = Modifier.align(Alignment.BottomCenter),
                badge = { index ->
                    // 仅「更新」项带红点，且只在有更新时显示。
                    if (index == 1 && state.showUpdateDot) {
                        { Badge() }
                    } else {
                        null
                    }
                },
            )

            // ---------- miuix 风格对话框（最上层，覆盖底栏） ----------
            // 放在 Box 里最后一个，所以它渲染在底栏之上；WindowDialog 自身是独立窗口，
            // 不受这里层级影响，这样放只是让「同一棵 Compose 树里只有一个对话框宿主」这件事直观。
            PumpkinDialogHost(state = state, actions = actions)
        }
    }
}
