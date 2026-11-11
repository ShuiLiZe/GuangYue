package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * R7 花火⑧：MG 装饰件，逐函数对译 Folia hanabi/mg/ornaments.ts + ornamentKit.ts + ornamentShapes.ts + ornamentParticles.ts
 * + ornamentLines.ts（21 种装饰件 × 7 种落点）。
 * Pixi 的节点树对译成一份「节点表」（HbOrnSprite / HbOrnGraphics / HbOrnText，按 Pixi 子节点顺序），构建时一次建好、形状一次录好，
 * 每帧只改 transform / alpha / 显隐——渲染在 HanabiStage.kt 里按表画（共享白色纹理 + 预录的路径）。
 * 只有锯齿线、波线每帧重录（同 Folia 每帧 clear 重画）。金标 hanabi_ornament_golden.txt（docs/port/scripts/hanabi-ornament-golden-gen.js，
 * 假 Pixi 跑真的 createOrnament）逐节点对拍。
 */

// ─── mgMath.ts（缓动都先钳到 0..1，与文字引擎那套不钳的缓动不同） ─────────────────
private const val TAU = PI * 2
private fun c01(v: Double) = if (v <= 0) 0.0 else if (v >= 1) 1.0 else v
private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
private fun fract(v: Double) = v - floor(v)
private fun wrap(v: Double, span: Double) = v - span * floor(v / span)
private fun easeOutCubic(t: Double) = 1 - (1 - c01(t)).pow(3)
private fun easeInCubic(t: Double) = c01(t).pow(3)
private fun easeOutExpo(t: Double): Double { val x = c01(t); return if (x >= 1) 1.0 else 1 - 2.0.pow(-10 * x) }
private fun easeOutBack(t: Double, o: Double = 1.70158): Double { val x = c01(t) - 1; return 1 + x * x * ((o + 1) * x + o) }
private fun easeInBack(t: Double, o: Double = 1.70158): Double { val x = c01(t); return x * x * ((o + 1) * x - o) }
private fun spring(seconds: Double, freq: Double = 2.6, decay: Double = 7.0) =
    if (seconds <= 0) 0.0 else 1 - exp(-decay * seconds) * cos(TAU * freq * seconds)
private fun beatSteps(beats: Double, snap: Double = 0.35): Double { val whole = floor(beats); return whole + easeOutBack(fract(beats) / snap, 2.2) }
private fun hash01(n: Double, salt: Double = 0.0) = hbHash01(n, salt)
private fun jsRound(v: Double) = floor(v + 0.5)

// ─── 节点表（Pixi 对象的最小对译） ────────────────────────────────────────
internal enum class HbOrnTex { DOT, GLOW, STAR4, STREAK, RING, SQUARE, TRIANGLE, PETAL }

/** 一个节点：Pixi 的 position / rotation / alpha / visible / tint。 */
internal abstract class HbOrnNode {
    var visible = true
    var x = 0.0
    var y = 0.0
    var rotation = 0.0
    var alpha = 1.0
    var tint = 0xffffff
    /** 印章的 holder 里的子节点：画时套上 holder 的变换。 */
    var parent: HbOrnHolder? = null
}

/** Sprite（anchor 0.5）：width / height 直接是画出来的尺寸。 */
internal class HbOrnSprite(val tex: HbOrnTex) : HbOrnNode() { var width = 0.0; var height = 0.0 }

/** 一段录好的矢量形状（Graphics / GraphicsContext）：命令 + 每次 stroke / fill 收成一笔。 */
internal class HbOrnShape {
    /** 命令：0 moveTo(x,y) 1 lineTo(x,y) 2 arc(cx,cy,r,a0,a1) 3 circle(cx,cy,r) 4 ellipse(cx,cy,rx,ry) 5 rect(x,y,w,h) 6 closePath。 */
    val ops = ArrayList<DoubleArray>()
    /** 每一笔：ops 的结束下标、是否填充、线宽、端点（0 butt 1 round 2 square）、拐角（0 miter 1 round）。 */
    val passes = ArrayList<DoubleArray>()
    private var from = 0
    /** 版本号：渲染端据此决定要不要重建 Path。 */
    var version = 0
        private set
    /** 金标用：moveTo / lineTo 点数与坐标和（同假 Pixi）。 */
    var points = 0
        private set
    var sumX = 0.0
        private set
    var sumY = 0.0
        private set

    private fun op(vararg v: Double): HbOrnShape { ops += v; return this }
    fun moveTo(x: Double, y: Double): HbOrnShape { points++; sumX += x; sumY += y; return op(0.0, x, y) }
    fun lineTo(x: Double, y: Double): HbOrnShape { points++; sumX += x; sumY += y; return op(1.0, x, y) }
    fun arc(cx: Double, cy: Double, r: Double, a0: Double, a1: Double) = op(2.0, cx, cy, r, a0, a1)
    fun circle(cx: Double, cy: Double, r: Double) = op(3.0, cx, cy, r)
    fun ellipse(cx: Double, cy: Double, rx: Double, ry: Double) = op(4.0, cx, cy, rx, ry)
    fun rect(x: Double, y: Double, w: Double, h: Double) = op(5.0, x, y, w, h)
    fun closePath() = op(6.0)
    fun stroke(width: Double, cap: Int = 0, join: Int = 0): HbOrnShape { passes += doubleArrayOf(ops.size.toDouble(), 0.0, width, cap.toDouble(), join.toDouble(), from.toDouble()); from = ops.size; version++; return this }
    fun fill(): HbOrnShape { passes += doubleArrayOf(ops.size.toDouble(), 1.0, 0.0, 0.0, 0.0, from.toDouble()); from = ops.size; version++; return this }
    fun clear() { ops.clear(); passes.clear(); from = 0; points = 0; sumX = 0.0; sumY = 0.0; version++ }
}

/** Graphics：一段形状 + 统一缩放。shared = 共用 GraphicsContext（＋号、箭头）。 */
internal class HbOrnGraphics(val shape: HbOrnShape, val shared: Boolean) : HbOrnNode() { var scale = 1.0 }

/** 印章的字（Pixi Text，anchor 0.5 / 0.54）。 */
internal class HbOrnText(val text: String, val fontSize: Double) : HbOrnNode()

/** 印章的 holder（Container）：tint 传给子节点。 */
internal class HbOrnHolder : HbOrnNode() { var scale = 1.0 }

// ─── 装饰件的构建参数（types.ts OrnamentContext） ─────────────────────────
internal class HbOrnamentContext(
    val width: Double,
    val height: Double,
    val seed: String,
    val palette: HbPalette,
    val bounds: HbBounds,
    val anchorsAt: (Double) -> List<HbGlyphAnchor>,
    val enterAt: Double,
    val exitAt: Double,
    val exitDuration: Double,
    val amount: Double,
)

private class Site(val x: Double, val y: Double, val w: Double, val h: Double, val angle: Double, val glyph: Int, val start: Double, val hollow: Boolean)
private class GlyphInfo(val index: Int, val char: String, val x: Double, val y: Double, val size: Double, val sungAt: Double, val sungEnd: Double, val landedAt: Double)
private class SitePos { var x = 0.0; var y = 0.0; var alpha = 1.0; var size = 0.0 }

/** ornamentKit.ts createKit。 */
private class Kit(val spec: HbOrnamentSpec, val ctx: HbOrnamentContext) {
    /** 节点表（Pixi 子节点顺序，印章 holder 的子节点紧跟在 holder 后面）。 */
    val nodes = ArrayList<HbOrnNode>()
    private val rand = lumiereRng("${ctx.seed}:ornament:${spec.kind}:${spec.placement}")
    fun rng() = rand.next()
    val start = ctx.enterAt + spec.delay
    val end = ctx.exitAt + max(0.0, ctx.exitDuration)
    val cx = ctx.bounds.x + ctx.bounds.width / 2
    val cy = ctx.bounds.y + ctx.bounds.height / 2
    val hw = max(1.0, ctx.bounds.width / 2)
    val hh = max(1.0, ctx.bounds.height / 2)
    val glyphs = ArrayList<GlyphInfo>()
    val base: Double
    val size: Double
    var vertical = false
    private val sung: DoubleArray
    private val factor = max(0.0, ctx.amount) * (0.35 + 0.9 * c01(spec.density))
    private val settled: List<HbGlyphAnchor>

    init {
        val initial = ctx.anchorsAt(ctx.enterAt)
        val landed = initial.fold(ctx.enterAt) { m, a -> max(m, a.landedAt) }
        val settle = min(ctx.exitAt - 1e-3, landed + 0.05)
        settled = if (settle > ctx.enterAt) ctx.anchorsAt(settle) else initial
        settled.forEachIndexed { index, a ->
            if (!a.blank) glyphs += GlyphInfo(index, a.char, a.x, a.y, a.size, a.sungAt, a.sungEnd, a.landedAt)
        }
        val sizes = glyphs.map { it.size }.sorted()
        base = if (sizes.isNotEmpty()) sizes[sizes.size / 2] else max(max(12.0, min(hw, hh) * 0.8), min(ctx.width, ctx.height) * 0.06)
        size = base * max(0.1, spec.scale)
        if (glyphs.size > 1) {
            val xs = glyphs.map { it.x }; val ys = glyphs.map { it.y }
            vertical = ys.max() - ys.min() > (xs.max() - xs.min()) * 1.3
        }
        sung = glyphs.map { it.sungAt }.sorted().toDoubleArray()
    }

    fun count(baseCount: Double, minN: Int = 1): Int = if (factor <= 0) 0 else max(minN, jsRound(baseCount * factor).toInt())

    private fun pick(list: List<Int>, i: Int, fallback: Int) = if (list.isNotEmpty()) list[((i % list.size) + list.size) % list.size] else fallback
    fun color(i: Int): Int {
        val p = ctx.palette
        return when (spec.color) {
            "spark" -> pick(p.sparks, i, p.accent)
            "block" -> pick(p.blocks, i, p.accent)
            "paper" -> p.paper
            "glow" -> p.glow
            else -> p.accent
        }
    }

    private val pad = base * 0.35
    private fun make(x: Double, y: Double, w: Double, h: Double, angle: Double, glyph: Int = -1, st: Double = start, hollow: Boolean = false) =
        Site(x, y, w, h, angle, glyph, st, hollow)

    fun sites(mode: String, maxGlyphSites: Int): List<Site> = when (spec.placement) {
        "behind" -> listOf(make(cx, cy, hw, hh, -PI / 2))
        "around" -> listOf(make(cx, cy, hw + pad, hh + pad, -PI / 2, hollow = mode == "scatter"))
        "corners" -> listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).mapIndexed { k, (sx, sy) ->
            make(cx + sx * (hw + pad * 0.6), cy + sy * (hh + pad * 0.6), base * 0.6, base * 0.6, atan2(sy.toDouble(), sx.toDouble()), st = start + k * 0.07)
        }
        "line-start" -> listOf(if (vertical) make(cx, cy - hh - pad, base * 0.7, base * 0.7, -PI / 2) else make(cx - hw - pad, cy, base * 0.7, base * 0.7, PI))
        "line-end" -> listOf(if (vertical) make(cx, cy + hh + pad, base * 0.7, base * 0.7, PI / 2) else make(cx + hw + pad, cy, base * 0.7, base * 0.7, 0.0))
        "screen" -> listOf(make(0.0, 0.0, ctx.width * 0.55, ctx.height * 0.55, -PI / 2))
        else -> if (glyphs.isEmpty()) listOf(make(cx, cy, hw, hh, -PI / 2)) else {
            val limit = max(1, min(glyphs.size, maxGlyphSites))
            List(limit) { i -> glyphs[floor(((i + 0.5) * glyphs.size) / limit).toInt()] }.map { g ->
                make(g.x, g.y, g.size * 0.55, g.size * 0.55, -PI / 2, glyph = g.index, st = max(start, g.sungAt))
            }
        }
    }

    private var cachedTime = Double.NaN
    private var cached: List<HbGlyphAnchor> = settled
    fun anchors(time: Double): List<HbGlyphAnchor> {
        if (time != cachedTime) { cachedTime = time; cached = ctx.anchorsAt(time) }
        return cached
    }

    fun pos(site: Site, list: List<HbGlyphAnchor>, out: SitePos): SitePos {
        val a = if (site.glyph >= 0) list.getOrNull(site.glyph) else null
        if (a != null) { out.x = a.x; out.y = a.y; out.alpha = a.alpha; out.size = a.size }
        else { out.x = site.x; out.y = site.y; out.alpha = 1.0; out.size = max(site.w, site.h) }
        return out
    }

    private val exitSpan = max(0.05, ctx.exitDuration)
    fun exit(time: Double, stagger: Double = 0.0) = c01((time - ctx.exitAt - stagger) / max(0.05, exitSpan - stagger))

    fun scatter(site: Site): DoubleArray {
        if (!site.hollow) { val x = (rng() * 2 - 1) * site.w; val y = (rng() * 2 - 1) * site.h; return doubleArrayOf(x, y) }
        val ow = site.w + base * 1.3
        val oh = site.h + base * 1.0
        for (tries in 0 until 12) {
            val x = (rng() * 2 - 1) * ow
            val y = (rng() * 2 - 1) * oh
            if (abs(x) > site.w || abs(y) > site.h) return doubleArrayOf(x, y)
        }
        val a = rng() * PI * 2
        return doubleArrayOf(cos(a) * ow, sin(a) * oh)
    }

    fun lastSung(time: Double): Double {
        var result = Double.NEGATIVE_INFINITY
        for (at in sung) { if (at > time) break; result = at }
        return result
    }

    fun sprite(tex: HbOrnTex, parent: HbOrnHolder? = null) = HbOrnSprite(tex).also { it.parent = parent; nodes += it }
    fun graphics(shape: HbOrnShape? = null, parent: HbOrnHolder? = null) =
        HbOrnGraphics(shape ?: HbOrnShape(), shape != null).also { it.parent = parent; nodes += it }
    fun context() = HbOrnShape()
}

private typealias Update = (Double) -> Unit

/** 一个装饰件：节点表 + 每帧更新（ornaments.ts createOrnament）。 */
internal class HbOrnament(val spec: HbOrnamentSpec, ctx: HbOrnamentContext) {
    private val kit = Kit(spec, ctx)
    private val draw: Update? = if (ctx.amount <= 0) null else builderOf(spec.kind)(kit)
    val nodes: List<HbOrnNode> get() = kit.nodes
    /** Pixi 里 view.visible。 */
    var visible = true
        private set
    private val from: Double = min(kit.start, kit.glyphs.fold(Double.POSITIVE_INFINITY) { m, g -> min(m, g.sungAt) }) - 0.05

    fun update(time: Double) {
        visible = draw != null && time >= from && time <= kit.end
        if (visible) draw!!(time)
    }
}

private fun builderOf(kind: String): (Kit) -> Update = when (kind) {
    "ring" -> ::ringOrnament
    "spokes" -> ::spokesOrnament
    "sparkle" -> ::sparkleOrnament
    "orbit" -> ::orbitOrnament
    "chevrons" -> ::chevronsOrnament
    "brackets" -> ::bracketsOrnament
    "cross" -> ::crossOrnament
    "zigzag" -> ::zigzagOrnament
    "wave" -> ::waveOrnament
    "halftone" -> ::halftoneOrnament
    "confetti" -> ::confettiOrnament
    "shards" -> ::shardsOrnament
    "dash-circle" -> ::dashCircleOrnament
    "squares" -> ::squaresOrnament
    "streaks" -> ::streaksOrnament
    "petals" -> ::petalsOrnament
    "stamp" -> ::stampOrnament
    "rays" -> ::raysOrnament
    "underline" -> ::underlineOrnament
    "dots-line" -> ::dotsLineOrnament
    else -> ::glintsOrnament
}

private const val CAP_BUTT = 0
private const val CAP_ROUND = 1
private const val CAP_SQUARE = 2
private const val JOIN_MITER = 0
private const val JOIN_ROUND = 1

// ═══ ornamentShapes.ts ═══════════════════════════════════════════════════

private fun siteRadius(placement: String, site: Site, fallback: Double) = when (placement) {
    "behind" -> hypot(site.w, site.h) * 0.8
    "around" -> hypot(site.w, site.h) * 0.95
    "screen" -> min(site.w, site.h) * 0.75
    else -> max(site.w, fallback) * 1.3
}

private fun isBig(p: String) = p == "behind" || p == "around" || p == "screen"

private fun ringOrnament(kit: Kit): Update {
    val oneShot = kit.spec.placement == "glyphs"
    val layers = if (oneShot) 1 else min(3, kit.count(2.4, 1))
    val line = max(1.5, kit.size * 0.055)
    class U(val site: Site, val rings: List<HbOrnGraphics>, val pulse: HbOrnGraphics?)
    val units = kit.sites("single", 8).mapIndexed { si, site ->
        val radius = siteRadius(kit.spec.placement, site, kit.size * 0.5) * (if (oneShot) 1.3 else 1.0)
        val rings = List(layers) { j ->
            kit.graphics().also { g -> g.shape.circle(0.0, 0.0, radius * (1 + 0.14 * j)).stroke(line * (if (j == 0) 1.6 else 0.8)); g.tint = kit.color(si + j) }
        }
        var pulse: HbOrnGraphics? = null
        if (!oneShot) pulse = kit.graphics().also { g -> g.shape.circle(0.0, 0.0, radius).stroke(line * 0.6); g.tint = kit.color(si + 1) }
        U(site, rings, pulse)
    }
    val p = SitePos()
    return { time ->
        val anchors = kit.anchors(time)
        for (u in units) {
            kit.pos(u.site, anchors, p)
            val e = time - u.site.start
            u.rings.forEachIndexed { j, g ->
                val local = e - j * 0.09
                val x = kit.exit(time, j * 0.05)
                if (local < 0 || x >= 1) { g.visible = false; return@forEachIndexed }
                var scale: Double
                var alpha: Double
                if (oneShot) {
                    val k = c01(local / 0.8)
                    scale = 0.3 + 1.1 * easeOutExpo(k)
                    alpha = (1 - k).pow(1.5)
                } else {
                    scale = lerp(0.2, 1.0, easeOutBack(local / 0.55, 2.4)) * (1 + 0.025 * sin(TAU * (time * 0.5 + j / 3.0)))
                    alpha = c01(local / 0.08)
                    scale *= 1 - easeInBack(x, 2.0)
                    alpha *= 1 - x
                }
                g.visible = alpha > 0.005 && scale > 0.001
                g.x = p.x; g.y = p.y
                g.scale = max(0.001, scale)
                g.alpha = alpha * p.alpha
            }
            u.pulse?.let { pulse ->
                val q = fract((e - 0.6) / 1.8)
                val x = kit.exit(time)
                pulse.visible = e > 0.6 && x < 1
                pulse.x = p.x; pulse.y = p.y
                pulse.scale = 1 + 0.55 * easeOutCubic(q)
                pulse.alpha = 0.55 * (1 - q).pow(2) * (1 - x) * p.alpha
            }
        }
    }
}

private fun spokesOrnament(kit: Kit): Update {
    val oneShot = kit.spec.placement == "glyphs"
    val big = isBig(kit.spec.placement)
    val pad = kit.size * 0.45
    class S(val sprite: HbOrnSprite, val cos: Double, val sin: Double, val inner: Double, val length: Double, val thick: Double, val delay: Double, val salt: Double)
    class U(val site: Site, val spokes: List<S>)
    val units = kit.sites("single", 6).mapIndexed { si, site ->
        val n = if (oneShot) 8 else kit.count(if (big) 28.0 else 10.0, 6)
        val spokes = List(n) { i ->
            val angle = (TAU * i) / n + (kit.rng() - 0.5) * (TAU / n) * 0.7
            val c = cos(angle); val s = sin(angle)
            val inner = if (big) 1 / sqrt((c / (site.w + pad)).pow(2) + (s / (site.h + pad)).pow(2)) else site.w * 1.1
            val sprite = kit.sprite(HbOrnTex.STREAK)
            sprite.rotation = angle + PI / 2
            sprite.tint = kit.color(si + i)
            val length = kit.size * (if (oneShot) 0.5 + kit.rng() * 0.5 else 0.7 + kit.rng() * 1.7)
            val thick = max(1.2, kit.size * (0.035 + kit.rng() * 0.05))
            S(sprite, c, s, inner, length, thick, (i % 6) * 0.015, (si * 97 + i).toDouble())
        }
        U(site, spokes)
    }
    val p = SitePos()
    return { time ->
        val anchors = kit.anchors(time)
        val beat = floor(time * 12)
        val x = kit.exit(time)
        for (u in units) {
            kit.pos(u.site, anchors, p)
            val e = time - u.site.start
            for (s in u.spokes) {
                val local = e - s.delay
                if (local < 0 || x >= 1) { s.sprite.visible = false; continue }
                val grow = easeOutExpo(local / 0.25)
                val jitter = 0.72 + 0.45 * hash01(beat, s.salt)
                var length = s.length * grow * jitter * (1 - x)
                var push = kit.size * 2.5 * easeInCubic(x)
                var alpha = (0.55 + 0.45 * hash01(beat + 7, s.salt)) * p.alpha
                if (oneShot) {
                    val k = c01((local - 0.3) / 0.35)
                    push += kit.size * 0.8 * easeOutCubic(k)
                    length *= 1 - k
                    alpha *= 1 - k
                }
                val r = s.inner + push + length / 2
                s.sprite.visible = length > 0.5 && alpha > 0.01
                s.sprite.x = p.x + s.cos * r; s.sprite.y = p.y + s.sin * r
                s.sprite.width = s.thick * (1 - x * 0.5)
                s.sprite.height = length
                s.sprite.alpha = alpha
            }
        }
    }
}

private fun orbitOrnament(kit: Kit): Update {
    val big = isBig(kit.spec.placement)
    val tilt = -0.18
    class Path(val g: HbOrnGraphics, val delay: Double)
    class Dot(val sprite: HbOrnSprite, val theta: Double, val omega: Double, val rx: Double, val ry: Double, val size: Double, val delay: Double)
    class U(val site: Site, val dots: List<Dot>, val paths: List<Path>)
    class Ring(val omega: Double, val reach: Double, val trains: Int)
    val units = kit.sites("single", 4).mapIndexed { si, site ->
        val rx = if (big) site.w + kit.size * 0.5 else site.w * 1.5
        val ry = if (big) site.h + kit.size * 0.6 else site.h * 0.8
        val trains = if (big) max(2, min(6, kit.count(4.0, 2))) else 2
        val cars = if (big) 5 else 3
        val rings = listOf(Ring(0.55, 1.0, trains), Ring(-0.85, 1.2, max(1, trains - 1)))
        val paths = rings.mapIndexed { r, ring ->
            val g = kit.graphics()
            g.shape.ellipse(0.0, 0.0, rx * ring.reach, ry * ring.reach).stroke(max(1.0, kit.size * 0.02))
            g.rotation = tilt
            g.tint = kit.color(si + r)
            Path(g, r * 0.12)
        }
        val dots = rings.flatMapIndexed { r, ring ->
            List(ring.trains * cars) { index ->
                val train = index / cars
                val car = index % cars
                val leader = car == 0
                val sprite = kit.sprite(if (leader) HbOrnTex.STAR4 else HbOrnTex.DOT)
                sprite.tint = kit.color(si + r + train)
                Dot(sprite, (TAU * train) / ring.trains + r * 0.5 - sign(ring.omega) * car * 0.11, ring.omega, rx * ring.reach, ry * ring.reach,
                    kit.size * (if (leader) 0.34 else 0.14 * (1 - car.toDouble() / (cars + 1))), train * 0.06 + car * 0.03 + r * 0.12)
            }
        }
        U(site, dots, paths)
    }
    val p = SitePos()
    val cosT = cos(tilt); val sinT = sin(tilt)
    return { time ->
        val anchors = kit.anchors(time)
        for (u in units) {
            kit.pos(u.site, anchors, p)
            val e = time - u.site.start
            for (path in u.paths) {
                val local = e - path.delay
                val x = kit.exit(time)
                path.g.visible = local >= 0 && x < 1
                if (!path.g.visible) continue
                path.g.x = p.x; path.g.y = p.y
                path.g.scale = max(0.001, easeOutBack(local / 0.5, 1.8) * (1 + 1.2 * easeInCubic(x)))
                path.g.alpha = 0.3 * c01(local / 0.1) * (1 - x) * p.alpha
            }
            u.dots.forEachIndexed { i, d ->
                val local = e - d.delay
                val x = kit.exit(time, (i % 8) * 0.02)
                if (local < 0 || x >= 1) { d.sprite.visible = false; return@forEachIndexed }
                val reach = spring(local, 1.6, 5.5) * (1 + 1.2 * easeInCubic(x))
                val theta = d.theta + d.omega * time - sign(d.omega) * 2.2 * (1 - easeOutCubic(local / 0.9))
                val ox = cos(theta) * d.rx * reach
                val oy = sin(theta) * d.ry * reach
                val near = 0.5 + 0.5 * sin(theta)
                d.sprite.visible = true
                d.sprite.x = p.x + ox * cosT - oy * sinT; d.sprite.y = p.y + ox * sinT + oy * cosT
                val s = d.size * (0.65 + 0.35 * near)
                d.sprite.width = s; d.sprite.height = s
                d.sprite.rotation = theta
                d.sprite.alpha = c01(local / 0.05) * (0.45 + 0.55 * near) * (1 - x) * p.alpha
            }
        }
    }
}

private val CORNERS = listOf(doubleArrayOf(-1.0, -1.0, 0.0), doubleArrayOf(1.0, -1.0, PI / 2), doubleArrayOf(1.0, 1.0, PI), doubleArrayOf(-1.0, 1.0, PI * 1.5))

private fun bracketsOrnament(kit: Kit): Update {
    val placement = kit.spec.placement
    val thick = max(2.0, kit.size * 0.08)
    val pad = kit.size * 0.4
    fun make(a: Double, b: Double, rotation: Double, color: Int): HbOrnGraphics {
        val g = kit.graphics()
        g.shape.moveTo(a, 0.0).lineTo(0.0, 0.0).lineTo(0.0, b).stroke(thick, CAP_SQUARE, JOIN_MITER)
        g.rotation = rotation
        g.tint = color
        return g
    }
    if (placement == "glyphs" && kit.glyphs.isNotEmpty()) {
        val arm = kit.size * 0.28
        class C(val g: HbOrnGraphics, val sx: Double, val sy: Double)
        val corners = CORNERS.mapIndexed { k, c -> C(make(arm, arm, c[2], kit.color(k)), c[0], c[1]) }
        val order = kit.glyphs.sortedBy { it.sungAt }
        return { time ->
            val x = kit.exit(time)
            var current = -1
            for (i in order.indices) if (order[i].sungAt <= time) current = i
            val visible = current >= 0 && time >= kit.start && x < 1
            for (c in corners) c.g.visible = visible
            if (visible) {
                val anchors = kit.anchors(time)
                val cur = order[current]
                val prev = order[max(0, current - 1)]
                val a = anchors.getOrNull(cur.index)
                val b = anchors.getOrNull(prev.index)
                val k = if (current == 0) 1.0 else easeOutBack((time - cur.sungAt) / 0.2, 1.8)
                val px = lerp(b?.x ?: prev.x, a?.x ?: cur.x, k)
                val py = lerp(b?.y ?: prev.y, a?.y ?: cur.y, k)
                val half = lerp(b?.size ?: prev.size, a?.size ?: cur.size, k) * 0.62
                val snap = 1 + 0.35 * exp(-(time - cur.sungAt) / 0.08)
                for (c in corners) {
                    c.g.x = px + c.sx * half * snap * (1 + x); c.g.y = py + c.sy * half * snap * (1 + x)
                    c.g.alpha = (1 - x) * (a?.alpha ?: 1.0)
                }
            }
        }
    }
    var rw = kit.hw + pad
    var rh = kit.hh + pad * 0.7
    var cx = kit.cx
    var cy = kit.cy
    if (placement == "screen") { rw = kit.ctx.width * 0.42; rh = kit.ctx.height * 0.38; cx = 0.0; cy = 0.0 }
    var chosen = CORNERS
    var armA = min(rw * 0.35, kit.size * 0.9)
    var armB = armA
    if (placement == "behind" || placement == "around" || placement == "line-start" || placement == "line-end") {
        chosen = when (placement) { "line-start" -> listOf(CORNERS[0]); "line-end" -> listOf(CORNERS[2]); else -> listOf(CORNERS[0], CORNERS[2]) }
        armA = if (kit.vertical) rw * 1.2 else kit.size * 0.55
        armB = if (kit.vertical) kit.size * 0.55 else rh * 1.2
    }
    class P(val g: HbOrnGraphics, val sx: Double, val sy: Double, val delay: Double)
    val pieces = chosen.mapIndexed { k, c ->
        val swap = k % 2 == 1 && chosen.size == 4
        P(make(if (swap) armB else armA, if (swap) armA else armB, c[2], kit.color(k)), c[0], c[1], k * 0.06)
    }
    return { time ->
        val beat = exp(-(time - kit.lastSung(time)) / 0.12)
        pieces.forEachIndexed { k, piece ->
            val local = time - kit.start - piece.delay
            val x = kit.exit(time, k * 0.04)
            if (local < 0 || x >= 1) { piece.g.visible = false; return@forEachIndexed }
            var off = (1 - easeOutBack(local / 0.45, 2.6)) * kit.size * 1.4
            off += kit.size * 0.06 * sin(TAU * time * 0.7) - kit.size * 0.15 * beat
            off += easeInCubic(x) * kit.size * 2
            val flick = if (local < 0.12) (if (floor(local * 40) % 2 == 0.0) 1.0 else 0.25) else 1.0
            piece.g.visible = true
            piece.g.x = cx + piece.sx * (rw + off); piece.g.y = cy + piece.sy * (rh + off)
            piece.g.alpha = flick * (1 - x)
        }
    }
}

private fun dashCircleOrnament(kit: Kit): Update {
    val oneShot = kit.spec.placement == "glyphs"
    val line = max(1.5, kit.size * 0.05)
    class U(val site: Site, val outer: HbOrnGraphics, val inner: HbOrnGraphics)
    val units = kit.sites("single", 6).mapIndexed { si, site ->
        val r1 = if (oneShot) site.w * 1.35 else siteRadius(kit.spec.placement, site, kit.size * 0.6) * 1.05
        val r2 = r1 * 0.84
        val outer = kit.graphics()
        val dashes = if (r1 > kit.size * 2) 36 else 18
        for (k in 0 until dashes) {
            val a0 = (k * TAU) / dashes
            val a1 = a0 + (TAU / dashes) * 0.55
            outer.shape.moveTo(cos(a0) * r1, sin(a0) * r1).arc(0.0, 0.0, r1, a0, a1)
        }
        outer.shape.stroke(line, CAP_ROUND)
        outer.tint = kit.color(si)
        val inner = kit.graphics()
        val dots = dashes * 2
        for (k in 0 until dots) { val a = (k * TAU) / dots; inner.shape.circle(cos(a) * r2, sin(a) * r2, line * 0.7) }
        inner.shape.fill()
        inner.tint = kit.color(si + 1)
        U(site, outer, inner)
    }
    val p = SitePos()
    return { time ->
        val anchors = kit.anchors(time)
        val x = kit.exit(time)
        for (u in units) {
            kit.pos(u.site, anchors, p)
            val local = time - u.site.start
            val fadeOut = if (oneShot) c01((local - 1) / 0.4) else 0.0
            val visible = local >= 0 && x < 1 && fadeOut < 1
            u.outer.visible = visible; u.inner.visible = visible
            if (!visible) continue
            val scale = lerp(0.55, 1.0, easeOutBack(local / 0.5, 2.0)) * (1 + 0.3 * easeInCubic(x))
            val spinIn = (1 - easeOutCubic(local / 0.9)) * PI * 1.2
            val alpha = c01(local / 0.1) * (1 - x) * (1 - fadeOut) * p.alpha
            u.outer.x = p.x; u.outer.y = p.y; u.outer.scale = scale
            u.outer.rotation = 0.35 * time + spinIn + x * x * 3
            u.outer.alpha = alpha
            u.inner.x = p.x; u.inner.y = p.y; u.inner.scale = scale
            u.inner.rotation = -beatSteps((time - u.site.start) / 0.5) * (PI / 18) - spinIn
            u.inner.alpha = alpha * 0.85
        }
    }
}

private fun squaresOrnament(kit: Kit): Update {
    val big = isBig(kit.spec.placement)
    val oneShot = kit.spec.placement == "glyphs"
    val layers = min(4, max(2, kit.count(3.2, 2)))
    val line = max(1.5, kit.size * 0.05)
    val pad = kit.size * 0.35
    class Sq(val g: HbOrnGraphics, val dir: Int, val delay: Double, val offset: Double)
    class U(val site: Site, val squares: List<Sq>)
    val units = kit.sites("single", 5).mapIndexed { si, site ->
        U(site, List(layers) { j ->
            val g = kit.graphics()
            val w = if (big) site.w + pad * (j + 1) else site.w * (1.1 + 0.3 * j)
            val h = if (big) site.h + pad * (j + 1) else w
            g.shape.rect(-w, -h, w * 2, h * 2).stroke(line * (if (j == 0) 1.5 else 1.0), CAP_BUTT, JOIN_MITER)
            g.tint = kit.color(si + j)
            Sq(g, if (j % 2 == 0) 1 else -1, j * 0.07, j * 0.25)
        })
    }
    val p = SitePos()
    return { time ->
        val anchors = kit.anchors(time)
        for (u in units) {
            kit.pos(u.site, anchors, p)
            u.squares.forEachIndexed { j, s ->
                val local = time - u.site.start - s.delay
                val x = max(kit.exit(time, (layers - 1 - j) * 0.05), if (oneShot) c01((local - 1.1) / 0.4) else 0.0)
                if (local < 0 || x >= 1) { s.g.visible = false; return@forEachIndexed }
                val beats = local / 1.0 + s.offset
                val rotation = if (big) {
                    val phase = if (floor(beats) % 2 == 0.0) 1.0 else -1.0
                    s.dir * 0.09 * lerp(-phase, phase, easeOutBack(fract(beats) / 0.35, 2.0))
                } else s.dir * beatSteps(beats) * (PI / 4)
                val scale = easeOutBack(local / 0.4, 2.2) * (1 - easeInBack(x, 2.0))
                s.g.visible = abs(scale) > 0.001
                s.g.x = p.x; s.g.y = p.y
                s.g.rotation = rotation
                s.g.scale = max(0.001, scale)
                s.g.alpha = c01(local / 0.06) * (1 - x) * p.alpha
            }
        }
    }
}

private fun raysOrnament(kit: Kit): Update {
    val oneShot = kit.spec.placement == "glyphs"
    val faint = kit.spec.placement == "behind" || kit.spec.placement == "screen"
    class U(val site: Site, val g: HbOrnGraphics, val dir: Int)
    val units = kit.sites("single", 3).mapIndexed { si, site ->
        val radius = when (kit.spec.placement) {
            "screen" -> hypot(kit.ctx.width, kit.ctx.height) * 0.6
            "behind", "around" -> hypot(site.w, site.h) * 1.7
            else -> site.w * 2.4
        }
        val wedges = if (oneShot) 8 else max(6, kit.count(12.0, 6))
        val g = kit.graphics()
        for (k in 0 until wedges) {
            val a0 = (k * TAU) / wedges
            val a1 = a0 + (TAU / wedges) * 0.5
            g.shape.moveTo(0.0, 0.0).lineTo(cos(a0) * radius, sin(a0) * radius).lineTo(cos(a1) * radius, sin(a1) * radius).closePath()
        }
        g.shape.fill()
        g.tint = kit.color(si)
        U(site, g, if (si % 2 == 0) 1 else -1)
    }
    val p = SitePos()
    val baseAlpha = if (faint) 0.26 else 0.45
    return { time ->
        val anchors = kit.anchors(time)
        val x = kit.exit(time)
        val tick = exp(-(time - kit.lastSung(time)) / 0.15)
        for (u in units) {
            kit.pos(u.site, anchors, p)
            val local = time - u.site.start
            if (local < 0 || x >= 1 || (oneShot && local > 1.2)) { u.g.visible = false; continue }
            var scale = easeOutBack(local / 0.6, 1.6) * (1 + 0.03 * sin(TAU * time * 0.8) + 0.04 * tick) * (1 + 0.25 * x)
            var alpha = baseAlpha * c01(local / 0.1) * (1 - x)
            if (oneShot) { val k = c01((local - 0.5) / 0.7); alpha *= 1 - k; scale *= 1 + 0.3 * k }
            u.g.visible = true
            u.g.x = p.x; u.g.y = p.y
            u.g.scale = max(0.001, scale)
            u.g.rotation = u.dir * (time * 0.12 - 0.8 * (1 - easeOutCubic(local / 0.8)) + x * x * 1.5)
            u.g.alpha = alpha * p.alpha
        }
    }
}

private val STAMP_CHARS = listOf("花", "火", "夏", "夜", "空", "光")

private fun stampOrnament(kit: Kit): Update {
    val placement = kit.spec.placement
    val pad = kit.size * 0.6
    var radius = kit.size * 0.72
    val x0: Double
    val y0: Double
    var faint = false
    val corner = floor(kit.rng() * 4).toInt()
    val sx = if (corner == 0 || corner == 3) -1 else 1
    val sy = if (corner < 2) -1 else 1
    when (placement) {
        "behind" -> { radius = min(kit.hw, kit.hh) * 1.1 + kit.size * 0.3; x0 = kit.cx; y0 = kit.cy; faint = true }
        "screen" -> { x0 = sx * kit.ctx.width * 0.36; y0 = sy * kit.ctx.height * 0.32; radius = min(kit.ctx.width, kit.ctx.height) * 0.08 * kit.spec.scale }
        "line-start" -> {
            x0 = if (kit.vertical) kit.cx else kit.cx - kit.hw - pad - radius
            y0 = if (kit.vertical) kit.cy - kit.hh - pad - radius else kit.cy
        }
        "line-end", "glyphs" -> {
            x0 = if (kit.vertical) kit.cx else kit.cx + kit.hw + pad * 0.6 + radius
            y0 = if (kit.vertical) kit.cy + kit.hh + pad * 0.6 + radius else kit.cy + kit.hh * 0.4
        }
        else -> { x0 = kit.cx + sx * (kit.hw + radius * 0.6); y0 = kit.cy + sy * (kit.hh + radius * 0.5) }
    }
    val holder = HbOrnHolder()
    kit.nodes += holder
    holder.x = x0; holder.y = y0
    val frame = kit.graphics(parent = holder)
    frame.shape.circle(0.0, 0.0, radius).stroke(radius * 0.1)
    frame.shape.circle(0.0, 0.0, radius * 0.8).stroke(radius * 0.035)
    val char = STAMP_CHARS[floor(kit.rng() * STAMP_CHARS.size).toInt() % STAMP_CHARS.size]
    val text = HbOrnText(char, max(8.0, radius * 1.02)).also { it.parent = holder; kit.nodes += it }
    holder.tint = kit.color(0)
    val shock = kit.sprite(HbOrnTex.RING)
    shock.tint = kit.color(1)
    val tilt = (kit.rng() - 0.5) * 0.3
    val slam = 0.18
    return { time ->
        val local = time - kit.start
        val x = kit.exit(time)
        if (local < 0 || x >= 1) { holder.visible = false; shock.visible = false }
        else {
            holder.visible = true
            var scale: Double
            val rotation: Double
            if (local < slam) {
                val k = easeInCubic(local / slam)
                scale = lerp(2.4, 1.0, k)
                rotation = lerp(-0.5, tilt, k)
            } else {
                val after = local - slam
                scale = 1 + 0.14 * exp(-after * 9) * cos(after * 38)
                rotation = tilt + 0.02 * sin(time * 0.6)
            }
            scale *= 1 - easeInBack(x, 2.4)
            holder.scale = max(0.001, scale)
            holder.rotation = rotation
            holder.alpha = c01(local / 0.06) * (if (faint) 0.3 else 1.0) * (if (x > 0.6) (if (floor(x * 20) % 2 == 0.0) 1.0 else 0.3) else 1.0)
            val impact = local - slam
            val k = c01(impact / 0.45)
            shock.visible = impact >= 0 && k < 1
            if (shock.visible) {
                val size = radius * 2 * (1 + 0.9 * easeOutCubic(k))
                shock.x = x0; shock.y = y0
                shock.width = size; shock.height = size
                shock.alpha = (1 - k).pow(2) * (if (faint) 0.3 else 0.8)
            }
        }
    }
}

private fun halftoneOrnament(kit: Kit): Update {
    val placement = kit.spec.placement
    val perSite = when (placement) { "glyphs" -> 25; "corners" -> 36; else -> kit.count(170.0, 40) }
    class D(val sprite: HbOrnSprite, val ox: Double, val oy: Double, val proj: Double)
    class U(val site: Site, val dots: List<D>, val spacing: Double)
    val units = kit.sites("single", 6).mapIndexed { si, site ->
        var w = site.w
        var h = site.h
        when (placement) {
            "around" -> { w = w * 1.2 + kit.size * 0.4; h = h * 1.35 + kit.size * 0.6 }
            "behind" -> { w = w * 1.15 + kit.size * 0.3; h = h * 1.6 + kit.size * 0.4 }
            "glyphs" -> { w *= 1.4; h *= 1.4 }
        }
        val spacing = max(kit.size * 0.18, sqrt((4 * w * h) / min(260, perSite)))
        val cols = max(2, jsRound((2 * w) / spacing).toInt())
        val rows = max(2, jsRound((2 * h) / spacing).toInt())
        val dirA = (floor(kit.rng() * 4) * PI) / 2 + PI / 4
        val dx = cos(dirA); val dy = sin(dirA)
        val dots = ArrayList<D>()
        for (r in 0 until rows) for (c in 0 until cols) {
            val ox = (c - (cols - 1) / 2.0) * spacing + (if (r % 2 == 0) 0.0 else spacing * 0.5)
            val oy = (r - (rows - 1) / 2.0) * spacing
            val proj = c01(0.5 + (ox * dx + oy * dy) / (2 * hypot(w, h)) * 1.4)
            val sprite = kit.sprite(HbOrnTex.DOT)
            sprite.tint = kit.color(si)
            dots += D(sprite, ox, oy, proj)
        }
        U(site, dots, spacing)
    }
    val p = SitePos()
    return { time ->
        val anchors = kit.anchors(time)
        val x = kit.exit(time)
        for (u in units) {
            kit.pos(u.site, anchors, p)
            val local = time - u.site.start
            for (d in u.dots) {
                val reveal = local - d.proj * 0.55
                val leave = x * 1.6 - (1 - d.proj) * 0.6
                val pop = easeOutBack(reveal / 0.22, 2.5) * (1 - c01(leave / 0.4))
                val wave = 0.22 * sin(TAU * (d.proj * 1.5 - time * 0.4))
                val value = c01(1 - d.proj * 0.9 + wave)
                val size = u.spacing * 0.92 * value * pop
                d.sprite.visible = size > 0.4 && reveal > 0
                if (!d.sprite.visible) continue
                d.sprite.x = p.x + d.ox; d.sprite.y = p.y + d.oy
                d.sprite.width = size; d.sprite.height = size
                d.sprite.alpha = 0.75 * p.alpha
            }
        }
    }
}

// ═══ ornamentLines.ts ════════════════════════════════════════════════════

private class Track(val x: Double, val y: Double, val dx: Double, val dy: Double, val length: Double, val start: Double)

private fun tracksFor(kit: Kit, reach: Double, across: Boolean): List<Track> {
    val pad = kit.size * 0.35
    val cx = kit.cx; val cy = kit.cy; val hw = kit.hw; val hh = kit.hh; val vertical = kit.vertical; val start = kit.start
    fun along(sign: Int) = if (vertical) Track(cx, cy + sign * (hh + pad), 0.0, sign.toDouble(), reach, start)
        else Track(cx + sign * (hw + pad), cy, sign.toDouble(), 0.0, reach, start)
    return when (kit.spec.placement) {
        "line-start" -> listOf(along(-1))
        "line-end" -> listOf(along(1))
        "corners" -> listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).mapIndexed { k, (sx, sy) ->
            Track(cx + sx * (hw + pad * 0.5), cy + sy * (hh + pad * 0.5), sx * SQRT1_2, sy * SQRT1_2, reach * 0.6, start + k * 0.06)
        }
        "screen" -> {
            val w = kit.ctx.width * 0.55; val h = kit.ctx.height * 0.55
            if (vertical) listOf(Track(cx - hw - pad * 2, -h, 0.0, 1.0, h * 2, start), Track(cx + hw + pad * 2, h, 0.0, -1.0, h * 2, start + 0.1))
            else listOf(Track(-w, cy - hh - pad * 2, 1.0, 0.0, w * 2, start), Track(w, cy + hh + pad * 2, -1.0, 0.0, w * 2, start + 0.1))
        }
        else -> if (across) {
            if (vertical) listOf(Track(cx + hw + pad, cy - hh - reach * 0.5, 0.0, 1.0, hh * 2 + reach, start))
            else listOf(Track(cx - hw - reach * 0.5, cy + hh + pad, 1.0, 0.0, hw * 2 + reach, start))
        } else listOf(along(-1), along(1))
    }
}

private val SQRT1_2 = sqrt(0.5)

private fun strokeTrack(g: HbOrnShape, track: Track, from: Double, to: Double, steps: Int, offset: (Double) -> Double, ox: Double = 0.0, oy: Double = 0.0): Boolean {
    if (to - from <= 1e-4) return false
    val nx = -track.dy; val ny = track.dx
    for (i in 0..steps) {
        val s = lerp(from, to, i.toDouble() / steps)
        val d = s * track.length
        val o = offset(s)
        val x = track.x + ox + track.dx * d + nx * o
        val y = track.y + oy + track.dy * d + ny * o
        if (i == 0) g.moveTo(x, y) else g.lineTo(x, y)
    }
    return true
}

private fun zigzagOrnament(kit: Kit): Update {
    val reach = kit.size * (2.6 + kit.rng() * 1.4)
    val tracks = tracksFor(kit, reach, false)
    val g = kit.graphics(); g.tint = kit.color(0)
    val echo = kit.graphics(); echo.tint = kit.color(1)
    val amp = kit.size * 0.18
    val width = max(1.5, kit.size * 0.06)
    return { time ->
        g.shape.clear(); echo.shape.clear()
        val x = kit.exit(time)
        var any = false
        val flip = if (floor(time * 6) % 2 == 0.0) 0.0 else 0.5
        val pump = 1 + 0.3 * (if (floor(time * 3) % 3 == 0.0) 1 else 0)
        for (track in tracks) {
            val local = time - track.start
            if (local < 0 || x >= 1) continue
            val teeth = max(3, jsRound(track.length / (kit.size * 0.34)).toInt())
            val to = easeOutCubic(local / 0.5)
            val from = easeInCubic(x)
            val steps = teeth * 2
            val zig = { s: Double ->
                val k = jsRound((s * teeth + flip) * 2)
                (if (k % 2 == 0.0) 1 else -1) * amp * pump * min(1.0, s * 6)
            }
            any = strokeTrack(g.shape, track, from, to, steps, zig) || any
            strokeTrack(echo.shape, track, from, to * 0.8, steps, { s -> zig(s) * 0.6 }, -track.dy * amp * 1.8, track.dx * amp * 1.8)
        }
        if (any) {
            g.shape.stroke(width, CAP_SQUARE, JOIN_MITER)
            echo.shape.stroke(width * 0.5, CAP_BUTT, JOIN_MITER)
        }
        g.visible = any; echo.visible = any
        g.alpha = 1 - x; echo.alpha = 0.6 * (1 - x)
    }
}

private fun waveOrnament(kit: Kit): Update {
    val reach = kit.size * 4
    val tracks = tracksFor(kit, reach, true)
    val layers = min(3, max(2, kit.count(2.4, 2)))
    class W(val g: HbOrnGraphics, val phase: Double, val amp: Double, val speed: Double, val width: Double)
    val waves = List(layers) { j ->
        val g = kit.graphics(); g.tint = kit.color(j)
        W(g, j * 1.9, kit.size * (0.22 - j * 0.05), 0.6 + j * 0.25, max(1.2, kit.size * (0.06 - j * 0.015)))
    }
    val wavelength = kit.size * 1.6
    return { time ->
        val x = kit.exit(time)
        val bump = 1 + 0.6 * exp(-(time - kit.lastSung(time)) / 0.25)
        waves.forEachIndexed { j, w ->
            w.g.shape.clear()
            var any = false
            for (track in tracks) {
                val local = time - track.start - j * 0.08
                if (local < 0 || x >= 1) continue
                val to = easeOutCubic(local / 0.7)
                val from = easeInCubic(x)
                val steps = min(140, max(16, jsRound(track.length / (kit.size * 0.12)).toInt()))
                val cycles = track.length / wavelength
                any = strokeTrack(w.g.shape, track, from, to, steps, { s ->
                    sin((s * cycles - time * w.speed) * TAU + w.phase) * w.amp * bump * sin(PI * s)
                }) || any
            }
            if (any) w.g.shape.stroke(w.width, CAP_ROUND, JOIN_ROUND)
            w.g.visible = any
            w.g.alpha = (1 - x) * (if (j == 0) 1.0 else 0.65)
        }
    }
}

private fun underlineOrnament(kit: Kit): Update {
    val placement = kit.spec.placement
    fun thick(size: Double) = max(2.0, size * 0.09 * kit.spec.scale)
    val vertical = kit.vertical
    fun bar(sprite: HbOrnSprite, x: Double, y: Double, length: Double, from: Double, to: Double, height: Double, alpha: Double) {
        val visible = to - from > 0.002 && alpha > 0.01
        sprite.visible = visible
        if (!visible) return
        val mid = (from + to) / 2 * length
        val len = (to - from) * length
        if (vertical) { sprite.x = x; sprite.y = y + mid; sprite.width = height; sprite.height = len }
        else { sprite.x = x + mid; sprite.y = y; sprite.width = len; sprite.height = height }
        sprite.alpha = alpha
    }
    if (placement == "glyphs" || placement == "corners" || kit.glyphs.isEmpty()) {
        class It(val glyph: GlyphInfo, val main: HbOrnSprite, val echo: HbOrnSprite)
        val items = kit.glyphs.mapIndexed { i, glyph ->
            val main = kit.sprite(HbOrnTex.SQUARE); val echo = kit.sprite(HbOrnTex.SQUARE)
            main.tint = kit.color(i); echo.tint = kit.color(i + 1)
            It(glyph, main, echo)
        }
        return { time ->
            val anchors = kit.anchors(time)
            val x = kit.exit(time)
            for (it in items) {
                val anchor = anchors.getOrNull(it.glyph.index)
                val size = anchor?.size ?: it.glyph.size
                val gx = anchor?.x ?: it.glyph.x
                val gy = anchor?.y ?: it.glyph.y
                val alpha = (anchor?.alpha ?: 1.0) * (1 - x)
                val local = time - it.glyph.sungAt
                val retract = easeInCubic((time - it.glyph.sungEnd - 0.1) / 0.25)
                val grow = easeOutExpo(local / 0.18)
                val length = size * 1.04
                val ox = if (vertical) gx + size * 0.6 else gx - length / 2
                val oy = if (vertical) gy - length / 2 else gy + size * 0.6
                bar(it.main, ox, oy, length, retract, if (local < 0) 0.0 else grow, thick(size), alpha)
                val growEcho = easeOutExpo((local - 0.06) / 0.2)
                val retractEcho = easeInCubic((time - it.glyph.sungEnd - 0.16) / 0.25)
                bar(it.echo, ox + (if (vertical) thick(size) * 1.6 else 0.0), oy + (if (vertical) 0.0 else thick(size) * 1.6), length,
                    retractEcho, if (local < 0.06) 0.0 else growEcho, thick(size) * 0.35, alpha * 0.9)
            }
        }
    }
    val order = kit.glyphs.sortedBy { it.sungAt }
    val first = order.first()
    val last = order.last()
    val pad = kit.size * 0.3
    val lineStart = placement == "line-start"
    val lineEnd = placement == "line-end"
    val main = kit.sprite(HbOrnTex.SQUARE); val echo = kit.sprite(HbOrnTex.SQUARE)
    main.tint = kit.color(0); echo.tint = kit.color(1)
    val length = if (vertical) kit.hh * 2 else kit.hw * 2
    val short = kit.size * 1.2
    return { time ->
        val x = kit.exit(time)
        var progress = 0.0
        for (glyph in order) if (time >= glyph.sungAt) progress += c01((time - glyph.sungAt) / max(0.08, glyph.sungEnd - glyph.sungAt))
        val k = easeOutCubic(progress / order.size)
        val retract = easeInCubic((time - last.sungEnd - 0.15) / 0.35)
        val pulse = 1 + 0.5 * exp(-(time - kit.lastSung(time)) / 0.15)
        val height = thick(kit.size / kit.spec.scale) * (if (lineStart || lineEnd) pulse else 1.2)
        val alpha = (1 - x) * (if (time >= first.sungAt) 1.0 else 0.0)
        if (lineStart || lineEnd) {
            val bx = if (vertical) kit.cx - short / 2 else if (lineStart) kit.cx - kit.hw - pad - short else kit.cx + kit.hw + pad
            val by = if (vertical) (if (lineStart) kit.cy - kit.hh - pad - short else kit.cy + kit.hh + pad) else kit.cy - short / 2
            val grow = easeOutBack((time - first.sungAt) / 0.3, 2.0)
            bar(main, if (vertical) kit.cx else bx, if (vertical) by else kit.cy, short, retract, grow, height, alpha)
            echo.visible = false
        } else {
            val bx = if (vertical) kit.cx + kit.hw + pad else kit.cx - kit.hw
            val by = if (vertical) kit.cy - kit.hh else kit.cy + kit.hh + pad
            bar(main, bx, by, length, retract, k, height, alpha)
            bar(echo, bx + (if (vertical) height * 1.6 else 0.0), by + (if (vertical) 0.0 else height * 1.6), length, retract, max(0.0, k - 0.04), height * 0.35, alpha * 0.9)
        }
    }
}

private fun dotsLineOrnament(kit: Kit): Update {
    val placement = kit.spec.placement
    val vertical = kit.vertical
    val spacing = kit.size * 0.3
    class LineDot(val sprite: HbOrnSprite, val x: Double, val y: Double, val glyph: GlyphInfo?, val order: Double, val litAt: Double, val size: Double)
    val dots = ArrayList<LineDot>()
    fun litOf(along: Double): Double {
        var best = Double.POSITIVE_INFINITY
        var at = kit.start + 0.6
        for (glyph in kit.glyphs) {
            val d = abs((if (vertical) glyph.y else glyph.x) - along)
            if (d < best) { best = d; at = glyph.sungAt }
        }
        return at
    }
    fun push(x: Double, y: Double, order: Double, litAt: Double, size: Double, glyph: GlyphInfo? = null) {
        val sprite = kit.sprite(HbOrnTex.DOT)
        sprite.tint = kit.color(dots.size)
        dots += LineDot(sprite, x, y, glyph, order, litAt, size)
    }
    val pad = kit.size * 0.45
    fun lineAlong(fromX: Double, fromY: Double, dx: Double, dy: Double, length: Double, fade: Boolean) {
        val n = max(3, min(kit.count(48.0, 8), jsRound(length / spacing).toInt()))
        for (i in 0 until n) {
            val s = if (n == 1) 0.0 else i.toDouble() / (n - 1)
            val x = fromX + dx * s * length
            val y = fromY + dy * s * length
            push(x, y, s, litOf(if (vertical) y else x), kit.size * (if (fade) 0.14 * (1 - s * 0.7) else 0.12))
        }
    }
    when (placement) {
        "glyphs" -> kit.glyphs.forEachIndexed { i, glyph ->
            push(if (vertical) glyph.size * 0.62 else 0.0, if (vertical) 0.0 else glyph.size * 0.62, i.toDouble() / max(1, kit.glyphs.size - 1), glyph.sungAt, kit.size * 0.16, glyph)
        }
        "line-start", "line-end" -> {
            val sign = if (placement == "line-start") -1.0 else 1.0
            val length = kit.size * 3
            if (vertical) lineAlong(kit.cx, kit.cy + sign * (kit.hh + pad), 0.0, sign, length, true)
            else lineAlong(kit.cx + sign * (kit.hw + pad), kit.cy, sign, 0.0, length, true)
        }
        "screen" -> {
            val w = kit.ctx.width * 0.5
            if (vertical) lineAlong(kit.cx + kit.hw + pad * 2, -kit.ctx.height * 0.5, 0.0, 1.0, kit.ctx.height, false)
            else lineAlong(-w, kit.cy + kit.hh + pad * 1.8, 1.0, 0.0, w * 2, false)
        }
        "corners" -> {
            val arm = kit.size * 1.5
            for ((sx, sy) in listOf(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0)) {
                val x0 = kit.cx + sx * (kit.hw + pad)
                val y0 = kit.cy + sy * (kit.hh + pad)
                lineAlong(x0, y0, -sx, 0.0, arm, true)
                lineAlong(x0, y0, 0.0, -sy, arm * 0.7, true)
            }
        }
        else -> {
            if (vertical) lineAlong(kit.cx + kit.hw + pad, kit.cy - kit.hh, 0.0, 1.0, kit.hh * 2, false)
            else lineAlong(kit.cx - kit.hw, kit.cy + kit.hh + pad, 1.0, 0.0, kit.hw * 2, false)
            if (placement == "around") {
                if (vertical) lineAlong(kit.cx - kit.hw - pad, kit.cy + kit.hh, 0.0, -1.0, kit.hh * 2, false)
                else lineAlong(kit.cx + kit.hw, kit.cy - kit.hh - pad, -1.0, 0.0, kit.hw * 2, false)
            }
        }
    }
    return { time ->
        val anchors = kit.anchors(time)
        val x = kit.exit(time)
        val runner = fract((time - kit.start) / 2.4)
        for (d in dots) {
            val appear = time - kit.start - d.order * 0.5
            val off = x * 1.4 - d.order * 0.4 >= 0.5
            if (appear < 0 || off) { d.sprite.visible = false; continue }
            var px = d.x; var py = d.y; var alpha = 1.0
            if (d.glyph != null) {
                val anchor = anchors.getOrNull(d.glyph.index)
                px += anchor?.x ?: d.glyph.x
                py += anchor?.y ?: d.glyph.y
                alpha = anchor?.alpha ?: 1.0
            }
            val lit = time >= d.litAt
            val pop = if (lit) 1 + 0.8 * exp(-(time - d.litAt) / 0.12) else 1.0
            val run = max(0.0, 1 - abs(runner - d.order) * 12)
            val size = d.size * (if (lit) 1.0 else 0.6) * pop * easeOutBack(appear / 0.2, 2.5) * (1 + run * 0.6)
            d.sprite.visible = size > 0.3
            d.sprite.x = px; d.sprite.y = py
            d.sprite.width = size; d.sprite.height = size
            d.sprite.alpha = alpha * (if (lit) 1.0 else 0.35 + run * 0.5)
        }
    }
}

// ═══ ornamentParticles.ts ════════════════════════════════════════════════

private class Trigger(val at: Double, val glyph: GlyphInfo?)

private fun triggers(kit: Kit, count: Int): List<Trigger> {
    if (kit.glyphs.isNotEmpty()) {
        val limit = max(1, min(kit.glyphs.size, count))
        return List(limit) { i ->
            val g = kit.glyphs[floor(((i + 0.5) * kit.glyphs.size) / limit).toInt()]
            Trigger(max(kit.start, g.sungAt), g)
        }
    }
    val span = max(0.5, kit.ctx.exitAt - kit.start)
    return List(count) { i -> Trigger(kit.start + (span * i) / count, null) }
}

private fun sparkleOrnament(kit: Kit): Update {
    val onGlyph = kit.spec.placement == "glyphs"
    val sites = kit.sites("scatter", 1)
    class It(val main: HbOrnSprite, val satA: HbOrnSprite, val satB: HbOrnSprite, val trigger: Trigger, val site: Site, val side: Int,
             val ox: Double, val oy: Double, val size: Double, val spin: Double)
    val items = triggers(kit, kit.count(14.0, 3)).mapIndexed { i, trigger ->
        val main = kit.sprite(HbOrnTex.STAR4); val satA = kit.sprite(HbOrnTex.STAR4); val satB = kit.sprite(HbOrnTex.DOT)
        listOf(main, satA, satB).forEachIndexed { k, s -> s.tint = kit.color(i + k) }
        val side = if (kit.rng() < 0.5) -1 else 1
        val site = sites[i % sites.size]
        val sc = kit.scatter(site)
        val ox = if (onGlyph) side * 0.48 else sc[0]
        val oy = if (onGlyph) -0.45 - kit.rng() * 0.1 else sc[1]
        val size = kit.size * (0.55 + kit.rng() * 0.35)
        val spin = (kit.rng() - 0.5) * 1.2
        It(main, satA, satB, trigger, site, side, ox, oy, size, spin)
    }
    val p = SitePos()
    return { time ->
        val anchors = kit.anchors(time)
        items.forEachIndexed { i, it ->
            val local = time - it.trigger.at
            val x = kit.exit(time, (i % 6) * 0.03)
            if (local < 0 || x >= 1) { it.main.visible = false; it.satA.visible = false; it.satB.visible = false; return@forEachIndexed }
            val cx: Double; val cy: Double; var alpha = 1.0
            val tg = it.trigger.glyph
            if (onGlyph && tg != null) {
                val anchor = anchors.getOrNull(tg.index)
                val size = anchor?.size ?: tg.size
                cx = (anchor?.x ?: tg.x) + it.ox * size
                cy = (anchor?.y ?: tg.y) + it.oy * size
                alpha = anchor?.alpha ?: 1.0
            } else {
                kit.pos(it.site, anchors, p)
                cx = p.x + it.ox; cy = p.y + it.oy
            }
            val pop = spring(local, 3.2, 8.0)
            val settle = c01((local - 0.45) / 0.35)
            val idle = 0.32 + 0.1 * sin(time * 5 + i * 1.7)
            val scale = lerp(1.25 * pop, idle, easeOutCubic(settle)) * (1 - easeInCubic(x))
            val s = it.size * max(0.0, scale)
            it.main.visible = s > 0.5
            it.main.x = cx; it.main.y = cy
            it.main.width = s; it.main.height = s
            it.main.rotation = -PI / 4 * (1 - easeOutCubic(local / 0.3)) + it.spin * local + x * 3
            it.main.alpha = alpha * (1 - x)
            fun sat(sprite: HbOrnSprite, delay: Double, dx: Double, dy: Double, sizeK: Double) {
                val l = local - delay
                val k = c01(l / 0.5)
                sprite.visible = l >= 0 && k < 1
                if (!sprite.visible) return
                val ss = it.size * sizeK * sin(PI * k)
                sprite.x = cx + dx * it.size; sprite.y = cy + dy * it.size
                sprite.width = ss; sprite.height = ss
                sprite.alpha = alpha
            }
            sat(it.satA, 0.08, it.side * 0.55, 0.35, 0.45)
            sat(it.satB, 0.15, -it.side * 0.4, -0.5, 0.16)
        }
    }
}

private fun crossOrnament(kit: Kit): Update {
    val shape = kit.context()
    shape.rect(-5.0, -1.4, 10.0, 2.8).rect(-1.4, -5.0, 2.8, 10.0).fill()
    val oneShot = kit.spec.placement == "glyphs"
    val sites = kit.sites("scatter", 8)
    val n = if (oneShot) sites.size * 2 else kit.count(12.0, 4)
    class It(val g: HbOrnGraphics, val site: Site, val ox: Double, val oy: Double, val size: Double, val delay: Double, val offset: Double, val rise: Double)
    val items = List(n) { i ->
        val site = sites[i % sites.size]
        val o = if (oneShot) { val a = (kit.rng() - 0.5) * site.w * 3; val b = -site.h * (0.9 + kit.rng() * 0.6); doubleArrayOf(a, b) } else kit.scatter(site)
        val g = kit.graphics(shape)
        g.tint = kit.color(i)
        val size = kit.size * (0.18 + kit.rng() * 0.22) / 10
        val delay = if (oneShot) (if (i >= sites.size) 0.08 else 0.0) else i * 0.06
        val offset = kit.rng()
        val rise = kit.size * (0.1 + kit.rng() * 0.2)
        It(g, site, o[0], o[1], size, delay, offset, rise)
    }
    val p = SitePos()
    return { time ->
        val anchors = kit.anchors(time)
        items.forEachIndexed { i, it ->
            val local = time - it.site.start - it.delay
            val x = kit.exit(time, (i % 8) * 0.03)
            if (local < 0 || x >= 1 || (oneShot && local > 1.4)) { it.g.visible = false; return@forEachIndexed }
            kit.pos(it.site, anchors, p)
            var scale = easeOutBack(local / 0.3, 3.0) * (1 - easeInCubic(x))
            var alpha = p.alpha * (1 - x)
            if (oneShot) { val k = c01((local - 0.6) / 0.8); scale *= 1 - k; alpha *= 1 - k }
            it.g.visible = scale > 0.01
            it.g.x = p.x + it.ox; it.g.y = p.y + it.oy - it.rise * sin(time * 0.8 + it.offset * TAU)
            it.g.scale = max(0.001, it.size * scale)
            it.g.rotation = beatSteps(local / 1.2 + it.offset) * (PI / 2) + x * 4
            it.g.alpha = alpha
        }
    }
}

private class Faller(
    val sprite: HbOrnSprite, val site: Site, val x0: Double, val period: Double, val offset: Double, val sway: Double, val swayFreq: Double,
    val flip: Double, val spin: Double, val size: Double, val aspect: Double, val delay: Double, val vx: Double, val vy: Double,
)

private fun confettiOrnament(kit: Kit): Update {
    val sites = kit.sites("scatter", 6)
    val n = kit.count(26.0, 8)
    val pieces = List(n) { i ->
        val site = sites[i % sites.size]
        val sprite = kit.sprite(if (i % 3 == 2) HbOrnTex.TRIANGLE else HbOrnTex.SQUARE)
        sprite.tint = kit.color(i)
        val angle = -PI / 2 + (kit.rng() - 0.5) * 2.4
        val speed = kit.size * (3 + kit.rng() * 4)
        val x0 = (kit.rng() * 2 - 1) * (site.w + kit.size)
        val period = 3 + kit.rng() * 2.5
        val offset = kit.rng()
        val sway = kit.size * (0.2 + kit.rng() * 0.35)
        val swayFreq = 0.4 + kit.rng() * 0.5
        val flip = 3 + kit.rng() * 6
        val spin = (kit.rng() - 0.5) * 6
        val size = kit.size * (0.2 + kit.rng() * 0.14)
        val aspect = if (i % 3 == 2) 1.0 else 0.5 + kit.rng() * 0.3
        Faller(sprite, site, x0, period, offset, sway, swayFreq, flip, spin, size, aspect, (i % 5) * 0.02, cos(angle) * speed, sin(angle) * speed)
    }
    val p = SitePos()
    val burstLife = 2.2
    return { time ->
        val anchors = kit.anchors(time)
        val x = kit.exit(time)
        for (it in pieces) {
            val local = time - it.site.start - it.delay
            if (local < 0 || x >= 1) { it.sprite.visible = false; continue }
            kit.pos(it.site, anchors, p)
            val top = -it.site.h - kit.size * 2
            val bottom = it.site.h + kit.size * 1.5
            var ox: Double; var oy: Double; var alpha: Double
            if (local < burstLife) {
                val drag = 2.6
                val fall = kit.size * 1.6
                val e = exp(-drag * local)
                val f = (1 - e) / drag
                ox = it.vx * f + sin(local * it.swayFreq * TAU) * it.sway * (1 - e)
                oy = it.vy * f + fall * (local - f)
                alpha = 1 - c01((local - burstLife + 0.4) / 0.4)
            } else {
                val cycle = fract((local - burstLife) / it.period + it.offset)
                ox = it.x0 + sin(local * it.swayFreq * TAU) * it.sway
                oy = lerp(top, bottom, cycle)
                alpha = c01(cycle / 0.1) * c01((1 - cycle) / 0.15)
            }
            oy += kit.size * 3 * x * x
            val flip = cos(local * it.flip)
            it.sprite.visible = alpha > 0.01
            it.sprite.x = p.x + ox; it.sprite.y = p.y + oy
            it.sprite.width = it.size * max(0.08, abs(flip))
            it.sprite.height = it.size * it.aspect
            it.sprite.rotation = local * it.spin
            it.sprite.alpha = alpha * (1 - x) * p.alpha * (0.75 + 0.25 * abs(flip))
        }
    }
}

private fun petalsOrnament(kit: Kit): Update {
    val sites = kit.sites("scatter", 4)
    val n = kit.count(16.0, 5)
    val petals = List(n) { i ->
        val site = sites[i % sites.size]
        val sprite = kit.sprite(HbOrnTex.PETAL)
        sprite.tint = kit.color(i)
        val x0 = (kit.rng() * 2 - 1) * (site.w + kit.size * 1.5)
        val period = 4.5 + kit.rng() * 3
        val offset = kit.rng()
        val sway = kit.size * (0.3 + kit.rng() * 0.4)
        val swayFreq = 0.25 + kit.rng() * 0.3
        val flip = 1.5 + kit.rng() * 2.5
        val spin = (kit.rng() - 0.5) * 2
        val size = kit.size * (0.22 + kit.rng() * 0.16)
        Faller(sprite, site, x0, period, offset, sway, swayFreq, flip, spin, size, 1.35, i * 0.05, 0.0, 0.0)
    }
    val p = SitePos()
    return { time ->
        val anchors = kit.anchors(time)
        val x = kit.exit(time)
        for (it in petals) {
            val local = time - it.site.start - it.delay
            if (local < 0 || x >= 1) { it.sprite.visible = false; continue }
            kit.pos(it.site, anchors, p)
            val top = -it.site.h - kit.size * 2
            val bottom = it.site.h + kit.size * 1.5
            val cycle = fract(local / it.period + it.offset)
            val span = it.site.w + kit.size * 2
            val drift = wrap(it.x0 + span + cycle * kit.size * 2.5 + span, span * 2) - span
            val ox = drift + sin(local * it.swayFreq * TAU) * it.sway + kit.size * 6 * x * x
            val oy = lerp(top, bottom, cycle)
            val flip = cos(local * it.flip)
            val alpha = c01(cycle / 0.1) * c01((1 - cycle) / 0.15) * c01(local / 0.3)
            val pop = easeOutBack(local / 0.35, 2.0)
            it.sprite.visible = alpha > 0.01
            it.sprite.x = p.x + ox; it.sprite.y = p.y + oy
            it.sprite.width = it.size * max(0.1, abs(flip)) * pop
            it.sprite.height = it.size * it.aspect * pop
            it.sprite.rotation = 0.6 + sin(local * 0.9 + it.offset * TAU) * 0.7 + local * it.spin * 0.2
            it.sprite.alpha = alpha * (1 - x) * p.alpha * 0.9
        }
    }
}

private fun shardsOrnament(kit: Kit): Update {
    val oneShot = kit.spec.placement == "glyphs"
    val big = isBig(kit.spec.placement)
    val sites = kit.sites("single", 8)
    val per = if (oneShot) 4 else max(3, jsRound(kit.count(18.0, 5).toDouble() / sites.size).toInt())
    class Sh(val sprite: HbOrnSprite, val site: Site, val cos: Double, val sin: Double, val edge: Double, val reach: Double, val size: Double, val rot: Double, val spin: Double, val stretch: Double)
    val shards = sites.flatMapIndexed { si, site ->
        List(per) { i ->
            val angle = (TAU * (i + kit.rng() * 0.8)) / per + si
            val c = cos(angle); val s = sin(angle)
            val edge = if (big) 1 / sqrt((c / (site.w + kit.size * 0.2)).pow(2) + (s / (site.h + kit.size * 0.2)).pow(2)) else site.w * 0.8
            val sprite = kit.sprite(HbOrnTex.TRIANGLE)
            sprite.tint = kit.color(si + i)
            val reach = kit.size * (if (oneShot) 0.6 + kit.rng() * 0.6 else 0.8 + kit.rng() * 1.6)
            val size = kit.size * (if (oneShot) 0.14 + kit.rng() * 0.1 else 0.18 + kit.rng() * 0.22)
            val rot = kit.rng() * TAU
            val spin = (kit.rng() - 0.5) * 10
            val stretch = 1.3 + kit.rng() * 0.8
            Sh(sprite, site, c, s, edge, reach, size, rot, spin, stretch)
        }
    }
    val p = SitePos()
    return { time ->
        val anchors = kit.anchors(time)
        val x = kit.exit(time)
        for (s in shards) {
            val local = time - s.site.start
            if (local < 0 || x >= 1 || (oneShot && local > 1.1)) { s.sprite.visible = false; continue }
            kit.pos(s.site, anchors, p)
            val out = s.reach * (1 - exp(-5 * local)) + kit.size * 0.08 * local + kit.size * 4 * easeInCubic(x)
            val r = s.edge + out
            var scale = easeOutBack(local / 0.2, 2.0) * lerp(1.0, 0.7, c01(local / 3)) * (1 - x)
            var alpha = p.alpha * (1 - x)
            if (oneShot) { val k = c01((local - 0.4) / 0.7); scale *= 1 - k; alpha *= 1 - k }
            s.sprite.visible = scale > 0.01
            s.sprite.x = p.x + s.cos * r; s.sprite.y = p.y + s.sin * r
            s.sprite.width = s.size * scale
            s.sprite.height = s.size * scale * s.stretch
            s.sprite.rotation = s.rot + s.spin * (1 - exp(-2 * local)) + 0.2 * local
            s.sprite.alpha = alpha
        }
    }
}

private fun streaksOrnament(kit: Kit): Update {
    val sites = kit.sites("scatter", 6)
    val n = kit.count(if (kit.spec.placement == "screen") 30.0 else 20.0, 6)
    class L(val sprite: HbOrnSprite, val site: Site, val ox: Double, val y0: Double, val length: Double, val thick: Double, val speed: Double, val delay: Double, val salt: Double)
    val lines = List(n) { i ->
        val site = sites[i % sites.size]
        val ox = kit.scatter(site)[0]
        val sprite = kit.sprite(HbOrnTex.STREAK)
        sprite.rotation = PI
        sprite.tint = kit.color(i)
        val y0 = kit.rng()
        val length = kit.size * (1.4 + kit.rng() * 2.6)
        val thick = max(1.0, kit.size * (0.025 + kit.rng() * 0.05))
        val speed = kit.size * (5 + kit.rng() * 7)
        val delay = kit.rng() * 0.3
        L(sprite, site, ox, y0, length, thick, speed, delay, (i * 13 + 1).toDouble())
    }
    val p = SitePos()
    return { time ->
        val anchors = kit.anchors(time)
        val x = kit.exit(time)
        for (l in lines) {
            val local = time - l.site.start - l.delay
            if (local < 0 || x >= 1) { l.sprite.visible = false; continue }
            kit.pos(l.site, anchors, p)
            val top = -l.site.h - kit.size * 2 - l.length
            val span = l.site.h * 2 + kit.size * 4 + l.length * 2
            val travel = l.speed * (local + 2 * x * x)
            val y = top + wrap(l.y0 * span + travel, span)
            val cycle = (y - top) / span
            val window = c01(cycle / 0.15) * c01((1 - cycle) / 0.15)
            val flicker = if (hash01(floor(time * 15), l.salt) < 0.2) 0.3 else 1.0
            l.sprite.visible = window > 0.01
            l.sprite.x = p.x + l.ox; l.sprite.y = p.y + y
            l.sprite.width = l.thick
            l.sprite.height = l.length * (1 + x)
            l.sprite.alpha = window * flicker * c01(local / 0.25) * (1 - x) * p.alpha * 0.85
        }
    }
}

private fun chevronsOrnament(kit: Kit): Update {
    val shape = kit.context()
    shape.moveTo(-5.0, 3.0).lineTo(0.0, -2.0).lineTo(5.0, 3.0).stroke(2.4, CAP_SQUARE, JOIN_MITER)
    val placement = kit.spec.placement
    class Col(val site: Site, val dx: Double, val scale: Double, val alpha: Double)
    val sites = kit.sites("single", 6)
    val columns: List<Col> = when (placement) {
        "around" -> { val site = sites[0]; listOf(-1, 1).map { side -> Col(site, side * (site.w + kit.size * 0.8), 1.0, 1.0) } }
        "screen" -> { val site = sites[0]; listOf(-0.4, -0.2, 0.2, 0.4).map { k -> Col(site, k * kit.ctx.width, 1.2, 0.7) } }
        "behind" -> listOf(Col(sites[0], 0.0, 2.2, 0.35))
        else -> sites.map { site -> Col(site, 0.0, if (placement == "glyphs") 0.6 else 1.0, 1.0) }
    }
    val perColumn = max(2, min(6, kit.count(4.0, 2)))
    val oneShot = placement == "glyphs"
    val spacing = kit.size * 0.42
    class It(val g: HbOrnGraphics, val col: Col, val j: Int)
    val items = columns.flatMapIndexed { ci, col -> List(perColumn) { j -> val g = kit.graphics(shape); g.tint = kit.color(ci + j); It(g, col, j) } }
    val p = SitePos()
    return { time ->
        val anchors = kit.anchors(time)
        val x = kit.exit(time)
        for (it in items) {
            val col = it.col; val j = it.j
            val local = time - col.site.start - j * 0.05
            if (local < 0 || x >= 1 || (oneShot && local > 1.6)) { it.g.visible = false; continue }
            kit.pos(col.site, anchors, p)
            val s = spacing * col.scale
            val total = perColumn * s
            val travel = beatSteps(local * 3.2, 0.4) * s * 0.5 + x * x * s * 6
            val y = wrap(j * s - travel, total)
            val k = y / total
            val fade = sin(PI * c01(k))
            val pop = easeOutBack(local / 0.3, 2.4)
            val baseY = if (oneShot) p.y - p.size * 0.8 - total * 0.5 else p.y - total / 2
            it.g.visible = true
            it.g.x = p.x + col.dx; it.g.y = baseY + y
            it.g.scale = max(0.001, (kit.size * 0.06) * col.scale * pop)
            var alpha = fade * col.alpha * (1 - x) * p.alpha
            if (oneShot) alpha *= 1 - c01((local - 1) / 0.6)
            it.g.alpha = alpha
        }
    }
}

/** aroundBox：方框周长参数化（从左上角顺时针）。 */
internal fun hbAroundBox(u: Double, halfW: Double, halfH: Double): DoubleArray {
    val perimeter = 4 * (halfW + halfH)
    var d = fract(u) * perimeter
    if (d < 2 * halfW) return doubleArrayOf(-halfW + d, -halfH)
    d -= 2 * halfW
    if (d < 2 * halfH) return doubleArrayOf(halfW, -halfH + d)
    d -= 2 * halfH
    if (d < 2 * halfW) return doubleArrayOf(halfW - d, halfH)
    d -= 2 * halfW
    return doubleArrayOf(-halfW, halfH - d)
}

private fun glintsOrnament(kit: Kit): Update {
    val onGlyph = kit.spec.placement == "glyphs" || kit.glyphs.isEmpty()
    val run = 0.55
    if (onGlyph) {
        class It(val head: HbOrnSprite, val tail: HbOrnSprite, val spark: HbOrnSprite, val trigger: Trigger, val corner: Double)
        val items = triggers(kit, kit.count(16.0, 4)).mapIndexed { i, trigger ->
            val head = kit.sprite(HbOrnTex.GLOW); val tail = kit.sprite(HbOrnTex.DOT); val spark = kit.sprite(HbOrnTex.STAR4)
            listOf(head, tail, spark).forEach { s -> s.tint = kit.color(i) }
            It(head, tail, spark, trigger, floor(kit.rng() * 4) / 4)
        }
        val site = kit.sites("single", 1)[0]
        val p = SitePos()
        return { time ->
            val anchors = kit.anchors(time)
            val x = kit.exit(time)
            for (it in items) {
                val local = time - it.trigger.at
                val hide = local < 0 || x >= 1
                it.head.visible = !hide; it.tail.visible = !hide; it.spark.visible = !hide
                if (hide) continue
                val gx: Double; val gy: Double; val size: Double; var alpha = 1.0
                val tg = it.trigger.glyph
                if (tg != null) {
                    val anchor = anchors.getOrNull(tg.index)
                    gx = anchor?.x ?: tg.x; gy = anchor?.y ?: tg.y; size = anchor?.size ?: tg.size; alpha = anchor?.alpha ?: 1.0
                } else {
                    kit.pos(site, anchors, p)
                    gx = p.x; gy = p.y; size = kit.size
                }
                val half = size * 0.56
                val k = c01(local / run)
                val running = k < 1
                val u = easeOutCubic(k)
                val h = hbAroundBox(u, half, half)
                val t = hbAroundBox(max(0.0, u - 0.08), half, half)
                val hs = kit.size * 0.35
                it.head.visible = running; it.tail.visible = running
                if (running) {
                    it.head.x = gx + h[0]; it.head.y = gy + h[1]; it.head.width = hs; it.head.height = hs; it.head.alpha = alpha
                    it.tail.x = gx + t[0]; it.tail.y = gy + t[1]; it.tail.width = hs * 0.25; it.tail.height = hs * 0.25; it.tail.alpha = alpha * 0.7
                }
                val c = hbAroundBox(it.corner, half, half)
                val after = local - run
                val twinkle = if (after < 0) 0.0 else spring(after, 2.5, 6.0) * (0.55 + 0.45 * sin(time * 7 + it.corner * 20))
                val ss = kit.size * 0.4 * twinkle * (1 - easeInCubic(x))
                it.spark.visible = ss > 0.5
                it.spark.x = gx + c[0]; it.spark.y = gy + c[1]
                it.spark.width = ss; it.spark.height = ss
                it.spark.alpha = alpha * (1 - x)
            }
        }
    }
    val sites = kit.sites("single", 1)
    class R(val site: Site, val phase: Double, val head: HbOrnSprite, val tails: List<HbOrnSprite>)
    val runners = sites.flatMapIndexed { si, site ->
        listOf(0.0, 0.5).mapIndexed { r, phase ->
            val head = kit.sprite(HbOrnTex.GLOW)
            val tails = listOf(kit.sprite(HbOrnTex.DOT), kit.sprite(HbOrnTex.DOT), kit.sprite(HbOrnTex.DOT))
            (listOf(head) + tails).forEach { s -> s.tint = kit.color(si + r) }
            R(site, phase, head, tails)
        }
    }
    val p = SitePos()
    return { time ->
        val anchors = kit.anchors(time)
        val x = kit.exit(time)
        val boost = exp(-(time - kit.lastSung(time)) / 0.2)
        for (r in runners) {
            val local = time - r.site.start
            val hide = local < 0 || x >= 1
            r.head.visible = !hide
            r.tails.forEach { t -> t.visible = !hide }
            if (hide) continue
            kit.pos(r.site, anchors, p)
            val halfW = r.site.w + kit.size * 0.15
            val halfH = r.site.h + kit.size * 0.15
            val u = r.phase + local / 2.8 + 0.1 * beatSteps(local * 1.2, 0.3) / 1.2
            val alpha = c01(local / 0.2) * (1 - x) * p.alpha
            val h = hbAroundBox(u, halfW, halfH)
            val hs = kit.size * (0.3 + 0.25 * boost)
            r.head.x = p.x + h[0]; r.head.y = p.y + h[1]; r.head.width = hs; r.head.height = hs; r.head.alpha = alpha
            r.tails.forEachIndexed { k, t ->
                val tt = hbAroundBox(u - 0.012 * (k + 1), halfW, halfH)
                val ts = hs * 0.3 * (1 - k * 0.25)
                t.x = p.x + tt[0]; t.y = p.y + tt[1]; t.width = ts; t.height = ts
                t.alpha = alpha * (0.7 - k * 0.2)
            }
        }
    }
}

/**
 * scene.ts buildRig 的装饰件部分：一个镜头按 profile.ornaments 建 0..3 件；字锚点按时刻缓存给同镜头的几件共用
 * （Folia 每件各算一次，结果相同）。没有字的镜头（间奏）用 region 当包围盒、锚点为空。
 */
internal fun hbShotOrnaments(item: HbBuiltShot, width: Double, height: Double, seed: String, tuning: HbTuning): List<HbOrnament> {
    if (tuning.ornamentAmount <= 0) return emptyList()
    val region = item.shot.profile.region
    val text = item.text
    val bounds = text?.bounds ?: HbBounds(-region.w * width / 2, -region.h * height / 2, region.w * width, region.h * height)
    var memoT = Double.NaN
    var memo: List<HbGlyphAnchor> = emptyList()
    val anchorsAt: (Double) -> List<HbGlyphAnchor> = if (text == null) { _ -> emptyList() } else { t ->
        if (t != memoT) { memoT = t; memo = text.anchors(t) }
        memo
    }
    return item.shot.profile.ornaments.mapIndexed { k, spec ->
        HbOrnament(spec, HbOrnamentContext(width, height, "$seed:${item.shot.id}:ornament$k", item.palette, bounds, anchorsAt,
            item.timing.enterAt, item.timing.exitAt, item.timing.exitDuration, min(2.0, tuning.ornamentAmount)))
    }
}
