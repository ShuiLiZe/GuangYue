// 凝彩构图：稀疏族（compositions/temperaSparseCompositions.ts）与遮幅族（compositions/temperaCinemaCompositions.ts），逐函数对译。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val quietLine: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val gridWidth = w * 0.7
    val gridX = (w - gridWidth) / 2
    listOf(0.38, 0.5, 0.62).forEachIndexed { index, ratio ->
        ctx.add(drawPolygonFill(rectPolygon(gridX, h * ratio, gridWidth, 1.0), p.line, 1.0, ctx.gradient),
            delay = index * 0.08, span = 0.6, enterDX = (if (index % 2 == 0) -1 else 1) * w * 0.2)
    }
    if (ctx.showDecor) {
        ctx.add(drawPolyline(buildScribblePath(ctx.decor.scribbleSeed, 47, w * 0.2, h * 0.26, min(w, h) * 0.09, 2.0), p.tone4, 1.6, 0.7), delay = 0.24, span = 0.6)
        val tuftX = w * 0.82; val tuftY = h * 0.74
        ctx.add(drawLines(List(6) { index ->
            val lean = (th01(ctx.decor.scribbleSeed, index, 59) - 0.5) * 34
            TpLine(tuftX + index * 7, tuftY, tuftX + index * 7 + lean, tuftY - 24 - index * 3)
        }, p.tone4, 1.4, 0.7), delay = 0.3, span = 0.55)
    }
}

private val starfieldDots: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val spacing = max(24.0, sqrt((w * h) / 900))
    val marks = buildDotGrid(w + b, h + b, spacing, 2.6)
    val dense = marks.filter { it.y > h * 0.45 }
    val sparse = marks.filter { it.y <= h * 0.45 && (it.x + it.y) % 3 < 1 }
    ctx.add(drawSquareMarks(dense, p.tone4, 0.45), span = 0.6, enterDY = h * 0.2)
    ctx.add(drawSquareMarks(sparse, p.tone4, 0.25), delay = 0.08, span = 0.6, enterDY = -h * 0.15)
    if (ctx.showDecor) ctx.add(drawLines(listOf(TpLine(-b, h * 0.45, w + b, h * 0.45)), p.tone4, 1.2, 0.5), delay = 0.2, span = 0.5, enterDX = w * 0.2)
}

private val rippleLines: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    for (index in 0 until 7) {
        val y = h * (0.16 + index * 0.11)
        val amplitude = h * (0.006 + index * 0.004)
        ctx.add(drawPolyline(buildWavyPath(ctx.seed, 89 + index, -b, w + b, y, amplitude, 26), p.tone4, if (index % 3 == 0) 2.0 else 1.1, 0.55),
            delay = index * 0.045, span = 0.6, enterDX = (if (index % 2 == 0) -1 else 1) * w * 0.15)
    }
}

private val hairGrid: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val columns = 6; val rows = 4
    val vertical = List(columns + 1) { i -> TpLine((w / columns) * i, -b, (w / columns) * i, h + b) }
    val horizontal = List(rows + 1) { i -> TpLine(-b, (h / rows) * i, w + b, (h / rows) * i) }
    ctx.add(drawLines(vertical, p.line, 1.0, 0.8), span = 0.6, enterDY = -h * 0.1)
    ctx.add(drawLines(horizontal, p.line, 1.0, 0.8), delay = 0.08, span = 0.6, enterDX = w * 0.1)
}

private val marginRule: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val fromLeft = th01(ctx.seed, 121, 127) > 0.5
    val x = if (fromLeft) w * 0.12 else w * 0.88
    ctx.add(drawPolygonFill(rectPolygon(x - 3, -b, 6.0, h + b * 2), p.tone4, 0.9, ctx.gradient), span = 0.6, enterDY = h * 0.15)
    if (ctx.showDecor) {
        ctx.add(drawSquareMarks(List(3) { i -> TpMark(x + (if (fromLeft) 22 else -22), h * (0.32 + i * 0.18), 7.0, 0.0) }, p.tone4, 0.8), delay = 0.22, drift = true)
    }
}

private val dotDrift: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val spacing = max(22.0, sqrt((w * h) / 1200))
    val marks = buildDotGrid(w + b, h + b, spacing, 2.4)
    val span = w + h
    val near = marks.filter { it.x + it.y > span * 0.5 }
    val far = marks.filter { it.x + it.y <= span * 0.5 && (it.x + it.y) % 2 < 1 }
    ctx.add(drawSquareMarks(near, p.tone4, 0.5), span = 0.6, enterDX = w * 0.12)
    ctx.add(drawSquareMarks(far, p.tone4, 0.24), delay = 0.08, span = 0.6, enterDX = -w * 0.12)
}

private val arcSweep: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val cx = w * (if (th01(ctx.seed, 131, 137) > 0.5) 1.15 else -0.15)
    val cy = h * 1.05
    val base = hypot(w, h) * 0.42
    for (index in 0 until 5) {
        ctx.add(drawPolygonOutline(circlePolygon(cx, cy, base + index * base * 0.22, 72), p.tone4, if (index % 2 == 0) 1.8 else 1.0, 0.55),
            delay = index * 0.05, span = 0.6, enterDY = h * 0.08)
    }
}

private val blankPage: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    ctx.add(drawLines(listOf(TpLine(w * 0.18, h * 0.7, w * 0.36, h * 0.7)), p.line, 1.4, 0.8), span = 0.6, enterDX = -w * 0.12)
    if (ctx.showDecor) {
        ctx.add(drawPolyline(buildScribblePath(ctx.decor.scribbleSeed, 139, w * 0.82, h * 0.26, min(w, h) * 0.06, 2.0), p.tone4, 1.4, 0.6), delay = 0.28, span = 0.6)
    }
}

internal val TEMPERA_SPARSE_COMPOSITIONS: Map<String, TpDrawer> = mapOf(
    "quiet-line" to quietLine, "starfield-dots" to starfieldDots, "ripple-lines" to rippleLines, "hair-grid" to hairGrid,
    "margin-rule" to marginRule, "dot-drift" to dotDrift, "arc-sweep" to arcSweep, "blank-page" to blankPage,
)

// ───────────────────────────── 遮幅族 ─────────────────────────────

private class TpWindow(val x: Double, val y: Double, val width: Double, val height: Double)

/** 按真实像素比例在画框里放一个给定宽高比的窗口（「正方形」在任何屏上都是方的）。 */
private fun fitWindow(width: Double, height: Double, aspect: Double, fill: Double): TpWindow {
    val ww = min(width * fill, height * fill * aspect)
    val wh = ww / aspect
    return TpWindow((width - ww) / 2, (height - wh) / 2, ww, wh)
}

private fun addMatte(ctx: TpCompositionContext, hole: TpWindow, tone: TpTone) {
    val w = ctx.width; val h = ctx.height; val b = ctx.bleed
    val bars = listOf(
        Triple(rectPolygon(-b, -b, w + b * 2, hole.y + b), 0.0, -h * 0.2),
        Triple(rectPolygon(-b, hole.y + hole.height, w + b * 2, h - hole.y - hole.height + b), 0.0, h * 0.2),
        Triple(rectPolygon(-b, hole.y, hole.x + b, hole.height), -w * 0.2, 0.0),
        Triple(rectPolygon(hole.x + hole.width, hole.y, w - hole.x - hole.width + b, hole.height), w * 0.2, 0.0),
    )
    bars.forEachIndexed { index, (poly, dx, dy) ->
        ctx.add(drawPolygonFill(poly, tone, 0.96, ctx.gradient), delay = index * 0.04, span = 0.5, enterDX = dx, enterDY = dy)
    }
    ctx.add(drawPolygonOutline(rectPolygon(hole.x, hole.y, hole.width, hole.height), ctx.palette.ink, 1.6, 0.5), delay = 0.2, span = 0.5)
}

private fun addWindowWash(ctx: TpCompositionContext, hole: TpWindow, alpha: Double) {
    val polygon = rectPolygon(hole.x, hole.y, hole.width, hole.height)
    ctx.add(drawPolygonFill(polygon, ctx.palette.tone1, alpha, ctx.gradient), span = 0.55)
    ctx.add(drawHatchFill(polygon, buildHatchSpec(ctx.seed, 149, 1.5), ctx.palette.tone4, 0.2), delay = 0.1, span = 0.6, grow = true)
}

private fun matte(ctx: TpCompositionContext, aspect: Double, fill: Double, washAlpha: Double = 0.35) {
    val hole = fitWindow(ctx.width, ctx.height, aspect, fill)
    addWindowWash(ctx, hole, washAlpha)
    addMatte(ctx, hole, ctx.palette.tone4)
    if (!ctx.showDecor) return
    val h = ctx.height
    ctx.add(drawLines(listOf(
        TpLine(hole.x, hole.y - h * 0.05, hole.x, hole.y - h * 0.02),
        TpLine(hole.x + hole.width, hole.y + hole.height + h * 0.02, hole.x + hole.width, hole.y + hole.height + h * 0.05),
    ), ctx.palette.paper, 2.0, 0.6), delay = 0.26, span = 0.5)
}

private val cinemaTwin: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val inset = h * 0.16
    val gutter = w * 0.04
    val left = TpWindow(w * 0.08, inset, w * 0.5, h - inset * 2)
    val right = TpWindow(left.x + left.width + gutter, inset + h * 0.1, w * 0.28, h - inset * 2 - h * 0.2)
    ctx.add(drawPolygonFill(rectPolygon(-b, -b, w + b * 2, h + b * 2), p.tone4, 0.96, ctx.gradient), span = 0.5)
    listOf(left, right).forEachIndexed { index, hole ->
        val polygon = rectPolygon(hole.x, hole.y, hole.width, hole.height)
        ctx.add(drawPolygonFill(polygon, p.tone1, if (index == 0) 0.4 else 0.85, ctx.gradient), delay = index * 0.08, span = 0.55, enterDX = (if (index == 0) -1 else 1) * w * 0.14)
        ctx.add(drawPolygonOutline(polygon, p.ink, 1.6, 0.5), delay = 0.16 + index * 0.05, span = 0.5)
    }
    if (ctx.showDecor) {
        ctx.add(drawHatchFill(rectPolygon(right.x, right.y, right.width, right.height), buildHatchSpec(ctx.seed, 151), p.tone4, 0.35), delay = 0.24, span = 0.6, grow = true)
    }
}

private fun jitteredFill(ctx: TpCompositionContext, base: Double) = base + (th01(ctx.seed, 157, 163) - 0.5) * 0.06

internal val TEMPERA_CINEMA_COMPOSITIONS: Map<String, TpDrawer> = mapOf(
    "cinema-scope" to { ctx -> matte(ctx, 2.39, jitteredFill(ctx, 0.88)) },
    "cinema-wide" to { ctx -> matte(ctx, 1.85, jitteredFill(ctx, 0.84)) },
    "cinema-academy" to { ctx -> matte(ctx, 1.33, jitteredFill(ctx, 0.78)) },
    "cinema-square" to { ctx -> matte(ctx, 1.0, jitteredFill(ctx, 0.72)) },
    "cinema-portrait" to { ctx -> matte(ctx, 0.75, jitteredFill(ctx, 0.72)) },
    "cinema-tall" to { ctx -> matte(ctx, 0.5625, jitteredFill(ctx, 0.72)) },
    "cinema-twin" to cinemaTwin,
)
