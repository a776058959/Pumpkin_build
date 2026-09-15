// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// 对话框的「契约」：kind 常量 + 条目数据类。
//
// 为什么单独放一个文件、而不是塞进 PumpkinUiState：
//   这些 kind 是 Java 侧（MainActivity）和 Compose 侧共同的约定，
//   两边都要引用。放在独立顶层对象里，任何一侧 import 进来就能用，
//   不必依赖 PumpkinUiState 的实例，语义也更清楚。

package com.pumpkin.server.ui

import androidx.compose.runtime.Immutable

/**
 * 对话框类型。
 *
 * Java 侧调用 state.showListDialog(PumpkinDialogs.KIND_START_MODE, …) 打开，
 * Compose 侧按 kind 决定点击后回传哪个 actions 方法。
 * 新增对话框时三处都要加：这里的常量、Java 的 show 调用、Compose 的 when 分支。
 */
object PumpkinDialogs {

    /** 不显示。 */
    const val NONE = 0

    /** 选择服务端启动方式（普通 / Root）。 */
    const val START_MODE = 1

    /** 选择要下载的服务端版本。 */
    const val PICK_DOWNLOAD = 2

    /** 更新前需要先停止服务端 —— 确认型。 */
    const val NEED_STOP = 3

    /** 选择要运行的已安装版本。 */
    const val PICK_RUN = 4

    /** 选择要删除的已安装版本。 */
    const val PICK_DELETE = 5

    /** 通用二次确认（清理数据等）—— 确认型。 */
    const val CONFIRM = 6

    /** 检查到壳有新版本，询问是否下载 —— 确认型。 */
    const val SHELL_UPDATE = 7

    /**
     * 每个 kind 的「确定」按钮含义。
     * 列表型对话框点条目即生效（等同于确定并带上索引），
     * 确认型的确定按钮不带索引（索引传 -1）。
     */
    fun isList(kind: Int): Boolean = when (kind) {
        PICK_DOWNLOAD, PICK_RUN, PICK_DELETE, START_MODE -> true
        else -> false
    }
}

/**
 * 对话框里的一行。
 *
 * @param title     主文案，如版本号
 * @param summary   次要说明，如大小 / 状态
 * @param selected  是否当前选中项（画一个勾）
 * @param enabled   是否可点。用于「不能删除正在使用的版本」这类需要置灰的情况。
 * @param tag       业务侧标识（版本 tag 等），点击时原样回传，Compose 不解释它。
 */
@Immutable
data class PumpkinDialogItem(
    val title: String,
    val summary: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val tag: String? = null,
)
