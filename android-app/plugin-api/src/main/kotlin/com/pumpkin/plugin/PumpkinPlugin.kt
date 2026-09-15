package com.pumpkin.plugin

/**
 * 插件入口。
 *
 * **必须是 public 类，且有一个无参构造函数** —— App 是用反射按类名实例化的
 *（见 plugin.json 的 entry 字段）。构造函数里不要做耗时或需要 Context 的事，
 * 那些放到 [onLoad] 里做。
 */
interface PumpkinPlugin {

    /** 唯一 id，小写字母/数字/连字符。用于目录名与偏好存储的命名空间。 */
    val id: String

    /** 显示名。 */
    val name: String

    /** 版本号，仅用于展示。 */
    val version: String

    /** 一句话说明这个插件干什么，显示在设置页。 */
    val description: String

    /**
     * 被加载时调用一次。
     *
     * 在这里声明设置项、写覆盖板、打日志。**不要在构造函数里做这些** ——
     * 构造函数可能在插件被停用时也会被调用。
     */
    fun onLoad(host: PluginHost)
}
