import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// 读取 local.properties（本地文件，不提交到 git）：SDK 路径 + 发布签名
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.andersonlin.moneybook"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.andersonlin.moneybook"
        minSdk = 28
        targetSdk = 34
        versionCode = 5
        versionName = "1.2.1"
    }

    signingConfigs {
        // 存在本地 keystore 时才启用发布签名（keystore 不提交到 git）
        create("release") {
            val keystore = localProperties.getProperty("release.keystore")
            if (keystore != null) {
                storeFile = rootProject.file(keystore)
                storePassword = localProperties.getProperty("release.storePassword")
                keyAlias = localProperties.getProperty("release.keyAlias")
                keyPassword = localProperties.getProperty("release.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (localProperties.getProperty("release.keystore") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        // Kotlin 1.9.24 对应的 Compose 编译器版本
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room 本地数据库
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore 偏好设置（主题模式、应用锁）
    implementation(libs.androidx.datastore.preferences)

    // 应用锁指纹识别（Jetpack，免费）
    implementation(libs.androidx.biometric)

    // 桌面小组件（Jetpack Glance，免费）
    implementation(libs.androidx.glance.appwidget)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
