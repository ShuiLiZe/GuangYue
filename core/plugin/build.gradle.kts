plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.guangyue.core.plugin"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.quickjs.android)
    // quickjs-kt 的返回值转换走 KType / TypeConverter 反射；R8 剥掉 kotlin-reflect 之后就会抛
    // "No such type converter ... (Kotlin reflection is not available)" —— 0.0.1 的 release 包
    // 因此让 57 个插件全部导入失败。代码层已改成纯 JSON 文本桥接，这里再补依赖做双保险。
    implementation(libs.kotlin.reflect)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // 插件元信息宽松解析的单元测试（纯 JVM，不需要模拟器）
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}