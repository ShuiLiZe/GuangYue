// 凝彩构图：框窗族（compositions/temperaFrameCompositions.ts），逐函数对译。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import kotlin.math.min

private fun TpCompositionContext.fullBleed() = rectPolygon(-bleed, -bleed, width + bleed * 2, height + bleed * 2)

private val frameWindow: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val cx = w / 2; val cy = h / 2; val rx = w * 0.42; val ry = h * 0.44
    ctx.add(drawHatchFill(diamondPolygon(cx, cy, rx * 0.78, ry * 0.78), buildHatchSpec(ctx.seed, 23, 1.4), p.tone2, 0.45), grow = true, span = 0.6)
    ctx.add(drawConcentricDiamonds(cx, cy, rx, ry, 3, p.ink, 0.9), delay = 0.08, enterDY = h * 0.06, span = 0.55)
    if (ctx.showDecor) {
        ctx.add(drawCrossMarks(buildCrossRow(ctx.seed, 29, w * 0.08, h * 0.14, 3, w * 0.045, 8.0), p.tone4, 1.8, 0.85), delay = 0.22)
        ctx.add(drawSquareMarks(buildDotRow(ctx.seed, 37, w * 0.92, h * 0.62, 4, h * 0.06, 7.0), p.tone4, 0.8), delay = 0.28)
    }
}

private val doubleFrame: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val offset = w * 0.05
    val box = rectPolygon(w * 0.16, h * 0.2, w * 0.68, h * 0.6)
    val shifted = rectPolygon(w * 0.16 + offset, h * 0.2 + offset * 0.6, w * 0.68, h * 0.6)
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone1, 0.9, ctx.gradient), span = 0.5)
    ctx.add(drawPolygonFill(shifted, p.tone3, 0.8, ctx.gradient), delay = 0.06, span = 0.55, enterDX = offset * 3)
    ctx.add(drawHatchFill(shifted, buildHatchSpec(ctx.seed, 43), p.tone4, 0.4), delay = 0.1, span = 0.55, grow = true)
    ctx.add(drawPolygonOutline(box, p.ink, 3.5, 0.9), delay = 0.14, span = 0.5, enterDY = -h * 0.08)
}

private val circleWindow: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val radius = min(w, h) * 0.34
    val circle = circlePolygon(w / 2, h / 2, radius)
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone3, 0.92, ctx.gradient), span = 0.5)
    ctx.add(drawHatchFill(ctx.fullBleed(), buildHatchSpec(ctx.seed, 47), p.tone4, 0.4), delay = 0.04, span = 0.6, grow = true)
    ctx.add(drawPolygonFill(circle, p.paper, 0.95, ctx.gradient), delay = 0.08, span = 0.55)
    ctx.add(drawPolygonOutline(circle, p.ink, 3.0, 0.9), delay = 0.14, span = 0.5)
    if (ctx.showDecor) ctx.add(drawPolygonOutline(circlePolygon(w / 2, h / 2, radius * 1.12), p.ink, 1.2, 0.55), delay = 0.2, drift = true)
}

private val ladderFrame: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val left = w * 0.14
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone1, 0.9, ctx.gradient), span = 0.5)
    for (index in 0 until 5) {
        val y = h * (0.18 + index * 0.14)
        val run = w * (0.06 + index * 0.03)
        ctx.add(drawLines(listOf(TpLine(left, y, left + run, y), TpLine(left, y, left, y + h * 0.14)), p.tone4, if (index % 2 == 0) 3.0 else 1.4, 0.85),
            delay = index * 0.05, span = 0.5, enterDX = -w * 0.1)
    }
    ctx.add(drawLines(listOf(TpLine(w * 0.9, -b, w * 0.9, h + b)), p.tone4, 1.4, 0.6), delay = 0.28, span = 0.5, enterDY = h * 0.2)
}

private val cornerBrackets: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val inset = min(w, h) * 0.12
    val arm = min(w, h) * 0.16
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone2, 0.9, ctx.gradient), span = 0.5)
    val corners = listOf(
        doubleArrayOf(inset, inset, 1.0, 1.0), doubleArrayOf(w - inset, inset, -1.0, 1.0),
        doubleArrayOf(inset, h - inset, 1.0, -1.0), doubleArrayOf(w - inset, h - inset, -1.0, -1.0),
    )
    corners.forEachIndexed { index, (x, y, sx, sy) ->
        ctx.add(drawLines(listOf(TpLine(x, y, x + sx * arm, y), TpLine(x, y, x, y + sy * arm)), p.ink, 3.5, 0.9),
            delay = index * 0.05, span = 0.5, enterDX = sx * w * 0.06, enterDY = sy * h * 0.06)
    }
    if (ctx.showDecor) {
        val dotted = th01(ctx.seed, 2, 53) > 0.5
        ctx.add(
            if (dotted) drawSquareMarks(buildDotRow(ctx.seed, 59, w * 0.5, h * 0.16, 3, w * 0.03, 6.0, 0.0), p.tone4, 0.8)
            else drawCrossMarks(buildCrossRow(ctx.seed, 61, w * 0.44, h * 0.84, 3, w * 0.04, 7.0), p.tone4, 1.8, 0.8),
            delay = 0.24, drift = true,
        )
    }
}

private val insetBox: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val box = rectPolygon(w * 0.14, h * 0.2, w * 0.72, h * 0.6)
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone1, 0.9, ctx.gradient), span = 0.5)
    ctx.add(drawPolygonFill(box, p.tone3, 0.55, ctx.gradient), delay = 0.05, span = 0.55, enterDY = h * 0.06)
    ctx.add(drawPolygonOutline(box, p.ink, 2.4, 0.85), delay = 0.1, span = 0.5)
}

private val bracketPair: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val inset = w * 0.16; val arm = w * 0.09; val top = h * 0.26; val bottom = h * 0.74
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone2, 0.9, ctx.gradient), span = 0.5)
    listOf(1, -1).forEachIndexed { index, side ->
        val x = if (side == 1) inset else w - inset
        ctx.add(drawLines(listOf(TpLine(x, top, x + side * arm, top), TpLine(x, top, x, bottom), TpLine(x, bottom, x + side * arm, bottom)), p.ink, 4.0, 0.9),
            delay = index * 0.07, span = 0.5, enterDX = side * w * 0.08)
    }
}

private val archWindow: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val radius = w * 0.24; val cx = w / 2; val shoulder = h * 0.42
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone3, 0.92, ctx.gradient), span = 0.5)
    val arch = circlePolygon(cx, shoulder, radius, 48)
    val body = rectPolygon(cx - radius, shoulder, radius * 2, h * 0.4)
    ctx.add(drawPolygonFill(arch, p.paper, 0.95, ctx.gradient), delay = 0.05, span = 0.55, enterDY = -h * 0.08)
    ctx.add(drawPolygonFill(body, p.paper, 0.95, ctx.gradient), delay = 0.05, span = 0.55, enterDY = h * 0.08)
    ctx.add(drawPolygonOutline(arch, p.ink, 2.4, 0.8), delay = 0.12, span = 0.5)
    ctx.add(drawPolygonOutline(body, p.ink, 2.4, 0.8), delay = 0.12, span = 0.5)
}

private val gridCells: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone1, 0.9, ctx.gradient), span = 0.5)
    val left = w * 0.12; val top = h * 0.18; val cw = (w * 0.76) / 3; val ch = (h * 0.64) / 3
    for (row in 0 until 3) for (column in 0 until 3) {
        val cell = rectPolygon(left + cw * column, top + ch * row, cw, ch)
        val index = row * 3 + column
        if (row == 1 && column == 1) ctx.add(drawPolygonFill(cell, p.tone4, 0.75, ctx.gradient), delay = index * 0.03, span = 0.5)
        ctx.add(drawPolygonOutline(cell, p.tone4, 1.2, 0.6), delay = index * 0.03, span = 0.5)
    }
}

private val keyhole: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val radius = min(w, h) * 0.19; val cx = w / 2; val cy = h * 0.36
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone4, 0.94, ctx.gradient), span = 0.5)
    val head = circlePolygon(cx, cy, radius, 48)
    val shaft = rectPolygon(cx - radius * 0.55, cy, radius * 1.1, h * 0.46)
    ctx.add(drawPolygonFill(head, p.paper, 0.96, ctx.gradient), delay = 0.05, span = 0.55)
    ctx.add(drawPolygonFill(shaft, p.paper, 0.96, ctx.gradient), delay = 0.08, span = 0.55, enterDY = h * 0.1)
    ctx.add(drawPolygonOutline(head, p.ink, 2.0, 0.7), delay = 0.14, span = 0.5)
}

internal val TEMPERA_FRAME_COMPOSITIONS: Map<String, TpDrawer> = mapOf(
    "frame-window" to frameWindow, "double-frame" to doubleFrame, "circle-window" to circleWindow, "ladder-frame" to ladderFrame,
    "corner-brackets" to cornerBrackets, "inset-box" to insetBox, "bracket-pair" to bracketPair, "arch-window" to archWindow,
    "grid-cells" to gridCells, "keyhole" to keyhole,
)
