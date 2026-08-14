// 顶层构建文件：只声明插件版本，不在此处配置模块
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
}
