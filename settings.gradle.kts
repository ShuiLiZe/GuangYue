pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // 国内镜像放后面兜底：优先走官方，避免镜像同步不全导致的解析失败
        maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/central")
        maven(url = "https://maven.aliyun.com/repository/public")
    }

    /**
     * 插件 ID 直连模块坐标。
     *
     * Gradle 解析 `plugins { id("com.google.devtools.ksp") }` 时默认去仓库找
     * marker 构件 com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin，
     * 部分镜像（含本工程原先配置的阿里云组合）并不提供这个 marker，于是出现
     *   Plugin [id: 'com.google.devtools.ksp', version: '2.3.12'] was not found
     * 而底层 jar（symbol-processing-gradle-plugin）其实是存在的。
     *
     * 这里显式指定真实模块坐标，彻底绕开 marker 查找，同时兼容本地离线缓存。
     */
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.google.devtools.ksp" ->
                    useModule("com.google.devtools.ksp:symbol-processing-gradle-plugin:${requested.version}")

                "com.google.dagger.hilt.android" ->
                    useModule("com.google.dagger:hilt-android-gradle-plugin:${requested.version}")

                "org.jetbrains.kotlin.android", "org.jetbrains.kotlin.jvm" ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")

                "org.jetbrains.kotlin.plugin.serialization" ->
                    useModule("org.jetbrains.kotlin:kotlin-serialization:${requested.version}")

                "org.jetbrains.kotlin.plugin.compose" ->
                    useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version}")

                "com.android.application", "com.android.library" ->
                    useModule("com.android.tools.build:gradle:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google()
        mavenCentral()
        maven(url = "https://maven.aliyun.com/repository/google")
        maven(url = "https://maven.aliyun.com/repository/central")
        maven(url = "https://maven.aliyun.com/repository/public")
    }
}
rootProject.name = "GuangYue"

include(":app")
include(":core:plugin")
include(":core:player")
include(":core:download")
include(":core:data")
include(":feature:search")
include(":feature:player")
include(":feature:library")