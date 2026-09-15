// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 顶层 Compose 界面：一块铺满屏幕的内容背板（负责被底栏模糊采样）+ 四页内容 + 液态玻璃底栏。
//
// 层级自下而上：
//   1. 渐变背景（Box 铺满）
//   2. 内容层：用 layerBackdrop(contentBackdrop) 把四页画面录进背板 —— 底栏的模糊就是采这里
//   3. 底栏 LiquidGlassNavBar（悬浮在内容之上，采样 contentBackdrop）
//
// 只依赖 PumpkinUiState / PumpkinActions，不引用 MainActivity（原因见 PumpkinActions.kt）。

package com.pumpkin.server.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import com.pumpkin.server.ui.pages.PluginsPage
import com.pumpkin.server.ui.pages.RunPage
import com.pumpkin.server.ui.pages.SettingsPage
import com.pumpkin.server.ui.pages.UpdatePage
import top.yukonga.miuix.kmp.basic.Badge
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.GridView
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
                    // 四页常驻组合，只让当前页可交互。页序 0 运行 / 1 更新 / 2 插件 / 3 设置（与 MainActivity 的 PAGE_* 对应）。
                    //
                    // 为什么不用 when 每次重新组合：切页要重建一整页（卡片/输入框/可滚动列），
                    // 实测切页时 UI 线程会掉一帧（90th 19ms → 38ms、99th 53ms → 109ms）。
                    //
                    // 为什么不用 drawWithContent 跳过绘制（曾经那么干过，是错的）：
                    // **绘制与触摸是两套独立的遍历**。drawWithContent 只是不画，
                    // 节点仍是全屏大小，照样吃掉点击 —— 结果是「很多按钮功能错乱」。
                    // 详见 PageSlot 的注释与 tools/test-hidden-page-hit.sh。
                    PageSlot(visible = state.page == 0) {
                        RunPage(state = state, actions = actions)
                    }
                    PageSlot(visible = state.page == 1) {
                        UpdatePage(state = state, actions = actions)
                    }
                    PageSlot(visible = state.page == 2) {
                        PluginsPage(state = state, actions = actions)
                    }
                    PageSlot(visible = state.page == 3) {
                        SettingsPage(state = state, actions = actions)
                    }
                }
            }

            // ---------- 液态玻璃底栏（悬浮在最上层） ----------
            val items = remember {
                listOf(
                    NavigationItem("运行", MiuixIcons.Play),
                    NavigationItem("更新", MiuixIcons.Download),
                    NavigationItem("插件", MiuixIcons.GridView),
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
 * 一页内容：**常驻组合**，但不可见时不可交互、也不绘制。
 *
 * 核心是 `Modifier.layout` 把不可见的页报成 **0×0**，而不是用 `drawWithContent` 不画。
 * 这两者的差别是致命的，本喵踩过：
 *
 * **Compose 的绘制遍历与触摸遍历是两套独立的东西。**
 * - `drawWithContent { if (visible) drawContent() }` 只是不画，节点**仍然是全屏大小**，
 *   于是照样参与命中测试、照样吃掉点击。表现为「在某些页面点空白处，别的页面的按钮被触发」。
 * - 报成 0×0 的节点不在任何触摸范围内，才算真的"不存在"。
 *
 * 代价与收益：页面仍会 measure（所以滚动位置等状态保留、切页不必重新组合），
 * 但不绘制、不可交互。切页时的重新组合正是掉帧的来源，这里把它省掉。
 *
 * 验证方式（必须做，别只看代码）：`tools/test-hidden-page-hit.sh` ——
 * 在运行页点一个"该页没有控件"的坐标，然后读 Prefs 看设置有没有被误改。
 * 用 drawWithContent 那版会真的改掉（实测 palette 从 cyan 变成 green）；这一版不会。
 */
@Composable
private fun PageSlot(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            if (visible) {
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            } else {
                layout(0, 0) { }
            }
        },
    ) {
        content()
    }
}
