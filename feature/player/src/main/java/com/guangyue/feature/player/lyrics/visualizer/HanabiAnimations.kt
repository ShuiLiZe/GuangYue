package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/*
 * R7 花火⑥b：逐函数对译 Folia hanabi/text/animations.ts（逐字动画曲线，纯函数）。
 * 17 种入场、9 种强调、7 种退场，外加入场 / 退场时间表与 stamp 整组震动。
 * 位移以字号为单位，旋转为弧度；幅度统一乘 motion，透明度不乘。金标 hanabi_text_golden.txt。
 */

internal class HbGlyphPose(
    var dx: Double = 0.0, var dy: Double = 0.0, var scaleX: Double = 1.0, var scaleY: Double = 1.0,
    var rotation: Double = 0.0, var alpha: Double = 1.0, var skew: Double = 0.0,
    /** scramble：≥ 0 时显示乱码字形表里的第几个（取模），-1 = 原字。 */
    var scramble: Int = -1,
    var rgbSplit: Double = 0.0, var blur: Double = 0.0, var split: Double = 0.0,
    var backside: Boolean = false, var reveal: Double = 1.0,
) {
    fun set(o: HbGlyphPose): HbGlyphPose {
        dx = o.dx; dy = o.dy; scaleX = o.scaleX; scaleY = o.scaleY; rotation = o.rotation; alpha = o.alpha; skew = o.skew
        scramble = o.scramble; rgbSplit = o.rgbSplit; blur = o.blur; split = o.split; backside = o.backside; reveal = o.reveal
        return this
    }
    fun rest(): HbGlyphPose = set(HB_REST_POSE)
}

internal class HbSingPose(
    var dx: Double = 0.0, var dy: Double = 0.0, var scale: Double = 1.0, var scaleX: Double = 1.0, var scaleY: Double = 1.0,
    var rotation: Double = 0.0, var flash: Double = 0.0, var glow: Double = 0.0, var color: Double = 0.0,
) {
    fun set(o: HbSingPose): HbSingPose {
        dx = o.dx; dy = o.dy; scale = o.scale; scaleX = o.scaleX; scaleY = o.scaleY; rotation = o.rotation; flash = o.flash; glow = o.glow; color = o.color
        return this
    }
}

private val HB_REST_POSE = HbGlyphPose()

internal class HbFxContext(
    var index: Int, var count: Int,
    val rand: DoubleArray,
    var toCenterX: Double, var toCenterY: Double,
    var dirX: Double, var dirY: Double,
    var time: Double,
    var motion: Double,
    var slideDistance: Double? = null,
)

internal val HB_ENTER_FX = listOf("pop", "drop", "rise", "spin", "wipe", "type", "scramble", "stretch", "elastic", "split", "spiral", "flip", "glitch", "stamp", "wave", "zoom", "burst")
internal val HB_SING_FX = listOf("none", "bounce", "flash", "swell", "shake", "jump", "glow", "underline", "color")
internal val HB_EXIT_FX = listOf("ascend", "scatter", "shrink", "slide", "dissolve", "fall", "blink")

internal val HB_ENTER_DURATION: Map<String, Double> = mapOf(
    "pop" to 0.45, "drop" to 0.62, "rise" to 0.6, "spin" to 0.62, "wipe" to 0.42, "type" to 0.12, "scramble" to 0.75, "stretch" to 0.55,
    "elastic" to 0.85, "split" to 0.5, "spiral" to 0.85, "flip" to 0.55, "glitch" to 0.6, "stamp" to 0.5, "wave" to 0.75, "zoom" to 0.6, "burst" to 0.75,
)

internal val HB_ENTER_ARRIVAL: Map<String, Double> = mapOf(
    "pop" to 0.0, "drop" to 0.4, "rise" to 0.3, "spin" to 0.2, "wipe" to 0.0, "type" to 0.0, "scramble" to 0.0, "stretch" to 0.1, "elastic" to 0.1,
    "split" to 0.3, "spiral" to 0.45, "flip" to 0.15, "glitch" to 0.0, "stamp" to 0.35, "wave" to 0.3, "zoom" to 0.25, "burst" to 0.3,
)

internal const val HB_STAMP_IMPACT = 0.5

// ─── 缓动 ───
internal fun hbC01(v: Double) = if (v <= 0) 0.0 else if (v >= 1) 1.0 else v
internal fun hbEaseOutCubic(t: Double) = 1 - (1 - t).pow(3)
private fun easeInCubic(t: Double) = t * t * t
private fun easeInQuad(t: Double) = t * t
private fun easeOutQuad(t: Double) = 1 - (1 - t) * (1 - t)
internal fun hbEaseOutQuart(t: Double) = 1 - (1 - t).pow(4)
internal fun hbEaseOutExpo(t: Double) = if (t >= 1) 1.0 else 1 - 2.0.pow(-10 * t)
internal fun hbEaseOutBack(t: Double, s: Double = 1.70158) = 1 + (s + 1) * (t - 1).pow(3) + s * (t - 1).pow(2)
private fun easeInBack(t: Double, s: Double = 1.70158) = (s + 1) * t * t * t - s * t * t
internal fun hbEaseInOutCubic(t: Double) = if (t < 0.5) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2
private fun dampedCos(q: Double, freq: Double, decay: Double) = exp(-decay * q) * cos(freq * q) * (1 - q)
private fun dampedSin(q: Double, freq: Double, decay: Double) = exp(-decay * q) * sin(freq * q) * (1 - q)

/** 整数哈希 → 0..1（Math.imul 语义 = Kotlin Int 乘法回绕）。 */
internal fun hbTextHash01(a: Int, b: Int = 0): Double {
    var h = ((a xor 0x9e3779b9.toInt()) * 0x85ebca6b.toInt()) xor ((b + 0x632be5ab) * 0xc2b2ae35.toInt())
    h = h xor (h ushr 15)
    h *= 0x2c1b3c6d
    h = h xor (h ushr 12)
    h *= 0x297a2d39
    h = h xor (h ushr 15)
    return (h.toLong() and 0xffffffffL) / 4294967296.0
}

private fun applyMotion(pose: HbGlyphPose, motion: Double): HbGlyphPose {
    if (motion == 1.0) return pose
    pose.dx *= motion; pose.dy *= motion; pose.rotation *= motion; pose.skew *= motion; pose.rgbSplit *= motion
    pose.scaleX = 1 + (pose.scaleX - 1) * motion
    pose.scaleY = 1 + (pose.scaleY - 1) * motion
    return pose
}

private fun scaled(pose: HbGlyphPose, s: Double): HbGlyphPose { pose.scaleX = s; pose.scaleY = s; return pose }

/** 入场：p = 这个字的入场进度 0..1；p ≥ 1 时一定回到静止姿态。 */
internal fun hbEnterPose(fx: String, p: Double, ctx: HbFxContext, pose: HbGlyphPose = HbGlyphPose()): HbGlyphPose {
    pose.rest()
    if (p >= 1) return pose
    val q = hbC01(p)
    val r0 = ctx.rand[0]; val r1 = ctx.rand[1]; val r2 = ctx.rand[2]
    when (fx) {
        "pop" -> {
            val s = hbEaseOutBack(q, 2.4)
            scaled(pose, s)
            pose.scaleY = s * (1 + 0.14 * dampedSin(q, 14.0, 3.0))
            pose.scaleX = s * (1 - 0.1 * dampedSin(q, 14.0, 3.0))
            pose.rotation = (r0 - 0.5) * 0.7 * (1 - hbEaseOutCubic(q))
            pose.dy = 0.25 * (1 - hbEaseOutCubic(q))
            pose.alpha = hbC01(q * 6)
        }
        "drop" -> {
            if (q < 0.48) {
                val f = q / 0.48
                pose.dy = -2.4 * (1 - f * f)
                pose.scaleY = 1 + 0.3 * f
                pose.scaleX = 1 - 0.12 * f
            } else {
                val f = (q - 0.48) / 0.52
                pose.dy = -0.32 * abs(sin(f * PI * 2)) * exp(-3 * f) * (1 - f)
                pose.scaleY = 1 - 0.3 * dampedCos(f, 16.0, 4.0)
                pose.scaleX = 1 + 0.22 * dampedCos(f, 16.0, 4.0)
            }
            pose.alpha = hbC01(q * 7)
        }
        "rise" -> {
            val f = hbC01(q / 0.58)
            val e = hbEaseOutCubic(f)
            pose.dy = 1.7 * (1 - e)
            if (q < 0.58) {
                pose.scaleY = 1 + 0.55 * (1 - e)
                pose.scaleX = 1 - 0.28 * (1 - e)
            } else {
                val g = (q - 0.58) / 0.42
                pose.scaleY = 1 - 0.2 * dampedSin(g, 11.0, 2.5)
                pose.scaleX = 1 + 0.16 * dampedSin(g, 11.0, 2.5)
                pose.dy += 0.08 * dampedSin(g, 11.0, 2.5)
            }
            pose.alpha = hbC01(q * 4)
        }
        "spin" -> {
            val e = hbEaseOutBack(q, 1.9)
            pose.rotation = -2.6 * (1 - e) * (if (r0 < 0.5) 1 else -1)
            scaled(pose, 0.15 + 0.85 * e)
            pose.dx = -0.7 * (1 - hbEaseOutCubic(q)) * ctx.dirX
            pose.dy = -0.7 * (1 - hbEaseOutCubic(q)) * ctx.dirY - 0.3 * sin(PI * q) * (1 - q)
            pose.alpha = hbC01(q * 4)
        }
        "wipe" -> {
            val e = hbEaseOutCubic(q)
            pose.reveal = hbEaseOutCubic(hbC01(q / 0.8))
            pose.dx = -0.35 * (1 - e) * ctx.dirX
            pose.dy = -0.35 * (1 - e) * ctx.dirY
            pose.skew = -0.3 * (1 - e)
            pose.alpha = hbC01(q * 8)
        }
        "type" -> {
            pose.alpha = if (q > 0) 1.0 else 0.0
            scaled(pose, 1 + 0.3 * (1 - hbC01(q / 0.6)).pow(2))
        }
        "scramble" -> {
            pose.alpha = hbC01(q * 8)
            if (q < 0.72) {
                val frame = floor(ctx.time * 22).toInt()
                pose.scramble = floor(hbTextHash01(frame, floor(r0 * 1e6).toInt()) * 1e6).toInt()
                pose.dy = (hbTextHash01(frame + 7, floor(r1 * 1e6).toInt()) - 0.5) * 0.12
                pose.alpha *= 0.75 + 0.25 * hbTextHash01(frame + 13, 1)
            } else {
                scaled(pose, 1 + 0.16 * (1 - (q - 0.72) / 0.28).pow(2))
            }
        }
        "stretch" -> {
            val k = (1 - q).pow(3)
            pose.scaleX = 1 + 2 * k - 0.16 * dampedSin(q, 13.0, 3.0)
            pose.scaleY = 1 - 0.72 * k + 0.12 * dampedSin(q, 13.0, 3.0)
            pose.dx = -1.4 * k * ctx.dirX
            pose.dy = -1.4 * k * ctx.dirY
            pose.alpha = hbC01(q * 5)
        }
        "elastic" -> {
            val angle = r0 * PI * 2
            val spring = exp(-4.2 * q) * cos(q * PI * 4.5) * (1 - q)
            pose.dx = cos(angle) * 1.6 * spring
            pose.dy = sin(angle) * 1.6 * spring
            pose.rotation = (r1 - 0.5) * 0.8 * spring
            pose.scaleX = 1 + 0.18 * abs(spring)
            pose.scaleY = 1 - 0.12 * abs(spring)
            pose.alpha = hbC01(q * 5)
        }
        "split" -> {
            pose.split = 1 - hbEaseOutExpo(hbC01(q / 0.8))
            if (q > 0.8) {
                val g = (q - 0.8) / 0.2
                pose.scaleX = 1 + 0.1 * dampedSin(g, 10.0, 2.0)
                pose.scaleY = 1 - 0.08 * dampedSin(g, 10.0, 2.0)
            }
            pose.alpha = hbC01(q * 5)
        }
        "spiral" -> {
            val e = hbEaseOutCubic(q)
            val radius = 3.4 * (1 - e)
            val angle = r0 * PI * 2 + PI * 2.5 * (1 - e)
            pose.dx = cos(angle) * radius
            pose.dy = sin(angle) * radius * 0.8
            pose.rotation = -PI * 2 * (1 - e) * (if (r1 < 0.5) 1 else -1)
            scaled(pose, 0.25 + 0.75 * hbEaseOutBack(q, 1.5))
            pose.alpha = hbC01(q * 3)
        }
        "flip" -> {
            val phi = PI * 2.5 * (1 - hbEaseOutCubic(q))
            pose.scaleX = cos(phi)
            pose.backside = pose.scaleX < 0
            pose.scaleY = 1 + 0.08 * sin(phi)
            pose.dy = -0.3 * sin(PI * q) * (1 - q)
            pose.alpha = hbC01(q * 6)
        }
        "glitch" -> {
            if (q < 0.8) {
                val frame = floor(ctx.time * 24).toInt()
                val seed = floor(r0 * 1e6).toInt()
                val intensity = 1 - q / 0.8
                val h1 = hbTextHash01(frame, seed); val h2 = hbTextHash01(frame, seed + 1); val h3 = hbTextHash01(frame, seed + 2); val h4 = hbTextHash01(frame, seed + 3)
                pose.dx = if (h2 > 0.35) (h1 - 0.5) * 0.9 * intensity else 0.0
                pose.rgbSplit = (0.05 + 0.13 * h3) * intensity
                pose.skew = if (h4 > 0.7) (h1 - 0.5) * 0.9 * intensity else 0.0
                pose.scaleX = if (h2 > 0.82) 1 + 0.4 * intensity else 1.0
                pose.alpha = hbC01(q * 10) * (if (hbTextHash01(frame, seed + 4) > 0.18) 1.0 else 0.25)
            }
        }
        "stamp" -> {
            if (q < HB_STAMP_IMPACT) {
                val f = q / HB_STAMP_IMPACT
                scaled(pose, 2.6 - 1.6 * easeInQuad(f))
                pose.rotation = (r0 - 0.5) * 0.35 * (1 - f)
                pose.alpha = hbC01(f * 2.2)
            } else {
                val f = (q - HB_STAMP_IMPACT) / (1 - HB_STAMP_IMPACT)
                pose.scaleX = 1 + 0.22 * dampedCos(f, 17.0, 4.0)
                pose.scaleY = 1 - 0.22 * dampedCos(f, 17.0, 4.0)
            }
        }
        "wave" -> {
            val k = (1 - q).pow(1.5)
            pose.dy = 1.1 * k * cos(q * PI * 3)
            pose.rotation = 0.4 * k * sin(q * PI * 3)
            pose.scaleY = 1 + 0.18 * k * sin(q * PI * 3 + 0.6)
            pose.alpha = hbC01(q * 4)
        }
        "zoom" -> {
            val e = hbEaseOutExpo(q)
            val k = 1.8 * (1 - e)
            scaled(pose, 1 + k * 1.5)
            pose.dx = -ctx.toCenterX * k
            pose.dy = -ctx.toCenterY * k
            pose.blur = (1 - q).pow(1.4)
            pose.alpha = hbC01(q * 3.5)
        }
        "burst" -> {
            val e = hbEaseOutBack(q, 1.8)
            pose.dx = ctx.toCenterX * (1 - e)
            pose.dy = ctx.toCenterY * (1 - e)
            scaled(pose, 0.2 + 0.8 * hbEaseOutBack(hbC01(q * 1.3), 1.6))
            pose.rotation = (r2 - 0.5) * 4 * (1 - hbEaseOutCubic(q))
            pose.alpha = hbC01(q * 6)
        }
    }
    return applyMotion(pose, ctx.motion)
}

/** 退场：p = 这个字的退场进度 0..1；p ≤ 0 为静止姿态，p ≥ 1 时 alpha 一定为 0。 */
internal fun hbExitPose(fx: String, p: Double, ctx: HbFxContext, pose: HbGlyphPose = HbGlyphPose()): HbGlyphPose {
    pose.rest()
    if (p <= 0) return pose
    val q = hbC01(p)
    val r0 = ctx.rand[0]; val r1 = ctx.rand[1]; val r2 = ctx.rand[2]
    when (fx) {
        "ascend" -> {
            val e = easeInCubic(q)
            val dip = if (q < 0.22) sin((q / 0.22) * PI) * 0.12 else 0.0
            pose.dy = dip - 2.8 * e
            pose.dx = (r0 - 0.5) * 0.7 * e
            pose.rotation = (r1 - 0.5) * 0.5 * e
            pose.scaleY = 1 + 0.45 * e
            pose.scaleX = 1 - 0.22 * e
            pose.alpha = 1 - easeInQuad(q)
        }
        "scatter" -> {
            var ox = -ctx.toCenterX + (r0 - 0.5) * 2
            var oy = -ctx.toCenterY + (r1 - 0.5) * 2
            val len = hypot(ox, oy).let { if (it == 0.0) 1.0 else it }
            ox /= len; oy /= len
            val e = hbEaseOutCubic(q)
            val dist = 2.4 + r2 * 1.8
            pose.dx = ox * dist * e
            pose.dy = oy * dist * e
            pose.rotation = (r1 - 0.5) * 3.2 * e
            scaled(pose, 1 - 0.65 * e)
            pose.alpha = 1 - q.pow(1.6)
        }
        "shrink" -> {
            val s = if (q < 0.2) 1 + 0.16 * sin((q / 0.2) * PI / 2) else 1.16 * (1 - easeInBack((q - 0.2) / 0.8, 1.4))
            scaled(pose, max(0.0, s))
            pose.rotation = (r0 - 0.5) * 0.9 * q
            pose.alpha = if (q > 0.8) (1 - q) / 0.2 else 1.0
        }
        "slide" -> {
            val e = easeInBack(q, 1.6)
            val dist = ctx.slideDistance ?: 7.0
            pose.dx = ctx.dirX * dist * e
            pose.dy = ctx.dirY * dist * e
            pose.scaleX = 1 + 0.45 * easeInQuad(q) * abs(ctx.dirX)
            pose.scaleY = 1 + 0.45 * easeInQuad(q) * abs(ctx.dirY)
            pose.skew = -0.45 * easeInQuad(q) * (if (ctx.dirX == 0.0) 1.0 else sign(ctx.dirX))
            pose.alpha = 1 - hbC01((q - 0.45) / 0.55).pow(1.5)
        }
        "dissolve" -> {
            val frame = floor(ctx.time * 20).toInt()
            val flicker = hbTextHash01(frame, floor(r0 * 1e6).toInt())
            pose.alpha = (1 - q).pow(1.3) * (1 - 0.45 * q * flicker)
            scaled(pose, 1 + 0.14 * easeOutQuad(q))
            pose.dy = -0.35 * easeOutQuad(q)
            pose.blur = q
        }
        "fall" -> {
            val e = easeInQuad(q)
            pose.dy = 3 * e
            pose.dx = 0.28 * sin(q * PI * 2 + r0 * 6) * q
            pose.rotation = (r1 - 0.5) * 1.2 * e
            pose.scaleY = 1 + 0.4 * e
            pose.scaleX = 1 - 0.25 * e
            pose.alpha = 1 - q.pow(1.6)
        }
        "blink" -> {
            val on = q < 0.14 || (q >= 0.24 && q < 0.4) || (q >= 0.5 && q < 0.6) || (q >= 0.7 && q < 0.76) || (q >= 0.82 && q < 0.85)
            pose.alpha = if (on && q < 1) 1.0 else 0.0
            scaled(pose, 1 + (if (on) 0.05 * q else 0.0))
        }
    }
    if (q >= 1) pose.alpha = 0.0
    return applyMotion(pose, ctx.motion)
}

/** 强调：u = 被唱到后过了多少秒（< 0 为还没唱到），duration = 这个字被唱的时长。 */
internal fun hbSingPose(fx: String, u: Double, duration: Double, rand: Double, motion: Double, pose: HbSingPose = HbSingPose()): HbSingPose {
    pose.dx = 0.0; pose.dy = 0.0; pose.scale = 1.0; pose.scaleX = 1.0; pose.scaleY = 1.0; pose.rotation = 0.0; pose.flash = 0.0; pose.glow = 0.0; pose.color = 0.0
    if (u < 0 || fx == "none") return pose
    val hold = max(duration, 0.12)
    val envelope = hbC01(u / 0.08) * (1 - hbC01((u - hold) / 0.35))
    when (fx) {
        "bounce" -> {
            val tt = 0.3
            if (u < tt) pose.dy = -0.26 * sin((u / tt) * PI)
            else if (u < tt + 0.16) pose.dy = -0.06 * sin(((u - tt) / 0.16) * PI)
            val land = u - tt
            if (land >= 0 && land < 0.3) {
                val f = land / 0.3
                pose.scaleY = 1 - 0.14 * dampedCos(f, 14.0, 3.0)
                pose.scaleX = 1 + 0.1 * dampedCos(f, 14.0, 3.0)
            } else if (u < tt) {
                pose.scaleY = 1 + 0.08 * sin((u / tt) * PI)
            }
        }
        "flash" -> {
            pose.flash = exp(-u / 0.16)
            pose.scale = 1 + 0.2 * exp(-u / 0.1) * hbC01(u / 0.03)
        }
        "swell" -> pose.scale = 1 + 0.24 * hbEaseOutBack(hbC01(u / 0.18), 2.0) * (1 - hbC01((u - hold) / 0.35))
        "shake" -> {
            val decay = exp(-max(0.0, u - hold) / 0.08)
            val k = hbC01(u / 0.04) * decay * (if (u < hold + 0.4) 1 else 0)
            pose.dx = 0.06 * sin(u * 61 + rand * 9) * k
            pose.dy = 0.04 * sin(u * 47 + rand * 5) * k
            pose.rotation = 0.08 * sin(u * 53 + rand * 7) * k
        }
        "jump" -> {
            val tt = 0.42
            if (u < tt) {
                val f = u / tt
                pose.dy = -0.5 * sin(f * PI)
                pose.rotation = (if (rand < 0.5) 1 else -1) * 0.22 * sin(f * PI * 2) * (1 - f)
                pose.scaleY = 1 + 0.2 * sin(f * PI) * (1 - f)
                pose.scaleX = 1 - 0.12 * sin(f * PI) * (1 - f)
            } else if (u < tt + 0.35) {
                val f = (u - tt) / 0.35
                pose.scaleY = 1 - 0.2 * dampedCos(f, 13.0, 3.0)
                pose.scaleX = 1 + 0.16 * dampedCos(f, 13.0, 3.0)
            }
        }
        "glow" -> { pose.glow = envelope; pose.flash = 0.35 * envelope; pose.scale = 1 + 0.07 * envelope }
        "underline" -> pose.dy = -0.05 * envelope
        "color" -> {
            pose.color = hbC01(u / 0.1)
            pose.scale = 1 + 0.14 * exp(-u / 0.12) * hbC01(u / 0.03)
        }
    }
    if (motion != 1.0) {
        pose.dx *= motion; pose.dy *= motion; pose.rotation *= motion
        pose.scale = 1 + (pose.scale - 1) * motion
        pose.scaleX = 1 + (pose.scaleX - 1) * motion
        pose.scaleY = 1 + (pose.scaleY - 1) * motion
    }
    return pose
}

/** 叠加：enter ∘ exit（位移相加、缩放相乘、透明度相乘，特殊字段取非静止的一方）。 */
internal fun hbCombinePoses(a: HbGlyphPose, b: HbGlyphPose, out: HbGlyphPose = HbGlyphPose()): HbGlyphPose {
    val dx = a.dx + b.dx; val dy = a.dy + b.dy; val sx = a.scaleX * b.scaleX; val sy = a.scaleY * b.scaleY
    val rot = a.rotation + b.rotation; val al = a.alpha * b.alpha; val sk = a.skew + b.skew
    val sc = if (a.scramble >= 0) a.scramble else b.scramble
    val rgb = a.rgbSplit + b.rgbSplit; val bl = max(a.blur, b.blur); val sp = max(a.split, b.split)
    val bs = a.backside || b.backside; val rv = min(a.reveal, b.reveal)
    out.dx = dx; out.dy = dy; out.scaleX = sx; out.scaleY = sy; out.rotation = rot; out.alpha = al; out.skew = sk
    out.scramble = sc; out.rgbSplit = rgb; out.blur = bl; out.split = sp; out.backside = bs; out.reveal = rv
    return out
}

internal class HbEnterSchedule(val start: DoubleArray, val duration: Double, val landed: DoubleArray)

/** 入场次序：阅读顺序；scramble / glitch 打乱；zoom / burst 从中间往两边（名次可并列，取 0.5 步）。 */
internal fun hbEnterOrder(fx: String, count: Int, rand: () -> Double): DoubleArray {
    val order = IntArray(count) { it }
    if (fx == "scramble" || fx == "glitch") {
        for (i in count - 1 downTo 1) {
            val j = floor(rand() * (i + 1)).toInt()
            val t = order[i]; order[i] = order[j]; order[j] = t
        }
        val rank = DoubleArray(count)
        order.forEachIndexed { k, glyph -> rank[glyph] = k.toDouble() }
        return rank
    }
    if (fx == "zoom" || fx == "burst") {
        val mid = (count - 1) / 2.0
        return DoubleArray(count) { jsRound(abs(it - mid) * 2) / 2.0 }
    }
    return DoubleArray(count) { it.toDouble() }
}

private fun jsRound(v: Double) = floor(v + 0.5)

private val STAGGER_FACTOR: Map<String, Double> = mapOf("burst" to 0.35, "zoom" to 0.6, "stamp" to 1.35, "type" to 1.0)

/** 入场时间表：ranks[i] = 第 i 个字的出场名次（空白字 -1）；最后一个字入场完成不晚于 deadline。 */
internal fun hbScheduleEnter(fx: String, ranks: DoubleArray, enterAt: Double, stagger: Double, deadline: Double): HbEnterSchedule {
    val maxRank = ranks.fold(0.0) { m, r -> max(m, r) }
    var step = max(0.0, stagger) * (STAGGER_FACTOR[fx] ?: 1.0)
    var duration = HB_ENTER_DURATION[fx] ?: 0.6
    val available = deadline - enterAt
    if (step * maxRank + duration > available) {
        step = if (maxRank > 0) max(0.0, min(step, (available - duration) / maxRank)) else 0.0
        if (step * maxRank + duration > available) {
            val ratio = max(0.0, available) / (step * maxRank + duration)
            step *= ratio
            duration = max(0.1, duration * ratio)
            if (step * maxRank + duration > available) step = if (maxRank > 0) max(0.0, (available - duration) / maxRank) else 0.0
        }
    }
    val start = DoubleArray(ranks.size) { if (ranks[it] < 0) Double.NaN else enterAt + ranks[it] * step }
    return HbEnterSchedule(start, duration, DoubleArray(ranks.size) { start[it] + duration })
}

internal class HbExitSchedule(val start: DoubleArray, val end: DoubleArray)

internal fun hbScheduleExit(ranks: DoubleArray, exitAt: Double, exitDuration: Double): HbExitSchedule {
    val maxRank = ranks.fold(0.0) { m, r -> max(m, r) }
    val total = max(0.001, exitDuration)
    val step = if (maxRank > 0) min(0.05, (total * 0.4) / maxRank) else 0.0
    val each = total - step * maxRank
    val start = DoubleArray(ranks.size) { if (ranks[it] < 0) Double.NaN else exitAt + ranks[it] * step }
    return HbExitSchedule(start, DoubleArray(ranks.size) { start[it] + each })
}

/** stamp 落定时整组的震动（像素）。 */
internal fun hbStampShake(time: Double, impacts: List<Double>, amplitude: Double): Pair<Double, Double> {
    var x = 0.0; var y = 0.0
    for (i in impacts.indices) {
        val tau = time - impacts[i]
        if (tau < 0 || tau > 0.45) continue
        val k = amplitude * exp(-tau / 0.085)
        x += k * sin(tau * 96 + i * 1.7)
        y += k * 0.8 * sin(tau * 77 + i * 2.3 + 1)
    }
    return x to y
}
