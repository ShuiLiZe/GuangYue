// 花火烟花解析模型：逐函数对译 Folia hanabi/mg/burstPlan.ts + mg/mgMath.ts。
// 每朵烟花开花时一次排好所有星（球面斐波那契方向、阻尼 + 终端下落的闭式轨迹 p(τ) = p0 + v0·f + fall·(τ − f)，
// f = (1 − e^{−kτ})/k），之后任意时刻的位置 / 亮度 / 颜色 / 尾迹段都由时刻直接算出，seek 到哪都一样。
// 绘制只产出「精灵」（柔光点 / 实心点 / 四角星 / 拖尾段 / 闪光）与 Folia 的 SparkEmit 一一对应，金标逐个对拍。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ───────────────────────────── mgMath.ts ─────────────────────────────

internal const val HB_TAU = PI * 2
internal const val HB_WHITE = 0xffffff
internal const val HB_GOLD = 0xffc760
internal const val HB_EMBER = 0xff9a3c

internal fun hbClamp01(v: Double) = if (v <= 0) 0.0 else if (v >= 1) 1.0 else v
internal fun hbLerp(a: Double, b: Double, t: Double) = a + (b - a) * t
internal fun hbFract(v: Double) = v - floor(v)
private fun hbRound(v: Double) = floor(v + 0.5).toInt()

/** hash01：fract(sin(n·127.1 + salt·311.7)·43758.5453123)。用 StrictMath（fdlibm，与 V8 同一实现）保证逐位一致。 */
internal fun hbHash01(n: Double, salt: Double = 0.0) = hbFract(StrictMath.sin(n * 127.1 + salt * 311.7) * 43758.5453123)

internal fun hbHashString(key: String): Long {
    var h = 0x811c9dc5.toInt()
    for (ch in key) { h = h xor ch.code; h *= 16777619 }
    return h.toLong() and 0xffffffffL
}

internal fun hbMixColor(a: Int, b: Int, t: Double): Int {
    val k = hbClamp01(t)
    if (k <= 0) return a
    if (k >= 1) return b
    val r = hbRound(((a shr 16) and 255) + ((((b shr 16) and 255) - ((a shr 16) and 255)) * k))
    val g = hbRound(((a shr 8) and 255) + ((((b shr 8) and 255) - ((a shr 8) and 255)) * k))
    val bl = hbRound((a and 255) + (((b and 255) - (a and 255)) * k))
    return (r shl 16) or (g shl 8) or bl
}

// ───────────────────────────── 类型 ─────────────────────────────

internal class HbBurstSpec(val kind: String, val count: Double, val radius: Double, val duration: Double, val colors: List<Int>, val trail: Double, val twinkle: Double)
internal class HbBurstEvent(val x: Double, val y: Double, val at: Double, val spec: HbBurstSpec, val seed: String, val scale: Double, val squash: Double? = null, val rotation: Double? = null)
internal class HbLaunchEvent(val fromX: Double, val fromY: Double, val toX: Double, val toY: Double, val at: Double, val duration: Double, val color: Int, val seed: String)

internal class HbStar(
    val x0: Double, val y0: Double, val vx: Double, val vy: Double, val drag: Double, val fall: Double,
    val start: Double, val life: Double, val size: Double, val color: Int, val endColor: Int, val shift: Double,
    val trailColor: Int, val trail: Double, val segments: Int, val fadeTail: Double, val strobe: Double, val strobeFrom: Double,
    val twinkle: Double, val glitter: Double, val spiral: Double, val spin: Double, val phase: Double, val depth: Double,
    var rank: Double, val tex: Int,
)

internal class HbFlash(val x: Double, val y: Double, val at: Double, val size: Double, val color: Int, val life: Double)
internal class HbBurstPlan(val at: Double, val end: Double, val stars: List<HbStar>, val flashes: List<HbFlash>)

private const val GOLDEN = 0.6180339887498949
private const val GOLDEN_ANGLE = 2.399963229728653

private fun sparkColor(sparks: List<Int>, colors: List<Int>, i: Int): Int {
    val s = sparks.ifEmpty { listOf(HB_WHITE) }
    val n = s.size
    val index = if (colors.isNotEmpty()) colors[((i % colors.size) + colors.size) % colors.size] else i
    return s[((index % n) + n) % n]
}

/** planBurst。sparks = 调色板的火花色（palette.sparks）。 */
internal fun hbPlanBurst(event: HbBurstEvent, sparks: List<Int>, unit: Double, amount: Double): HbBurstPlan {
    val spec = event.spec
    val kind = spec.kind
    val rng = SonnetRandom(sonnetHash("${event.seed}:$kind"))
    fun rnd() = rng.next()
    val R = max(4.0, spec.radius * unit * (if (event.scale == 0.0) 1.0 else event.scale))
    val D = max(0.2, spec.duration)
    val at = event.at
    val n = max(0, hbRound(spec.count * max(0.0, amount)))
    val trailK = hbClamp01(spec.trail)
    val twinkleK = hbClamp01(spec.twinkle)
    val squash = event.squash ?: 1.0
    val rotation = event.rotation ?: 0.0
    val cosR = cos(rotation); val sinR = sin(rotation)
    fun c(i: Int) = sparkColor(sparks, spec.colors, i)
    val stars = ArrayList<HbStar>()
    val flashes = ArrayList<HbFlash>()
    val minSize = max(1.4, unit * 0.0022)

    fun add(
        vx: Double, vy: Double, drag: Double, life: Double, size: Double, color: Int,
        x0: Double = 0.0, y0: Double = 0.0, fall: Double = 0.0, start: Double = 0.0, endColor: Int? = null, shift: Double = 1.0,
        trailColor: Int? = null, trail: Double = 0.0, segments: Int = 3, fadeTail: Double = 0.3, strobe: Double = 0.0, strobeFrom: Double = 0.0,
        twinkle: Double = 0.0, glitter: Double = 0.0, spiral: Double = 0.0, spin: Double = 0.0, phase: Double? = null, depth: Double = 1.0, tex: Int = 0,
    ) {
        val lifeC = min(life, D - start - 0.02)
        if (lifeC <= 0.05) return
        stars += HbStar(
            event.x + x0, event.y + y0, vx, vy, drag, fall, at + start, lifeC, max(minSize, size), color, endColor ?: color, shift,
            trailColor ?: hbMixColor(color, HB_EMBER, 0.3), trail, if (trail > 0) max(1, segments) else 0, fadeTail, strobe, strobeFrom,
            twinkle, glitter, spiral, spin, phase ?: rnd() * HB_TAU, depth, 0.0, tex,
        )
    }

    fun flash(x: Double, y: Double, delay: Double, size: Double, color: Int, life: Double = 0.32) {
        if (delay >= D) return
        flashes += HbFlash(event.x + x, event.y + y, at + delay, size, hbMixColor(color, HB_WHITE, 0.7), min(life, D - delay))
    }

    val tiltA = rnd() * HB_TAU
    val tiltCos = cos(tiltA); val tiltSin = sin(tiltA)
    fun sphere(i: Int, count: Int, jitter: Double = 0.25): DoubleArray {
        val z = 1 - (2 * (i + 0.5)) / count
        val r = sqrt(max(0.0, 1 - z * z))
        val phi = i * GOLDEN_ANGLE + (rnd() - 0.5) * jitter
        val x = r * cos(phi); val y0 = r * sin(phi)
        return doubleArrayOf(x, y0 * tiltCos - z * tiltSin, y0 * tiltSin + z * tiltCos)
    }
    fun planar(x: Double, y: Double): DoubleArray { val sy = y * squash; return doubleArrayOf(x * cosR - sy * sinR, x * sinR + sy * cosR) }
    fun depthOf(z: Double) = 0.62 + 0.38 * (z + 1) / 2
    fun life(low: Double, high: Double) = D * hbLerp(low, high, rnd())
    val c0 = c(0); val c1 = c(1); val c2 = c(2)

    when (kind) {
        "kiku" -> {
            val core = hbRound(n * 0.2); val main = n - core; val k = 3.2
            for (i in 0 until main) {
                val (dx, dy, dz) = sphere(i, main)
                val speed = R * k * (0.94 + rnd() * 0.12)
                add(dx * speed, dy * speed, k, life(0.78, 0.98), R * 0.02, c0, fall = R * 0.3, endColor = hbMixColor(c0, HB_WHITE, 0.65), shift = 0.7,
                    trail = 0.1 + trailK * 0.38, segments = 3, trailColor = hbMixColor(c0, HB_EMBER, 0.3), twinkle = twinkleK, depth = depthOf(dz))
            }
            for (i in 0 until core) {
                val (dx, dy, dz) = sphere(i, core)
                val speed = R * 3.6 * 0.42 * (0.95 + rnd() * 0.1)
                add(dx * speed, dy * speed, 3.6, life(0.4, 0.55), R * 0.017, c1, fall = R * 0.2, trail = 0.04 + trailK * 0.08, segments = 1, fadeTail = 0.2, depth = depthOf(dz))
            }
            flash(0.0, 0.0, 0.0, R * 1.1, c0, 0.36)
        }
        "botan" -> {
            val core = hbRound(n * 0.22); val main = n - core; val k = 3.6
            for (i in 0 until main) {
                val (dx, dy, dz) = sphere(i, main, 0.12)
                val speed = R * k * (0.97 + rnd() * 0.06)
                add(dx * speed, dy * speed, k, life(0.85, 0.97), R * 0.034, c0, fall = R * 0.24, endColor = c1, shift = 0.48, fadeTail = 0.12, twinkle = twinkleK * 0.6, depth = depthOf(dz))
            }
            for (i in 0 until core) {
                val (dx, dy, dz) = sphere(i, core, 0.12)
                val speed = R * k * 0.5
                add(dx * speed, dy * speed, k, life(0.6, 0.72), R * 0.03, c2, fall = R * 0.2, endColor = hbMixColor(c2, HB_WHITE, 0.5), shift = 0.55, fadeTail = 0.12, depth = depthOf(dz))
            }
            flash(0.0, 0.0, 0.0, R * 1.2, c0, 0.3)
        }
        "yanagi" -> {
            val k = 2.5; val gold = hbMixColor(c0, HB_GOLD, 0.6)
            for (i in 0 until n) {
                val (dx, dy, dz) = sphere(i, n, 0.4)
                val speed = R * k * 0.72 * (0.9 + rnd() * 0.2)
                add(dx * speed, dy * speed - R * 0.25, k, life(0.88, 0.99), R * 0.013, gold, fall = R * 0.95, endColor = hbMixColor(gold, HB_EMBER, 0.6), shift = 0.6,
                    trail = 0.35 + trailK * 0.95, segments = 4, trailColor = hbMixColor(gold, HB_EMBER, 0.35), fadeTail = 0.45, glitter = 0.25 + twinkleK * 0.5, depth = depthOf(dz) * 0.9)
            }
            flash(0.0, 0.0, 0.0, R * 0.8, gold, 0.3)
        }
        "kamuro" -> {
            val k = 3.0; val gold = hbMixColor(c0, HB_GOLD, 0.78)
            for (i in 0 until n) {
                val (dx, dy, dz) = sphere(i, n, 0.2)
                val speed = R * k * 1.05 * (0.95 + rnd() * 0.1)
                add(dx * speed, dy * speed - R * 0.12, k, life(0.9, 0.99), R * 0.017, gold, fall = R * 0.55, endColor = hbMixColor(gold, HB_EMBER, 0.5), shift = 0.55,
                    trail = 0.45 + trailK * 0.8, segments = 4, trailColor = hbMixColor(gold, HB_EMBER, 0.25), fadeTail = 0.5, glitter = 0.55 + twinkleK * 0.45, depth = depthOf(dz))
            }
            flash(0.0, 0.0, 0.0, R * 1.3, gold, 0.42)
        }
        "senrin" -> {
            val flowers = max(4, min(10, hbRound(n / 14.0)))
            val per = max(6, hbRound(n.toDouble() / flowers))
            val k = 4.4
            for (f in 0 until flowers) {
                val angle = f * GOLDEN_ANGLE + rnd() * 0.6
                val dist = R * 0.78 * sqrt((f + 0.5) / flowers)
                val ox = cos(angle) * dist; val oy = sin(angle) * dist
                val delay = 0.06 + (f.toDouble() / flowers) * D * 0.42 + rnd() * 0.05
                val r = R * (0.2 + rnd() * 0.08)
                val color = c(f)
                for (i in 0 until per) {
                    val (dx, dy, dz) = sphere(i, per, 0.3)
                    val speed = r * k * (0.92 + rnd() * 0.16)
                    add(dx * speed, dy * speed, k, D * (0.32 + rnd() * 0.12), R * 0.017, color, x0 = ox, y0 = oy, start = delay, fall = R * 0.16,
                        endColor = hbMixColor(color, HB_WHITE, 0.6), shift = 0.65, trail = trailK * 0.08, segments = 1, fadeTail = 0.25, twinkle = 0.3 + twinkleK * 0.7, depth = depthOf(dz))
                }
                flash(ox, oy, delay, r * 1.4, color, 0.22)
            }
        }
        "ring" -> {
            val k = 3.4
            val outer = hbRound(n * 0.7); val inner = hbRound(n * 0.2); val core = n - outer - inner
            fun ringOf(count: Int, reach: Double, color: Int, size: Double, lifeLow: Double) {
                for (i in 0 until count) {
                    val (dx, dy) = planar(cos((HB_TAU * i) / count), sin((HB_TAU * i) / count))
                    val speed = R * k * reach * (0.99 + rnd() * 0.02)
                    add(dx * speed, dy * speed, k, life(lifeLow, lifeLow + 0.12), size, color, fall = R * 0.18, endColor = hbMixColor(color, HB_WHITE, 0.5), shift = 0.75,
                        trail = 0.05 + trailK * 0.2, segments = 2, twinkle = twinkleK)
                }
            }
            ringOf(outer, 1.0, c0, R * 0.022, 0.85)
            ringOf(inner, 0.58, c1, R * 0.018, 0.7)
            for (i in 0 until core) {
                val (dx, dy, dz) = sphere(i, max(1, core))
                val speed = R * k * 0.18
                add(dx * speed, dy * speed, k, life(0.45, 0.6), R * 0.02, c2, fall = R * 0.15, depth = depthOf(dz))
            }
            flash(0.0, 0.0, 0.0, R * 0.9, c0, 0.3)
        }
        "heart", "star" -> {
            val k = 3.4
            val outline = max(12, hbRound(n * 0.88))
            for (i in 0 until outline) {
                val u = i.toDouble() / outline
                val x: Double; val y: Double
                if (kind == "heart") {
                    val t = u * HB_TAU
                    x = (16 * sin(t).pow(3)) / 17
                    y = -(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t)) / 17
                } else {
                    val edge = u * 10; val e = floor(edge).toInt(); val f = edge - e
                    fun vertex(index: Int): Pair<Double, Double> {
                        val radius = if (index % 2 == 0) 1.0 else 0.4
                        val a = -PI / 2 + (index * PI) / 5
                        return (cos(a) * radius) to (sin(a) * radius)
                    }
                    val (ax, ay) = vertex(e); val (bx, by) = vertex(e + 1)
                    x = hbLerp(ax, bx, f); y = hbLerp(ay, by, f)
                }
                val (dx, dy) = planar(x, y)
                val speed = R * k * (0.99 + rnd() * 0.02)
                add(dx * speed, dy * speed, k, life(0.8, 0.92), R * 0.024, c0, fall = R * 0.1, endColor = hbMixColor(c0, HB_WHITE, 0.55), shift = 0.7,
                    trail = 0.04 + trailK * 0.1, segments = 1, fadeTail = 0.25, twinkle = twinkleK)
            }
            val core = n - outline
            for (i in 0 until core) {
                val (dx, dy, dz) = sphere(i, max(1, core))
                val speed = R * k * 0.15
                add(dx * speed, dy * speed, k, life(0.4, 0.55), R * 0.018, c1, fall = R * 0.1, depth = depthOf(dz))
            }
            flash(0.0, 0.0, 0.0, R * 0.8, c0, 0.28)
        }
        "palm" -> {
            val branches = max(6, min(14, hbRound(n / 9.0)))
            val crumbs = max(0, min(6, floor((n - branches).toDouble() / branches).toInt()))
            val k = 2.3; val gold = hbMixColor(c0, HB_GOLD, 0.45)
            for (b in 0 until branches) {
                val angle = (HB_TAU * b) / branches + (rnd() - 0.5) * 0.35
                val up = -sin(angle)
                val reach = 1 + up * 0.15 + (rnd() - 0.5) * 0.1
                val dx = cos(angle); val dy = sin(angle)
                val speed = R * k * reach
                add(dx * speed, dy * speed, k, life(0.88, 0.98), R * 0.05, gold, fall = R * 0.7, endColor = hbMixColor(gold, HB_EMBER, 0.55), shift = 0.55,
                    trail = 0.4 + trailK * 0.55, segments = 5, trailColor = hbMixColor(gold, HB_EMBER, 0.3), fadeTail = 0.4, glitter = 0.3 + twinkleK * 0.4)
                for (j in 0 until crumbs) {
                    val f = 0.9 - j * 0.1
                    val cvx = dx * speed * f + (rnd() - 0.5) * R * 0.3
                    val cvy = dy * speed * f + (rnd() - 0.5) * R * 0.3
                    add(cvx, cvy, k, life(0.55, 0.85), R * 0.014, hbMixColor(gold, HB_WHITE, 0.3), fall = R * 0.75, trail = 0.08, segments = 1, fadeTail = 0.5, glitter = 0.8)
                }
            }
            flash(0.0, 0.0, 0.0, R * 1.0, gold, 0.4)
        }
        "crossette" -> {
            val parents = max(6, min(16, hbRound(n / 5.0)))
            val k = 2.8; val kc = 3.4; val fall = R * 0.32
            for (p in 0 until parents) {
                val angle = (HB_TAU * p) / parents + (rnd() - 0.5) * 0.4
                val speed = R * k * 0.72 * (0.9 + rnd() * 0.2)
                val vx = cos(angle) * speed; val vy = sin(angle) * speed
                val split = D * (0.28 + rnd() * 0.08)
                add(vx, vy, k, split, R * 0.024, c0, fall = fall, trail = 0.08 + trailK * 0.2, segments = 2, fadeTail = 0.05)
                val e = exp(-k * split); val f = (1 - e) / k
                val sx = vx * f; val sy = vy * f + fall * (split - f)
                val svx = vx * e; val svy = (vy - fall) * e + fall
                val turn = angle + PI / 4 + (rnd() - 0.5) * 0.3
                for (q in 0 until 4) {
                    val a = turn + (q * PI) / 2
                    val cs = R * kc * 0.36
                    add(svx * 0.35 + cos(a) * cs, svy * 0.35 + sin(a) * cs, kc, D * (0.45 + rnd() * 0.12), R * 0.018, c1, x0 = sx, y0 = sy, start = split,
                        fall = R * 0.38, endColor = hbMixColor(c1, HB_WHITE, 0.5), shift = 0.7, trail = 0.06 + trailK * 0.18, segments = 2, twinkle = twinkleK)
                }
                flash(sx, sy, split, R * 0.2, c1, 0.16)
            }
            flash(0.0, 0.0, 0.0, R * 0.8, c0, 0.3)
        }
        "strobe" -> {
            val k = 4.0; val white = hbMixColor(c0, HB_WHITE, 0.55)
            for (i in 0 until n) {
                val (dx, dy, dz) = sphere(i, n, 0.6)
                val speed = R * k * 0.88 * (0.8 + rnd() * 0.3)
                val lifeV = life(0.85, 0.99)
                val strobe = 7 + rnd() * 7
                val strobeFrom = 0.3 + rnd() * 0.25
                val tex = if (rnd() < 0.3) 1 else 0
                add(dx * speed, dy * speed, k, lifeV, R * 0.028, white, fall = R * 0.12, endColor = c(i), shift = 0.25,
                    strobe = strobe, strobeFrom = strobeFrom, fadeTail = 0.15, depth = depthOf(dz), tex = tex)
            }
            flash(0.0, 0.0, 0.0, R * 0.9, white, 0.3)
        }
        "hachi" -> {
            val k = 2.5; val silver = hbMixColor(c0, HB_WHITE, 0.7)
            for (i in 0 until n) {
                val angle = rnd() * HB_TAU
                val speed = R * k * (0.3 + rnd() * 0.6)
                val lifeV = life(0.5, 0.98)
                val spiral = R * (0.05 + rnd() * 0.11)
                val spin = (if (rnd() < 0.5) -1 else 1) * (9 + rnd() * 12)
                add(cos(angle) * speed, sin(angle) * speed, k, lifeV, R * 0.013, silver, fall = R * 0.25, endColor = c(i), shift = 0.6,
                    trail = 0.08 + trailK * 0.14, segments = 2, trailColor = hbMixColor(silver, HB_EMBER, 0.3), spiral = spiral, spin = spin, twinkle = 0.4 + twinkleK * 0.6, fadeTail = 0.3)
            }
            flash(0.0, 0.0, 0.0, R * 0.6, silver, 0.24)
        }
        "willow-ring" -> {
            val inner = hbRound(n * 0.35); val outer = n - inner
            val gold = hbMixColor(c1, HB_GOLD, 0.7)
            for (i in 0 until inner) {
                val (dx, dy) = planar(cos((HB_TAU * i) / inner), sin((HB_TAU * i) / inner))
                val speed = R * 3.6 * 0.46
                add(dx * speed, dy * speed, 3.6, life(0.45, 0.55), R * 0.03, c0, fall = R * 0.15, endColor = hbMixColor(c0, HB_WHITE, 0.5), shift = 0.6, fadeTail = 0.1)
            }
            for (i in 0 until outer) {
                val (dx, dy, dz) = sphere(i, outer, 0.4)
                val speed = R * 2.6 * (0.92 + rnd() * 0.12)
                add(dx * speed, dy * speed - R * 0.2, 2.6, life(0.88, 0.99), R * 0.013, gold, fall = R * 0.9, endColor = hbMixColor(gold, HB_EMBER, 0.6), shift = 0.6,
                    trail = 0.35 + trailK * 0.9, segments = 4, trailColor = hbMixColor(gold, HB_EMBER, 0.3), fadeTail = 0.45, glitter = 0.3 + twinkleK * 0.4, depth = depthOf(dz) * 0.9)
            }
            flash(0.0, 0.0, 0.0, R * 1.0, c0, 0.34)
        }
        "mine" -> {
            val volleys = max(4, min(8, hbRound(D * 2.6)))
            val per = max(4, hbRound(n.toDouble() / volleys))
            val k = 1.7
            for (v in 0 until volleys) {
                val delay = (v.toDouble() / volleys) * D * 0.55
                val lean = (if (v % 2 == 0) -1 else 1) * (0.12 + rnd() * 0.2)
                val color = c(v)
                val lifeV = min(0.9 + rnd() * 0.5, D * 0.6)
                for (i in 0 until per) {
                    val angle = -PI / 2 + lean + ((i + 0.5) / per - 0.5) * 1.3 + (rnd() - 0.5) * 0.12
                    val speed = R * k * (1.45 + rnd() * 0.45)
                    val l = lifeV * (0.8 + rnd() * 0.2)
                    add(cos(angle) * speed, sin(angle) * speed, k, l, R * 0.02, color, start = delay, fall = R * 0.9, endColor = hbMixColor(color, HB_WHITE, 0.6), shift = 0.7,
                        trail = 0.1 + trailK * 0.3, segments = 3, fadeTail = 0.3, twinkle = twinkleK)
                }
                flash(0.0, 0.0, delay, R * 0.35, color, 0.18)
            }
        }
    }
    stars.forEachIndexed { i, s -> s.rank = ((i + 1) * GOLDEN) % 1 }
    var end = at
    stars.forEach { end = max(end, it.start + it.life) }
    flashes.forEach { end = max(end, it.at + it.life) }
    return HbBurstPlan(at, end, stars, flashes)
}

private operator fun DoubleArray.component1() = this[0]
private operator fun DoubleArray.component2() = this[1]
private operator fun DoubleArray.component3() = this[2]

// ───────────────────────────── 逐时刻 ─────────────────────────────

internal fun hbStarPosition(s: HbStar, tau: Double): Pair<Double, Double> {
    val t = if (tau < 0) 0.0 else tau
    val e = exp(-s.drag * t)
    val f = (1 - e) / s.drag
    var x = s.x0 + s.vx * f
    var y = s.y0 + s.vy * f + s.fall * (t - f)
    if (s.spiral > 0) {
        val r = s.spiral * (1 - exp(-3 * t))
        val a = s.phase + s.spin * t
        x += r * cos(a); y += r * sin(a)
    }
    return x to y
}

private fun starBase(s: HbStar, tau: Double): Double {
    if (tau < 0 || tau > s.life) return 0.0
    val u = tau / s.life
    var b = s.depth * hbClamp01(tau / 0.035)
    if (u > 1 - s.fadeTail) { val k = (1 - u) / s.fadeTail; b *= k * k * (3 - 2 * k) }
    return b
}

internal fun hbStarBrightness(s: HbStar, tau: Double): Double {
    var b = starBase(s, tau)
    if (b <= 0) return 0.0
    if (s.strobe > 0 && tau > s.strobeFrom) {
        val on = ((tau * s.strobe + s.phase) % 1) < 0.42
        b *= if (on) 1.0 else 0.05
    }
    val u = tau / s.life
    if (s.twinkle > 0 && u > 0.5) {
        val n = hbHash01(floor(tau * 24), s.phase * 97)
        b *= 1 - s.twinkle * hbClamp01((u - 0.5) / 0.2) * (if (n < 0.45) 0.85 else 0.0)
    }
    return b
}

private fun glitterOn(s: HbStar, tau: Double) = s.glitter > 0 && hbHash01(floor(tau * 30), s.phase * 53) < s.glitter * 0.35

internal fun hbStarColor(s: HbStar, tau: Double): Int {
    val u = tau / s.life
    var color = if (s.shift < 1) hbMixColor(s.color, s.endColor, (u - s.shift) / 0.1) else s.color
    if (glitterOn(s, tau)) color = hbMixColor(color, HB_WHITE, 0.75)
    return color
}

internal fun hbStarSize(s: HbStar, tau: Double): Double {
    val u = hbClamp01(tau / s.life)
    var size = s.size * (1 - 0.45 * u)
    if (glitterOn(s, tau)) size *= 1.6
    return size
}

/** 精灵种类：柔光点、实心点、四角星、拖尾段、闪光（加色混合）。 */
internal enum class HbSparkTexture { GLOW, DOT, STAR4, STREAK, FLASH }

internal fun interface HbSparkEmit {
    fun emit(texture: HbSparkTexture, x: Double, y: Double, width: Double, height: Double, rotation: Double, alpha: Double, tint: Int): Boolean
}

private fun emitSegment(emit: HbSparkEmit, ax: Double, ay: Double, bx: Double, by: Double, thickness: Double, alpha: Double, tint: Int): Boolean {
    val dx = ax - bx; val dy = ay - by
    val length = hypot(dx, dy)
    if (length < 0.6 || alpha < 0.01) return true
    val rotation = atan2(dy, dx) + PI / 2
    return emit.emit(HbSparkTexture.STREAK, (ax + bx) / 2, (ay + by) / 2, thickness, length + thickness * 0.5, rotation, alpha, tint)
}

private fun burstActive(p: HbBurstPlan, time: Double) = time >= p.at && time <= p.end

internal fun hbDrawBursts(plans: List<HbBurstPlan>, time: Double, budget: Int, emit: HbSparkEmit): Int {
    var dStars = 0; var dFlashes = 0
    for (p in plans) {
        if (!burstActive(p, time)) continue
        for (f in p.flashes) if (time >= f.at && time < f.at + f.life) dFlashes += 2
        for (s in p.stars) { val tau = time - s.start; if (tau >= 0 && tau <= s.life) dStars += 1 + s.segments }
    }
    if (dStars + dFlashes == 0 || budget <= 0) return 0
    val keep = if (dStars + dFlashes <= budget) 2.0 else max(0.0, (budget - dFlashes) * 0.97) / max(1, dStars)
    var used = 0
    val put = HbSparkEmit { t, x, y, w, h, r, a, tint ->
        if (used >= budget) return@HbSparkEmit false
        if (!emit.emit(t, x, y, w, h, r, a, tint)) return@HbSparkEmit false
        used += 1
        true
    }
    for (p in plans) {
        if (!burstActive(p, time)) continue
        for (f in p.flashes) {
            val age = time - f.at
            if (age < 0 || age >= f.life) continue
            val k = age / f.life
            val alpha = hbClamp01(age / 0.02) * (1 - k).pow(2)
            val halo = f.size * (0.55 + 0.6 * sqrt(k))
            if (!put.emit(HbSparkTexture.FLASH, f.x, f.y, halo, halo, 0.0, alpha * 0.55, f.color)) return used
            val core = f.size * 0.35 * (1 - k * 0.6)
            if (!put.emit(HbSparkTexture.FLASH, f.x, f.y, core, core, 0.0, alpha, HB_WHITE)) return used
        }
    }
    for (p in plans) {
        if (!burstActive(p, time)) continue
        for (s in p.stars) {
            val tau = time - s.start
            if (tau < 0 || tau > s.life || s.rank >= keep) continue
            val base = starBase(s, tau)
            if (base <= 0) continue
            val (hx, hy) = hbStarPosition(s, tau)
            val size = hbStarSize(s, tau)
            if (s.segments > 0) {
                val step = s.trail / s.segments
                var px = hx; var py = hy
                for (j in 1..s.segments) {
                    val back = max(0.0, tau - step * j)
                    val (ox, oy) = hbStarPosition(s, back)
                    val fade = (1 - (j - 1).toDouble() / s.segments).pow(1.3)
                    val thickness = max(1.2, size * 0.75 * (1 - 0.45 * (j.toDouble() / s.segments)))
                    if (!emitSegment(put, px, py, ox, oy, thickness, base * fade * 0.95, s.trailColor)) return used
                    px = ox; py = oy
                    if (back <= 0) break
                }
            }
            val alpha = hbStarBrightness(s, tau)
            if (alpha < 0.01) continue
            val drawn = size * 3.4
            if (!put.emit(if (s.tex == 1) HbSparkTexture.STAR4 else HbSparkTexture.GLOW, hx, hy, drawn, drawn, s.phase, alpha, hbStarColor(s, tau))) return used
        }
    }
    return used
}

// ───────────────────────────── 升空尾迹 ─────────────────────────────

internal const val HB_LAUNCH_EMBER_LIFE = 0.6
private const val LAUNCH_EMBER_STEP = 0.032
private const val LAUNCH_TRAIL_SEGMENTS = 5

internal fun hbLaunchEnd(l: HbLaunchEvent) = l.at + l.duration + HB_LAUNCH_EMBER_LIFE

internal fun hbLaunchHead(l: HbLaunchEvent, p: Double, salt: Double): Pair<Double, Double> {
    val k = hbClamp01(p)
    val rise = 1 - (1 - k).pow(2)
    val span = hypot(l.toX - l.fromX, l.toY - l.fromY)
    val freq = 2.2 + hbHash01(1.0, salt) * 1.6
    val phase = hbHash01(2.0, salt) * HB_TAU
    val sway = sin(k * freq * HB_TAU + phase) * span * 0.018 * (1 - k * 0.7)
    return (hbLerp(l.fromX, l.toX, rise) + sway) to hbLerp(l.fromY, l.toY, rise)
}

internal fun hbDrawLaunch(l: HbLaunchEvent, time: Double, unit: Double, emit: HbSparkEmit): Boolean {
    val age = time - l.at
    if (age < 0 || time > hbLaunchEnd(l)) return true
    val salt = (hbHashString(l.seed) % 100000).toDouble()
    val duration = max(0.05, l.duration)
    val color = l.color
    val ember = hbMixColor(color, HB_GOLD, 0.55)
    val size = unit * 0.009
    val firstJ = max(0, ceil((age - HB_LAUNCH_EMBER_LIFE) / LAUNCH_EMBER_STEP).toInt())
    val lastJ = floor(min(age, duration) / LAUNCH_EMBER_STEP).toInt()
    for (j in firstJ..lastJ) {
        val born = j * LAUNCH_EMBER_STEP
        val tau = age - born
        if (tau < 0 || tau > HB_LAUNCH_EMBER_LIFE) continue
        val (fx, fy) = hbLaunchHead(l, born / duration, salt)
        val vx = (hbHash01((j * 3 + 1).toDouble(), salt) - 0.5) * unit * 0.12
        val vy = (hbHash01((j * 3 + 2).toDouble(), salt) - 0.2) * unit * 0.05
        val drag = 2.4; val fall = unit * 0.09
        val e = exp(-drag * tau); val f = (1 - e) / drag
        val x = fx + vx * f; val y = fy + vy * f + fall * (tau - f)
        val k = tau / HB_LAUNCH_EMBER_LIFE
        val flicker = if (hbHash01(j * 7 + floor(tau * 20), salt) < 0.3) 0.4 else 1.0
        val alpha = (1 - k).pow(1.5) * flicker * 0.85
        val s = size * (0.35 + hbHash01((j * 5).toDouble(), salt) * 0.35) * (1 - k * 0.5)
        if (!emit.emit(HbSparkTexture.GLOW, x, y, s * 2.6, s * 2.6, 0.0, alpha, ember)) return false
    }
    if (age > duration) return true
    val p = age / duration
    val (hx, hy) = hbLaunchHead(l, p, salt)
    var px = hx; var py = hy
    for (j in 1..LAUNCH_TRAIL_SEGMENTS) {
        val (ox, oy) = hbLaunchHead(l, max(0.0, p - (0.07 * j) / duration), salt)
        val fade = 1 - (j - 1).toDouble() / LAUNCH_TRAIL_SEGMENTS
        if (!emitSegment(emit, px, py, ox, oy, max(1.0, size * 0.9 * fade), 0.8 * fade, hbMixColor(color, HB_EMBER, 0.3))) return false
        px = ox; py = oy
    }
    val pulse = 0.85 + 0.15 * sin(age * 40 + salt)
    val dim = 1 - 0.35 * hbClamp01((p - 0.8) / 0.2)
    if (!emit.emit(HbSparkTexture.GLOW, hx, hy, size * 4.2 * pulse, size * 4.2 * pulse, 0.0, 0.9 * dim, color)) return false
    return emit.emit(HbSparkTexture.DOT, hx, hy, size * 0.9, size * 0.9, 0.0, dim, hbMixColor(color, HB_WHITE, 0.8))
}
