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
//   :plugin-api        —— 插件编译时依赖的稳定接口（纯 Kotlin，不打进 App 的 dex 之外）
//   :plugins:lan-address —— 首个插件，编译成 jar 后由 CI 用 D8 转 dex
include(":plugin-api")
include(":plugins:lan-address")
