// 凝彩构图：镂空公共件（compositions/temperaCutout.ts）与镂空族（compositions/temperaApertureCompositions.ts），逐函数对译。
// 洞是真透明的：舞台画色块时用 EvenOdd 把洞挖掉，露出底下的背景层。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

internal class TpPoint(val x: Double, val y: Double)

internal fun fieldPolygon(ctx: TpCompositionContext) = rectPolygon(-ctx.bleed, -ctx.bleed, ctx.width + ctx.bleed * 2, ctx.height + ctx.bleed * 2)

internal fun addCutField(ctx: TpCompositionContext, tone: TpTone, holes: List<DoubleArray>, alpha: Double? = null, delay: Double? = null, span: Double? = null) {
    ctx.add(drawPolygonFillWithHoles(fieldPolygon(ctx), holes, tone, alpha ?: 0.94, ctx.gradient), delay = delay ?: 0.0, span = span ?: 0.5)
}

internal fun addHoleLip(ctx: TpCompositionContext, hole: DoubleArray, width: Double = 2.4, alpha: Double = 0.85, delay: Double = 0.1) {
    ctx.add(drawPolygonOutline(hole, ctx.palette.ink, width, alpha), delay = delay, span = 0.5)
}

internal fun axisRect(cx: Double, cy: Double, length: Double, width: Double, angle: Double) =
    rotatePolygon(rectPolygon(cx - length / 2, cy - width / 2, length, width), cx, cy, angle)

internal fun channelAxis(ctx: TpCompositionContext): Double {
    val axis = (if (sin(ctx.flowAngle) >= 0) 1 else -1) * PI / 2
    return axis + (ctx.flowAngle - axis) * 0.5
}

internal fun acrossFlow(angle: Double): TpPoint {
    val across = angle + PI / 2
    val sign = if (cos(across) >= 0) 1 else -1
    return TpPoint(cos(across) * sign, sin(across) * sign)
}

internal fun acrossPoint(ctx: TpCompositionContext, offset: Double): TpPoint {
    val a = acrossFlow(channelAxis(ctx))
    return TpPoint(ctx.width / 2 + a.x * offset, ctx.height / 2 + a.y * offset)
}

internal fun flowPoint(ctx: TpCompositionContext, distance: Double, offset: Double): TpPoint {
    val base = acrossPoint(ctx, offset)
    val angle = channelAxis(ctx)
    return TpPoint(base.x + cos(angle) * distance, base.y + sin(angle) * distance)
}

internal fun flowSpan(ctx: TpCompositionContext) = hypot(ctx.width, ctx.height) * 1.6

private val irisHole: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    val cx = w * 0.68; val cy = h * 0.42; val radius = unit * 0.3
    val hole = circlePolygon(cx, cy, radius, 56)
    addCutField(ctx, p.tone3, listOf(hole))
    addHoleLip(ctx, hole, 3.0, 0.9, 0.08)
    ctx.add(drawPolygonFill(rectPolygon(-ctx.bleed, cy - unit * 0.035, cx - radius * 0.7 + ctx.bleed, unit * 0.07), p.tone4, 0.9, ctx.gradient), delay = 0.14, span = 0.55, enterDX = -w * 0.12)
    if (ctx.showDecor) ctx.add(drawRings(listOf(TpDisc(cx, cy, radius * 1.14)), p.tone4, 1.4, 0.6), delay = 0.2, span = 0.5, drift = true)
}

private val slotRail: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val top = h * 0.22
    val slot = rectPolygon(-b, top, w + b * 2, h * 0.16)
    addCutField(ctx, p.tone4, listOf(slot))
    addHoleLip(ctx, slot, 2.4, 0.8, 0.1)
    ctx.add(drawPolygonFill(rectPolygon(w * 0.66, top - h * 0.06, w * 0.12, h * 0.28), p.tone2, 0.95, ctx.gradient), delay = 0.16, span = 0.55, enterDY = -h * 0.1)
    ctx.add(drawLines(listOf(TpLine(-b, top + h * 0.22, w + b, top + h * 0.22)), p.tone2, 1.4, 0.55), delay = 0.22, span = 0.5, enterDX = w * 0.14)
}

private val punchRow: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val radius = min(w, h) * 0.028
    val holes = ArrayList<DoubleArray>()
    for (index in 0 until 9) {
        val x = w * (0.08 + index * 0.105)
        holes += circlePolygon(x, h * 0.14, radius, 20)
        holes += circlePolygon(x, h * 0.86, radius, 20)
    }
    addCutField(ctx, p.tone3, holes)
    listOf(0.22, 0.78).forEachIndexed { index, y ->
        ctx.add(drawLines(listOf(TpLine(0.0, h * y, w, h * y)), p.tone4, 1.6, 0.6), delay = 0.12 + index * 0.04, span = 0.5, enterDX = (if (index == 0) 1 else -1) * w * 0.12)
    }
}

private val filmGate: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val gate = rectPolygon(w * 0.2, h * 0.1, w * 0.6, h * 0.52)
    val holes = arrayListOf(gate)
    for (index in 0 until 4) {
        val y = h * (0.14 + index * 0.13)
        holes += rectPolygon(w * 0.08, y, w * 0.05, h * 0.06)
        holes += rectPolygon(w * 0.87, y, w * 0.05, h * 0.06)
    }
    addCutField(ctx, p.tone4, holes)
    addHoleLip(ctx, gate, 3.0, 0.9, 0.08)
    ctx.add(drawHatchFill(rectPolygon(w * 0.2, h * 0.68, w * 0.6, h * 0.1), buildHatchSpec(ctx.seed, 131, 1.2), p.tone2, 0.5), delay = 0.18, span = 0.6, grow = true)
}

private val crossVent: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val cx = w * 0.5; val cy = h * 0.38
    val arm = min(w, h) * 0.3; val bar = min(w, h) * 0.1
    val cross = doubleArrayOf(
        cx - bar, cy - arm, cx + bar, cy - arm, cx + bar, cy - bar,
        cx + arm, cy - bar, cx + arm, cy + bar, cx + bar, cy + bar,
        cx + bar, cy + arm, cx - bar, cy + arm, cx - bar, cy + bar,
        cx - arm, cy + bar, cx - arm, cy - bar, cx - bar, cy - bar,
    )
    addCutField(ctx, p.tone3, listOf(cross))
    addHoleLip(ctx, cross, 2.6, 0.85, 0.1)
    if (ctx.showDecor) ctx.add(drawRings(listOf(TpDisc(cx, cy, arm * 1.24)), p.tone4, 1.2, 0.5), delay = 0.2, span = 0.5)
}

private val louvre: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val holes = List(6) { index ->
        val y = h * (0.08 + index * 0.1)
        val lean = h * 0.03
        val t = h * (0.05 - index * 0.004)
        doubleArrayOf(-b, y, w + b, y - lean, w + b, y - lean + t, -b, y + t)
    }
    addCutField(ctx, p.tone4, holes)
    ctx.add(drawPolygonFill(rectPolygon(w * 0.1, h * 0.72, w * 0.08, h * 0.2), p.tone2, 0.9, ctx.gradient), delay = 0.2, span = 0.55, enterDY = h * 0.1)
}

private val ringEye: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    val cx = w * 0.5; val cy = h * 0.5
    val inner = unit * 0.31; val outer = unit * 0.44
    val segments = 12; val gap = 0.03
    val holes = List(segments) { index ->
        val start = (index.toDouble() / segments) * PI * 2 + gap
        val end = ((index + 1).toDouble() / segments) * PI * 2 - gap
        annularSectorPolygon(cx, cy, inner, outer, start, end, 8.0)
    }
    addCutField(ctx, p.tone3, holes)
    ctx.add(drawRings(listOf(TpDisc(cx, cy, inner), TpDisc(cx, cy, outer)), p.ink, 2.4, 0.85), delay = 0.12, span = 0.5)
}

private val notchStack: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    val holes = List(4) { index ->
        val size = unit * (0.26 - index * 0.05)
        rectPolygon(w * (0.6 + index * 0.06), h * (0.1 + index * 0.19), size, size)
    }
    addCutField(ctx, p.tone4, holes)
    holes.forEachIndexed { index, hole -> addHoleLip(ctx, hole, 2.0, 0.75, 0.1 + index * 0.04) }
}

private val wedgeGap: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val apexX = w * (0.4 + th01(ctx.seed, 3, 137) * 0.2)
    val wedge = doubleArrayOf(w * 0.1, -b, w * 0.92, -b, apexX, h * 0.56)
    addCutField(ctx, p.tone3, listOf(wedge))
    addHoleLip(ctx, wedge, 3.0, 0.9, 0.08)
    ctx.add(drawLines(listOf(TpLine(apexX, h * 0.58, apexX, h + b)), p.tone4, 1.6, 0.6), delay = 0.18, span = 0.5, enterDY = h * 0.12)
}

private val dotSieve: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    val holes = ArrayList<DoubleArray>()
    for (row in 0 until 6) for (column in 0 until 5) {
        val density = 1 - column / 4.5
        if (th01(ctx.seed, row * 9 + column, 139) > density) continue
        val x = w * (0.06 + column * 0.105) + (if (row % 2 == 0) 0.0 else w * 0.05)
        holes += circlePolygon(x, h * (0.1 + row * 0.16), unit * 0.045 * density + unit * 0.012, 18)
    }
    addCutField(ctx, p.tone2, holes, alpha = 0.92)
}

internal val TEMPERA_APERTURE_COMPOSITIONS: Map<String, TpDrawer> = mapOf(
    "iris-hole" to irisHole, "slot-rail" to slotRail, "punch-row" to punchRow, "film-gate" to filmGate,
    "cross-vent" to crossVent, "louvre-slats" to louvre, "ring-eye" to ringEye, "notch-stack" to notchStack,
    "wedge-gap" to wedgeGap, "dot-sieve" to dotSieve,
)
