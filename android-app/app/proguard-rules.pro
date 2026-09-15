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
