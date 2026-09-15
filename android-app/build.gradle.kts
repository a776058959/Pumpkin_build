plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    // 插件 API 与插件本身都是纯 Kotlin/JVM 模块（不含 Android 类型），
    // 这样插件不必依赖 Android SDK，产物是普通 jar，D8 一转就是 dex。
    id("org.jetbrains.kotlin.jvm") version "2.4.0" apply false
}
