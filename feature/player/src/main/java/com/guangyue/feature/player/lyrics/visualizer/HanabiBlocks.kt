package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * R7 花火⑥b：逐函数对译 Folia hanabi/text/blocks.ts（字底色块，12 种 BlockStyle）。
 * Pixi 的 Graphics 换成「形状指令表」（局部坐标，构建时生成一次）+ 每帧的 transform / alpha 状态；
 * 需要逐段画出的（outline 框线、弧形排的色带）在进度变化时重算指令（同 TS 的 lastKey）。随机流 createRng("<seed>:blocks")。
 * 金标 hanabi_text_golden.txt（node 里用假 Pixi 跑真的 createBlocks 后读每个 Graphics 的状态）。
 */

internal class HbBlockLayoutParams(val tracking: Double, val wordGap: Double, val lineHeight: Double, val minAdvance: Double, val stairStep: Double, val padding: Double)

internal fun hbBlockLayoutParams(style: String, hardShadow: Boolean): HbBlockLayoutParams {
    val b = when (style) {
        "tile" -> doubleArrayOf(0.14, 0.12, 1.3, 0.86, 0.0, 0.1)
        "band" -> doubleArrayOf(0.04, 0.14, 1.42, 0.0, 0.0, 0.3)
        "slant" -> doubleArrayOf(0.04, 0.72, 1.42, 0.0, 0.0, 0.36)
        "double" -> doubleArrayOf(0.04, 0.68, 1.5, 0.0, 0.0, 0.4)
        "pill" -> doubleArrayOf(0.04, 0.6, 1.42, 0.0, 0.0, 0.4)
        "split" -> doubleArrayOf(0.04, 0.58, 1.42, 0.0, 0.0, 0.3)
        "ticket" -> doubleArrayOf(0.05, 0.72, 1.48, 0.0, 0.0, 0.45)
        "marker" -> doubleArrayOf(0.03, 0.14, 1.3, 0.0, 0.0, 0.14)
        "outline" -> doubleArrayOf(0.04, 0.3, 1.55, 0.0, 0.0, 0.32)
        "circle" -> doubleArrayOf(0.24, 0.14, 1.4, 0.95, 0.0, 0.12)
        "stair" -> doubleArrayOf(0.06, 0.12, 1.6, 0.82, 0.16, 0.3)
        else -> doubleArrayOf(0.02, 0.12, 1.12, 0.0, 0.0, 0.1)
    }
    return HbBlockLayoutParams(b[0], b[1], b[2], b[3], b[4], b[5] + if (hardShadow) 0.08 else 0.0)
}

/** 字色：实心色块上用 ink，没有色块 / 只有框线 / 荧光笔时用 paper。 */
internal fun hbBlockTextRole(style: String) = if (style == "none" || style == "outline" || style == "marker") "paper" else "ink"

internal fun hbDarken(color: Int, amount: Double): Int {
    val k = 1 - amount
    val r = jsRoundI(((color shr 16) and 0xff) * k)
    val g = jsRoundI(((color shr 8) and 0xff) * k)
    val b = jsRoundI((color and 0xff) * k)
    return (r shl 16) or (g shl 8) or b
}

private fun jsRoundI(v: Double): Int = kotlin.math.floor(v + 0.5).toInt()

internal class HbRunPoint(val x: Double, val y: Double, val nx: Double, val ny: Double, val s: Double)

internal class HbRunFrame(
    val cx: Double, val cy: Double, val angle: Double, val length: Double, val thickness: Double,
    val curved: Boolean, val points: List<HbRunPoint>,
)

/** 一串字的色块框：沿行方向与法向投影所有字框取范围，两端加 padL、上下加 padT。 */
internal fun hbRunFrame(glyphs: List<HbPlacedGlyph>, indices: List<Int>, padL: Double, padT: Double): HbRunFrame? {
    val members = indices.map { glyphs[it] }.filter { !it.blank }
    if (members.isEmpty()) return null
    var sn = 0.0; var cs = 0.0
    members.forEach { sn += sin(it.angle); cs += cos(it.angle) }
    val angle = atan2(sn, cs)
    val tx = cos(angle); val ty = sin(angle)
    val nx = -ty; val ny = tx
    val origin = members[0]
    var minT = Double.POSITIVE_INFINITY; var maxT = Double.NEGATIVE_INFINITY
    var minN = Double.POSITIVE_INFINITY; var maxN = Double.NEGATIVE_INFINITY
    var curved = false
    members.forEach { g ->
        if (abs(atan2(sin(g.angle - angle), cos(g.angle - angle))) > 0.08) curved = true
        val px = g.x - origin.x; val py = g.y - origin.y
        val pt = px * tx + py * ty
        val pn = px * nx + py * ny
        minT = min(minT, pt - g.advance / 2); maxT = max(maxT, pt + g.advance / 2)
        minN = min(minN, pn - g.size * 0.56); maxN = max(maxN, pn + g.size * 0.56)
    }
    val midT = (minT + maxT) / 2
    val midN = (minN + maxN) / 2
    val thickness = maxN - minN + padT * 2
    var points: List<HbRunPoint> = emptyList()
    if (curved) {
        class Raw(val x: Double, val y: Double, val nx: Double, val ny: Double)
        fun edge(g: HbPlacedGlyph, side: Int, extra: Double) = Raw(
            g.x + cos(g.angle) * (g.advance / 2 + extra) * side,
            g.y + sin(g.angle) * (g.advance / 2 + extra) * side,
            -sin(g.angle), cos(g.angle),
        )
        val raw = arrayListOf(edge(members[0], -1, padL))
        for (k in 1 until members.size) {
            val a = edge(members[k - 1], 1, 0.0); val b = edge(members[k], -1, 0.0)
            raw += Raw((a.x + b.x) / 2, (a.y + b.y) / 2, (a.nx + b.nx) / 2, (a.ny + b.ny) / 2)
        }
        raw += edge(members.last(), 1, padL)
        var s = 0.0
        points = raw.mapIndexed { i, p ->
            if (i > 0) s += hypot(p.x - raw[i - 1].x, p.y - raw[i - 1].y)
            val len = hypot(p.nx, p.ny).let { if (it == 0.0) 1.0 else it }
            HbRunPoint(p.x, p.y, p.nx / len, p.ny / len, s)
        }
    }
    return HbRunFrame(origin.x + tx * midT + nx * midN, origin.y + ty * midT + ny * midN, angle, maxT - minT + padL * 2, thickness, curved, points)
}

// ─── 形状指令 ───

/** 色块的一笔：kind = RRECT / CIRCLE / RECT / POLY / TICKET / POLYLINE；color = -1 时用部件的 tint。 */
internal class HbShapeOp(val kind: Int, val f: DoubleArray, val color: Int = -1, val alpha: Double = 1.0) {
    companion object {
        const val RRECT = 0   // x, y, w, h, r
        const val CIRCLE = 1  // cx, cy, r
        const val RECT = 2    // x, y, w, h
        const val POLY = 3    // x0, y0, x1, y1, …
        const val TICKET = 4  // L, T, r
        const val POLYLINE = 5 // width, x0, y0, x1, y1, …（描边）
    }
}

internal class HbBlockPart(val back: Boolean, val lag: Double, val ox: Double, val oy: Double, val slide: Double) {
    val ops = ArrayList<HbShapeOp>()
    var tint: Int = 0xffffff
    // 每帧状态（Pixi Graphics 的 visible / alpha / position / rotation / scale）。
    var visible = false
    var alpha = 1.0
    var x = 0.0
    var y = 0.0
    var rotation = 0.0
    var sx = 1.0
    var sy = 1.0
}

internal class HbBlockPiece(
    val parts: List<HbBlockPart>,
    val anchor: String,
    val x: Double, val y: Double, val angle: Double, val length: Double, val thickness: Double, val unit: Double,
    val enterStart: Double, val enterDur: Double, val exitStart: Double, val exitEnd: Double,
    val rand: DoubleArray,
    var toCenterX: Double, var toCenterY: Double,
) {
    /** 逐帧重画：参数为可见区间 [a, b] 与厚度倍率，结果写回 parts[0].ops。 */
    var redraw: ((HbBlockPart, Double, Double, Double) -> Unit)? = null
    var lastKey = ""
}

private val SWEEP = setOf("band", "slant", "double", "marker", "outline")

internal class HbBlocksOptions(
    val style: String,
    val placement: HbTextPlacement,
    val wordBlank: List<Boolean>,
    val palette: HbPalette,
    val opacity: Double,
    val motion: Double,
    val exit: String,
    val seed: String,
    val enterStart: DoubleArray,
    val exitStart: DoubleArray,
    val exitEnd: DoubleArray,
    val centerX: Double,
    val centerY: Double,
)

internal class HbBlocks(private val o: HbBlocksOptions) {
    val style = o.style
    val pieces = ArrayList<HbBlockPiece>()
    private val glyphColor = HashMap<Int, Int>()
    private val colors: List<Int> = if (o.palette.blocks.isNotEmpty()) o.palette.blocks else listOf(o.palette.accent)
    private fun colorAt(i: Int) = colors[((i % colors.size) + colors.size) % colors.size]
    private fun second(i: Int) = if (colors.size > 1) colorAt(i + 1) else hbDarken(colorAt(i), 0.35)
    private val rng = lumiereRng("${o.seed}:blocks")
    private val perGlyph = style == "tile" || style == "circle" || style == "stair" || style == "marker"
    private val exitPose = HbGlyphPose()
    private val exitCtx = HbFxContext(0, 1, DoubleArray(4), 0.0, 0.0, 1.0, 0.0, 0.0, o.motion, 7.0)

    fun colorOf(glyph: Int): Int? = glyphColor[glyph]

    private class Times(val first: Double, val last: Double, val exitStart: Double, val exitEnd: Double)

    private fun timesOf(indices: List<Int>): Times {
        var first = Double.POSITIVE_INFINITY; var last = Double.NEGATIVE_INFINITY
        var es = Double.POSITIVE_INFINITY; var ee = Double.NEGATIVE_INFINITY
        indices.forEach { i ->
            val start = o.enterStart[i]
            if (!start.isFinite()) return@forEach
            first = min(first, start); last = max(last, start)
            es = min(es, o.exitStart[i]); ee = max(ee, o.exitEnd[i])
        }
        return Times(first, last, es, ee)
    }

    private class PartSpec(val back: Boolean = false, val lag: Double = 0.0, val ox: Double = 0.0, val oy: Double = 0.0, val slide: Double = 0.0)

    private fun newPiece(specs: List<PartSpec>, anchor: String, x: Double, y: Double, angle: Double, length: Double, thickness: Double, unit: Double,
                         enterStart: Double, enterDur: Double, exitStart: Double, exitEnd: Double): HbBlockPiece {
        val parts = specs.map { HbBlockPart(it.back, it.lag, it.ox, it.oy, it.slide) }
        val rand = doubleArrayOf(rng.next(), rng.next(), rng.next(), rng.next())
        val piece = HbBlockPiece(parts, anchor, x, y, angle, length, thickness, unit, enterStart, enterDur, exitStart, exitEnd, rand,
            (o.centerX - x) / max(1.0, unit), (o.centerY - y) / max(1.0, unit))
        pieces += piece
        return piece
    }

    init { build() }

    private fun build() {
        val glyphs = o.placement.glyphs
        val fontPx = o.placement.fontPx
        // ─── 逐字（tile / circle / stair / marker） ───
        val stairDrop = HashMap<Int, Double>()
        if (style == "stair") {
            val groups = LinkedHashMap<String, MutableList<Int>>()
            glyphs.forEachIndexed { i, g -> if (!g.blank) groups.getOrPut("${g.row}:${g.word}") { ArrayList() } += i }
            groups.values.forEach { indices ->
                val first = glyphs[indices[0]]
                val nx = -sin(first.angle); val ny = cos(first.angle)
                val bottoms = indices.map { val g = glyphs[it]; (g.x - first.x) * nx + (g.y - first.y) * ny + g.size * 0.56 }
                val lowest = bottoms.max()
                indices.forEachIndexed { k, i -> stairDrop[i] = lowest - bottoms[k] }
            }
        }
        if (perGlyph) {
            var ordinal = 0
            glyphs.forEachIndexed { index, glyph ->
                if (glyph.blank || !o.enterStart[index].isFinite()) return@forEachIndexed
                val color = if (style == "marker") colorAt(glyph.word) else colorAt(ordinal)
                ordinal += 1
                val start = o.enterStart[index]
                val es = start - (if (style == "marker") 0.0 else 0.05)
                val ed = if (style == "marker") 0.28 else 0.34
                val xs = o.exitStart[index]; val xe = o.exitEnd[index]
                val size = glyph.size
                if (style == "tile" || style == "circle") {
                    val piece = newPiece(listOf(PartSpec()), "center", glyph.x, glyph.y, glyph.rotation, glyph.boxW, glyph.boxH, size, es, ed, xs, xe)
                    val part = piece.parts[0]
                    if (style == "tile") {
                        val inset = size * 0.035
                        part.ops += HbShapeOp(HbShapeOp.RRECT, doubleArrayOf(-glyph.boxW / 2 + inset, -glyph.boxH / 2, glyph.boxW - inset * 2, glyph.boxH, size * 0.06))
                    } else {
                        part.ops += HbShapeOp(HbShapeOp.CIRCLE, doubleArrayOf(0.0, 0.0, max(size * 0.64, max(glyph.boxW, glyph.boxH) * 0.5)))
                    }
                    part.tint = color
                    glyphColor[index] = color
                    return@forEachIndexed
                }
                val tx = cos(glyph.angle); val ty = sin(glyph.angle)
                val nx = -ty; val ny = tx
                if (style == "stair") {
                    val h = size * 1.12
                    val depth = size * 0.3 + (stairDrop[index] ?: 0.0)
                    val piece = newPiece(listOf(PartSpec()), "bottom", glyph.x + nx * (h / 2 + depth), glyph.y + ny * (h / 2 + depth),
                        glyph.angle, glyph.advance, h + depth, size, es, ed, xs, xe)
                    val part = piece.parts[0]
                    part.ops += HbShapeOp(HbShapeOp.RECT, doubleArrayOf(-glyph.advance / 2, -(h + depth), glyph.advance, h + depth))
                    part.ops += HbShapeOp(HbShapeOp.RECT, doubleArrayOf(-glyph.advance / 2, -(h + depth), glyph.advance, size * 0.07), -1, 0.45)
                    part.tint = color
                    glyphColor[index] = color
                    return@forEachIndexed
                }
                val len = glyph.advance + size * 0.08
                val piece = newPiece(listOf(PartSpec()), "start", glyph.x - tx * len / 2, glyph.y - ty * len / 2, glyph.angle, len, size * 0.5, size, es, ed, xs, xe)
                val part = piece.parts[0]
                val top = size * 0.06; val bottom = size * 0.58; val wob = size * 0.06
                part.ops += HbShapeOp(HbShapeOp.POLY, doubleArrayOf(wob, top + wob * 0.5, len, top, len - wob, bottom, 0.0, bottom - wob * 0.4), -1, 0.92)
                part.tint = color
                glyphColor[index] = color
            }
        }
        // ─── 逐词（slant / double / pill / split / ticket） ───
        val perWord = style == "slant" || style == "double" || style == "pill" || style == "split" || style == "ticket"
        if (perWord) {
            class Group(val indices: MutableList<Int>, val word: Int)
            val groups = ArrayList<Group>()
            glyphs.forEachIndexed { index, glyph ->
                if (glyph.blank) return@forEachIndexed
                val punct = o.wordBlank.getOrNull(glyph.word) ?: false
                val last = groups.lastOrNull()
                val lastGlyph = last?.let { glyphs[it.indices.last()] }
                if (last != null && lastGlyph != null && lastGlyph.row == glyph.row && (last.word == glyph.word || punct)) {
                    last.indices += index
                    return@forEachIndexed
                }
                groups += Group(arrayListOf(index), glyph.word)
            }
            groups.forEachIndexed { ordinal, group ->
                val frame = hbRunFrame(glyphs, group.indices, fontPx * (if (style == "pill") 0.26 else 0.2), fontPx * 0.1) ?: return@forEachIndexed
                val times = timesOf(group.indices)
                if (!times.first.isFinite()) return@forEachIndexed
                val color = colorAt(ordinal)
                val color2 = second(ordinal)
                val len = frame.length; val th = frame.thickness
                val tx = cos(frame.angle); val ty = sin(frame.angle)
                val sweep = style == "slant" || style == "double"
                val es = times.first - 0.08
                val ed = if (sweep) times.last - times.first + 0.42 else 0.44
                val bx = if (sweep) frame.cx - tx * len / 2 else frame.cx
                val by = if (sweep) frame.cy - ty * len / 2 else frame.cy
                group.indices.forEach { glyphColor[it] = color }
                when (style) {
                    "slant" -> {
                        val piece = newPiece(listOf(PartSpec()), "start", bx, by, frame.angle, len, th, th / 1.3, es, ed, times.exitStart, times.exitEnd)
                        val sk = th * 0.36
                        piece.parts[0].ops += HbShapeOp(HbShapeOp.POLY, doubleArrayOf(sk / 2, -th / 2, len + sk / 2, -th / 2, len - sk / 2, th / 2, -sk / 2, th / 2))
                        piece.parts[0].tint = color
                    }
                    "double" -> {
                        val off = th * 0.13
                        val piece = newPiece(listOf(PartSpec(back = true, lag = 0.09, ox = off, oy = off * 1.1), PartSpec()), "start", bx, by, frame.angle, len, th, th / 1.3, es, ed, times.exitStart, times.exitEnd)
                        piece.parts.forEachIndexed { k, part ->
                            part.ops += HbShapeOp(HbShapeOp.RECT, doubleArrayOf(0.0, -th / 2, len, th))
                            part.tint = if (k == 0) color2 else color
                        }
                    }
                    "pill" -> {
                        val piece = newPiece(listOf(PartSpec()), "center", bx, by, frame.angle, len, th, th / 1.3, es, ed, times.exitStart, times.exitEnd)
                        piece.parts[0].ops += HbShapeOp(HbShapeOp.RRECT, doubleArrayOf(-len / 2, -th / 2, len, th, th / 2))
                        piece.parts[0].tint = color
                    }
                    "split" -> {
                        val piece = newPiece(listOf(PartSpec(slide = -1.0), PartSpec(slide = 1.0)), "center", bx, by, frame.angle, len, th, th / 1.3, es, ed, times.exitStart, times.exitEnd)
                        piece.parts[0].ops += HbShapeOp(HbShapeOp.RECT, doubleArrayOf(-len / 2, -th / 2, len, th / 2 + 0.5))
                        piece.parts[0].tint = color
                        piece.parts[1].ops += HbShapeOp(HbShapeOp.RECT, doubleArrayOf(-len / 2, 0.0, len, th / 2))
                        piece.parts[1].tint = color2
                    }
                    else -> {
                        val piece = newPiece(listOf(PartSpec()), "center", bx, by, frame.angle, len, th, th / 1.3, es, ed, times.exitStart, times.exitEnd)
                        val part = piece.parts[0]
                        val r = th * 0.2
                        part.ops += HbShapeOp(HbShapeOp.TICKET, doubleArrayOf(len, th, r))
                        val stubX = len / 2 - min(th * 0.62, len * 0.2)
                        var yy = -th / 2 + th * 0.14
                        while (yy < th / 2 - th * 0.1) {
                            part.ops += HbShapeOp(HbShapeOp.CIRCLE, doubleArrayOf(stubX, yy, max(0.8, th * 0.028)), 0x000000, 0.28)
                            yy += th * 0.16
                        }
                        part.tint = color
                    }
                }
            }
        }
        // ─── 逐行（band / outline） ───
        if (style == "band" || style == "outline") {
            o.placement.rows.forEachIndexed { rowIndex, row ->
                val padL = fontPx * (if (style == "band") 0.32 else 0.26)
                val padT = fontPx * (if (style == "band") 0.12 else 0.16)
                val frame = hbRunFrame(glyphs, row, padL, padT) ?: return@forEachIndexed
                val times = timesOf(row)
                if (!times.first.isFinite()) return@forEachIndexed
                val color = colorAt(rowIndex)
                row.forEach { if (!glyphs[it].blank) glyphColor[it] = color }
                val len = frame.length; val th = frame.thickness
                val tx = cos(frame.angle); val ty = sin(frame.angle)
                val es = times.first - 0.1
                val ed = times.last - times.first + 0.45
                if (style == "band" && frame.curved) {
                    val piece = newPiece(listOf(PartSpec()), "center", 0.0, 0.0, 0.0, len, th, th / 1.3, es, ed, times.exitStart, times.exitEnd)
                    piece.toCenterX = 0.0; piece.toCenterY = 0.0
                    val points = frame.points
                    val total = points.last().s.let { if (it == 0.0) 1.0 else it }
                    piece.redraw = { part, a, b, thick ->
                        part.ops.clear()
                        if (b > a) {
                            val sa = a * total; val sb = b * total
                            fun at(s: Double): HbRunPoint {
                                var k = 1
                                while (k < points.size - 1 && points[k].s < s) k += 1
                                val p0 = points[k - 1]; val p1 = points[k]
                                val f = if (p1.s > p0.s) hbC01((s - p0.s) / (p1.s - p0.s)) else 0.0
                                return HbRunPoint(p0.x + (p1.x - p0.x) * f, p0.y + (p1.y - p0.y) * f, p0.nx + (p1.nx - p0.nx) * f, p0.ny + (p1.ny - p0.ny) * f, s)
                            }
                            val spine = ArrayList<HbRunPoint>()
                            spine += at(sa); points.forEach { if (it.s > sa && it.s < sb) spine += it }; spine += at(sb)
                            val half = (th / 2) * thick
                            val pts = DoubleArray(spine.size * 4)
                            var w = 0
                            spine.forEach { pts[w++] = it.x - it.nx * half; pts[w++] = it.y - it.ny * half }
                            for (k in spine.indices.reversed()) { val p = spine[k]; pts[w++] = p.x + p.nx * half; pts[w++] = p.y + p.ny * half }
                            part.ops += HbShapeOp(HbShapeOp.POLY, pts)
                        }
                    }
                    piece.parts[0].tint = color
                    return@forEachIndexed
                }
                if (style == "band") {
                    val piece = newPiece(listOf(PartSpec()), "start", frame.cx - tx * len / 2, frame.cy - ty * len / 2, frame.angle, len, th, th / 1.3, es, ed, times.exitStart, times.exitEnd)
                    val part = piece.parts[0]
                    part.ops += HbShapeOp(HbShapeOp.RECT, doubleArrayOf(0.0, -th / 2, len, th), color)
                    if (colors.size > 1) part.ops += HbShapeOp(HbShapeOp.RECT, doubleArrayOf(0.0, th / 2 - th * 0.1, len, th * 0.1), second(rowIndex))
                    return@forEachIndexed
                }
                // outline：矩形框线从两个对角同时顺时针画出。
                val piece = newPiece(listOf(PartSpec()), "center", frame.cx, frame.cy, frame.angle, len, th, th / 1.3, es, ed, times.exitStart, times.exitEnd)
                val stroke = max(1.5, th * 0.07)
                val perimeter = 2 * (len + th)
                val corners = arrayOf(doubleArrayOf(-len / 2, -th / 2), doubleArrayOf(len / 2, -th / 2), doubleArrayOf(len / 2, th / 2), doubleArrayOf(-len / 2, th / 2))
                fun trace(part: HbBlockPart, startCorner: Int, from: Double, to: Double) {
                    var travelled = 0.0
                    val pts = ArrayList<Double>()
                    var started = false
                    var k = 0
                    while (k < 4 && travelled < to * perimeter) {
                        val a = corners[(startCorner + k) % 4]; val b = corners[(startCorner + k + 1) % 4]
                        val seg = hypot(b[0] - a[0], b[1] - a[1])
                        val s0 = max(from * perimeter, travelled)
                        val s1 = min(to * perimeter, travelled + seg)
                        if (s1 > s0) {
                            val f0 = (s0 - travelled) / seg; val f1 = (s1 - travelled) / seg
                            if (!started) { pts += a[0] + (b[0] - a[0]) * f0; pts += a[1] + (b[1] - a[1]) * f0; started = true }
                            pts += a[0] + (b[0] - a[0]) * f1; pts += a[1] + (b[1] - a[1]) * f1
                        }
                        travelled += seg
                        k += 1
                    }
                    if (started) part.ops += HbShapeOp(HbShapeOp.POLYLINE, (listOf(stroke) + pts).toDoubleArray())
                }
                piece.redraw = { part, a, b, _ ->
                    part.ops.clear()
                    if (b > a) {
                        trace(part, 0, a / 2, b / 2)
                        trace(part, 2, a / 2, b / 2)
                    }
                }
                piece.parts[0].tint = color
            }
        }
    }

    private fun exitContext(piece: HbBlockPiece, time: Double): HbFxContext {
        exitCtx.rand[0] = piece.rand[0]; exitCtx.rand[1] = piece.rand[1]; exitCtx.rand[2] = piece.rand[2]; exitCtx.rand[3] = piece.rand[3]
        exitCtx.toCenterX = piece.toCenterX; exitCtx.toCenterY = piece.toCenterY
        exitCtx.dirX = cos(piece.angle); exitCtx.dirY = sin(piece.angle)
        exitCtx.time = time
        return exitCtx
    }

    private fun key4(v: Double) = Math.round(v * 10000)

    fun update(time: Double) {
        for (piece in pieces) for (part in piece.parts) {
            val t = time - part.lag
            if (t < piece.enterStart || t >= piece.exitEnd) { part.visible = false; continue }
            val q = hbC01((t - piece.enterStart) / max(1e-3, piece.enterDur))
            val xq = if (piece.exitEnd > piece.exitStart) hbC01((t - piece.exitStart) / (piece.exitEnd - piece.exitStart)) else 0.0
            val tx = cos(piece.angle); val ty = sin(piece.angle)
            var sx = 1.0; var sy = 1.0; var rot = 0.0; var alpha = 1.0; var dx = 0.0; var dy = 0.0
            var a = 0.0; var b = 1.0
            when (style) {
                "tile" -> {
                    val s = hbEaseOutBack(q, 2.2)
                    sx = s; sy = s
                    rot = (piece.rand[0] - 0.5) * 0.6 * (1 - hbEaseOutCubic(q))
                    alpha = hbC01(q * 5)
                }
                "circle" -> {
                    val s = if (q >= 1) 1.0 else 1 - cos(q * PI * 3.5) * kotlin.math.exp(-5 * q) * (1 - q)
                    sx = s; sy = s
                    alpha = hbC01(q * 5)
                }
                "stair" -> { sy = max(0.0, hbEaseOutBack(q, 1.8)); alpha = hbC01(q * 6) }
                "marker" -> b = hbEaseOutCubic(q)
                "band" -> {
                    b = hbEaseOutQuart(q)
                    sy = if (q < 0.22) 0.14 else 0.14 + 0.86 * hbEaseOutBack((q - 0.22) / 0.78, 1.8)
                }
                "slant", "double" -> b = hbEaseOutExpo(q)
                "outline" -> b = hbEaseInOutCubic(q)
                "pill" -> {
                    sx = 0.2 + 0.8 * hbEaseOutBack(q, 1.4)
                    sy = max(0.0, hbEaseOutBack(hbC01(q * 1.5), 2.2))
                    alpha = hbC01(q * 4)
                }
                "split" -> {
                    val e = hbEaseOutExpo(q)
                    dx = tx * part.slide * (1 - e) * piece.length * 0.7
                    dy = ty * part.slide * (1 - e) * piece.length * 0.7
                    alpha = hbC01(q * 4)
                }
                "ticket" -> {
                    sy = max(0.0, hbEaseOutBack(q, 2.5))
                    sx = 0.7 + 0.3 * hbEaseOutCubic(q)
                    rot = -0.25 * (1 - hbEaseOutCubic(q))
                    alpha = hbC01(q * 4)
                }
            }
            if (xq > 0) {
                if (style in SWEEP) {
                    a = max(a, hbEaseInOutCubic(xq))
                    alpha *= if (o.exit == "blink" || o.exit == "dissolve") hbExitPose(o.exit, xq, exitContext(piece, t - piece.exitStart), exitPose).alpha
                    else 1 - hbC01((xq - 0.85) / 0.15)
                } else {
                    val pose = hbExitPose(o.exit, xq, exitContext(piece, t - piece.exitStart), exitPose)
                    dx += pose.dx * piece.unit
                    dy += pose.dy * piece.unit
                    rot += pose.rotation * (if (perGlyph) 1.0 else 0.4)
                    sx *= pose.scaleX
                    sy *= pose.scaleY
                    alpha *= pose.alpha
                }
            }
            if (b <= a || alpha <= 0.001) { part.visible = false; continue }
            part.visible = true
            part.alpha = alpha * o.opacity
            val redraw = piece.redraw
            if (redraw != null) {
                val key = "${key4(a)}:${key4(b)}:${key4(sy)}"
                if (key != piece.lastKey) { piece.lastKey = key; redraw(part, a, b, sy) }
                part.x = piece.x + dx; part.y = piece.y + dy
                part.rotation = piece.angle + rot
                part.sx = sx; part.sy = if (style == "outline") sy else 1.0
                continue
            }
            var px = piece.x + dx + part.ox
            var py = piece.y + dy + part.oy
            if (piece.anchor == "start") {
                px += tx * piece.length * a
                py += ty * piece.length * a
                sx *= b - a
            }
            part.x = px; part.y = py
            part.rotation = piece.angle + rot
            part.sx = sx; part.sy = sy
        }
    }
}
