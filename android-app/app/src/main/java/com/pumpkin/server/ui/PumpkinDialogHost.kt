// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// miuix 风格对话框。
//
// 背景：原来所有弹窗都是 android.app.AlertDialog（Material 深色主题下的原生样式），
// 和已经换成 miuix + 液态玻璃的主界面格格不入 —— 点「选择服务端版本」会突然跳出
// 一个纯黑方角的系统框。这里用 miuix 的 WindowDialog + BasicComponent + Button 重画，
// 让它和页面是同一套视觉语言。
//
// 设计要点：
//   * 用 WindowDialog 而不是自己搭 Dialog：它自带 miuix 的弹出/收起动画、
//     底部对齐（手机）与居中（大屏）的自适应、返回键与点外部关闭。
//   * 列表用 BasicComponent 逐行画：它是 miuix 里「一行带主副文案 + 尾部动作」
//     的标准组件，自带走马灯式的按下反馈和正确的行高/内边距。
//   * 确认型按钮用 Button（主色）+ TextButton（次要），与设置页保持一致。
//   * 只负责「画」和「把点击回传」，不含任何业务判断 —— 业务仍在 Java 侧。

package com.pumpkin.server.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 全应用唯一的对话框入口。
 *
 * 直接读 [PumpkinUiState.dialogKind]，为 0 时不渲染任何东西。
 *
 * 三个回调都把 kind 一起回传：对话框的语义由 Java 侧的 kind 决定，
 * 这里只负责画和转发，不做任何业务判断。
 */
@Composable
fun PumpkinDialogHost(
    state: PumpkinUiState,
    actions: PumpkinActions,
) {
    val kind = state.dialogKind
    if (kind == PumpkinDialogs.NONE) return

    WindowDialog(
        show = true,
        title = state.dialogTitle,
        summary = state.dialogMessage,
        onDismissRequest = { actions.onDialogDismissedFromUi(kind) },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ---- 列表型：逐行 ----
            if (state.dialogItems.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 版本多的时候对话框不能无限长；给个上限后内部滚动。
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.dialogItems.forEach { item ->
                        DialogRow(
                            item = item,
                            onClick = { actions.onDialogItemFromUi(kind, item.tag) },
                        )
                    }
                }
                Spacer(modifier = Modifier.padding(top = 8.dp))
            }

            // ---- 按钮区 ----
            val positive = state.dialogPositive
            val negative = state.dialogNegative
            if (positive != null || negative != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // 列表型对话框只有「取消」；确认型是「取消 + 确定」。
                    if (negative != null) {
                        TextButton(
                            text = negative,
                            onClick = { actions.onDialogDismissedFromUi(kind) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (positive != null) {
                        Button(
                            onClick = { actions.onDialogPositiveFromUi(kind) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(text = positive)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一行：主文案 +（可选）副文案，尾部可选勾。
 *
 * tag 为 null 且 enabled 为 false 的行画成灰色，用于「当前正在使用的版本不能删」这类提示。
 */
@Composable
private fun DialogRow(
    item: PumpkinDialogItem,
    onClick: () -> Unit,
) {
    BasicComponent(
        title = item.title,
        summary = item.summary,
        enabled = item.enabled,
        onClick = if (item.enabled) onClick else null,
        endActions = if (item.selected) {
            {
                Text(
                    text = "✓",
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            null
        },
    )
}
