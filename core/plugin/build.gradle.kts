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
    // 真·QuickJS 的 JVM 产物：android 版只有 .so，Windows/Linux 的 JVM 加载不了。
    // 有了它，求值通道 / 插件加载才能在 CI 上端到端跑（见 PluginRuntimeJvmTest）。
    testImplementation(libs.quickjs.jvm)
}

tasks.withType<Test>().configureEach {
    // 测试要读 app/src/main/assets/prelude.js 这份真产物（cheerio/CryptoJS 都在里面），
    // 统一把工作目录钉在模块目录，避免受 daemon / IDE 的 cwd 影响。
    workingDir = projectDir
    // 体检用例要定位仓库根的 build/plugin-cache-0.0.4；测试 JVM 的 cwd 在部分环境里会被
    // 改写成「<模块>/."」，相对路径不可靠，这里显式把仓库根也传进去兜底。
    systemProperty("guangyue.rootDir", rootProject.projectDir.absolutePath)
    // prelude 有 54 万字符，QuickJS 冷启动要几百毫秒；给足余量。
    maxHeapSize = "2g"
}