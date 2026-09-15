// 插件 API：插件编译时依赖的稳定接口。
//
// 刻意做成**纯 Kotlin/JVM 模块**（不是 Android 库）：
//   - 接口里没有任何 Android 类型，插件因此不必依赖 Android SDK，编译也快；
//   - 产物是个普通 jar，用 D8 转 dex 很直接（见 CI）；
//   - App 侧当普通依赖引入即可 —— Android 能消费 JVM jar。
//
// 另一个关键点：**App 必须对 com.pumpkin.plugin.** 加 R8 keep 规则**
//（见 app/proguard-rules.pro）。插件是用反射按类名实例化的，
// 接口类被混淆/删掉会导致插件全部加载失败。
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}
