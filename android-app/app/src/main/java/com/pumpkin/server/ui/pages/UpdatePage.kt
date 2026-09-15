// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 「更新」页：当前版本 / 下载（三态按钮 + 进度）/ 本地版本列表 / 服务端版本选择。
// 对应原 Java 版 MainActivity.buildUpdatePage()。

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
import androidx.compose.ui.unit.sp
import com.pumpkin.server.ui.PumpkinActions
import com.pumpkin.server.ui.PumpkinColors
import com.pumpkin.server.ui.PumpkinUiState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 更新页。 */
@Composable
fun UpdatePage(
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
        SmallTitle(text = "服务端版本")

        // ---------------------------------------------------------------- 版本信息
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "当前版本",
                    style = MiuixTheme.textStyles.subtitle,
                    color = PumpkinColors.TextDim,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = state.versionCurrent.ifEmpty { "尚未安装服务端" },
                    style = MiuixTheme.textStyles.title3,
                )

                if (state.versionInstalled.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.versionInstalled,
                        style = MiuixTheme.textStyles.footnote1,
                        color = PumpkinColors.TextDim,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = state.versionSelected.ifEmpty { "未选择要下载的版本" },
                    fontSize = 15.sp,
                    color = PumpkinColors.Text,
                )

                if (state.versionHint.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.versionHint,
                        style = MiuixTheme.textStyles.footnote1,
                        color = PumpkinColors.TextDim,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { actions.doCheckFromUi() },
                        modifier = Modifier.weight(1f),
                        enabled = state.checkEnabled,
                    ) { Text("检查更新") }
                    Button(
                        onClick = { actions.showVersionPickerFromUi() },
                        modifier = Modifier.weight(1f),
                    ) { Text("选择版本") }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------------------------------------------------------------- 下载
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 这一块**只在有事发生时才显示文字**：正在下载多少、装好了、失败了。
                // 以前这里常驻一句「点「下载」开始。下载中按钮会变成「暂停」，暂停后才能删除
                // 下载任务。」—— 那是把按钮自己的行为念了一遍，纯噪音。
                if (state.downloadHint.isNotEmpty()) {
                    Text(
                        text = state.downloadHint,
                        style = MiuixTheme.textStyles.footnote1,
                        color = PumpkinColors.TextDim,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (state.downloadProgressVisible) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = state.downloadProgress,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Button(
                    onClick = { actions.onDownloadButtonClickedFromUi() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.installButtonEnabled,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(state.installButtonText) }

                // 只在能删的时候出现：不能删的时候画一个灰按钮，不如不给。
                if (state.deleteTaskEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { actions.onDeleteTaskClickedFromUi() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("删除下载任务") }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------------------------------------------------------------- 本地版本
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "本地版本",
                    style = MiuixTheme.textStyles.subtitle,
                    color = PumpkinColors.TextDim,
                )
                if (state.localCount.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.localCount,
                        style = MiuixTheme.textStyles.footnote1,
                        color = PumpkinColors.TextDim,
                    )
                }

                if (state.installedTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    state.installedTags.forEach { tag ->
                        val isCurrent = tag == state.versionCurrent
                        Text(
                            text = (if (isCurrent) "● " else "○ ") + tag,
                            fontSize = 14.sp,
                            color = if (isCurrent) PumpkinColors.Accent else PumpkinColors.Text,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { actions.showDeleteDialogFromUi() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("删除已安装的版本") }
                }
            }
        }
    }
}
