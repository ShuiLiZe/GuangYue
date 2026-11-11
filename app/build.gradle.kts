/**
 * 广乐 0.0.5 —— 界面从 WebView 换成原生 Jetpack Compose。
 *
 * 这个模块现在只剩「壳」的职责：
 *  * 应用版本号与签名；
 *  * 把各 feature 模块的 Compose 页面挂到 [com.guangyue.music.MainActivity] 的导航图上；
 *  * release 包开 R8 + 资源压缩。
 *
 * 0.0.4 之前这里有两条和 Folia Web 有关的东西，已整块删除：
 *  1. `foliaWeb` Exec 任务（node build-folia-web.mjs --build → app/src/main/assets/folia）；
 *  2. `implementation(project(":core:web"))`。WebView、WebViewAssetLoader、JS 桥、
 *     打包进去的 20.9MB 前端产物都不再参与构建，APK 因此显著变小。
 *  `web/` 与 `core/web/` 目录仍留在仓库里，只是不参与构建（历史与对照用）。
 */
import java.io.File
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * 把构建脚本找到的密钥文件变成可用的二进制 keystore。
 *
 * 仓库里放的是 base64 文本版（.jks 二进制被 .gitignore 挡掉），必须解码后才能交给签名器，
 * 否则会报 toDerInputStream rejects tag type 77（首字符 M）。
 * 已经解码过的结果写到同目录 signing/ 下并复用，避免每次配置都写盘。
 */
fun resolveKeystore(src: File): File {
    val bytes = src.readBytes()
    if (!looksLikeBase64(bytes)) return src
    val text = String(bytes, Charsets.US_ASCII).filterNot { it.isWhitespace() }
    val decoded = try {
        Base64.getDecoder().decode(text)
    } catch (e: IllegalArgumentException) {
        throw GradleException("signing key $src is text but not valid base64: ${e.message}")
    }
    val out = File(src.parentFile, "signing/guangyue-release.jks")
    out.parentFile.mkdirs()
    val cached = out.takeIf { it.exists() }?.readBytes()
    if (cached == null || !cached.contentEquals(decoded)) out.writeBytes(decoded)
    return out
}

/** 二进制 JKS 开头是 0xFE 0xED 0xFE 0xED；base64 文本开头是 MII。 */
fun looksLikeBase64(bytes: ByteArray): Boolean {
    val head = bytes.take(3).joinToString("") { it.toInt().and(0xFF).toChar().toString() }
    if (head == "MII") return true
    return bytes.take(256).all { b ->
        val v = b.toInt().and(0xFF)
        v == 0x0A || v == 0x0D || v == 0x20 || v == 0x09 ||
            (v in 'A'.code..'Z'.code) || (v in 'a'.code..'z'.code) ||
            (v in '0'.code..'9'.code) || v == '+'.code || v == '/'.code || v == '='.code
    }
}

android {
    namespace = "com.guangyue.music"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.guangyue.music"
        minSdk = 26
        targetSdk = 36
        // 版本号按 0.x 小步走：0.0.1 → 0.0.2 → …（tag 与 versionName 一致，不带 v 前缀）。
        // versionCode 必须始终大于上一版，否则装不上去覆盖。
        versionCode = 22
        versionName = "0.0.22"
        vectorDrawables { useSupportLibrary = true }
    }

    /**
     * 正式签名。
     *
     * 顺序：环境变量 / gradle.properties 指定的路径优先，否则依次找
     * build/keystore/guangyue-release.jks 与 .jks.base64。base64 文本会被
     * resolveKeystore 自动解码。都找不到时签名配置为空，Gradle 回退默认
     * 调试签名，产物始终是可安装的已签名 APK。
     */
    signingConfigs {
        create("release") {
            val declared = System.getenv("GUANGYUE_KEYSTORE")?.takeIf { it.isNotBlank() }
                ?: (project.findProperty("guangyue.keystore") as String?)?.takeIf { it.isNotBlank() }
            val declaredFile = declared?.let { file(if (File(it).isAbsolute) it else "${rootDir}/$it") }
            val candidates = listOfNotNull(
                declaredFile,
                file("${rootDir}/build/keystore/guangyue-release.jks"),
                file("${rootDir}/build/keystore/guangyue-release.jks.base64"),
            )
            val found = candidates.distinctBy { it.absolutePath }.firstOrNull { it.exists() }
            val ksFile = found?.let(::resolveKeystore)
            if (ksFile != null) {
                storeFile = ksFile
                storePassword = System.getenv("GUANGYUE_STORE_PASSWORD")
                    ?: (project.findProperty("guangyue.storePassword") as String?)
                    ?: "GuangYue2026Release"
                keyAlias = System.getenv("GUANGYUE_KEY_ALIAS")
                    ?: (project.findProperty("guangyue.keyAlias") as String?)
                    ?: "guangyue"
                keyPassword = System.getenv("GUANGYUE_KEY_PASSWORD")
                    ?: (project.findProperty("guangyue.keyPassword") as String?)
                    ?: "GuangYue2026Release"
                logger.lifecycle("GuangYue signing: keystore = $ksFile")
            } else {
                logger.lifecycle("GuangYue signing: 未找到发布密钥，回退默认调试签名")
            }
        }
    }

    buildTypes {
        // 两个构建类型共用同一个签名配置对象：有发布密钥就套发布密钥，
        // 没密钥时该对象为空，Gradle 自动使用默认调试签名。
        release {
            // 0.0.1 的 bug 只在开 R8 的真机包上暴露（JVM 单测不走 R8），所以 release 必须真的开 R8。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // 调试包也套同一把发布密钥，避免 debug 装过之后正式包覆盖不上。
            signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }

    /**
     * UI 截图用例在 JVM 上跑真 Compose 渲染（Robolectric + Roborazzi），需要读资源与 assets。
     */
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all { test ->
                // Roborazzi：截图写到仓库里的 docs/ui-0.0.8/。只在显式传入
                // GUANGYUE_RECORD_SCREENSHOTS=true 时录制，平时跑测试不会改仓库文件。
                test.systemProperty(
                    "roborazzi.test.record",
                    System.getenv("GUANGYUE_RECORD_SCREENSHOTS") ?: "false",
                )
                // V0 歌词样式截图要落到别的目录时传 -ProborazziOutputDir=...（默认仍是 docs/ui-u7）。
                test.systemProperty(
                    "roborazzi.output.dir",
                    (findProperty("roborazziOutputDir") as String?) ?: "${rootDir}/docs/ui-u7",
                )
                test.maxHeapSize = "3g"
            }
        }
    }
}

dependencies {
    implementation(project(":core:data"))
    // B1b：Bravais 墙几何（core/wall 暂放在纯 JVM 的 core:lyrics 模块里）。
    implementation(project(":core:lyrics"))
    implementation(project(":core:download"))
    implementation(project(":core:player"))
    implementation(project(":core:plugin"))
    implementation(project(":feature:search"))
    implementation(project(":feature:player"))
    implementation(project(":feature:library"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    // 0.0.6：图标全部换成 Folia 同款 Lucide 矢量（feature/library 的 GuangYueIcons），
    // material-icons-core/extended 依赖已移除，不再往包里塞一套 Material 图标。
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.palette.ktx)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

