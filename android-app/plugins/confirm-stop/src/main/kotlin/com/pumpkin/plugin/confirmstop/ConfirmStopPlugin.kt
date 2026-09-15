package com.pumpkin.plugin.confirmstop

import com.pumpkin.plugin.PluginHost
import com.pumpkin.plugin.PluginKeys
import com.pumpkin.plugin.PluginSetting
import com.pumpkin.plugin.PumpkinPlugin

/**
 * 点「停止」前先确认一次。
 *
 * 为什么需要它：运行页上「启动」和「停止」并排，而停止会把正在游戏里的玩家直接踢下线。
 * 手机上一滑一碰就停服，是这一页代价最高的一次误触。
 *
 * 这是插件体系里的第二种形态：上面那个「控制台字号」改的是**显示**，
 * 这个改的是**行为** —— 但它同样只是往覆盖板写一个约定的键，
 * 由 App 在真正做那件事的地方去读（见 `PluginKeys.CONFIRM_STOP`）。
 * 插件本身**不碰 App 的任何内部状态**，所以它不可能把 App 弄崩。
 *
 * 注意这里用的是 [PluginSetting.Kind.TOGGLE]：开关的值是 `"true"` / `"false"` 字符串，
 * 控件由 App 渲染成按钮。插件自己一行 UI 代码都没有。
 */
class ConfirmStopPlugin : PumpkinPlugin {

    override val id: String = "confirm-stop"

    override val name: String = "停止前确认"

    override val version: String = "1.0"

    override val description: String =
        "点「停止」时先弹一次确认，避免误触把玩家踢下线。关掉它就恢复成一点即停。"

    override fun onLoad(host: PluginHost) {
        host.log("停止前确认插件已加载")

        host.declareSetting(
            PluginSetting(
                key = PluginKeys.CONFIRM_STOP,
                title = "停止前先确认",
                kind = PluginSetting.Kind.TOGGLE,
                defaultValue = "true",
                summary = "关掉「确认」后，运行页的「停止」会一点即停 —— 玩家会被立刻断开。",
            ),
        )
    }
}
