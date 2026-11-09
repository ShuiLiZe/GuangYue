// 花火镜头时间线：逐函数对译 Folia hanabi/timeline.ts。一个段落一个场景，世界随镜头边界上升、贯穿整个单元一直在转
// （镜头内慢转、边界跟着上升甩过 34°–80°，easeSwing 圆顶鼓包 + 七次 smootherstep 换速，位置 / 角速度 / 角加速度都连续），
// 镜头内推近、轻摇、入场冲击。纯函数，可单测（金标 hanabi_timeline_golden.txt）。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal class HbTimelineShot(
    val startTime: Double,
    val endTime: Double,
    val firstSung: Double?,
    val lastSungEnd: Double?,
    val isBridge: Boolean,
    val rise: Double,
    val push: Double,
    val roll: Double,
    val punch: Double,
)

internal class HbShotTiming(
    val start: Double,
    val end: Double,
    val worldY: Double,
    val launchAt: Double,
    val enterAt: Double,
    var exitAt: Double,
    var exitDuration: Double,
    val punchAt: Double,
    val riseFrom: Double?,
    val riseTo: Double?,
    val drift: Double,
    val spin: Double,
    val spinDrift: Double,
)

internal class HbCameraOffset(val scale: Double, val rotation: Double, val dx: Double, val dy: Double)

internal const val HB_RISE_BEFORE = 0.6
internal const val HB_RISE_AFTER = 0.6
internal const val HB_LAUNCH_DURATION = 0.45
internal const val HB_ENTER_LEAD = 0.4
private const val DRIFT_PER_SECOND = 0.03
private const val DRIFT_MAX = 0.15
private const val SWING_SHARPNESS = 4
private const val PUNCH_ATTACK = 0.05
private const val SPIN_CRUISE = 0.2
private const val SPIN_CRUISE_TOTAL = 0.85
private const val SPIN_SWING_MIN = 0.6
private const val SPIN_SWING_RANGE = 0.8
private const val SPIN_FLIP = 0.2

private fun hbClamp(v: Double, lo: Double, hi: Double) = min(hi, max(lo, v))
private fun hbSmooth(v: Double): Double { val t = hbClamp(v, 0.0, 1.0); return t * t * (3 - 2 * t) }

internal fun hbEaseSeptic(v: Double): Double {
    val t = hbClamp(v, 0.0, 1.0)
    return t.pow(4) * (35 + t * (-84 + t * (70 - 20 * t)))
}

private fun easeSepticIntegral(t: Double) = t.pow(5) * (7 + t * (-14 + t * (10 - 2.5 * t)))

private fun binomial(n: Int, k: Int): Double {
    var r = 1.0
    for (i in 1..k) r = (r * (n - k + i)) / i
    return r
}

private val SWING_TERMS = DoubleArray(SWING_SHARPNESS + 1) { k -> (binomial(SWING_SHARPNESS, k) * (-1.0).pow(k)) / (2 * k + 1) }

private fun swingPrimitive(u: Double): Double {
    val u2 = u * u
    var power = u
    var sum = 0.0
    for (term in SWING_TERMS) { sum += term * power; power *= u2 }
    return sum
}

private val SWING_HALF = swingPrimitive(1.0)

internal fun hbEaseSwing(v: Double) = 0.5 + swingPrimitive(2 * hbClamp(v, 0.0, 1.0) - 1) / (2 * SWING_HALF)

/** planSpin：每个镜头的世界角度与镜头内慢转角速度。 */
internal fun hbPlanSpin(durations: List<Double>, amount: Double, startDirection: Int, random: () -> Double): Pair<List<Double>, List<Double>> {
    val angles = ArrayList<Double>(); val drift = ArrayList<Double>()
    var direction = startDirection
    durations.forEachIndexed { index, duration ->
        if (index > 0) {
            val last = index == durations.size - 1
            val roll = random()
            val reversed = direction != startDirection
            if (if (reversed) last || roll < 0.5 else !last && roll < SPIN_FLIP) direction = -direction
        }
        val cruise = direction * amount * min(SPIN_CRUISE, SPIN_CRUISE_TOTAL / max(duration, 0.5))
        drift += cruise
        if (index == 0) { angles += 0.0; return@forEachIndexed }
        val swing = direction * amount * (SPIN_SWING_MIN + SPIN_SWING_RANGE * random())
        angles += angles[index - 1] + (drift[index - 1] * durations[index - 1] + cruise * duration) / 2 + swing
    }
    return angles to drift
}

internal class HbTimeline(
    val shots: List<HbShotTiming>,
    private val src: List<HbTimelineShot>,
    private val height: Double,
    private val motion: Double,
) {
    private val count = shots.size
    private val mids = shots.map { (it.start + it.end) / 2 }

    fun shotAt(time: Double): Int {
        var found = 0
        for (i in 0 until count) if (shots[i].start <= time) found = i
        return found
    }

    private inline fun blended(time: Double, value: (Int, Double) -> Double): Double {
        for (i in 1 until count) {
            val from = shots[i].riseFrom!!; val to = shots[i].riseTo!!
            if (time >= from && time <= to) {
                val k = hbEaseSwing((time - from) / max(to - from, 1e-3))
                return value(i - 1, time) + (value(i, time) - value(i - 1, time)) * k
            }
        }
        return value(shotAt(time), time)
    }

    private fun target(i: Int, time: Double): Double { val s = shots[i]; return -s.worldY + s.drift * (time - (s.start + s.end) / 2) }

    fun rise(time: Double): Double = if (count == 0) 0.0 else blended(time) { i, t -> target(i, t) }

    private fun spinStep(i: Int, time: Double): Double {
        val timing = shots[i]; val previous = shots[i - 1]
        val from = timing.riseFrom!!; val to = timing.riseTo!!
        val ramp = max(to - from, 1e-3)
        val midFrom = mids[i - 1]
        fun drift(t: Double): Double {
            val x = (t - from) / ramp
            val switched = if (x <= 0) 0.0 else if (x >= 1) ramp / 2 + (t - to) else ramp * easeSepticIntegral(x)
            return previous.spinDrift * (t - midFrom) + (timing.spinDrift - previous.spinDrift) * switched
        }
        val swing = timing.spin - previous.spin - drift(mids[i])
        return previous.spin + drift(time) + swing * hbEaseSwing((time - from) / ramp)
    }

    fun spin(time: Double): Double {
        if (count == 0) return 0.0
        if (time <= mids[0]) return shots[0].spin + shots[0].spinDrift * (time - mids[0])
        for (i in 1 until count) if (time < mids[i]) return spinStep(i, time)
        val last = shots[count - 1]
        return last.spin + last.spinDrift * (time - mids[count - 1])
    }

    private fun pushOf(i: Int, time: Double): Double {
        val s = shots[i]
        return 1 + src[i].push * hbSmooth((time - s.start) / max(s.end - s.start, 0.05))
    }

    private fun rollOf(i: Int, time: Double): Double = src[i].roll * motion * sin((time - shots[i].start) * 0.9 + i * 1.3)

    fun camera(time: Double): HbCameraOffset {
        if (count == 0) return HbCameraOffset(1.0, 0.0, 0.0, 0.0)
        var scale = blended(time) { i, t -> pushOf(i, t) }
        val rotation = blended(time) { i, t -> rollOf(i, t) }
        var dx = 0.0; var dy = 0.0
        for (i in 0 until count) {
            val age = time - shots[i].punchAt
            val punch = src[i].punch
            if (age < 0 || age > 0.9 || punch <= 0 || src[i].isBridge) continue
            val amp = punch * motion * exp(-age / 0.16) * hbSmooth(age / PUNCH_ATTACK)
            dx += amp * 0.012 * height * sin(age * 70 + i)
            dy += amp * 0.016 * height * cos(age * 55 + i * 2)
            scale *= 1 + amp * 0.03
        }
        return HbCameraOffset(scale, rotation, dx, dy)
    }
}

internal fun hbBuildTimeline(
    shots: List<HbTimelineShot>,
    unitEnd: Double,
    height: Double,
    riseSpeed: Double,
    motion: Double,
    exitStart: Double? = null,
    spinAngles: List<Double>? = null,
    spinDrift: List<Double>? = null,
): HbTimeline {
    val count = shots.size
    fun durationOf(i: Int) = max(shots[i].endTime - shots[i].startTime, 0.05)
    val before = shots.indices.map { i -> if (i == 0) 0.0 else min(HB_RISE_BEFORE, 0.45 * durationOf(i - 1)) }
    val after = shots.indices.map { i -> if (i == 0) 0.0 else min(HB_RISE_AFTER, 0.35 * durationOf(i)) }
    val timings = ArrayList<HbShotTiming>()
    var worldY = 0.0
    shots.forEachIndexed { i, shot ->
        if (i > 0) worldY -= hbClamp(shot.rise, 0.0, 1.2) * riseSpeed * height
        val boundary = shot.startTime
        val sung = shot.firstSung ?: boundary
        var enterAt = if (shot.isBridge) boundary else min(boundary, sung) - HB_ENTER_LEAD
        if (i > 0) enterAt = maxOf(enterAt, boundary - before[i], timings[i - 1].enterAt + 0.1)
        val duration = durationOf(i)
        timings += HbShotTiming(
            start = shot.startTime, end = shot.endTime, worldY = worldY,
            launchAt = enterAt - HB_LAUNCH_DURATION, enterAt = enterAt, exitAt = 0.0, exitDuration = 0.0,
            punchAt = enterAt + 0.08,
            riseFrom = if (i == 0) null else boundary - before[i], riseTo = if (i == 0) null else boundary + after[i],
            drift = riseSpeed * height * min(DRIFT_PER_SECOND, DRIFT_MAX / duration),
            spin = spinAngles?.getOrNull(i) ?: 0.0, spinDrift = spinDrift?.getOrNull(i) ?: 0.0,
        )
    }
    timings.forEachIndexed { i, timing ->
        val sungEnd = shots[i].lastSungEnd ?: timing.end
        val exitAt: Double; val exitDuration: Double
        if (i < count - 1) {
            val boundary = timing.end
            exitAt = hbClamp(sungEnd, boundary - before[i + 1], boundary - 0.15)
            exitDuration = hbClamp(boundary + after[i + 1] - exitAt, 0.35, 0.9)
        } else if (exitStart != null) {
            exitAt = min(max(sungEnd, exitStart), unitEnd - 0.2)
            exitDuration = hbClamp(unitEnd - exitAt, 0.25, 0.7)
        } else {
            exitAt = unitEnd; exitDuration = 0.5
        }
        timing.exitAt = max(exitAt, timing.enterAt + 0.3)
        timing.exitDuration = exitDuration
    }
    return HbTimeline(timings, shots, height, motion)
}
