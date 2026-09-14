// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 「设置」页：启动方式 / 下载源（含 GitHub 加速源多选）/ 清理数据 / 关于。
// 对应原 Java 版 MainActivity.buildSettingsPage()。

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
import com.pumpkin.server.MirrorOption
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

/** 设置页。 */
@Composable
fun SettingsPage(
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
        SmallTitle(text = "设置")

        // ---------------------------------------------------------------- 启动方式
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "服务端启动方式",
                    style = MiuixTheme.textStyles.subtitle,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.modeValue,
                    style = MiuixTheme.textStyles.body1,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "普通模式开箱即用。若启动时报权限错误，切到 Root 模式再试。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { actions.showModeDialogFromUi() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("切换启动方式") }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------------------------------------------------------------- 加速源快选
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "GitHub 加速源",
                    style = MiuixTheme.textStyles.subtitle,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "点一下即填入下面的「下载镜像前缀」。下载失败会自动换源，选中项会排在第一位优先尝试。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(10.dp))

                // 每个加速源一个按钮。选中当前镜像的项用主色高亮。
                MirrorOption.entries.forEach { opt ->
                    val selectedNow = state.mirror.trim() == opt.prefix
                    Button(
                        onClick = { actions.applyMirrorFromUi(opt.prefix) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (selectedNow) {
                            ButtonDefaults.buttonColorsPrimary()
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                    ) {
                        Text(
                            if (selectedNow) "✓ ${opt.label}" else opt.label
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------------------------------------------------------------- 下载源
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "下载源",
                    style = MiuixTheme.textStyles.subtitle,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "平时不用改。下载失败时会自动换源，这里可手动指定。",
                    style = MiuixTheme.textStyles.footnote1,
                    color = PumpkinColors.TextDim,
                )

                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = state.apiBase,
                    onValueChange = { state.apiBase = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "API 地址，如 https://api.github.com",
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = state.repo,
                    onValueChange = { state.repo = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "仓库，如 owner/repo",
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = state.mirror,
                    onValueChange = { state.mirror = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = "下载镜像前缀（可留空）",
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        actions.saveSourceFromUi(state.apiBase, state.repo, state.mirror)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text("保存下载源") }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------------------------------------------------------------- 清理
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "清理数据",
                    style = MiuixTheme.textStyles.subtitle,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { actions.clearVersionsFromUi() },
                        modifier = Modifier.weight(1f),
                    ) { Text("清服务端版本") }
                    Button(
                        onClick = { actions.clearGameDataFromUi() },
                        modifier = Modifier.weight(1f),
                    ) { Text("清游戏数据") }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { actions.clearAllFromUi() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("全部清空") }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------------------------------------------------------------- 关于
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "关于",
                    style = MiuixTheme.textStyles.subtitle,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "版本 " + state.shellVersion,
                    style = MiuixTheme.textStyles.body1,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { actions.checkShellUpdateFromUi() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("检查更新") }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { actions.openBatterySettingsFromUi() },
                        modifier = Modifier.weight(1f),
                    ) { Text("电池优化设置") }
                    Button(
                        onClick = { actions.copyWorkDirFromUi() },
                        modifier = Modifier.weight(1f),
                    ) { Text("数据目录路径") }
                }
            }
        }
    }
}
