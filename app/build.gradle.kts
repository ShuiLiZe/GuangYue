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
 * 仓库里放的是 base64 文本版（.jks 二进制被 .gitignore 挡掉），必须解码后
 * 才能交给签名器，否则会报 toDerInputStream rejects tag type 77（首字符 M）。
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
        versionCode = 1
        versionName = "1.0"
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
            // 声明的相对路径（如 CI 里写的 build/keystore/guangyue-release.jks.base64）
            // 按 rootDir 解析；同时把默认位置一并作为兜底，避免口令文件写错路径就整体失败。
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
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // 调试包也套用同一把发布密钥，避免 debug 装过之后正式包覆盖不上
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
}

dependencies {
    implementation(project(":core:data"))
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
    implementation(libs.androidx.compose.material.icons.extended)
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
}
