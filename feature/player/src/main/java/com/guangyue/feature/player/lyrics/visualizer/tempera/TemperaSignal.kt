// 凝彩构图：仪表族（compositions/temperaSignalCompositions.ts），逐函数对译。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private fun addField(ctx: TpCompositionContext, tone: TpTone, alpha: Double = 0.92) {
    ctx.add(drawPolygonFill(rectPolygon(-ctx.bleed, -ctx.bleed, ctx.width + ctx.bleed * 2, ctx.height + ctx.bleed * 2), tone, alpha, ctx.gradient), span = 0.5)
}

private val CORNERS = listOf(
    doubleArrayOf(0.2, 0.32, 1.0, 1.0), doubleArrayOf(0.8, 0.32, -1.0, 1.0),
    doubleArrayOf(0.2, 0.68, 1.0, -1.0), doubleArrayOf(0.8, 0.68, -1.0, -1.0),
)

private val sightMark: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val cx = w * 0.5; val cy = h * 0.5
    addField(ctx, p.tone1)
    ctx.add(drawLines(listOf(TpLine(-b, cy, w + b, cy), TpLine(cx, -b, cx, h + b)), p.tone4, 1.4, 0.6), delay = 0.04, span = 0.55, enterDX = w * 0.1)
    ctx.add(drawPolygonFill(rectPolygon(w * 0.24, h * 0.36, w * 0.52, h * 0.28), p.tone3, 0.94, ctx.gradient), delay = 0.1, span = 0.55, enterDY = h * 0.06)
    ctx.add(drawPolygonOutline(rectPolygon(w * 0.2, h * 0.32, w * 0.6, h * 0.36), p.ink, 2.4, 0.85), delay = 0.16, span = 0.5)
    if (ctx.showDecor) {
        CORNERS.forEachIndexed { index, (fx, fy, sx, sy) ->
            val x = w * fx; val y = h * fy
            ctx.add(drawLines(listOf(TpLine(x, y, x + sx * w * 0.04, y), TpLine(x, y, x, y + sy * h * 0.05)), p.ink, 3.0, 0.8), delay = 0.2 + index * 0.03, span = 0.5)
        }
    }
}

private val dialScale: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    val cx = w * 0.5; val cy = h * 0.5
    addField(ctx, p.tone2)
    ctx.add(drawPolygonFill(annularSectorPolygon(cx, cy, unit * 0.34, unit * 0.44, 0.0, PI * 2 - 0.006, 60.0), p.tone4, 0.92, ctx.gradient), delay = 0.05, span = 0.6)
    val ticks = List(24) { index ->
        val angle = (index / 24.0) * PI * 2
        val inner = unit * (if (index % 6 == 0) 0.46 else 0.475)
        TpLine(cx + cos(angle) * inner, cy + sin(angle) * inner, cx + cos(angle) * unit * 0.5, cy + sin(angle) * unit * 0.5)
    }
    ctx.add(drawLines(ticks, p.ink, 1.6, 0.7), delay = 0.14, span = 0.55)
    val pointer = 0.4 + th01(ctx.seed, 5, 149) * 0.5
    ctx.add(drawPolygonFill(axisRect(cx + cos(pointer * PI * 2) * unit * 0.2, cy + sin(pointer * PI * 2) * unit * 0.2, unit * 0.4, unit * 0.035, pointer * PI * 2), p.ink, 0.9), delay = 0.2, span = 0.5)
}

private val chevronRun: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val focus = 2
    addField(ctx, p.tone1)
    for (index in 0 until 5) {
        val y = h * (0.02 + index * 0.24)
        val rise = h * 0.12; val t = h * 0.07
        val chevron = doubleArrayOf(-b, y, w * 0.5, y - rise, w + b, y, w + b, y + t, w * 0.5, y - rise + t, -b, y + t)
        ctx.add(drawPolygonFill(chevron, if (index == focus) p.tone4 else p.tone3, if (index == focus) 0.95 else 0.7, ctx.gradient), delay = index * 0.04, span = 0.55, enterDY = -h * 0.12)
    }
}

private val tallyColumn: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val x = w * 0.82
    addField(ctx, p.tone1)
    val marks = List(11) { index ->
        val long = index % 4 == 0
        TpLine(x, h * (0.1 + index * 0.075), x + w * (if (long) 0.12 else 0.06), h * (0.1 + index * 0.075))
    }
    ctx.add(drawLines(marks, p.tone4, 2.4, 0.75), delay = 0.06, span = 0.6, enterDX = w * 0.1)
    ctx.add(drawPolygonFill(rectPolygon(x - w * 0.02, h * 0.4, w * 0.16, h * 0.12), p.tone4, 0.95, ctx.gradient), delay = 0.16, span = 0.55, enterDX = w * 0.14)
    ctx.add(drawLines(listOf(TpLine(x, h * 0.06, x, h * 0.92)), p.ink, 1.6, 0.6), delay = 0.2, span = 0.5)
}

private val gridFocus: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val left = w * 0.08; val top = h * 0.12
    val cw = (w * 0.84) / 4; val ch = (h * 0.76) / 3
    fun cell(column: Int, row: Int) = rectPolygon(left + cw * column, top + ch * row, cw, ch)
    addCutField(ctx, p.tone2, listOf(cell(0, 0), cell(3, 2)))
    addHoleLip(ctx, cell(0, 0), 2.0, 0.7, 0.1)
    addHoleLip(ctx, cell(3, 2), 2.0, 0.7, 0.14)
    ctx.add(drawPolygonFill(cell(3, 0), p.tone4, 0.95, ctx.gradient), delay = 0.08, span = 0.55, enterDX = w * 0.08)
    val lines = ArrayList<TpLine>()
    for (column in 0..4) lines += TpLine(left + cw * column, top, left + cw * column, top + ch * 3)
    for (row in 0..3) lines += TpLine(left, top + ch * row, left + cw * 4, top + ch * row)
    ctx.add(drawLines(lines, p.tone4, 1.2, 0.6), delay = 0.16, span = 0.55)
}

private val axisCaps: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    val cx = w * 0.5; val cy = h * 0.42
    val bar = axisRect(cx, cy, flowSpan(ctx), unit * 0.22, ctx.flowAngle)
    val port = circlePolygon(cx, cy, unit * 0.08, 32)
    addCutField(ctx, p.tone1, listOf(port), alpha = 0.92)
    ctx.add(drawPolygonFillWithHoles(bar, listOf(port), p.tone4, 0.94, ctx.gradient), delay = 0.05, span = 0.6)
    ctx.add(drawRings(listOf(TpDisc(cx, cy, unit * 0.08)), p.ink, 2.4, 0.85), delay = 0.12, span = 0.5)
    listOf(1, -1).forEachIndexed { index, side ->
        val distance = unit * 0.46
        val capX = cx + cos(ctx.flowAngle) * distance * side
        val capY = cy + sin(ctx.flowAngle) * distance * side
        ctx.add(drawPolygonFill(axisRect(capX, capY, unit * 0.1, unit * 0.34, ctx.flowAngle), p.ink, 0.85), delay = 0.16 + index * 0.04, span = 0.5)
    }
}

private val strobeSlats: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    addField(ctx, p.tone1)
    val widths = listOf(0.06, 0.03, 0.09, 0.04, 0.34, 0.04, 0.08, 0.03, 0.06)
    var cursor = w * 0.03
    widths.forEachIndexed { index, fraction ->
        val slat = rectPolygon(cursor, -b, w * fraction, h + b * 2)
        val wide = fraction > 0.2
        ctx.add(drawPolygonFill(slat, if (wide) p.tone2 else p.tone4, if (wide) 0.9 else 0.85, ctx.gradient), delay = index * 0.03, span = 0.55, enterDY = (if (index % 2 == 0) 1 else -1) * h * 0.14)
        cursor += w * (fraction + 0.02)
    }
}

private val offsetPlate: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    val step = unit * 0.06
    val holeX = w * 0.72; val holeY = h * 0.36
    val hole = circlePolygon(holeX, holeY, unit * 0.05, 28)
    addCutField(ctx, p.tone1, listOf(hole), alpha = 0.9)
    listOf(p.tone2, p.tone3, p.tone4).forEachIndexed { index, tone ->
        val plate = rectPolygon(w * 0.2 + step * index, h * 0.2 + step * index, w * 0.5, h * 0.5)
        ctx.add(drawPolygonFillWithHoles(plate, listOf(hole), tone, if (index == 2) 0.95 else 0.8, ctx.gradient), delay = index * 0.06, span = 0.55, enterDX = -step * 2, enterDY = -step)
    }
    ctx.add(drawRings(listOf(TpDisc(holeX, holeY, unit * 0.05)), p.ink, 2.0, 0.8), delay = 0.22, span = 0.5)
}

private val radialComb: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val hubX = w * 1.06; val hubY = h * 1.1
    val reach = hypot(w, h) * 1.3
    addField(ctx, p.tone2)
    val rays = List(11) { index ->
        val angle = PI + 0.16 + index * 0.062
        TpLine(hubX, hubY, hubX + cos(angle) * reach, hubY + sin(angle) * reach)
    }
    ctx.add(drawLines(rays, p.tone4, 2.6, 0.8), delay = 0.06, span = 0.6)
    val start = PI + 0.44; val end = PI + 0.56
    ctx.add(drawPolygonFill(doubleArrayOf(hubX, hubY, hubX + cos(start) * reach, hubY + sin(start) * reach, hubX + cos(end) * reach, hubY + sin(end) * reach), p.tone4, 0.95, ctx.gradient), delay = 0.14, span = 0.6)
    if (ctx.showDecor) {
        ctx.add(drawHatchFill(rectPolygon(w * 0.06, h * 0.08, w * 0.2, h * 0.14), buildHatchSpec(ctx.seed, 151), p.tone4, 0.4), delay = 0.24, span = 0.6, grow = true)
    }
}

private val bracketTarget: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val box = rectPolygon(w * 0.28, h * 0.54, w * 0.44, h * 0.34)
    addCutField(ctx, p.tone3, listOf(box))
    addHoleLip(ctx, box, 2.4, 0.85, 0.08)
    val arm = min(w, h) * 0.08
    listOf(
        doubleArrayOf(0.28, 0.54, 1.0, 1.0), doubleArrayOf(0.72, 0.54, -1.0, 1.0),
        doubleArrayOf(0.28, 0.88, 1.0, -1.0), doubleArrayOf(0.72, 0.88, -1.0, -1.0),
    ).forEachIndexed { index, (fx, fy, sx, sy) ->
        val x = w * fx + sx * w * 0.03
        val y = h * fy + sy * h * 0.04
        ctx.add(drawLines(listOf(TpLine(x, y, x + sx * arm, y), TpLine(x, y, x, y + sy * arm)), p.ink, 3.5, 0.9), delay = 0.12 + index * 0.04, span = 0.5, enterDX = sx * w * 0.05)
    }
    ctx.add(drawLines(listOf(TpLine(w * 0.1, h * 0.44, w * 0.9, h * 0.44)), p.tone4, 1.6, 0.6), delay = 0.24, span = 0.5, enterDX = -w * 0.1)
}

internal val TEMPERA_SIGNAL_COMPOSITIONS: Map<String, TpDrawer> = mapOf(
    "sight-mark" to sightMark, "dial-scale" to dialScale, "chevron-run" to chevronRun, "tally-column" to tallyColumn,
    "grid-focus" to gridFocus, "axis-caps" to axisCaps, "strobe-slats" to strobeSlats, "offset-plate" to offsetPlate,
    "radial-comb" to radialComb, "bracket-target" to bracketTarget,
)
