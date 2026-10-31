plugins {
    id("org.jetbrains.kotlin.jvm")
}

/**
 * V0：Folia 歌词渲染内核（Compose 移植版）。
 *
 * 纯 Kotlin/JVM 模块，**不依赖 Android**，也不依赖 Android Gradle 插件：
 *  - 模型与计算全部是普通 Kotlin，`gradlew :core:lyrics:test` 直接在 JVM 上跑；
 *  - Compose 层（GlowWord / VisualizerShell）在 `:feature:player` 里消费这里的输出，
 *    内核本身不认识 `android.graphics` / `androidx.compose.*`。
 *
 * 为什么要独立模块：Folia 的内核（renderHints / graphemeTiming / glowWordTiming /
 * wordColoring / paragraphStructure / songHandover / runtime）是一堆纯函数，各歌词样式共用；
 * 放成 Android 库会让它在单测里被迫起 Robolectric，数值对照（Folia 是 0.1 秒级的浮点常量）
 * 反而更慢更脏。
 */
/**
 * 本机只装了 JDK 21（D:\tool\jdk，Temurin 21.0.12.1），没有 17；
 * 所以不用 jvmToolchain(17)（会去找一个不存在的 JDK 17 并尝试下载），
 * 改为用当前 JDK 21 编译、产出 Java 17 字节码——和 AGP 侧 compileOptions Java 17 的目标一致。
 */
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    // 浮点对照用例失败时把断言原文打出来，不截断
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}