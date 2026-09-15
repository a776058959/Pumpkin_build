// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 「插件」页：插件在这一页里完成全部增删改查。
//
//   商店   —— 从发布的 plugins.json 索引列出可用插件，一键安装 / 更新
//   已安装 —— 启用 / 停用 / 卸载
//   设置   —— 插件声明的设置项（控件由 App 渲染，插件自己不画 UI）
//   日志   —— 插件写的日志，插件没加载起来时靠它看原因
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
import androidx.compose.ui.unit.dp
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

/** 插件页。 */
@Composable
fun PluginsPage(
    state: PumpkinUiState,
    actions: PumpkinActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
    ) {
        SmallTitle(text = "插件")

        // ---------------------------------------------------------------- 商店
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
                    // 正在安装时不允许再拉列表，避免两件事同时改插件目录
                    enabled = state.pluginInstalling.isEmpty(),
                ) { Text("刷新") }

                if (state.pluginStore.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    state.pluginStore.forEach { item ->
                        StoreItem(item = item, state = state, actions = actions)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------------------------------------------------------------- 已安装
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "已安装",
                    style = MiuixTheme.textStyles.subtitle,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = state.pluginSummary,
                    style = MiuixTheme.textStyles.body1,
                )

                state.installedPlugins.forEach { p ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = p.name + " " + p.version,
                        style = MiuixTheme.textStyles.body2,
                    )
                    if (p.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = p.description,
                            style = MiuixTheme.textStyles.footnote1,
                            color = PumpkinColors.TextDim,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { actions.setPluginEnabledFromUi(p.id, !p.enabled) },
                            modifier = Modifier.weight(1f),
                            enabled = state.pluginInstalling.isEmpty(),
                        ) { Text(if (p.enabled) "停用" else "启用") }
                        Button(
                            onClick = { actions.removePluginFromUi(p.id) },
                            modifier = Modifier.weight(1f),
                            enabled = state.pluginInstalling.isEmpty(),
                        ) { Text("卸载") }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "插件目录：" + state.pluginDir,
                    style = MiuixTheme.textStyles.footnote1,
                    color = PumpkinColors.TextDim,
                )
                if (state.pluginLog.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.pluginLog.trim(),
                        style = MiuixTheme.textStyles.footnote1,
                        color = PumpkinColors.TextDim,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { actions.reloadPluginsFromUi() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.pluginInstalling.isEmpty(),
                ) { Text("重新加载插件") }
            }
        }

        // ---------------------------------------------------------------- 插件设置
        if (state.pluginSettings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "插件设置",
                        style = MiuixTheme.textStyles.subtitle,
                        color = PumpkinColors.TextDim,
                    )
                    state.pluginSettings.forEach { item ->
                        Spacer(modifier = Modifier.height(12.dp))
                        if (item.isToggle) {
                            Button(
                                onClick = {
                                    actions.onPluginSettingChangedFromUi(
                                        item.key,
                                        if (item.value == "true") "false" else "true",
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
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
    }
}

/** 商店里的一项：名字、版本、说明，以及安装 / 更新 / 已装好的提示。 */
@Composable
private fun StoreItem(
    item: PluginStoreItemView,
    state: PumpkinUiState,
    actions: PumpkinActions,
) {
    val installed = item.installedVersion.isNotEmpty()
    val upToDate = installed && item.installedVersion == item.version
    val installing = state.pluginInstalling == item.id

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
        enabled = state.pluginInstalling.isEmpty(),
        colors = if (!upToDate) {
            ButtonDefaults.buttonColorsPrimary()
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(
            when {
                installing -> "正在安装…"
                !installed -> "安装"
                upToDate -> "已安装（可重装）"
                else -> "更新到 " + item.version
            },
        )
    }
}
