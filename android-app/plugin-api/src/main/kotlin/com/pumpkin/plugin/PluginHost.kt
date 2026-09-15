package com.pumpkin.plugin

/**
 * 插件设置项的形态。
 *
 * 刻意做成**声明式**而不是让插件自己画 UI：插件不需要依赖 Compose，
 * 编译产物就是一个纯 Kotlin 的 dex，体积和耦合都最小。
 * App 负责把这些声明渲染成设置页里的控件。
 */
data class PluginSetting(
    /** 覆盖板里的键。约定用 `<插件id>.<名字>` 形式，避免撞车。 */
    val key: String,
    /** 控件标题。 */
    val title: String,
    val kind: Kind,
    /** 用户没设置过时用的值。 */
    val defaultValue: String = "",
    /** 控件下方的说明文字。 */
    val summary: String = "",
    /** [Kind.TEXT] 时的输入框提示。 */
    val placeholder: String = "",
) {
    enum class Kind {
        /** 单行文本输入。 */
        TEXT,

        /** 开关，值为 "true" / "false"。 */
        TOGGLE,
    }
}

/**
 * 插件能对宿主做的事。
 *
 * 设计取向：**插件不直接改 App 的内部状态，而是往「覆盖板」上写约定的键**，
 * App 在渲染与行为发生的地方读这些键。这样：
 * - 插件不需要（也无法）依赖 App 的内部类，接口能长期稳定；
 * - App 侧哪些地方可被覆盖是**显式**的，一眼能看出插件能影响什么、不能影响什么；
 * - 插件写坏了最多是某个值不对，不会把 App 弄崩。
 */
interface PluginHost {

    /** 打日志。会进 App 的日志区，方便排查是哪个插件出的问题。 */
    fun log(message: String)

    /**
     * 写覆盖板。value 传 null 表示清除覆盖，该处恢复 App 的默认行为。
     *
     * 键的约定见 [PluginKeys]；用了未约定的键不会报错，只是没人读。
     */
    fun set(key: String, value: String?)

    /** 读覆盖板（插件自己之前写进去的值）。 */
    fun get(key: String): String?

    /** 声明一个设置项，App 会把控件渲染在「插件」卡片里。 */
    fun declareSetting(setting: PluginSetting)

    // ---------------------------------------------------------------- 只读的环境信息

    /**
     * App 自动探测到的局域网地址与端口。
     *
     * 让插件能基于真实值做修改（例如「探测到的不对，我要覆盖成另一个」），
     * 而不是只能盲填一个字符串。
     */
    fun detectedLanHost(): String

    fun detectedLanPort(): Int
}

/**
 * 覆盖板的键约定。
 *
 * 新增可覆盖项时在这里加常量，并在 App 对应渲染处读取 ——
 * 这是「插件能改什么」的唯一权威清单。
 */
object PluginKeys {

    /**
     * 对外告知的联机地址（主机部分）。App 会在运行页显示它，也是使用者拿去告诉玩家的那个。
     *
     * 说明：安卓应用**无法修改手机真实的局域网 IP**（那是路由器 DHCP 或系统 WLAN 静态设置
     * 决定的）。这个键改的是 App 对外呈现与使用的地址 —— 服务器本身监听全网卡，
     * 所以决定玩家能不能连上的，就是这里。
     */
    const val LAN_HOST = "lan.host"

    /** 对外告知的端口。 */
    const val LAN_PORT = "lan.port"
}
