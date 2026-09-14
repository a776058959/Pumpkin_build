plugins {
    id("com.android.application")
}

android {
    namespace = "com.pumpkin.server"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pumpkin.server"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    packaging {
        jniLibs {
            // 关键：必须把 jniLibs 里的可执行文件解压到 nativeLibraryDir，否则无法 exec
            useLegacyPackaging = true
            // 不要对可执行文件做 strip
            keepDebugSymbols += "**/libpumpkin.so"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}
