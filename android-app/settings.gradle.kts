pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PumpkinServer"
include(":app")

// 插件体系：
//   :plugin-api            —— 插件编译时依赖的稳定接口（纯 Kotlin/JVM，不依赖 Android）
//   :plugins:console-font  —— 示例插件；编译成 jar 后由 CI 用 D8 转 dex 并汇总成商店索引
//
// 新增插件：在 plugins/ 下建模块 + 写 plugin.json，再在这里 include 一行。
// CI 会自动构建 plugins/ 下的**所有**模块并生成索引（见 .github/workflows/apk-only.yml）。
include(":plugin-api")
include(":plugins:console-font")
