// 凝彩运动层：逐函数对译 Folia temperaMotionEasing.ts、temperaMotion.ts（逐字运动）、temperaEnterStyles.ts（七种入场）、
// temperaCamera.ts（shot 镜头关键帧插值 + 呼吸）、temperaTransitions.ts（段落转场帧）、temperaShotFrame.ts 的交接时长、
// temperaBlocks.ts 的色块进场时间表。全是纯函数，可单测。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

internal fun tClamp01(v: Double) = min(1.0, max(0.0, v))

private fun cubicCoordinate(p1: Double, p2: Double, t: Double): Double {
    val inv = 1 - t
    return 3 * inv * inv * t * p1 + 3 * inv * t * t * p2 + t * t * t
}

internal fun temperaCubicBezier(x1: Double, y1: Double, x2: Double, y2: Double, value: Double): Double {
    val target = tClamp01(value)
    if (target == 0.0 || target == 1.0) return target
    var low = 0.0; var high = 1.0; var p = target
    repeat(12) {
        val x = cubicCoordinate(x1, x2, p)
        if (x < target) low = p else high = p
        p = (low + high) / 2
    }
    return cubicCoordinate(y1, y2, p)
}

internal fun easeTemperaEnter(v: Double) = temperaCubicBezier(0.22, 1.0, 0.36, 1.0, v)
internal fun easeTemperaInOut(v: Double) = temperaCubicBezier(0.62, 0.0, 0.32, 1.0, v)
internal fun easeTemperaSoftBack(v: Double): Double {
    val t = tClamp01(v)
    val c = 1.42
    return 1 + (c + 1) * (t - 1).pow(3) + c * (t - 1).pow(2)
}

internal fun resolveShotPacedDuration(shotDuration: Double, fraction: Double, minSeconds: Double, maxSeconds: Double) =
    min(maxSeconds, max(minSeconds, shotDuration * fraction))

// ───────────────────────────── temperaEnterStyles.ts ─────────────────────────────

internal enum class TpEnterStyle { SLIDE, FROM_LEFT, FROM_RIGHT, FROM_ABOVE, FROM_BELOW, SWING, STAMP }

internal class TpEnterFrame(val x: Double, val y: Double, val rotation: Double, val scaleX: Double, val scaleY: Double, val echo: Double)

private fun directional(enterX: Double, enterY: Double, enterRotation: Double, travel: Double, uniform: Double, dirX: Double, dirY: Double, rotationScale: Double): TpEnterFrame {
    val magnitude = hypot(enterX, enterY)
    val length = hypot(dirX, dirY).let { if (it == 0.0) 1.0 else it }
    return TpEnterFrame((dirX / length) * magnitude * travel, (dirY / length) * magnitude * travel, enterRotation * rotationScale * travel, uniform, uniform, travel)
}

internal fun resolveTemperaEnterFrame(style: TpEnterStyle, g: TpGlyphMotion, travel: Double, linear: Double): TpEnterFrame {
    val settle = easeTemperaSoftBack(linear)
    val uniform = g.enterScale + (1 - g.enterScale) * settle
    return when (style) {
        TpEnterStyle.FROM_LEFT -> directional(g.enterX, g.enterY, g.enterRotation, travel, uniform, -1.0, 0.12, 0.4)
        TpEnterStyle.FROM_RIGHT -> directional(g.enterX, g.enterY, g.enterRotation, travel, uniform, 1.0, -0.12, 0.4)
        TpEnterStyle.FROM_ABOVE -> directional(g.enterX, g.enterY, g.enterRotation, travel, uniform, 0.12, -1.0, 0.4)
        TpEnterStyle.FROM_BELOW -> directional(g.enterX, g.enterY, g.enterRotation, travel, uniform, -0.12, 1.0, 0.4)
        TpEnterStyle.SWING -> TpEnterFrame(
            g.enterX * 0.6 * travel, g.enterY * 0.6 * travel,
            (g.enterRotation + sign(if (g.enterRotation == 0.0) 1.0 else g.enterRotation) * 0.95) * travel,
            uniform, uniform, travel * 0.8,
        )
        TpEnterStyle.STAMP -> TpEnterFrame(0.0, 0.0, g.enterRotation * 0.6 * travel, 1 + travel * 0.7, 1 + travel * 0.7, 0.0)
        TpEnterStyle.SLIDE -> TpEnterFrame(g.enterX * travel, g.enterY * travel, g.enterRotation * travel, uniform, uniform, travel)
    }
}

// ───────────────────────────── temperaMotion.ts ─────────────────────────────

internal class TpGlyphMotion(
    val startTime: Double,
    val settleTime: Double,
    val endTime: Double,
    val enterX: Double,
    val enterY: Double,
    val enterRotation: Double,
    val enterScale: Double,
    val rotation: Double,
    val enterStyle: TpEnterStyle,
    val releaseTime: Double,
    val trackingX: Double,
    val trackingY: Double,
)

internal class TpGlyphFrame(
    val visible: Boolean, val alpha: Double, val x: Double, val y: Double, val rotation: Double,
    val scaleX: Double, val scaleY: Double, val echoX: Double, val echoY: Double, val echoAlpha: Double,
)

private const val CURRENT_EMPHASIS = 0.05
private const val ECHO_ALPHA = 0.5
private const val MAX_REVEAL_WINDOW = 1.35
private const val RELEASE_TRACKING = 0.055

internal fun resolveTemperaGlyphMotion(g: TpGlyphMotion, time: Double, motion: Double): TpGlyphFrame {
    val window = max(g.settleTime - g.startTime, 0.08)
    val linear = tClamp01((time - g.startTime) / window)
    val travel = 1 - easeTemperaEnter(linear)
    val entrance = resolveTemperaEnterFrame(g.enterStyle, g, travel, linear)
    val reveal = tClamp01((time - g.startTime) / min(window, MAX_REVEAL_WINDOW))
    val alpha = easeTemperaInOut(tClamp01(reveal * 2.4))
    val sungWindow = g.endTime - g.startTime
    val attack = min(0.12, sungWindow * 0.5)
    val emphasis = if (sungWindow <= 0) 0.0 else easeTemperaInOut(tClamp01((time - g.startTime) / max(attack, 0.02)))
    val releaseStart = max(g.endTime, g.settleTime)
    val release = easeTemperaInOut(tClamp01((time - releaseStart) / max(g.releaseTime - releaseStart, 0.001)))
    val spread = release * tClamp01(motion) * RELEASE_TRACKING
    val amount = tClamp01(motion)
    val swell = emphasis * CURRENT_EMPHASIS * amount
    return TpGlyphFrame(
        visible = time >= g.startTime,
        alpha = alpha,
        x = entrance.x * motion + g.trackingX * spread,
        y = entrance.y * motion + g.trackingY * spread,
        rotation = g.rotation + entrance.rotation * motion,
        scaleX = entrance.scaleX + (1 - entrance.scaleX) * (1 - amount) + swell,
        scaleY = entrance.scaleY + (1 - entrance.scaleY) * (1 - amount) + swell,
        echoX = entrance.x * motion,
        echoY = entrance.y * motion,
        echoAlpha = entrance.echo * (1 - easeTemperaEnter(reveal)) * ECHO_ALPHA * amount,
    )
}

// ───────────────────────────── temperaCamera.ts ─────────────────────────────

internal class TpCameraFrame(var x: Double, var y: Double, var scale: Double, var rotation: Double)

private fun camEaseInOut(v: Double): Double {
    val t = tClamp01(v)
    return if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
}

internal fun resolveTemperaCameraFrame(shot: com.guangyue.feature.player.lyrics.visualizer.TpShot, progress: Double): TpCameraFrame {
    val c = tClamp01(progress)
    val eased = c * 0.5 + camEaseInOut(c) * 0.5
    val amount = eased + max(0.0, progress - 1) * 0.35
    val s = shot.camera; val e = shot.cameraEnd
    fun lerp(a: Double, b: Double) = a + (b - a) * amount
    return TpCameraFrame(lerp(s.x, e.x), lerp(s.y, e.y), lerp(s.zoom, e.zoom), lerp(s.rotation, e.rotation))
}

internal fun resolveTemperaCameraBreath(time: Double, phase: Double): TpCameraFrame {
    val tau = time * PI * 2
    return TpCameraFrame(
        (sin(tau * 0.13 + phase) * 0.65 + sin(tau * 0.31 + phase * 1.7) * 0.35) * 0.006,
        (cos(tau * 0.11 + phase * 2.3) * 0.65 + sin(tau * 0.29 + phase * 0.9) * 0.35) * 0.006,
        sin(tau * 0.09 + phase * 1.3) * 0.002,
        sin(tau * 0.07 + phase * 2.9) * 0.0015,
    )
}

internal fun resolveTemperaBreathWeight(time: Double, revealDoneTime: Double, ramp: Double = 1.2) =
    if (ramp <= 0) (if (time >= revealDoneTime) 1.0 else 0.0) else camEaseInOut(tClamp01((time - revealDoneTime) / ramp))

// ───────────────────────────── temperaShotFrame.ts（交接） ─────────────────────────────

internal fun resolveShotHandoff(shot: com.guangyue.feature.player.lyrics.visualizer.TpShot) =
    resolveShotPacedDuration(shot.endTime - shot.startTime, 0.3, 0.4, 1.1)

internal fun resolveTemperaShotExit(shot: com.guangyue.feature.player.lyrics.visualizer.TpShot, time: Double) =
    tClamp01((time - shot.endTime) / resolveShotHandoff(shot))

// ───────────────────────────── temperaTransitions.ts ─────────────────────────────

internal class TpTransitionFrame(
    val x: Double = 0.0, val y: Double = 0.0, val scale: Double = 1.0, val rotation: Double = 0.0,
    val alpha: Double = 1.0, val blur: Double = 0.0, val wipe: Double = 0.0, val wipeAngle: Double = 0.0,
)

internal val IDLE_TP_TRANSITION = TpTransitionFrame()

internal fun resolveTemperaTransitionEffectFrame(kind: String, exit: Boolean, progress: Double, flowAngle: Double): TpTransitionFrame {
    val linear = tClamp01(progress)
    val eased = easeTemperaInOut(linear)
    val fx = cos(flowAngle); val fy = sin(flowAngle)
    if (kind == "block-wipe") return TpTransitionFrame(wipe = if (exit) eased else 1 + eased, wipeAngle = flowAngle)
    if (kind == "camera-pan") {
        val offset = if (exit) eased * 0.5 else -(1 - eased) * 0.5
        val alpha = if (exit) 1 - tClamp01((linear - 0.72) / 0.28) else tClamp01(linear / 0.3)
        return TpTransitionFrame(x = fx * offset, y = fy * offset, scale = 1 + (if (exit) eased else 1 - eased) * 0.03, alpha = alpha, wipeAngle = flowAngle)
    }
    val drift = (if (exit) eased else eased - 1) * 0.09
    val away = if (exit) eased else 1 - eased
    return TpTransitionFrame(
        x = fx * drift, y = fy * drift, scale = 1 + away * 0.07,
        alpha = if (exit) 1 - tClamp01((linear - 0.55) / 0.45) else tClamp01(linear / 0.45),
        blur = away * 6, wipeAngle = flowAngle,
    )
}

internal fun resolveTemperaExitTransitionFrame(paragraph: com.guangyue.feature.player.lyrics.visualizer.TpParagraph, time: Double, enabled: Boolean): TpTransitionFrame {
    val tr = paragraph.transitionOut
    if (!enabled || tr == null || time < tr.startTime) return IDLE_TP_TRANSITION
    val progress = (time - tr.startTime) / max(tr.endTime - tr.startTime, 0.001)
    return resolveTemperaTransitionEffectFrame(tr.kind, true, progress, paragraph.shots.lastOrNull()?.flowAngle ?: 0.0)
}

internal fun resolveTemperaEnterTransitionFrame(kind: String?, timeSinceStart: Double, duration: Double, enabled: Boolean, flowAngle: Double): TpTransitionFrame {
    if (!enabled || kind == null || timeSinceStart < 0 || timeSinceStart > duration) return IDLE_TP_TRANSITION
    return resolveTemperaTransitionEffectFrame(kind, false, timeSinceStart / max(duration, 0.001), flowAngle)
}

/** temperaSceneFrame.resolveTemperaSceneTransitionFrame。 */
internal fun resolveTemperaSceneTransitionFrame(
    program: com.guangyue.feature.player.lyrics.visualizer.TpProgram,
    index: Int,
    time: Double,
    isIncoming: Boolean,
    outgoing: com.guangyue.feature.player.lyrics.visualizer.TpTransition?,
    enabled: Boolean,
): TpTransitionFrame {
    val paragraph = program.paragraphs[index]
    val prev = if (index > 0) program.paragraphs[index - 1].transitionOut else null
    val enterDuration = if (prev != null) max(0.35, min(1.0, prev.endTime - prev.startTime)) else 0.0
    val entering = enabled && prev != null && prev.kind == "block-wipe" && time >= paragraph.startTime && time <= paragraph.startTime + enterDuration
    val flow = paragraph.shots.firstOrNull()?.flowAngle ?: 0.0
    return when {
        isIncoming && outgoing != null -> resolveTemperaEnterTransitionFrame(outgoing.kind, time - outgoing.startTime, max(0.001, outgoing.endTime - outgoing.startTime), true, flow)
        entering && prev != null -> resolveTemperaEnterTransitionFrame(prev.kind, time - paragraph.startTime, enterDuration, true, flow)
        else -> resolveTemperaExitTransitionFrame(paragraph, time, enabled)
    }
}

// ───────────────────────────── temperaBlocks.ts（色块时间表） ─────────────────────────────

/** 每个色块的实际 delay / span（秒）：按 shot 歌词节奏换算，再整体压进预算。 */
internal fun temperaBlockSchedule(items: List<TpBlockOptions>, paceDuration: Double): Pair<DoubleArray, DoubleArray> {
    val budget = max(0.5, paceDuration)
    val delays = DoubleArray(items.size); val spans = DoubleArray(items.size)
    items.forEachIndexed { i, o ->
        val rawDelay = resolveShotPacedDuration(paceDuration, o.delay ?: 0.0, 0.0, 1.4)
        val rawSpan = resolveShotPacedDuration(paceDuration, o.span ?: 0.45, 0.7, 2.6)
        val compress = min(1.0, budget / (rawDelay + rawSpan))
        delays[i] = rawDelay * compress; spans[i] = rawSpan * compress
    }
    return delays to spans
}

internal fun absD(v: Double) = abs(v)
