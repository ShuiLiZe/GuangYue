// 町景的纯函数小件：逐函数对译 Folia v0.7.25 machi/engine/lens.ts（景深参数）、engine/clock.ts（氛围时钟）、
// engine/camera.ts 的 resolveMachiDrift（运镜偏移）。金标 machi_lens_golden.txt（docs/port/scripts/machi-lens-golden-gen.js）。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// —— lens.ts ————————————————————————————————————————————————————————
/** f/8、强度 1 时离焦最多处的离焦圆半径（画面高度的比例）：原来的微缩感。 */
internal const val MACHI_BASE_COC = 0.012
/** 光圈按这一档换算：f/8 是原来的样子。 */
internal const val MACHI_REFERENCE_APERTURE = 8.0

/** 设置里的镜头参数（MachiTuning 的 aperture/dof/dofRange/tiltShift，默认 f/8、1、1、0 见 types.ts）。 */
internal data class MachiLensTuning(val aperture: Double = 8.0, val dof: Double = 1.0, val dofRange: Double = 1.0, val tiltShift: Double = 0.0)

/** 镜头 pass 要的：对焦带半宽与过渡（米）、离焦圆、移轴离焦圆（画面高度比例）。 */
internal data class MachiLens(val focusBand: Double, val focusFalloff: Double, val coc: Double, val tilt: Double)

/**
 * 清晰的那一层按清晰范围放宽、随拉近变浅；光圈每大一档，清晰层薄到 1/√2、离焦圆大到 √2 倍；
 * 过渡只按光圈的平方根收窄。移轴的离焦圆只跟强度走，不随光圈变。
 */
internal fun resolveMachiLens(tuning: MachiLensTuning, zoom: Double): MachiLens {
    val open = MACHI_REFERENCE_APERTURE / tuning.aperture
    val depth = tuning.dofRange / max(1e-3, zoom)
    return MachiLens(
        focusBand = (3.4 * depth) / open,
        focusFalloff = (7.5 * depth) / sqrt(open),
        coc = MACHI_BASE_COC * tuning.dof * open,
        tilt = MACHI_BASE_COC * max(1.0, tuning.dof) * tuning.tiltShift,
    )
}

// —— camera.ts resolveMachiDrift ——————————————————————————————————————
/** 相对静止位置的视角偏移：转角、俯仰（度）、缩放倍数、左右与前后平移（米）（types.ts MachiCameraView）。 */
internal data class MachiCameraView(val azimuth: Double, val elevation: Double, val zoom: Double, val panX: Double, val panZ: Double)

/** 运镜从静止到全幅（或关掉后回到静止位置）用的秒数（真实时间）。 */
internal const val MACHI_DRIFT_RAMP_SECONDS = 4.0

/** 运镜在 time 秒（氛围时间）时相对静止位置的偏移：几道周期互不成倍数的慢正弦。 */
internal fun resolveMachiDrift(time: Double): MachiCameraView {
    fun wave(period: Double, phase: Double) = sin((time / period) * PI * 2 + phase)
    return MachiCameraView(
        azimuth = 15 * wave(67.0, 0.0) + 5 * wave(29.0, 1.3),
        elevation = 4 * wave(47.0, 0.7),
        zoom = exp(0.08 * wave(41.0, 2.1)),
        panX = 0.45 * wave(53.0, 0.4),
        panZ = 0.3 * wave(71.0, 2.6),
    )
}

// —— clock.ts ———————————————————————————————————————————————————————
private const val MACHI_CLOCK_MAX_STEP_SECONDS = 0.1

/**
 * 町景的统一时钟：雨、涟漪、滴水、招牌闪烁、门这些氛围动效都只读它。暂停时它停住；一帧最多走 0.1 秒，
 * 切回前台不会一下子跳一大截。歌词相关的东西读歌曲时间，不读它。
 */
internal class MachiClock {
    /** 氛围时间（秒），只在没暂停时前进。 */
    var elapsed = 0.0
    /** 这一帧氛围时间走了多少（暂停时是 0）。 */
    var delta = 0.0
    /** 这一帧的真实时间（镜头回正这类界面动画用，不随暂停停住）。 */
    var realDelta = 0.0
    var paused = false
    /** 动效强度（主题的 animationIntensity）乘在速度上。 */
    var timeScale = 1.0
    private var last = -1.0

    /** 每帧调一次，now 是帧时间戳（毫秒）。 */
    fun tick(now: Double) {
        val real = if (last < 0) 0.0 else min(MACHI_CLOCK_MAX_STEP_SECONDS, max(0.0, (now - last) / 1000))
        last = now
        realDelta = real
        delta = if (paused) 0.0 else real * timeScale
        elapsed += delta
    }

    /** 循环停过一阵后重新开始：不把停着的这段算进下一帧。 */
    fun resync() {
        last = -1.0
    }
}
