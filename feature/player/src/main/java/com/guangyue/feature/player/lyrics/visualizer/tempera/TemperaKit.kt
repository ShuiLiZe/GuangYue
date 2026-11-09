// 凝彩构图层的公共件：逐函数对译 Folia temperaHatch.ts（网线 / 记号 / 折线生成器）、temperaShapes.ts（图形节点）、
// temperaCompositionContext.ts（构图上下文）、temperaCompositions.ts（构图注册 + 贯穿线 + motif 叠加）。
// 构图只产出「节点 + 入场参数」的纯数据（与 Folia 往 Pixi 场景里 add 的 Graphics 一一对应），绘制交给舞台；
// 这样每一种构图都能和 Folia 原版逐节点对拍（金标 tempera_comp_golden.txt）。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import com.guangyue.feature.player.lyrics.visualizer.sonnetHash01
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

internal fun th01(seed: Long, index: Int, salt: Int) = sonnetHash01(seed, index, salt)
internal fun jsRound(v: Double): Int = floor(v + 0.5).toInt()
internal const val TAU = PI * 2

// ───────────────────────────── temperaHatch.ts ─────────────────────────────

internal data class TpHatchSpec(val angle: Double, val spacing: Double, val width: Double)
internal class TpLine(val x1: Double, val y1: Double, val x2: Double, val y2: Double)
internal class TpMark(val x: Double, val y: Double, val size: Double, val rotation: Double)
internal class TpDisc(val x: Double, val y: Double, val radius: Double)

private const val MAX_HATCH_LINES = 320

internal fun buildHatchSpec(seed: Long, salt: Int, scale: Double = 1.0): TpHatchSpec {
    val angleIndex = floor(th01(seed, 1, salt) * 4).toInt()
    val angle = listOf(-PI / 4, PI / 4, -PI / 3, PI / 6).getOrElse(angleIndex) { PI / 4 }
    val density = floor(th01(seed, 2, salt) * 3).toInt()
    val spacing = listOf(9.0, 13.0, 18.0).getOrElse(density) { 13.0 } * scale
    val width = max(0.8, spacing * (0.16 + th01(seed, 3, salt) * 0.14))
    return TpHatchSpec(angle, spacing, width)
}

internal fun rectPolygon(x: Double, y: Double, width: Double, height: Double) =
    doubleArrayOf(x, y, x + width, y, x + width, y + height, x, y + height)

internal fun circlePolygon(cx: Double, cy: Double, radius: Double, segments: Int = 28): DoubleArray {
    val count = max(6, segments)
    val out = DoubleArray(count * 2)
    for (i in 0 until count) {
        val a = i.toDouble() / count * TAU
        out[i * 2] = cx + cos(a) * radius
        out[i * 2 + 1] = cy + sin(a) * radius
    }
    return out
}

internal fun diamondPolygon(cx: Double, cy: Double, rx: Double, ry: Double) =
    doubleArrayOf(cx, cy - ry, cx + rx, cy, cx, cy + ry, cx - rx, cy)

private fun clipToConvex(polygon: DoubleArray, ax: Double, ay: Double, dx: Double, dy: Double, span: Double): TpLine? {
    val count = polygon.size / 2
    var cx = 0.0
    var cy = 0.0
    for (i in 0 until count) { cx += polygon[i * 2]; cy += polygon[i * 2 + 1] }
    cx /= count; cy /= count
    var tMin = -span
    var tMax = span
    for (i in 0 until count) {
        val x0 = polygon[i * 2]; val y0 = polygon[i * 2 + 1]
        val x1 = polygon[((i + 1) % count) * 2]; val y1 = polygon[((i + 1) % count) * 2 + 1]
        var nx = y1 - y0
        var ny = -(x1 - x0)
        if (nx * (cx - x0) + ny * (cy - y0) > 0) { nx = -nx; ny = -ny }
        val den = nx * dx + ny * dy
        val num = nx * (ax - x0) + ny * (ay - y0)
        if (abs(den) < 1e-9) { if (num > 0) return null; continue }
        val t = -num / den
        if (den > 0) tMax = min(tMax, t) else tMin = max(tMin, t)
        if (tMin > tMax) return null
    }
    if (tMax - tMin < 0.5) return null
    return TpLine(ax + dx * tMin, ay + dy * tMin, ax + dx * tMax, ay + dy * tMax)
}

/** 凸多边形里的平行网线（Folia buildHatchLines）。 */
internal fun buildHatchLines(polygon: DoubleArray, spec: TpHatchSpec, coverage: Double = 1.0): List<TpLine> {
    if (polygon.size < 6 || spec.spacing <= 0) return emptyList()
    var minX = Double.POSITIVE_INFINITY; var maxX = Double.NEGATIVE_INFINITY
    var minY = Double.POSITIVE_INFINITY; var maxY = Double.NEGATIVE_INFINITY
    for (i in polygon.indices step 2) {
        minX = min(minX, polygon[i]); maxX = max(maxX, polygon[i])
        minY = min(minY, polygon[i + 1]); maxY = max(maxY, polygon[i + 1])
    }
    val centerX = (minX + maxX) / 2
    val centerY = (minY + maxY) / 2
    val diagonal = hypot(maxX - minX, maxY - minY)
    if (diagonal <= 0) return emptyList()
    val dx = cos(spec.angle); val dy = sin(spec.angle)
    val px = -dy; val py = dx
    val steps = min(MAX_HATCH_LINES / 2, ceil(diagonal / (spec.spacing * 2)).toInt() + 2)
    val cov = min(1.0, max(0.0, coverage))
    val lines = ArrayList<TpLine>()
    for (step in -steps..steps) {
        val off = step * spec.spacing
        val c = clipToConvex(polygon, centerX + px * off, centerY + py * off, dx, dy, diagonal) ?: continue
        if (cov >= 1) { lines += c; continue }
        val mx = (c.x1 + c.x2) / 2; val my = (c.y1 + c.y2) / 2
        lines += TpLine(mx + (c.x1 - mx) * cov, my + (c.y1 - my) * cov, mx + (c.x2 - mx) * cov, my + (c.y2 - my) * cov)
    }
    return lines
}

internal fun buildScribblePath(seed: Long, salt: Int, cx: Double, cy: Double, radius: Double, turns: Double = 2.0): DoubleArray {
    val perTurn = 13
    val total = max(perTurn, jsRound(perTurn * max(1.0, turns)))
    val out = DoubleArray((total + 1) * 2)
    for (i in 0..total) {
        val p = i.toDouble() / total
        val angle = p * TAU * max(1.0, turns)
        val jr = (th01(seed, i, salt) - 0.5) * radius * 0.26
        val ja = (th01(seed, i, salt + 7) - 0.5) * 0.22
        val r = radius * (0.52 + 0.48 * p) + jr
        out[i * 2] = cx + cos(angle + ja) * r
        out[i * 2 + 1] = cy + sin(angle + ja) * r * 0.82
    }
    return out
}

internal fun buildWavyPath(seed: Long, salt: Int, x0: Double, x1: Double, y: Double, amplitude: Double, steps: Int = 24): DoubleArray {
    val n = max(2, steps)
    val out = DoubleArray((n + 1) * 2)
    for (i in 0..n) {
        val p = i.toDouble() / n
        val wave = sin(p * TAU * 1.5 + th01(seed, 0, salt) * TAU)
        val jitter = (th01(seed, i, salt) - 0.5) * amplitude * 0.5
        out[i * 2] = x0 + (x1 - x0) * p
        out[i * 2 + 1] = y + wave * amplitude + jitter
    }
    return out
}

private fun buildMarkRow(seed: Long, salt: Int, x: Double, y: Double, count: Int, spacing: Double, size: Double, angle: Double): List<TpMark> {
    val dx = cos(angle); val dy = sin(angle)
    return List(max(0, count)) { i ->
        val jitter = (th01(seed, i, salt) - 0.5) * spacing * 0.16
        val d = i * spacing + jitter
        TpMark(x + dx * d, y + dy * d, size * (0.82 + th01(seed, i, salt + 3) * 0.36), (th01(seed, i, salt + 11) - 0.5) * 0.3)
    }
}

internal fun buildCrossRow(seed: Long, salt: Int, x: Double, y: Double, count: Int, spacing: Double, size: Double = 9.0, angle: Double = 0.0) =
    buildMarkRow(seed, salt, x, y, count, spacing, size, angle)

internal fun buildDotRow(seed: Long, salt: Int, x: Double, y: Double, count: Int, spacing: Double, size: Double = 4.0, angle: Double = PI / 2) =
    buildMarkRow(seed, salt, x, y, count, spacing, size, angle)

internal fun buildDotGrid(width: Double, height: Double, spacing: Double, size: Double = 1.6): List<TpMark> {
    if (spacing <= 0 || width <= 0 || height <= 0) return emptyList()
    val columns = min(200, ceil(width / spacing).toInt() + 1)
    val rows = min(200, ceil(height / spacing).toInt() + 1)
    val out = ArrayList<TpMark>(rows * columns)
    for (row in 0 until rows) for (col in 0 until columns) {
        out += TpMark(col * spacing + (if (row % 2 == 0) 0.0 else spacing / 2), row * spacing, size, 0.0)
    }
    return out
}

internal fun buildCrossingLines(seed: Long, salt: Int, width: Double, height: Double, count: Int): List<TpLine> {
    val total = min(3, max(0, count))
    return List(total) { i ->
        val anchorY = height * (0.18 + th01(seed, i, salt) * 0.64)
        val sign = if (th01(seed, i, salt + 5) > 0.5) 1 else -1
        val angle = sign * (0.07 + th01(seed, i, salt + 9) * 0.11)
        val reach = width * 0.9
        TpLine(-width * 0.3, anchorY - tan(angle) * reach, width * 1.3, anchorY + tan(angle) * reach)
    }
}

// ───────────────────────────── temperaShapes.ts：节点 ─────────────────────────────

/** 调色板里的色位（temperaPalette.ts 的字段名）。 */
internal enum class TpTone(val id: String) { PAPER("paper"), INK("ink"), TONE1("tone1"), TONE2("tone2"), TONE3("tone3"), TONE4("tone4"), LINE("line") }

/** 构图里用到的色位名，照 Folia 的 `palette.tone1` 写法。 */
internal object TpPaletteRef {
    val paper = TpTone.PAPER
    val ink = TpTone.INK
    val tone1 = TpTone.TONE1
    val tone2 = TpTone.TONE2
    val tone3 = TpTone.TONE3
    val tone4 = TpTone.TONE4
    val line = TpTone.LINE
}

internal enum class TpNodeType(val id: String) {
    FILL("fill"), HOLES("holes"), OUTLINE("outline"), HATCH("hatch"), DISCS("discs"), RINGS("rings"),
    LINES("lines"), POLYLINE("polyline"), DIAMONDS("diamonds"), CROSSES("crosses"), SQUARES("squares"),
}

/**
 * 一个图形节点（= Folia 一个 Graphics）。nums 的排布与金标记录器一致：
 * FILL/HOLES `alpha, poly…`；OUTLINE `width, alpha, poly…`；HATCH `angle, spacing, width, alpha, poly…`；
 * DISCS `alpha, (x, y, r)…`；RINGS `width, alpha, (x, y, r)…`；LINES `width, alpha, (x1, y1, x2, y2)…`；
 * POLYLINE `width, alpha, pts…`；DIAMONDS `cx, cy, rx, ry, rings, alpha`；CROSSES `width, alpha, (x, y, size, rot)…`；
 * SQUARES `alpha, (x, y, size, rot)…`。
 */
internal class TpNode(val type: TpNodeType, val tone: TpTone, val nums: DoubleArray, val holes: List<DoubleArray> = emptyList(), val gradient: Boolean = false) {
    fun typeId() = type.id + if (gradient) "G" else ""
}

private fun marksNums(marks: List<TpMark>) = DoubleArray(marks.size * 4).also { a -> marks.forEachIndexed { i, m -> a[i * 4] = m.x; a[i * 4 + 1] = m.y; a[i * 4 + 2] = m.size; a[i * 4 + 3] = m.rotation } }
private fun discNums(discs: List<TpDisc>) = DoubleArray(discs.size * 3).also { a -> discs.forEachIndexed { i, d -> a[i * 3] = d.x; a[i * 3 + 1] = d.y; a[i * 3 + 2] = d.radius } }
private fun lineNums(lines: List<TpLine>) = DoubleArray(lines.size * 4).also { a -> lines.forEachIndexed { i, l -> a[i * 4] = l.x1; a[i * 4 + 1] = l.y1; a[i * 4 + 2] = l.x2; a[i * 4 + 3] = l.y2 } }

internal fun drawPolygonFill(polygon: DoubleArray, tone: TpTone, alpha: Double = 1.0, gradient: Boolean = false) =
    TpNode(TpNodeType.FILL, tone, doubleArrayOf(alpha) + polygon, gradient = gradient)
internal fun drawPolygonFillWithHoles(polygon: DoubleArray, holes: List<DoubleArray>, tone: TpTone, alpha: Double = 1.0, gradient: Boolean = false) =
    TpNode(TpNodeType.HOLES, tone, doubleArrayOf(alpha) + polygon, holes, gradient)
internal fun drawPolygonOutline(polygon: DoubleArray, tone: TpTone, width: Double, alpha: Double = 1.0) =
    TpNode(TpNodeType.OUTLINE, tone, doubleArrayOf(width, alpha) + polygon)
internal fun drawHatchFill(polygon: DoubleArray, spec: TpHatchSpec, tone: TpTone, alpha: Double = 1.0) =
    TpNode(TpNodeType.HATCH, tone, doubleArrayOf(spec.angle, spec.spacing, spec.width, alpha) + polygon)
internal fun drawDiscs(discs: List<TpDisc>, tone: TpTone, alpha: Double = 1.0, gradient: Boolean = false) =
    TpNode(TpNodeType.DISCS, tone, doubleArrayOf(alpha) + discNums(discs), gradient = gradient)
internal fun drawRings(discs: List<TpDisc>, tone: TpTone, width: Double, alpha: Double = 1.0) =
    TpNode(TpNodeType.RINGS, tone, doubleArrayOf(width, alpha) + discNums(discs))
internal fun drawLines(lines: List<TpLine>, tone: TpTone, width: Double, alpha: Double = 1.0) =
    TpNode(TpNodeType.LINES, tone, doubleArrayOf(width, alpha) + lineNums(lines))
internal fun drawPolyline(points: DoubleArray, tone: TpTone, width: Double, alpha: Double = 1.0) =
    TpNode(TpNodeType.POLYLINE, tone, doubleArrayOf(width, alpha) + points)
internal fun drawConcentricDiamonds(cx: Double, cy: Double, rx: Double, ry: Double, rings: Int, tone: TpTone, alpha: Double = 1.0) =
    TpNode(TpNodeType.DIAMONDS, tone, doubleArrayOf(cx, cy, rx, ry, rings.toDouble(), alpha))
internal fun drawCrossMarks(marks: List<TpMark>, tone: TpTone, width: Double, alpha: Double = 1.0) =
    TpNode(TpNodeType.CROSSES, tone, doubleArrayOf(width, alpha) + marksNums(marks))
internal fun drawSquareMarks(marks: List<TpMark>, tone: TpTone, alpha: Double = 1.0) =
    TpNode(TpNodeType.SQUARES, tone, doubleArrayOf(alpha) + marksNums(marks))

/** 图形的包围盒中心：hatch / 圆点场 / 同心菱形以自身中心为枢轴（grow 横向展开、drift 呼吸）。 */
internal fun TpNode.pivot(): Pair<Double, Double> {
    fun boundsOf(xs: List<Double>, ys: List<Double>) = ((xs.min() + xs.max()) / 2) to ((ys.min() + ys.max()) / 2)
    return when (type) {
        TpNodeType.HATCH -> {
            val p = nums.copyOfRange(4, nums.size)
            if (p.isEmpty()) 0.0 to 0.0 else boundsOf(p.filterIndexed { i, _ -> i % 2 == 0 }, p.filterIndexed { i, _ -> i % 2 == 1 })
        }
        TpNodeType.DISCS, TpNodeType.RINGS -> {
            val o = if (type == TpNodeType.DISCS) 1 else 2
            val n = (nums.size - o) / 3
            if (n == 0) 0.0 to 0.0 else {
                var a = Double.POSITIVE_INFINITY; var b = Double.NEGATIVE_INFINITY; var c = Double.POSITIVE_INFINITY; var d = Double.NEGATIVE_INFINITY
                for (i in 0 until n) {
                    val x = nums[o + i * 3]; val y = nums[o + i * 3 + 1]; val r = nums[o + i * 3 + 2]
                    a = min(a, x - r); b = max(b, x + r); c = min(c, y - r); d = max(d, y + r)
                }
                ((a + b) / 2) to ((c + d) / 2)
            }
        }
        TpNodeType.DIAMONDS -> nums[0] to nums[1]
        else -> 0.0 to 0.0
    }
}

// ───────────────────────────── temperaCompositionContext.ts ─────────────────────────────

/** 入场参数：delay / span 是 shot 时长的比例，由色块动画换算成秒。 */
internal class TpBlockOptions(
    val alpha: Double? = null,
    val enterDX: Double? = null,
    val enterDY: Double? = null,
    val delay: Double? = null,
    val span: Double? = null,
    val drift: Boolean = false,
    val grow: Boolean = false,
)

internal class TpGroup(val id: Int, val rotation: Double, val x: Double, val y: Double)

internal class TpItem(val node: TpNode, val options: TpBlockOptions, val parent: TpGroup?)

internal class TpCompositionContext(
    val kind: String,
    val decor: TpDecorLite,
    val width: Double,
    val height: Double,
    val seed: Long,
    val showDecor: Boolean,
    /** 满版形状要越出画框的余量（构图整个 shot 都沿 flow 漂移）。 */
    val bleed: Double,
    val flowAngle: Double,
    /** 渐变色模式才为 true：色块填充改四色渐变。 */
    val gradient: Boolean,
) {
    val palette = TpPaletteRef
    val items = ArrayList<TpItem>()
    val groups = ArrayList<TpGroup>()

    fun add(
        node: TpNode,
        alpha: Double? = null,
        enterDX: Double? = null,
        enterDY: Double? = null,
        delay: Double? = null,
        span: Double? = null,
        drift: Boolean = false,
        grow: Boolean = false,
        parent: TpGroup? = null,
    ) {
        items += TpItem(node, TpBlockOptions(alpha, enterDX, enterDY, delay, span, drift, grow), parent)
    }

    fun createGroup(rotation: Double, x: Double, y: Double): TpGroup = TpGroup(groups.size, rotation, x, y).also { groups += it }
}

/** 构图只读 decor 里的这几项（motif / 网线角 / 贯穿线条数 / 涂鸦种子）。 */
internal class TpDecorLite(val motif: String, val hatchAngle: Double, val crossCount: Int, val scribbleSeed: Long)

internal typealias TpDrawer = (TpCompositionContext) -> Unit

// ───────────────────────────── temperaCompositions.ts ─────────────────────────────

private val COMPOSITIONS: Map<String, TpDrawer> by lazy {
    TEMPERA_SPLIT_COMPOSITIONS + TEMPERA_BAND_COMPOSITIONS + TEMPERA_FRAME_COMPOSITIONS + TEMPERA_POSTER_COMPOSITIONS +
        TEMPERA_SPARSE_COMPOSITIONS + TEMPERA_CINEMA_COMPOSITIONS + TEMPERA_CHARM_COMPOSITIONS +
        TEMPERA_APERTURE_COMPOSITIONS + TEMPERA_SIGNAL_COMPOSITIONS + TEMPERA_CORRIDOR_COMPOSITIONS +
        TEMPERA_MONOLITH_COMPOSITIONS + TEMPERA_TERRAIN_COMPOSITIONS + TEMPERA_MONOGATARI_COMPOSITIONS
}

internal fun temperaCompositionImplemented(kind: String) = kind in COMPOSITIONS

internal fun resolveTemperaComposition(kind: String): TpDrawer = COMPOSITIONS[kind] ?: COMPOSITIONS.getValue("duo-split")

private fun addCrossingLines(ctx: TpCompositionContext) {
    val lines = buildCrossingLines(ctx.seed, 31, ctx.width, ctx.height, ctx.decor.crossCount)
    if (lines.isEmpty()) return
    ctx.add(drawLines(lines, ctx.palette.tone4, 1.3, 0.6), delay = 0.14, span = 0.6, enterDX = ctx.width * 0.25)
}

private fun addMotif(ctx: TpCompositionContext) {
    val width = ctx.width; val height = ctx.height; val palette = ctx.palette; val decor = ctx.decor
    val cornerX = if (th01(ctx.seed, 61, 7) > 0.5) width * 0.14 else width * 0.86
    val cornerY = if (th01(ctx.seed, 63, 7) > 0.5) height * 0.18 else height * 0.82
    when (decor.motif) {
        "diamonds" -> ctx.add(drawConcentricDiamonds(cornerX, cornerY, 34.0, 34.0, 3, palette.tone4, 0.8), delay = 0.34, drift = true)
        "hatch-twin" -> {
            val spec = buildHatchSpec(ctx.seed, 67).copy(angle = decor.hatchAngle)
            for (index in 0..1) {
                val box = rectPolygon(cornerX - 34 + index * 46, cornerY - 26, 38.0, 38.0)
                ctx.add(drawHatchFill(box, if (index == 0) spec else spec.copy(spacing = spec.spacing * 0.55), palette.tone4, 0.75), delay = 0.34 + index * 0.05, grow = true)
                ctx.add(drawPolygonOutline(box, palette.tone4, 1.2, 0.6), delay = 0.38 + index * 0.05)
            }
        }
        "band-cross" -> ctx.add(
            drawCrossMarks(buildCrossRow(ctx.seed, 71, cornerX - width * 0.1, cornerY, 5, width * 0.05, 8.0, decor.hatchAngle * 0.4), palette.tone4, 2.0, 0.8),
            delay = 0.34, span = 0.5,
        )
        "poster-diamond" -> ctx.add(
            drawPolygonFill(diamondPolygon(if (cornerX < width / 2) -width * 0.04 else width * 1.04, cornerY, width * 0.14, height * 0.2), palette.tone3, 0.85),
            delay = 0.32, span = 0.55, enterDX = (if (cornerX < width / 2) -1 else 1) * width * 0.1,
        )
        else -> ctx.add(
            drawPolyline(buildScribblePath(decor.scribbleSeed, 73, cornerX, cornerY, min(width, height) * 0.08, 3.0), palette.tone4, 1.5, 0.72),
            delay = 0.34, span = 0.6,
        )
    }
}

/** drawTemperaComposition：构图本体 + 贯穿线 + motif（留白卡跳过共享装饰）。 */
internal fun drawTemperaComposition(ctx: TpCompositionContext) {
    resolveTemperaComposition(ctx.kind)(ctx)
    if (!com.guangyue.feature.player.lyrics.visualizer.temperaShotProfile(ctx.kind).sharedDecor) return
    addCrossingLines(ctx)
    if (ctx.showDecor) addMotif(ctx)
}
