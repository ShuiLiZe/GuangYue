// 对应 Folia 文件：
//   src/components/visualizer/glowWordVariants.ts（弹簧 spring{stiffness:200,damping:20}、各状态时长）
//   src/components/visualizer/wordGlow.ts（easing 名 → 曲线）
//   src/components/visualizer/classic/tuning.ts（tuning 取值）
//
// V0 桥接层：把纯 Kotlin 内核（:core:lyrics）里的「秒 / 弹簧 / easing 名」换成 Compose 的
// 动画规格。这里不含任何布局或渲染逻辑，布局在内核，渲染在 GlowWord.kt。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import com.guangyue.core.lyrics.AnimationIntensity
import com.guangyue.core.lyrics.ClassicTuning
import com.guangyue.core.lyrics.GlowWordVariants
import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.SpringSpec as KernelSpringSpec

/**
 * 内核 easing 名（Folia 的 'easeOut' / 'easeInOut' / 'easeIn' / 'linear'）→ Compose 插值器。
 *
 * Folia 用 GSAP 的 easing 名。GSAP 的 easeOut 等价于三次贝塞尔 (0, 0, 0.58, 1)，
 * easeInOut 等价于 (0.42, 0, 0.58, 1)，easeIn 等价于 (0.42, 0, 1, 1)。
 * 不做近似替代，直接按 GSAP 公布的曲线参数构造，保证与 Folia 逐帧形状一致。
 */
fun easeByName(name: String): Easing = when (name) {
    "easeOut" -> CubicBezierEasing(0f, 0f, 0.58f, 1f)
    "easeInOut" -> CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    "easeIn" -> CubicBezierEasing(0.42f, 0f, 1f, 1f)
    else -> LinearEasing
}

/** 与内核 easing 名一一对应的便捷常量（正文层 color 过渡用 linear，辉光层用 easeOut）。 */
val kernelLinearEasing: Easing = easeByName("linear")
val kernelEaseOutEasing: Easing = easeByName("easeOut")
val kernelEaseInOutEasing: Easing = easeByName("easeInOut")

/** Compose 自带的近似（仅在与 GSAP 曲线不可比时兜底，例如系统关闭动画时的直切）。 */
val builtinEaseOut: Easing = LinearOutSlowInEasing
val builtinEaseInOut: Easing = FastOutLinearInEasing

/** 秒 → 毫秒（Compose 动画规格用 ms）；0 或负值按 0 处理。 */
fun secondsToMillis(seconds: Double): Int = (seconds * 1000.0).toInt().coerceAtLeast(0)

/**
 * Folia 的 `spring{stiffness:200, damping:20, mass:1}` 换算成 Compose 的 [SpringSpec]。
 *
 * 换算式：阻尼比 ζ = c / (2·√(k·m))，其中 c = damping = 20，k = stiffness = 200，m = 1
 *   ζ = 20 / (2·√200) = 20 / 28.284271247 = 0.7071067811865476
 * Compose 的 spring 只吃 (stiffness, dampingRatio)，所以这里做的是「绝对阻尼 → 阻尼比」的换算，
 * stiffness 原样传入，弹簧的角频率与衰减率与 Framer Motion 的 spring 等价（ζ<1，欠阻尼微回弹）。
 * 同一份公式在 [com.guangyue.core.lyrics.SpringSpec.dampingRatio] 里实现，内核单测已按
 * 20/(2√200) = 0.7071067811865476 断言过，此处只是转 Float。
 */
fun springOf(spec: KernelSpringSpec): SpringSpec<Float> = spring(
    dampingRatio = spec.dampingRatio.toFloat(),
    stiffness = spec.stiffness.toFloat(),
)

/** 内核默认激活弹簧（stiffness 200 / damping 20 / mass 1），对应 Folia active 的布局层弹簧。 */
val activeSpring: SpringSpec<Float> = springOf(
    KernelSpringSpec(
        stiffness = GlowWordVariants.ACTIVE_SPRING_STIFFNESS,
        damping = GlowWordVariants.ACTIVE_SPRING_DAMPING,
        mass = GlowWordVariants.ACTIVE_SPRING_MASS,
    ),
)

/**
 * 播放进度 → 秒。
 *
 * Folia 的播放时间是 `MotionValue<number>`，单位秒，逐帧变化且不写 React state。
 * Compose 侧同构：由播放器位置（毫秒）换算成秒，喂给 `withFrameNanos` 驱动的动画时钟，
 * 词组件只在自己「三态」变化时重组（见 GlowWord.kt 的 resolveStatus）。
 */
fun positionMsToSeconds(positionMs: Long): Double = positionMs / 1000.0

/** 空行判据：样式层用它跳过没有词的运行时帧。 */
fun Line.hasVisibleWords(): Boolean = words.isNotEmpty() && fullText.isNotBlank()

/**
 * 是否按「减少动效」渲染。
 *
 * 手机化：Folia 只在动画强度上分 calm/normal/chaotic，没有「系统关闭动画」这一档；
 * 广乐接系统的 `Settings.Global.ANIMATOR_DURATION_SCALE == 0`，此时三态直接切换、不做辉光动画。
 * 注意 calm 不等于减少动效——calm 仍带呼吸浮动（10/8.5s），只有系统动画关闭才降级。
 */
fun reducedMotionApplied(intensity: AnimationIntensity, reduceMotion: Boolean): Boolean = reduceMotion

/** tuning 快照，供弹层取消时回滚。 */
fun ClassicTuning.snapshot(): ClassicTuning = copy()

/**
 * 是否运行在 Robolectric（截图/单测环境）。
 *
 * Robolectric 下 `withFrameNanos` 的帧循环会把 `waitForIdle()` 挂死（与 feature:library 的 `FrameJank` 同一个坑），
 * 所以歌词页在 Robolectric 下改用固定时间的 `rememberStaticVisualizerClock`，只在真机/模拟器上跑帧循环。
 */
val runningUnderRobolectric: Boolean by lazy {
    try {
        Class.forName("org.robolectric.Robolectric")
        true
    } catch (_: Throwable) {
        false
    }
}