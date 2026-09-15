// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 「插件」页：插件在这一页里完成全部增删改查。
//
// **分两段，不是一张长卡片**，理由是用得着的频率差很远：
//
//   已安装（默认）—— 装好之后 99% 的操作都在这里：改插件声明的设置、
//                     临时停用某个插件、不想要了卸载。这些操作要求「一进来就够得着」。
//   商店           —— 偶尔来一次：看看有什么新插件、更新一下。
//
// 之所以不把两段都摊在一条滚动里（本喵第一版就是那样）：商店卡片在最上面，
// 把常用的「已安装」挤到屏幕外 —— 每次改个设置都要先滚过一整张商店列表。
//
// **全程不需要 root**：安装只是把 dex 写进 App 自己的私有目录。

package com.pumpkin.server.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pumpkin.server.ui.LocalPumpkinDark
import com.pumpkin.server.ui.PluginStoreItemView
import com.pumpkin.server.ui.PumpkinActions
import com.pumpkin.server.ui.PumpkinColors
import com.pumpkin.server.ui.PumpkinUiState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 插件页的两个分段。与 PumpkinUiState.pluginTab 对应。 */
object PluginTabs {
    /** 管理已安装的插件（默认）。 */
    const val INSTALLED = 0

    /** 从商店装新插件。 */
    const val STORE = 1
}

/** 插件页。 */
@Composable
fun PluginsPage(
    state: PumpkinUiState,
    actions: PumpkinActions,
    modifier: Modifier = Modifier,
) {
    // 安装进行中时锁住交互：切段、刷新、卸载都会去动插件目录，
    // 和「正在写 plugin.dex」撞上会留下半截的 dex，插件就再也加载不起来了。
    val busy = state.pluginInstalling.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
    ) {
        SmallTitle(text = "插件")

        TabBar(current = state.pluginTab, enabled = !busy, actions = actions)

        Spacer(modifier = Modifier.height(12.dp))

        if (state.pluginTab == PluginTabs.STORE) {
            StoreSection(state = state, actions = actions, busy = busy)
        } else {
            InstalledSection(state = state, actions = actions, busy = busy)
        }
    }
}

/**
 * 顶部的两段切换。
 *
 * 用两个 Button 而不是 miuix 的 TabRow：设置页的外观选择已经是这个做法
 *（选中态用主色），这里跟着走，页与页之间是同一套视觉语言，也不引入新依赖。
 */
@Composable
private fun TabBar(
    current: Int,
    enabled: Boolean,
    actions: PumpkinActions,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TabButton("已安装", PluginTabs.INSTALLED, current, enabled, actions, Modifier.weight(1f))
        TabButton("商店", PluginTabs.STORE, current, enabled, actions, Modifier.weight(1f))
    }
}

@Composable
private fun TabButton(
    label: String,
    tab: Int,
    current: Int,
    enabled: Boolean,
    actions: PumpkinActions,
    modifier: Modifier,
) {
    Button(
        onClick = { actions.setPluginTabFromUi(tab) },
        modifier = modifier,
        enabled = enabled,
        colors = if (tab == current) {
            ButtonDefaults.buttonColorsPrimary()
        } else {
            ButtonDefaults.buttonColors()
        },
    ) { Text(label) }
}

// ================================================================ 已安装

@Composable
private fun InstalledSection(
    state: PumpkinUiState,
    actions: PumpkinActions,
    busy: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "已安装",
                style = MiuixTheme.textStyles.subtitle,
                color = PumpkinColors.TextDim,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.pluginSummary.ifEmpty { "还没有安装任何插件" },
                style = MiuixTheme.textStyles.body1,
            )

            if (state.installedPlugins.isEmpty()) {
                // 空态要给下一步，不能只留一句「没有」。
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "去「商店」看看有什么可以装的 —— 装插件不需要 root。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { actions.setPluginTabFromUi(PluginTabs.STORE) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("去商店") }
            }

            state.installedPlugins.forEach { p ->
                InstalledRow(item = p, actions = actions, busy = busy)
            }

            // ---- 诊断：出问题时才看，所以放在最下面 ----
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "插件目录：" + state.pluginDir,
                style = MiuixTheme.textStyles.footnote1,
                color = PumpkinColors.TextDim,
            )
            if (state.pluginLog.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "日志",
                    style = MiuixTheme.textStyles.footnote1,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(2.dp))
                // 日志是排查「插件为什么没生效」的唯一入口（加载失败的原因就写在里面），
                // 所以给它一个可读的底色块，而不是和说明文字混在一起。
                Text(
                    text = state.pluginLog.trim(),
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (LocalPumpkinDark.current) Color(0xFFCFD6E4) else Color(0xFF2A3140),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { actions.reloadPluginsFromUi() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) { Text("重新加载插件") }
        }
    }

    if (state.pluginSettings.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        PluginSettingsCard(state = state, actions = actions, busy = busy)
    }
}

/** 一个已安装的插件：启用 / 停用 / 卸载。 */
@Composable
private fun InstalledRow(
    item: PluginStoreItemView,
    actions: PumpkinActions,
    busy: Boolean,
) {
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = item.name + " " + item.version,
        style = MiuixTheme.textStyles.body2,
    )
    if (item.description.isNotEmpty()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.description,
            style = MiuixTheme.textStyles.footnote1,
            color = PumpkinColors.TextDim,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { actions.setPluginEnabledFromUi(item.id, !item.enabled) },
            modifier = Modifier.weight(1f),
            enabled = !busy,
        ) { Text(if (item.enabled) "停用" else "启用") }
        Button(
            onClick = { actions.removePluginFromUi(item.id) },
            modifier = Modifier.weight(1f),
            enabled = !busy,
        ) { Text("卸载") }
    }
}

/** 插件声明的设置项。控件由 App 渲染，插件自己不画 UI。 */
@Composable
private fun PluginSettingsCard(
    state: PumpkinUiState,
    actions: PumpkinActions,
    busy: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "插件设置",
                style = MiuixTheme.textStyles.subtitle,
                color = PumpkinColors.TextDim,
            )
            state.pluginSettings.forEach { item ->
                Spacer(modifier = Modifier.height(14.dp))
                if (item.isToggle) {
                    // 开关画成一个显示当前状态的按钮：点一下翻转。
                    // 不用 Switch 是因为 miuix 的 Switch 需要 remember 本地状态，
                    // 而这里值的唯一来源是覆盖板 —— 用按钮就没有「本地值和真值不一致」的机会。
                    Button(
                        onClick = {
                            actions.onPluginSettingChangedFromUi(
                                item.key,
                                if (item.value == "true") "false" else "true",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        colors = if (item.value == "true") {
                            ButtonDefaults.buttonColorsPrimary()
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                    ) { Text(item.title + "：" + if (item.value == "true") "开" else "关") }
                } else {
                    TextField(
                        value = item.value,
                        onValueChange = { actions.onPluginSettingChangedFromUi(item.key, it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = if (item.placeholder.isEmpty()) {
                            item.title
                        } else {
                            item.title + "（默认 " + item.placeholder + "）"
                        },
                        singleLine = true,
                    )
                }
                if (item.summary.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.summary,
                        style = MiuixTheme.textStyles.footnote1,
                        color = PumpkinColors.TextDim,
                    )
                }
            }
        }
    }
}

// ================================================================ 商店

@Composable
private fun StoreSection(
    state: PumpkinUiState,
    actions: PumpkinActions,
    busy: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "插件商店",
                style = MiuixTheme.textStyles.subtitle,
                color = PumpkinColors.TextDim,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.pluginStoreStatus.ifEmpty { "点「刷新」拉取可用插件列表。" },
                style = MiuixTheme.textStyles.footnote1,
                color = PumpkinColors.TextDim,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { actions.refreshPluginStoreFromUi() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
            ) { Text("刷新") }

            state.pluginStore.forEach { item ->
                StoreRow(item = item, state = state, actions = actions, busy = busy)
            }
        }
    }
}

/** 商店里的一项：名字、版本、说明，以及安装 / 更新 / 已装好的提示。 */
@Composable
private fun StoreRow(
    item: PluginStoreItemView,
    state: PumpkinUiState,
    actions: PumpkinActions,
    busy: Boolean,
) {
    val installed = item.installedVersion.isNotEmpty()
    val upToDate = installed && item.installedVersion == item.version
    val installing = state.pluginInstalling == item.id

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = item.name + " " + item.version,
        style = MiuixTheme.textStyles.body2,
    )
    if (item.description.isNotEmpty()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.description,
            style = MiuixTheme.textStyles.footnote1,
            color = PumpkinColors.TextDim,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Button(
        onClick = { actions.installPluginFromUi(item.id) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !busy,
        colors = if (upToDate) {
            ButtonDefaults.buttonColors()
        } else {
            ButtonDefaults.buttonColorsPrimary()
        },
    ) {
        Text(
            when {
                installing -> "正在安装…"
                !installed -> "安装"
                upToDate -> "已安装（点一下重装）"
                else -> "更新到 " + item.version
            },
        )
    }
}
