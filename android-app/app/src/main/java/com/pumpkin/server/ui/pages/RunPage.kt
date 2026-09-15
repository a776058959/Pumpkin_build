// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 「运行」页：状态卡（状态点 / 局域网地址 / 数据目录 / 启停按钮）+ 控制台（实时日志 + 命令输入）。
// 对应原 Java 版 MainActivity.buildRunPage()。

package com.pumpkin.server.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pumpkin.server.ui.LocalPumpkinDark
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

/**
 * 运行页。
 *
 * 所有按钮都通过 PumpkinActions 回调到 Java 侧原有方法，业务逻辑一行不改。
 */
@Composable
fun RunPage(
    state: PumpkinUiState,
    actions: PumpkinActions,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    // 日志增长时自动滚到底部（对应原 logScroll.fullScroll(FOCUS_DOWN)）。
    LaunchedEffect(state.logText.length) {
        if (state.logText.isNotEmpty()) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            // 底部留出悬浮底栏的高度，避免最后一张卡被挡住。
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
    ) {
        SmallTitle(text = "运行")

        // ---------------------------------------------------------------- 状态卡
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 状态行：圆点 + 文字
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val dotColor = when {
                        state.running -> PumpkinColors.Ok
                        state.hasExitCode -> Color(0xFFE85D5D)
                        else -> PumpkinColors.TextDim
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.statusText,
                        style = MiuixTheme.textStyles.title3,
                    )
                }

                if (state.addrText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SelectionContainer {
                        Text(
                            text = state.addrText,
                            fontSize = 15.sp,
                            color = PumpkinColors.Text,
                        )
                    }
                }

                if (state.dirText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.dirText,
                        style = MiuixTheme.textStyles.footnote1,
                        color = PumpkinColors.TextDim,
                    )
                }

                // 启停按钮
                Spacer(modifier = Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { actions.startServerFromUi() },
                        modifier = Modifier.weight(1f),
                        enabled = !state.running,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) { Text("启动") }
                    Button(
                        onClick = { actions.stopServerFromUi() },
                        modifier = Modifier.weight(1f),
                        enabled = state.running,
                    ) { Text("停止") }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { actions.openBatterySettingsFromUi() },
                        modifier = Modifier.weight(1f),
                    ) { Text("电池优化") }
                    Button(
                        onClick = { actions.copyAddressFromUi() },
                        modifier = Modifier.weight(1f),
                    ) { Text("复制地址") }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { actions.showSwitchDialogFromUi() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("选择运行版本") }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---------------------------------------------------------------- 控制台卡
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                SmallTitle(
                    text = "控制台",
                    insideMargin = PaddingValues(0.dp),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 日志区：等宽字体 + 可选中，高度与原版 260dp 一致。
                //
                // 底色与日志字色都必须跟着明暗走。以前这里是写死的
                //   .background(Color(0x33000000))  +  color = Color(0xFFCFD6E4)
                // —— 20% 黑压在白卡片上是一块灰，浅灰字压在这块灰上几乎看不见，
                // 浅色模式下控制台等于瞎了。深色下这两个值仍然合适，所以按明暗二分。
                val dark = LocalPumpkinDark.current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (dark) Color(0x33000000) else Color(0x0F000000))
                        .padding(10.dp),
                ) {
                    val display = if (state.logText.isEmpty()) {
                        "（还没有日志）\n启动服务器后这里会实时输出"
                    } else {
                        state.logText
                    }
                    SelectionContainer {
                        Text(
                            text = display,
                            // 字号可由插件覆盖（「控制台字号」插件）：内置 10.5sp 在手机上偏小。
                            fontSize = (10.5f * state.consoleFontScale).sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (state.logText.isEmpty()) {
                                PumpkinColors.TextDim
                            } else {
                                // 深色下用偏亮的冷灰；浅色下必须用深色，否则读不出来。
                                if (dark) Color(0xFFCFD6E4) else Color(0xFF2A3140)
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 命令行
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = state.command,
                        onValueChange = { state.command = it },
                        modifier = Modifier.weight(1f),
                        label = "命令：list / op 玩家名 / stop",
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { actions.sendCommandFromUi(state.command) },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) { Text("发送") }
                }
            }
        }
    }
}
