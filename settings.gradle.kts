pluginManagement {
    // 仓库顺序保持原样：本地离线缓存按此顺序建立键，改动顺序会让缓存全部失效
    // 只有 GitHub Actions（环境变量 GITHUB_ACTIONS=true）里改成官方仓库优先、阿里云兜底，
    // 避开镜像 502；本地没有这个变量，走 else 分支，顺序与原来一字不差。
    val officialFirst = System.getenv("GITHUB_ACTIONS") == "true"
    repositories {
        if (officialFirst) {
            gradlePluginPortal()
            google()
            mavenCentral()
            maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
            maven(url = "https://maven.aliyun.com/repository/google")
            maven(url = "https://maven.aliyun.com/repository/central")
            maven(url = "https://maven.aliyun.com/repository/public")
        } else {
            maven(url = "https://maven.aliyun.com/repository/gradle-plugin")
            maven(url = "https://maven.aliyun.com/repository/google")
            maven(url = "https://maven.aliyun.com/repository/central")
            maven(url = "https://maven.aliyun.com/repository/public")
            gradlePluginPortal()
            google()
            mavenCentral()
        }
    }

    /**
     * 插件 ID 直连模块坐标。
     *
     * Gradle 解析 `plugins { id("com.google.devtools.ksp") }` 时默认去仓库找
     * marker 构件 com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin，
     * 部分镜像不提供这个 marker，于是报
     *   Plugin [id: 'com.google.devtools.ksp', version: '2.3.12'] was not found
     * 而底层 jar（symbol-processing-gradle-plugin）实际存在。
     * 显式指定真实模块坐标即可绕开 marker 查找。
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
    // 同上：CI 官方优先、阿里云兜底；本地顺序不变。
    val officialFirst = System.getenv("GITHUB_ACTIONS") == "true"
    repositories {
        if (officialFirst) {
            google()
            mavenCentral()
            maven(url = "https://maven.aliyun.com/repository/google")
            maven(url = "https://maven.aliyun.com/repository/central")
            maven(url = "https://maven.aliyun.com/repository/public")
        } else {
            maven(url = "https://maven.aliyun.com/repository/google")
            maven(url = "https://maven.aliyun.com/repository/central")
            maven(url = "https://maven.aliyun.com/repository/public")
            google()
            mavenCentral()
        }
    }
}
if (System.getenv("GITHUB_ACTIONS") == "true") println("广乐 CI：依赖仓库官方优先（google / mavenCentral / gradlePluginPortal），阿里云兜底")
rootProject.name = "GuangYue"

include(":app")
include(":core:plugin")
include(":core:player")
include(":core:download")
include(":core:data")
include(":core:web")
// V0：歌词渲染内核。纯 Kotlin/JVM，无 Android 依赖，单测直接跑 JVM。
include(":core:lyrics")
include(":feature:search")
include(":feature:player")
include(":feature:library")