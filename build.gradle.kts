plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // V0：:core:lyrics 是纯 JVM 模块（歌词内核），需要 Kotlin JVM 插件。
    // 原先它只在 catalog 里声明过（其他地方用的是 kotlin.android），这里补上才 assign 得动。
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

/**
 * Compose 编译器跳过策略。
 *
 * 0.0.5 使用的是 Kotlin 2.4 与 Compose 编译器 1.12.x，强跳过（StrongSkipping）已经是默认
 * 开启的特性，不需要再显式加 `-P plugin:...:featureFlag=StrongSkipping`（加了只会得到
 * 「已默认开启」的告警）。这一层留作将来需要覆盖编译器参数时的落点。
 */
