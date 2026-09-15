// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 顶层 Compose 界面：一块铺满屏幕的内容背板（负责被底栏模糊采样）+ 三页内容 + 液态玻璃底栏。
//
// 层级自下而上：
//   1. 渐变背景（Box 铺满）
//   2. 内容层：用 layerBackdrop(contentBackdrop) 把三页画面录进背板 —— 底栏的模糊就是采这里
//   3. 底栏 LiquidGlassNavBar（悬浮在内容之上，采样 contentBackdrop）
//
// 只依赖 PumpkinUiState / PumpkinActions，不引用 MainActivity（原因见 PumpkinActions.kt）。

package com.pumpkin.server.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
    // 色相与明暗是两条独立的轴：色相从 state 读，「跟随系统」只能在这里读系统设置。
    val palette = PumpkinPalettes.byId(state.paletteId)
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.themeMode) {
        PumpkinPalettes.MODE_LIGHT -> false
        PumpkinPalettes.MODE_AUTO -> systemDark
        else -> true
    }

    PumpkinTheme(palette = palette, dark = dark) {
        // 底栏模糊的采样源：必须包住「所有会出现在底栏背后的内容」。
        val contentBackdrop = rememberLayerBackdrop()

        // 取当前明暗下那份具体颜色。
        val scheme = palette.scheme(dark)
        val bgTop = scheme.bgTop
        val bgBottom = scheme.bgBottom

        Box(modifier = Modifier.fillMaxSize()) {
            // ---------- 背景 + 内容：**一起**录进背板 ----------
            //
            // 背景必须进背板。以前它被刻意排除（原注释写「玻璃底下也有底色可透」），
            // 后果是：底栏背后没有内容时，背板那一块是空的，模糊采样拿到的是空/黑，
            // 40% 透白的胶囊压在黑上就显成灰色 —— 白色背景下最明显。
            //
            // 道理上玻璃要模糊的本来就该是「它背后真实的样子」，而背景就是背后的一部分。
            // 背板仍要整屏录（底栏按屏幕坐标采样），内边距加在里层，
            // 而不是把这个 Box 缩小 —— 缩了背板坐标就跟底栏对不上，模糊会错位。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(contentBackdrop),
            ) {
                // 渐变背景（最底层）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(bgTop, bgBottom))),
                )

                // 内容层：该避让系统栏的部分由这里内缩
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    // 三个页面**常驻组合**，切页只换可见性（外加一次交叉淡入）。
                    //
                    // 以前是 when (state.page) 只组合当前页：每次切页都要从零组合一整页
                    //（卡片、输入框、可滚动列…），那一下在 UI 线程上要几十毫秒。
                    // 来回快点三个按钮时每点一次就重建一页 —— gfxinfo 实测正是
                    //「约 15 次点击 / 17 次 Slow UI thread / 最差帧 150ms」。
                    // 拖拽不触发切页，所以拖拽一直很顺，问题只在点击上。
                    //
                    // 现在页面第一次组合后就一直活着，切页几乎不花组合开销，只花一点绘制
                    //（交叉淡入）—— 而 GPU 实测余量很大（99th 仅 12ms），正是该把活挪过去的地方。
                    PageSlot(visible = state.page == 0) {
                        RunPage(state = state, actions = actions)
                    }
                    PageSlot(visible = state.page == 1) {
                        UpdatePage(state = state, actions = actions)
                    }
                    PageSlot(visible = state.page == 2) {
                        SettingsPage(state = state, actions = actions)
                    }
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

/**
 * 一页内容：**常驻组合**，只按可见性切换绘制，并做一次交叉淡入。
 *
 * 为什么要这么绕，而不是直接 `when (page)`：
 * 直接判断页面只有在切页时才组合目标页，而组合一整页（卡片 / 输入框 / 可滚动列）
 * 在 UI 线程上要几十毫秒 —— 手快连点底栏时每点一次就重建一页，掉帧就是这么来的。
 * 常驻组合把这笔开销挪到首次进入，之后切页只剩绘制，而绘制侧（GPU）实测余量很大。
 *
 * 不可见时用 `drawWithContent` 直接不画，而不是 `alpha = 0f`：
 * 后者仍会走完整的绘制流程，白花钱。
 */
@Composable
private fun PageSlot(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    // 180ms 与底栏胶囊的行程量级对齐，避免内容先到位、胶囊还在滑的割裂感。
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "pageAlpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
            .drawWithContent {
                // 完全透明就不画 —— 这三个页面始终在组合树里，能省一笔是一笔。
                if (alpha > 0.01f) {
                    drawContent()
                }
            },
    ) {
        content()
    }
}
