package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * R7 花火⑥d：逐函数对译 Folia hanabi/mg/ambient.ts（环境层，整单元一份）。
 * back = 薄烟、远处星点（缓慢闪烁）、远方的小烟花；front = 前景散景、上升的火星。都按屏幕坐标画，各自按视差系数随 rise 下移，
 * 在纵向 [−margin, height + margin) 里无缝循环。随机流 createRng("<seed>:ambient")，消费顺序与 TS 一致；金标 hanabi_ambient_golden.txt。
 */

internal object HbParallax {
    const val STARS_NEAR = 0.42
    const val STARS_FAR = 0.2
    const val SMOKE = 0.3
    const val DISTANT = 0.55
    const val EMBERS = 1.25
    const val BOKEH = 1.45
}

private fun wrap(v: Double, span: Double) = v - span * floor(v / span)

internal fun hbParallaxY(y0: Double, rise: Double, parallax: Double, drift: Double, height: Double, margin: Double) =
    wrap(y0 + rise * parallax + drift + margin, height + margin * 2) - margin

internal class HbDistantBurst(val at: Double, val x: Double, val y0: Double, val event: HbBurstEvent)

private val DISTANT_KINDS = listOf("kiku", "botan", "ring", "yanagi", "senrin", "kamuro", "strobe", "kiku", "botan")
internal const val HB_DISTANT_DURATION = 2.6

internal fun hbDistantBurstAt(seed: String, k: Int, rate: Double, startTime: Double, width: Double, span: Double): HbDistantBurst {
    val salt = (hbHashString(seed) % 9973).toDouble()
    fun h(i: Int) = hbHash01((k * 17 + i).toDouble(), salt + 0.37)
    val at = startTime + (k + 0.1 + h(1) * 0.8) / rate
    val kind = DISTANT_KINDS[floor(h(2) * DISTANT_KINDS.size).toInt() % DISTANT_KINDS.size]
    val x = width * (0.06 + h(3) * 0.88)
    val y0 = span * h(4)
    val spec = HbBurstSpec(
        kind, (26 + floor(h(5) * 20).toInt()).toDouble(), 0.045 + h(8) * 0.045, HB_DISTANT_DURATION * (0.75 + h(9) * 0.25),
        listOf(floor(h(10) * 5).toInt(), floor(h(11) * 5).toInt(), floor(h(12) * 5).toInt()), 0.3 + h(13) * 0.4, h(14) * 0.6,
    )
    return HbDistantBurst(at, x, y0, HbBurstEvent(x, 0.0, at, spec, "$seed:distant:$k", 1.0, 0.35 + h(6) * 0.65, (h(7) - 0.5) * 0.8))
}

internal fun hbDistantRange(time: Double, rate: Double, startTime: Double, endTime: Double): IntRange {
    if (rate <= 0) return IntRange.EMPTY
    val count = max(0, floor((endTime - startTime) * rate).toInt())
    val first = max(0, floor((time - HB_DISTANT_DURATION - startTime) * rate).toInt() - 1)
    val last = min(count - 1, floor((time - startTime) * rate).toInt())
    return first..last
}

/** 一个环境点。tex：0 glow / 1 dot / 2 star4 / 3 薄烟（glow 中间 0.8 的一块）。 */
internal class HbAmbientDot(
    val tex: Int, val tint: Int, val x: Double, val y0: Double, val size: Double, val alpha: Double,
    val parallax: Double, val speed: Double, val freq: Double, val phase: Double, val sway: Double,
) {
    var visible = false
    var px = 0.0
    var py = 0.0
    var ps = 0.0
    var pa = 0.0
}

internal const val HB_SMOKE_CROP = 0.8

internal class HbAmbient(
    private val seed: String,
    private val palette: HbPalette,
    private val width: Double,
    private val height: Double,
    private val startTime: Double,
    private val endTime: Double,
    density: Double,
    private val distantRate: Double,
) {
    private val unit = min(width, height)
    private val density = max(0.0, min(2.0, density))
    val margin = unit * 0.36
    private val span = height + margin * 2
    val stars: List<HbAmbientDot>
    val smoke: List<HbAmbientDot>
    val embers: List<HbAmbientDot>
    val bokeh: List<HbAmbientDot>
    private val distantMax = jsRound(240 * min(1.5, this.density))
    private val distantPlans = HashMap<Int, Pair<HbDistantBurst, HbBurstPlan>>()

    private fun tinted(i: Int) = palette.sparks.getOrNull(i % max(1, palette.sparks.size)) ?: HB_WHITE

    init {
        val rng = lumiereRng("$seed:ambient")
        val r = { rng.next() }
        val starTint = hbMixColor(HB_WHITE, palette.glow, 0.25)
        stars = List(jsRound(110 * this.density)) {
            val bright = r() < 0.08
            val depth = r()
            val x = r() * width; val y0 = r() * span
            val size = unit * (if (bright) 0.012 + r() * 0.01 else 0.002 + r() * 0.004)
            val alpha = if (bright) 0.7 else 0.2 + r() * 0.55
            val freq = 0.15 + r() * 0.7; val phase = r() * HB_TAU
            HbAmbientDot(if (bright) 2 else 1, if (bright) hbMixColor(HB_WHITE, palette.glow, 0.4) else starTint, x, y0, size, alpha,
                HbParallax.STARS_FAR + depth * (HbParallax.STARS_NEAR - HbParallax.STARS_FAR), 0.0, freq, phase, 0.0)
        }
        smoke = List(jsRound(5 * min(1.5, this.density))) { i ->
            val x = width * (0.1 + r() * 0.8); val y0 = r() * span
            val size = unit * (0.6 + r() * 0.5) * HB_SMOKE_CROP
            val alpha = 0.035 + r() * 0.035
            val freq = 0.03 + r() * 0.04; val phase = r() * HB_TAU; val sway = unit * (0.05 + r() * 0.08)
            HbAmbientDot(3, hbMixColor(tinted(i), 0x9aa0b4, 0.6), x, y0, size, alpha, HbParallax.SMOKE, 0.0, freq, phase, sway)
        }
        embers = List(jsRound(40 * this.density)) { i ->
            val x = r() * width; val y0 = r() * span
            val size = unit * (0.012 + r() * 0.016)
            val alpha = 0.55 + r() * 0.45
            val parallax = HbParallax.EMBERS + r() * 0.1
            val speed = unit * (0.05 + r() * 0.12)
            val freq = 0.4 + r() * 0.9; val phase = r() * HB_TAU; val sway = unit * (0.008 + r() * 0.02)
            HbAmbientDot(0, hbMixColor(HB_GOLD, tinted(i), 0.3), x, y0, size, alpha, parallax, speed, freq, phase, sway)
        }
        bokeh = List(jsRound(6 * this.density)) { i ->
            val x = r() * width; val y0 = r() * span
            val size = unit * (0.08 + r() * 0.1)
            val alpha = 0.06 + r() * 0.08
            val speed = unit * (0.01 + r() * 0.02)
            val freq = 0.1 + r() * 0.2; val phase = r() * HB_TAU
            HbAmbientDot(0, tinted(i + 2), x, y0, size, alpha, HbParallax.BOKEH, speed, freq, phase, unit * 0.02)
        }
    }

    private fun place(d: HbAmbientDot, x: Double, y: Double, size: Double, alpha: Double) {
        d.visible = alpha > 0.004 && y > -size && y < height + size
        if (!d.visible) return
        d.px = x; d.py = y; d.ps = size; d.pa = alpha
    }

    fun update(time: Double, rise: Double) {
        for (d in stars) {
            val y = hbParallaxY(d.y0, rise, d.parallax, 0.0, height, margin)
            val tw = 0.55 + 0.45 * sin(time * d.freq * HB_TAU + d.phase)
            place(d, d.x, y, d.size * (0.85 + 0.3 * tw), d.alpha * tw)
        }
        for (d in smoke) {
            val y = hbParallaxY(d.y0, rise, d.parallax, -time * unit * 0.01, height, margin)
            val x = d.x + sin(time * d.freq * HB_TAU + d.phase) * d.sway
            place(d, x, y, d.size * (1 + 0.1 * sin(time * 0.2 + d.phase)), d.alpha)
        }
        for (d in embers) {
            val y = hbParallaxY(d.y0, rise, d.parallax, -time * d.speed, height, margin)
            val x = d.x + sin(time * d.freq * HB_TAU + d.phase) * d.sway
            val flicker = if (hbHash01(floor(time * 14 + d.phase * 10), d.phase) < 0.25) 0.35 else 1.0
            val fade = 0.35 + 0.65 * hbClamp01(y / height)
            place(d, x, y, d.size * (0.8 + 0.4 * flicker), d.alpha * flicker * fade)
        }
        for (d in bokeh) {
            val y = hbParallaxY(d.y0, rise, d.parallax, -time * d.speed, height, margin)
            val x = d.x + sin(time * d.freq * HB_TAU + d.phase) * d.sway
            place(d, x, y, d.size, d.alpha * (0.8 + 0.2 * sin(time * 0.5 + d.phase)))
        }
    }

    private val one = ArrayList<HbBurstPlan>(1)

    /** 远方小烟花：按 rise 换算纵向位置后画；emit 收到的 y 已含偏移，alpha 已乘 0.6（远处更淡）。 */
    fun drawDistant(time: Double, rise: Double, emit: HbSparkEmit) {
        if (distantRate <= 0 || distantMax <= 0) return
        val range = hbDistantRange(time, distantRate, startTime, endTime)
        distantPlans.keys.retainAll { it in range }
        var used = 0
        for (k in range) {
            val entry = distantPlans.getOrPut(k) {
                val b = hbDistantBurstAt(seed, k, distantRate, startTime, width, span)
                b to hbPlanBurst(b.event, palette.sparks, unit, min(1.5, density))
            }
            val plan = entry.second
            if (time < plan.at || time > plan.end) continue
            val offsetY = hbParallaxY(entry.first.y0, rise, HbParallax.DISTANT, 0.0, height, margin)
            one.clear(); one += plan
            used += hbDrawBursts(one, time, distantMax - used) { t, x, y, w, h, rot, a, tint -> emit.emit(t, x, y + offsetY, w, h, rot, min(1.0, a) * 0.6, tint) }
        }
    }
}

private fun jsRound(v: Double) = floor(v + 0.5).toInt()
