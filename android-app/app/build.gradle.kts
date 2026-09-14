plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.pumpkin.server"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pumpkin.server"
        minSdk = 24
        // 关键：必须 <= 28。Android 10+ 只在 targetSdk <= 28 时把应用放进 untrusted_app_27 域，
        // 该域允许对应用私有目录里的文件 execve；>= 29 会被 SELinux 拒绝，
        // 那样就无法运行「在线下载」的服务端二进制。
        targetSdk = 28
        versionCode = 3
        versionName = "0.3.0"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
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
