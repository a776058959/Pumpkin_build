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
//   :plugins:console-font  —— 示例插件一：改**显示**（控制台字号，TEXT 设置项）
//   :plugins:confirm-stop  —— 示例插件二：改**行为**（停止前确认，TOGGLE 设置项）
//
// 新增插件：在 plugins/ 下建模块 + 写 plugin.json，再在这里 include 一行。
// CI 会自动构建 plugins/ 下的**所有**模块并生成索引（见 .github/workflows/apk-only.yml）。
// 写插件的完整说明见 docs/dev/plugins.md。
include(":plugin-api")
include(":plugins:console-font")
include(":plugins:confirm-stop")
