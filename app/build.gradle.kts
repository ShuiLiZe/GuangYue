plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
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
     * 密钥路径与口令按以下优先级解析（任何一级命中即生效）：
     *   1. 环境变量 GUANGYUE_KEYSTORE / GUANGYUE_STORE_PASSWORD / GUANGYUE_KEY_ALIAS / GUANGYUE_KEY_PASSWORD
     *   2. gradle.properties（含 CI 动态追加的几行）
     *   3. 仓库内 build/keystore/guangyue-release.jks.base64（CI 解码目标）
     *   4. 仓库内 build/keystore/guangyue-release.jks（本地构建）
     * 密钥缺失时两种构建类型都会退回默认调试签名，保证产物恒为可安装的已签名 APK。
     */
    signingConfigs {
        create("release") {
            val envKs = System.getenv("GUANGYUE_KEYSTORE")
            val propKs = project.findProperty("guangyue.keystore") as String?
            val candidates = listOfNotNull(
                envKs?.let(::file),
                propKs?.let(::file),
                file("${rootDir}/build/keystore/guangyue-release.jks"),
                file("${rootDir}/build/keystore/guangyue-release.jks.base64"),
            )
            val ksFile = candidates.firstOrNull { it.exists() }
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
                logger.lifecycle("GuangYue signing: keystore 缺失，回退调试签名")
            }
        }
    }

    buildTypes {
        // 两个构建类型共用同一个签名配置对象：有发布密钥就套发布密钥，
        // 没密钥时该对象为空，Gradle 自动使用默认调试签名，产物永远可安装。
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // 调试包也套用同一把发布密钥，避免「debug 装过之后正式包覆盖不上」
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