// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 顶层 Compose 壳：一块铺满屏幕的内容背板（负责被底栏模糊采样）+ 三页内容 + 液态玻璃底栏。
//
// 层级自下而上：
//   1. 渐变背景（Box 铺满）
//   2. 内容层：用 layerBackdrop(tabsBackdrop) 把三页画面录进背板 —— 底栏的模糊就是采这里
//   3. 底栏 LiquidGlassNavBar（悬浮在内容之上）

package com.pumpkin.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pumpkin.server.MainActivity
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
 * @param activity 宿主 Activity，用于调用保留下来的 Java 业务方法（startServer 等）。
 * @param state 共享状态。
 */
@Composable
fun PumpkinApp(
    activity: MainActivity,
    state: PumpkinUiState,
) {
    val context = LocalContext.current

    // Toast 是一次性事件，用序号驱动，避免重组时重复弹。
    LaunchedEffect(state.toastSeq) {
        if (state.toastSeq > 0 && state.toastMessage.isNotEmpty()) {
            android.widget.Toast.makeText(context, state.toastMessage, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    PumpkinTheme(dark = true) {
        // 底栏模糊的采样源。必须包住「所有会出现在底栏背后的内容」。
        val contentBackdrop = rememberLayerBackdrop()

        val dark = LocalPumpkinDark.current
        val bgTop = if (dark) Color(0xFF171A22) else Color(0xFFF2F4F9)
        val bgBottom = if (dark) Color(0xFF0E1016) else Color(0xFFE6EAF3)

        Box(modifier = Modifier.fillMaxSize()) {
            // ---------- 渐变背景（最底层，不被录制，所以玻璃底下也有底色） ----------
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(bgTop, bgBottom))),
            )

            // ---------- 内容层：录进背板 ----------
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop),
            ) {
                when (state.page) {
                    0 -> RunPage(activity = activity, state = state)
                    1 -> UpdatePage(activity = activity, state = state)
                    else -> SettingsPage(activity = activity, state = state)
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
                onItemClick = { idx -> activity.onNavItemSelected(idx) },
                backdrop = contentBackdrop,
                modifier = Modifier.align(Alignment.BottomCenter),
                badge = { index ->
                    // 仅「更新」页带红点，且只在有更新时显示。
                    if (index == 1 && state.showUpdateDot) {
                        { Badge() }
                    } else {
                        null
                    }
                },
            )
        }
    }
}
