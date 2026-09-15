// Copyright 2026, Pumpkin 南瓜坞 contributors
// SPDX-License-Identifier: Apache-2.0
//
// Compose 状态模型：MainActivity 里原本直接 setText 的地方，改成写这里的 state，
// Compose 侧读 state 自动重组。业务逻辑（下载/启动/版本管理）仍留在 Java 侧不动。

package com.pumpkin.server.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 三页共享的可观察状态。字段与原 Java UI 的控件一一对应，
 * 命名加注释说明它原来对应哪个 TextView/Button/ProgressBar。
 *
 * 所有写操作都在主线程（MainActivity 的 ticker / 回调都在主线程）。
 *
 * 说明：Java 侧通过 `getXxx() / setXxx()` 访问这些属性（Kotlin 的 var 会自动生成），
 * 列表属性同名生成 `getXxx()` 返回 MutableList，Java 侧可直接 add/clear。
 */
class PumpkinUiState {

    // ---------------------------------------------------------------- 页面导航

    /** 当前页：0=运行 1=更新 2=设置（对应 PAGE_RUN/PAGE_UPDATE/PAGE_SETTINGS）。 */
    var page by mutableIntStateOf(0)

    /** 更新页小红点（原 dotView）。 */
    var showUpdateDot by mutableStateOf(false)

    // ---------------------------------------------------------------- 运行页

    /** 状态点颜色用：是否运行中。 */
    var running by mutableStateOf(false)

    /** 状态文字：运行中 0:12 / 已停止（退出码 N）/ 未运行。 */
    var statusText by mutableStateOf("未运行")

    /** 是否处于「已停止且有退出码」状态（决定状态点颜色）。 */
    var hasExitCode by mutableStateOf(false)

    /** 局域网地址行。 */
    var addrText by mutableStateOf("")

    /** 数据目录行。 */
    var dirText by mutableStateOf("")

    /** 控制台日志全文（原 logView 的文本）。空串表示还没有日志。 */
    var logText by mutableStateOf("")

    /** 命令输入框内容。 */
    var command by mutableStateOf("")

    // ---------------------------------------------------------------- 更新页

    /** 当前运行版本（原 versionCurrent）。 */
    var versionCurrent by mutableStateOf("")

    /** 已安装版本统计 + 占用（原 versionInstalled）。 */
    var versionInstalled by mutableStateOf("")

    /** 选中的待下载版本描述（原 versionSelected）。 */
    var versionSelected by mutableStateOf("")

    /** 版本提示（原 versionHint）。 */
    var versionHint by mutableStateOf("")

    /** 本地版本数量描述（原 localCount）。 */
    var localCount by mutableStateOf("")

    /** 下载进度 0..1；null 表示不显示进度条。 */
    var downloadProgress by mutableFloatStateOf(0f)
    var downloadProgressVisible by mutableStateOf(false)

    /** 下载区提示文字（原 downloadHint）。 */
    var downloadHint by mutableStateOf("点「下载」开始。下载中按钮会变成「暂停」，暂停后才能删除下载任务。")

    /** 主下载按钮文案：下载 / 暂停 / 继续（原三态按钮）。 */
    var installButtonText by mutableStateOf("下载")
    var installButtonEnabled by mutableStateOf(false)

    // ---------------------------------------------------------------- App 自更新的下载

    /**
     * App 更新包的下载状态。
     *
     * 刻意与服务端下载那套字段分开：两者可能同时存在（一边下服务端、一边下 App），
     * 共用一套进度会让界面互相覆盖。
     */
    var appUpdateVisible by mutableStateOf(false)

    /**
     * App 更新包的下载进度。
     *
     * **-1 表示总大小未知**（服务端没给 Content-Length）：这时界面隐藏进度条、只显示已下载量，
     * 而不是把进度条钉在 0% —— 那看起来像卡死了。
     */
    var appUpdateProgress by mutableFloatStateOf(0f)
    var appUpdateText by mutableStateOf("")

    /** App 更新包的下载源显示名，例如「官方直连」或某个加速源。 */
    var appUpdateSourceLabel by mutableStateOf("")

    // ---------------------------------------------------------------- 插件

    /**
     * 已加载插件的摘要，例如「已加载 1 个：局域网地址」。
     *
     * 插件是运行时用 DexClassLoader 加载的（见 PluginManager），
     * 加载失败只体现在这段文字与 [pluginLog] 里，不会让 App 崩。
     */
    var pluginSummary by mutableStateOf("")

    /** 插件目录路径，显示出来让使用者知道往哪放插件。 */
    var pluginDir by mutableStateOf("")

    /** 插件日志（插件通过 PluginHost.log 写的）。 */
    var pluginLog by mutableStateOf("")

    /** 插件声明的设置项，由 Java 侧填。 */
    val pluginSettings = mutableStateListOf<PluginSettingView>()

    /** 控制台字号倍率，来自插件覆盖板（见 PluginKeys.CONSOLE_FONT_SCALE）。1 = 内置字号。 */
    var consoleFontScale by mutableFloatStateOf(1f)

    /**
     * 点「停止」前是否先确认，来自插件覆盖板（见 PluginKeys.CONFIRM_STOP）。
     *
     * 默认 true —— 与 App 内置行为一致，插件只能把它关掉。
     */
    var confirmStop by mutableStateOf(true)

    /**
     * 插件页当前的分段（见 pages/PluginTabs）。
     *
     * 默认「已安装」：装好插件之后，改设置 / 停用 / 卸载都在这一段，
     * 而商店只是偶尔来看一次。默认落在常用的那一段，省掉每次多一次点击。
     *
     * 不落盘：它是「刚才在看哪一栏」这种会话内的位置，不是用户的偏好设置。
     */
    var pluginTab by mutableIntStateOf(0)

    /** 已安装的插件（含启用状态），由 Java 侧填。 */
    val installedPlugins = mutableStateListOf<PluginStoreItemView>()

    /** 商店里的可用插件（来自插件的 plugins.json 索引）。 */
    val pluginStore = mutableStateListOf<PluginStoreItemView>()

    /** 商店状态提示：正在拉取 / 失败原因 / 共几个。 */
    var pluginStoreStatus by mutableStateOf("")

    /** 正在安装的插件 id；空串表示当前没有安装进行中。 */
    var pluginInstalling by mutableStateOf("")

    /** 删除下载任务按钮可用性。 */
    var deleteTaskEnabled by mutableStateOf(false)

    /** 检查更新按钮是否可用（下载中禁用）。 */
    var checkEnabled by mutableStateOf(true)

    // ---------------------------------------------------------------- 设置页

    /** 运行模式描述（原 modeValue）。 */
    var modeValue by mutableStateOf("")

    /** 下载源输入（原 apiInput / repoInput / mirrorInput 的初值）。 */
    var apiBase by mutableStateOf("")
    var repo by mutableStateOf("")
    var mirror by mutableStateOf("")

    /**
     * 选中的配色方案 id（见 PumpkinPalettes）。只管**色相**，不管明暗。
     *
     * 空串表示还没读到偏好，PumpkinTheme 会回落到默认。
     */
    var paletteId by mutableStateOf("")

    /**
     * 主题明暗模式："dark" / "light" / "auto"（跟随系统）。
     *
     * 与 [paletteId] 是**两条独立的轴** —— 色相和明暗可以自由组合。
     */
    var themeMode by mutableStateOf(PumpkinPalettes.MODE_DARK)

    /** 南瓜坞版本号（关于页用）。 */
    var appVersion by mutableStateOf("")

    // ---------------------------------------------------------------- 版本列表

    /** 可下载版本（原 available）。 */
    val availableTags = mutableStateListOf<String>()

    /** 已被选中的 tag（原 selected）。 */
    var selectedTag by mutableStateOf<String?>(null)

    /** 已安装版本 tag 列表。 */
    val installedTags = mutableStateListOf<String>()

    // ---------------------------------------------------------------- 对话框

    /**
     * 对话框类型。0 表示不显示。
     *
     * 为什么用「一个 kind + 一组字段」而不是给每个对话框建一个对象：
     * 这些对话框的内容全部由 Java 侧的现有业务逻辑填（版本列表、模式选项…），
     * 逻辑不动，只把「画」的部分从原生 AlertDialog 换成 miuix 组件。
     * Java 侧仍然是唯一知道「哪个 kind 对应什么行为」的地方，
     * Compose 只负责把 state 画出来并把点击回传。
     */
    var dialogKind by mutableIntStateOf(0)

    var dialogTitle by mutableStateOf<String?>(null)
    var dialogMessage by mutableStateOf<String?>(null)

    /** 列表型对话框的条目；空表示这是纯确认型对话框。 */
    val dialogItems = mutableStateListOf<PumpkinDialogItem>()

    /** 按钮文案；null 表示不显示该按钮。 */
    var dialogPositive by mutableStateOf<String?>(null)
    var dialogNegative by mutableStateOf<String?>(null)

    /** 是否有对话框正在显示。读 dialogKind 所以能触发重组。 */
    val dialogVisible: Boolean
        get() = dialogKind != 0

    /** 打开一个确认型对话框（只有标题/正文 + 按钮）。 */
    fun showConfirmDialog(kind: Int, title: String, message: String?, positive: String, negative: String?) {
        dialogItems.clear()
        dialogKind = kind
        dialogTitle = title
        dialogMessage = message
        dialogPositive = positive
        dialogNegative = negative
    }

    /** 打开一个列表型对话框。 */
    fun showListDialog(kind: Int, title: String, message: String?, items: List<PumpkinDialogItem>) {
        dialogItems.clear()
        dialogItems.addAll(items)
        dialogKind = kind
        dialogTitle = title
        dialogMessage = message
        dialogPositive = null
        dialogNegative = "取消"
    }

    /** 关闭对话框。 */
    fun dismissDialog() {
        dialogKind = 0
        dialogItems.clear()
    }

    // ---------------------------------------------------------------- 一次性事件

    /**
     * Toast / 对话框这类一次性事件用计数器驱动，避免重组时重复弹。
     * Compose 侧 LaunchedEffect(key) 消费后不需要清空，因为 key 变了才会再触发。
     */
    var toastSeq by mutableIntStateOf(0)
    var toastMessage by mutableStateOf("")

    fun toast(msg: String) {
        toastMessage = msg
        toastSeq++
    }

    /** 供诊断：把状态打成一行，便于 logcat 排查。 */
    fun debugLine(): String =
        "page=$page running=$running logLen=${logText.length} " +
            "ver=$versionCurrent installed=${installedTags.size} available=${availableTags.size}"
}

/**
 * 设置页里渲染的一个插件设置项。
 *
 * 插件只**声明**（标题、形态、默认值、说明），控件由 App 渲染 ——
 * 这样插件不必依赖 Compose，编译产物就是一个很小的纯 Kotlin dex。
 *
 * @param isToggle true 表示开关（值为 "true"/"false"），false 表示单行文本。
 */
data class PluginSettingView(
    val key: String,
    val title: String,
    val isToggle: Boolean,
    val value: String,
    val summary: String,
    val placeholder: String,
)


/**
 * 插件商店里的一项。
 *
 * @param installedVersion 已安装的版本；空串表示没装过。
 * @param enabled 已安装时是否启用（未安装时无意义）。
 */
data class PluginStoreItemView(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val installedVersion: String,
    val enabled: Boolean,
)
