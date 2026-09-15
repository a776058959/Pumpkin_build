# R8 规则。
#
# 本应用**没有反射、没有按名实例化**（已 grep 确认：无 Class.forName / getMethod /
# newInstance / ::class.java），所以业务类不需要任何 keep 规则。
# Compose / miuix / androidx 各自带 consumer 规则，构建时会自动合并进来。
#
# 这里只加两条为了「可调试性」的规则，不是为了让代码能跑。

# 崩溃堆栈里保留行号。本应用有崩溃处理（把堆栈写到 last_crash.txt），
# 没有行号的堆栈基本没法定位。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 保留本应用自己类的名字（但**不阻止** R8 优化与内联）。
#
# 用 -keepnames 而不是 -keep：-keep 会连带禁止优化，把本应用代码的内联收益一起丢掉；
# -keepnames 只保名字。体积的大头在依赖（Compose/miuix 那 20 多 MB），不在本应用这几十个类。
-keepnames class com.pumpkin.server.** { *; }

# ---------------------------------------------------------------- 插件 API
#
# 插件的 dex 是运行时用 DexClassLoader 加载的，父加载器是 App 自己的类加载器 ——
# 也就是说插件里的 PumpkinPlugin / PluginHost 会解析到 **App dex 里的这一份**。
# 一旦 R8 把这些接口类混淆或删掉，插件就会全部加载失败，而且症状很隐蔽
#（ClassNotFoundException / NoClassDefFoundError 只在插件加载时出现，主流程完全正常）。
#
# 这里必须用 -keep（不是 -keepnames）：方法名与签名都要原样保留，
# 因为插件编译时是按这些签名调用的。
-keep interface com.pumpkin.plugin.PumpkinPlugin { *; }
-keep interface com.pumpkin.plugin.PluginHost { *; }
-keep class com.pumpkin.plugin.PluginSetting { *; }
-keep class com.pumpkin.plugin.PluginSetting$Kind { *; }
-keep class com.pumpkin.plugin.PluginKeys { *; }
# 插件只用反射按类名实例化自己的入口类，那个类在插件的 dex 里，不受这里的规则影响；
# 但 App 里任何可能被反射碰到的兼容层类也一并保号，避免以后加了东西忘记加规则。
-keepnames class com.pumpkin.plugin.** { *; }

# Kotlin 标准库也必须保原名（不能只保号）。
#
# 原因：插件是**单独编译、没有经过 R8** 的 dex，它会按原始名字引用标准库 ——
# 实测插件一加载就报 `Failed resolution of: Lkotlin/jvm/internal/Intrinsics;`。
# R8 默认会把标准库里的类重打包并改名（proguard-android-optimize 自带 repackage），
# 于是插件按原名找不到东西。
#
# 代价：标准库不能再被删减，APK 会大一点、标准库函数也不再被内联。
# 这是「让插件能跑」必须付的代价 —— 相比把标准库塞进每个插件（每个插件多 1MB 以上），
# 还是让 App 大一点更划算。
-keep class kotlin.** { *; }
