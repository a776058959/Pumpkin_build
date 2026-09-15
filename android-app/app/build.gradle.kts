// 注意：这些 import 必须写在 plugins 块之前。
// 不能在脚本体里用 java.util.Locale 这类全限定名 —— Gradle Kotlin DSL 里的裸 `java`
// 会先被解析成 JavaPluginExtension（java 插件扩展），于是报 Unresolved reference 'util'。
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ─────────────────────────────────────────────────────────────────────────────
// 版本号由构建时间戳自动推导。
//
// 背景（踩过的坑）：以前 versionCode/versionName 是手写的常量，长期停在 3 / "0.3.0"，
// 结果「壳检查更新」只能拿安装时间戳去比，分不清新旧；而且新包装上去版本显示还是 0.3.0，
// 用户根本看不出装的是哪一版。现在每次构建都带一个唯一且单调递增的版本号。
//
// 公式（必须与 UpdateClient.parseVersionCodeFromTag 保持一致）：
//     versionCode = epochDay * 10000 + HHMM
// 例：20260915-0244 → 20711 * 10000 + 244 = 207110244
//
// 为什么不直接用 YYYYMMDDHHMM：那是 2026 亿，超过 Android versionCode 的 int 上限（21.47 亿）。
// 换算成天数后约 2.07 亿，一直够用到公元 2558 年。
//
// 时间戳来源优先级：-PbuildStamp=…（CI 传） > 环境变量 PUMPKIN_BUILD_STAMP > 当前本地时间。
// ─────────────────────────────────────────────────────────────────────────────
val buildStamp: String =
    (project.findProperty("buildStamp") as String?)?.takeIf { it.isNotBlank() }
        ?: System.getenv("PUMPKIN_BUILD_STAMP")?.takeIf { it.isNotBlank() }
        ?: SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

val computedVersionCode: Int = run {
    val m = Regex("(\\d{4})(\\d{2})(\\d{2})-(\\d{2})(\\d{2})").find(buildStamp)
    if (m == null) {
        // 兜底：时间戳解析不了时用一个远高于历史版本（3）的固定值，
        // 保证仍然能覆盖安装，不至于因为 versionCode 掉回 0 而装不上。
        20000000
    } else {
        val v = m.groupValues
        val epochDay = LocalDate.of(v[1].toInt(), v[2].toInt(), v[3].toInt()).toEpochDay()
        val hhmm = (v[4] + v[5]).toInt()
        (epochDay * 10000L + hhmm).toInt()
    }
}

// 人看的版本名：主版本号 + 构建时间戳，例如 0.4.0+20260915-0244
val computedVersionName: String = "0.4.0+$buildStamp"

android {
    namespace = "com.pumpkin.server"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pumpkin.server"
        // miuix-ui / icons / preference / shader 等模块自身只要求 minSdk 23；
        // 唯独 miuix-blur-android 在 manifest 里声明 minSdkVersion 33（液态玻璃折射用 API 33 RuntimeShader）。
        // 我们在 AndroidManifest.xml 用 tools:overrideLibrary 放开这一条检查（KernelSU 同款做法，
        // 它 minSdk 31 也是这么用 miuix-blur 的），< 33 的设备上模糊自动降级到软件回退实现。
        minSdk = 26
        // 关键：必须 <= 28。Android 10+ 只在 targetSdk <= 28 时把应用放进 untrusted_app_27 域，
        // 该域允许对应用私有目录里的文件 execve；>= 29 会被 SELinux 拒绝，
        // 那样就无法运行「在线下载」的服务端二进制。
        targetSdk = 28
        versionCode = computedVersionCode
        versionName = computedVersionName
    }

    packaging {
        jniLibs {
            // 壳不再内置原生库；保留旧行为以防将来又需要内嵌二进制
            useLegacyPackaging = true
        }
    }

    // 固定签名：用仓库里的 key，保证每次构建签名一致，安装新版能直接覆盖升级、不丢世界数据。
    // 注意：这是自签名测试密钥，密码是公开的，仅用于自己侧载，不要用于任何正式分发。
    signingConfigs {
        create("fixed") {
            storeFile = file("pumpkin-signing.p12")
            storePassword = "pumpkin123"
            keyAlias = "pumpkin"
            keyPassword = "pumpkin123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)

    // Jetpack Compose
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-core")

    // miuix (液态玻璃 / miuix 风格组件)
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.3")
}
