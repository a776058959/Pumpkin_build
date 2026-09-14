// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// Java → Compose 的入口。
//
// setContent 必须在这里调用：Compose 的 @Composable lambda 在字节码里带 $composer 参数，
// Java 无法直接构造它，所以由 Kotlin 侧持有这一步，Java 只负责传入状态与动作。

@file:JvmName("PumpkinUiBridge")

package com.pumpkin.server.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * 把 Compose 界面挂到 Activity 上。
 *
 * @param activity 宿主（ComponentActivity 及其子类）。
 * @param state 共享状态，Java 侧刷新业务时写它。
 * @param actions 界面能触发的动作，由 Java 侧实现。
 */
fun launchPumpkinUi(
    activity: ComponentActivity,
    state: PumpkinUiState,
    actions: PumpkinActions,
) {
    activity.setContent {
        PumpkinApp(state = state, actions = actions)
    }
}
