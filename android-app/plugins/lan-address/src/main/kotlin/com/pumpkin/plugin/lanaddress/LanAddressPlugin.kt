package com.pumpkin.plugin.lanaddress

import com.pumpkin.plugin.PluginHost
import com.pumpkin.plugin.PluginKeys
import com.pumpkin.plugin.PluginSetting
import com.pumpkin.plugin.PumpkinPlugin

/**
 * 把 App 对外显示的联机地址换成你指定的值。
 *
 * 为什么需要它：自动探测到的地址不一定是你想让玩家用的那个 ——
 * 手机可能同时有 Wi-Fi、热点、VPN 多张网卡，探测到的可能是错的那张；
 * 或者你做了端口转发 / 内网穿透，想让玩家用一个域名连。
 *
 * **它不会改手机真实的局域网 IP。** 那个地址由路由器 DHCP 或系统 WLAN 静态设置决定，
 * 安卓应用没有任何 API 能改。但服务端监听的是全部网卡，
 * 所以真正决定玩家能不能连上的，就是「你告诉玩家的那个地址」—— 也就是这里改的东西。
 */
class LanAddressPlugin : PumpkinPlugin {

    override val id: String = "lan-address"

    override val name: String = "局域网地址"

    override val version: String = "1.0"

    override val description: String = "覆盖运行页显示的联机地址（IP 或域名）与端口。"

    override fun onLoad(host: PluginHost) {
        // 打日志是排查插件问题的第一手段：App 的日志区能看到这行，
        // 就知道插件到底有没有被加载起来。
        host.log(
            "局域网地址插件已加载，当前探测到 " +
                "${host.detectedLanHost()}:${host.detectedLanPort()}",
        )

        host.declareSetting(
            PluginSetting(
                key = PluginKeys.LAN_HOST,
                title = "联机地址",
                kind = PluginSetting.Kind.TEXT,
                summary = "留空则用自动探测到的地址。可填 IP 或域名。",
                placeholder = host.detectedLanHost(),
            ),
        )

        host.declareSetting(
            PluginSetting(
                key = PluginKeys.LAN_PORT,
                title = "Java 端口",
                kind = PluginSetting.Kind.TEXT,
                summary = "留空则显示服务端默认的 25565。基岩端口（19132）不受影响。",
                placeholder = "25565",
            ),
        )
    }
}
