// 首个插件：局域网地址。
//
// **compileOnly 依赖 plugin-api 是必须的，不能改成 implementation**：
// 插件绝不能把 PumpkinPlugin / PluginHost 打进自己的 dex。
// 运行时 App 会用「自己的类加载器作为父加载器」来加载插件的 dex，
// 接口类必须只有一份（来自 App）；一旦插件里也有一份，
// instanceof 会因为两个类加载器各持一个同名类而失败，插件静默不生效。
plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(project(":plugin-api"))
}
