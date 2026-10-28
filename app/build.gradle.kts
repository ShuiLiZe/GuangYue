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
     * 密钥与口令优先从环境变量 / gradle.properties 读（CI 与本地发布用），
     * 没配就让 release 包退化为未签名产物（assembleDebug 不受影响）。
     * 口令一旦更换，已装机的版本必须卸载重装 —— 应用包名 com.guangyue.music 唯一。
     */
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("GUANGYUE_KEYSTORE")
                ?: (project.findProperty("guangyue.keystore") as String?)
                ?: "${rootDir}/build/keystore/guangyue-release.jks"
            val ksFile = file(ksPath)
            if (ksFile.exists()) {
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
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // 调试包也套用同一把发布密钥，避免「debug 装过之后正式包覆盖不上」
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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