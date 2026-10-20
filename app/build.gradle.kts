import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.anylyralabs.lyraproxy"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.anylyralabs.lyraproxy"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    // Release signing reads keystore.properties, which is git-ignored. When it is
    // absent the release build falls back to no signing so a fresh clone still
    // compiles; a signed artifact requires the properties file to be present.
    val keystoreProperties = Properties()
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use {
        keystoreProperties.load(it)
    }

    signingConfigs {
        create("release") {
            // A missing keystore.properties leaves these empty, and the build
            // then falls back to an unsigned artifact rather than failing.
            val store = keystoreProperties.getProperty("storeFile")
            if (!store.isNullOrBlank() && rootProject.file(store).exists()) {
                storeFile = rootProject.file(store)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 只有 keystore.properties 存在且 storeFile 有效时才挂上签名配置。
            // 若直接赋一个空的 signingConfig，AGP 会在打包阶段抛
            // `SigningConfig "release" is missing required property "storeFile"`，
            // 而不是像注释所说的那样回退成未签名产物。
            val releaseStore = keystoreProperties.getProperty("storeFile")
            if (!releaseStore.isNullOrBlank() && rootProject.file(releaseStore).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // 与 mochi-compose 自身构建时使用的 BOM 保持一致；Capsule 会把 Compose
    // 运行时抬到它要求的版本，BOM 只负责其余模块的默认版本。
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    // FileProvider 用于把账号备份包分享出去，需要显式声明 core。
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")

    // 组件库：MD3 Expressive + iOS 26 交互 + G2 连续圆角
    implementation("com.github.feice11:mochi-compose:0.1.180")
    // Nothing 外观：单色 + 红点、NDot 数字与语义图标
    implementation("com.github.feice11.mochi-compose:mochi-nothing:0.1.180")
    // mochi 的公开 API 用 ImmutableList 接收列表
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.5.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
