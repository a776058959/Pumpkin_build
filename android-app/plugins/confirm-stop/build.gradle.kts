// 「停止前确认」插件。
//
// 与 :plugin-api 一样是纯 Kotlin/JVM 模块。
// **compileOnly 依赖 plugin-api 不能改成 implementation** —— 理由见 plugin-api 的注释。
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(project(":plugin-api"))
}
