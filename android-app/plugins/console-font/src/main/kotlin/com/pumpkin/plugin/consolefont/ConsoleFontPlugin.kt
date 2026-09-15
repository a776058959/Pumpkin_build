package com.pumpkin.plugin.consolefont

import com.pumpkin.plugin.PluginHost
import com.pumpkin.plugin.PluginKeys
import com.pumpkin.plugin.PluginSetting
import com.pumpkin.plugin.PumpkinPlugin

/**
 * 调运行页控制台的字号。
 *
 * 为什么需要它：内置字号是 10.5sp，在手机上看日志偏小；
 * 想一次看更多行、或者反过来想放大看清，都得自己改代码才行。
 *
 * 这是插件体系里最小的一种插件：只声明一个设置项、只写一个覆盖键，
 * 控件与生效都在 App 侧 —— 插件自己几 KB，不含任何 UI 代码。
 */
class ConsoleFontPlugin : PumpkinPlugin {

    override val id: String = "console-font"

    override val name: String = "控制台字号"

    override val version: String = "1.0"

    override val description: String = "调整运行页控制台里日志文字的大小（0.5~3 倍）。"

    override fun onLoad(host: PluginHost) {
        host.log("控制台字号插件已加载")

        host.declareSetting(
            PluginSetting(
                key = PluginKeys.CONSOLE_FONT_SCALE,
                title = "字号倍率",
                kind = PluginSetting.Kind.TEXT,
                summary = "1 = 内置字号（10.5sp）；范围 0.5~3，超出会被夹回 1。",
                placeholder = "1.5",
            ),
        )
    }
}
