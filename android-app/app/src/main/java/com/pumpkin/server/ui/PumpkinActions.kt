// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// Compose 层与 Java 业务层之间的唯一契约。
//
// 为什么不直接让 Compose 拿 MainActivity：
//   1. 构建层面 —— Kotlin 编译期无法完整解析本模块的 Java 源（拿不到父类，
//      只能建出「父类 = Any」的残缺轻量类），引用 MainActivity 会直接编译失败。
//   2. 设计层面 —— UI 不该认识具体 Activity。只依赖这组动作，UI 可独立预览/测试，
//      也逼着「状态从哪来、动作往哪去」保持单向。
//
// 方法名与 MainActivity 里既有的 xxxFromUi() 一一对应（Java 侧 implements 本接口）。

package com.pumpkin.server.ui

/** 界面能触发的全部动作。实现方是 MainActivity（Java）。 */
interface PumpkinActions {

    /** 底部导航切到第 index 页（0=运行 1=更新 2=设置）。 */
    fun onNavItemSelected(index: Int)

    // ---------------- 运行页 ----------------

    fun startServerFromUi()

    fun stopServerFromUi()

    fun openBatterySettingsFromUi()

    fun copyAddressFromUi()

    fun showSwitchDialogFromUi()

    /** 发送一条服务端控制台命令。 */
    fun sendCommandFromUi(cmd: String)

    // ---------------- 更新页 ----------------

    fun doCheckFromUi()

    fun showVersionPickerFromUi()

    fun onDownloadButtonClickedFromUi()

    fun onDeleteTaskClickedFromUi()

    fun showDeleteDialogFromUi()

    // ---------------- 设置页 ----------------

    fun showModeDialogFromUi()

    fun copyWorkDirFromUi()

    fun checkAppUpdateFromUi()

    /** 保存下载源三件套；参数为 null 表示按空处理。 */
    fun saveSourceFromUi(api: String?, repo: String?, mirror: String?)

    /** 点选某个 GitHub 加速源；prefix 为空串表示直连。 */
    fun applyMirrorFromUi(prefix: String?)

    fun clearVersionsFromUi()

    fun clearGameDataFromUi()

    fun clearAllFromUi()

    // ---------------- 对话框 ----------------

    /**
     * 对话框里的列表项被点。
     *
     * @param kind 哪个对话框（PumpkinDialogs 常量），Java 侧据此分派。
     * @param tag  该行的业务标识（版本 tag 等）；纯确认型对话框不会走这里。
     */
    fun onDialogItemFromUi(kind: Int, tag: String?)

    /** 对话框的确定按钮。仅确认型（isList == false）会触发。 */
    fun onDialogPositiveFromUi(kind: Int)

    /** 取消按钮，以及点外部 / 返回键关闭。Java 侧应在这里清理 pending 状态。 */
    fun onDialogDismissedFromUi(kind: Int)
}
