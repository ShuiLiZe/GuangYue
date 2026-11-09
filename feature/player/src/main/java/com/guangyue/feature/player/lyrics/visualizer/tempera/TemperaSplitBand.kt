// 凝彩构图：分割族（compositions/temperaSplitCompositions.ts）与色带族（compositions/temperaBandCompositions.ts），逐函数对译。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import kotlin.math.PI

private class Panel(val polygon: DoubleArray, val tone: TpTone, val enterDX: Double, val enterDY: Double)

private fun addPanels(ctx: TpCompositionContext, panels: List<Panel>, hatchIndex: Int) {
    val hatch = buildHatchSpec(ctx.seed, 5)
    panels.forEachIndexed { index, panel ->
        ctx.add(drawPolygonFill(panel.polygon, panel.tone, 0.96, ctx.gradient), delay = index * 0.05, span = 0.5, enterDX = panel.enterDX, enterDY = panel.enterDY)
        if (index != hatchIndex) return@forEachIndexed
        ctx.add(drawHatchFill(panel.polygon, hatch, ctx.palette.tone4, 0.5), delay = index * 0.05 + 0.06, span = 0.5, grow = true)
    }
}

private fun addSeam(ctx: TpCompositionContext, polygon: DoubleArray, delay: Double) {
    ctx.add(drawPolygonFill(polygon, ctx.palette.ink, 0.85, ctx.gradient), delay = delay, span = 0.5)
}

private val duoSplit: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val horizontal = th01(ctx.seed, 1, 3) > 0.5
    addPanels(ctx, if (horizontal) listOf(
        Panel(rectPolygon(-b, -b, w + b * 2, h * 0.52 + b), p.tone1, 0.0, -h * 0.55),
        Panel(rectPolygon(-b, h * 0.52, w + b * 2, h * 0.48 + b), p.tone3, 0.0, h * 0.55),
    ) else listOf(
        Panel(rectPolygon(-b, -b, w * 0.5 + b, h + b * 2), p.tone1, -w * 0.5, 0.0),
        Panel(rectPolygon(w * 0.5, -b, w * 0.5 + b, h + b * 2), p.tone3, w * 0.5, 0.0),
    ), 0)
    addSeam(ctx, if (horizontal) rectPolygon(-b, h * 0.52 - 1.5, w + b * 2, 3.0) else rectPolygon(w * 0.5 - 1.5, -b, 3.0, h + b * 2), 0.16)
}

private val quadSplit: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val sx = w * (0.42 + th01(ctx.seed, 2, 7) * 0.16)
    val sy = h * (0.42 + th01(ctx.seed, 3, 11) * 0.16)
    addPanels(ctx, listOf(
        Panel(rectPolygon(-b, -b, sx + b, sy + b), p.tone1, -w * 0.3, -h * 0.3),
        Panel(rectPolygon(sx, -b, w - sx + b, sy + b), p.tone4, w * 0.3, -h * 0.3),
        Panel(rectPolygon(-b, sy, sx + b, h - sy + b), p.tone3, -w * 0.3, h * 0.3),
        Panel(rectPolygon(sx, sy, w - sx + b, h - sy + b), p.tone2, w * 0.3, h * 0.3),
    ), 3)
    addSeam(ctx, rectPolygon(sx - 1.5, -b, 3.0, h + b * 2), 0.2)
    addSeam(ctx, rectPolygon(-b, sy - 1.5, w + b * 2, 3.0), 0.24)
}

private val triColumn: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val edge = w * (0.26 + th01(ctx.seed, 4, 13) * 0.06)
    addPanels(ctx, listOf(
        Panel(rectPolygon(-b, -b, edge + b, h + b * 2), p.tone1, -w * 0.25, 0.0),
        Panel(rectPolygon(edge, -b, w - edge * 2, h + b * 2), p.tone4, 0.0, -h * 0.3),
        Panel(rectPolygon(w - edge, -b, edge + b, h + b * 2), p.tone1, w * 0.25, 0.0),
    ), 0)
}

private val thirdsStack: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val band = h * 0.34
    addPanels(ctx, listOf(
        Panel(rectPolygon(-b, -b, w + b * 2, band + b), p.tone2, -w * 0.2, 0.0),
        Panel(rectPolygon(-b, band, w + b * 2, band), p.tone4, w * 0.2, 0.0),
        Panel(rectPolygon(-b, band * 2, w + b * 2, h - band * 2 + b), p.tone1, -w * 0.2, 0.0),
    ), 0)
}

private val checkerQuad: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val sx = w * 0.5; val sy = h * 0.5
    addPanels(ctx, listOf(
        Panel(rectPolygon(-b, -b, sx + b, sy + b), p.tone4, 0.0, -h * 0.35),
        Panel(rectPolygon(sx, -b, w - sx + b, sy + b), p.tone1, 0.0, -h * 0.35),
        Panel(rectPolygon(-b, sy, sx + b, h - sy + b), p.tone1, 0.0, h * 0.35),
        Panel(rectPolygon(sx, sy, w - sx + b, h - sy + b), p.tone4, 0.0, h * 0.35),
    ), 1)
}

private val cornerWedge: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val fromLeft = th01(ctx.seed, 5, 17) > 0.5
    val apexX = if (fromLeft) w * 0.78 else w * 0.22
    addPanels(ctx, listOf(
        Panel(rectPolygon(-b, -b, w + b * 2, h + b * 2), p.tone1, 0.0, 0.0),
        Panel(
            if (fromLeft) doubleArrayOf(-b, -b, apexX, -b, -b, h + b) else doubleArrayOf(w + b, -b, apexX, -b, w + b, h + b),
            p.tone4, if (fromLeft) -w * 0.4 else w * 0.4, 0.0,
        ),
    ), 1)
}

private val diagonalHalves: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val lean = h * (0.2 + th01(ctx.seed, 6, 19) * 0.3)
    addPanels(ctx, listOf(
        Panel(doubleArrayOf(-b, -b, w + b, -b, w + b, lean, -b, h - lean), p.tone2, 0.0, -h * 0.4),
        Panel(doubleArrayOf(-b, h - lean, w + b, lean, w + b, h + b, -b, h + b), p.tone4, 0.0, h * 0.4),
    ), 1)
}

private val crossAxis: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val barX = w * 0.11; val barY = h * 0.13
    ctx.add(drawPolygonFill(rectPolygon(-b, -b, w + b * 2, h + b * 2), p.tone1, 0.9, ctx.gradient), span = 0.5)
    ctx.add(drawPolygonFill(rectPolygon((w - barX) / 2, -b, barX, h + b * 2), p.tone4, 0.95), delay = 0.06, span = 0.5, enterDY = -h * 0.5)
    ctx.add(drawPolygonFill(rectPolygon(-b, (h - barY) / 2, w + b * 2, barY), p.tone3, 0.95), delay = 0.12, span = 0.5, enterDX = w * 0.5)
}

private val offsetHalves: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val step = h * (0.06 + th01(ctx.seed, 7, 23) * 0.06)
    val seam = w * 0.48
    addPanels(ctx, listOf(
        Panel(rectPolygon(-b, -b, seam + b, h * 0.5 + step + b), p.tone1, -w * 0.3, 0.0),
        Panel(rectPolygon(seam, -b, w - seam + b, h * 0.5 - step + b), p.tone3, w * 0.3, 0.0),
        Panel(rectPolygon(-b, h * 0.5 + step, seam + b, h * 0.5 - step + b), p.tone4, -w * 0.2, 0.0),
        Panel(rectPolygon(seam, h * 0.5 - step, w - seam + b, h * 0.5 + step + b), p.tone2, w * 0.2, 0.0),
    ), 2)
}

private val stairBlocks: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val tones = listOf(p.tone1, p.tone2, p.tone3, p.tone4)
    ctx.add(drawPolygonFill(rectPolygon(-b, -b, w + b * 2, h + b * 2), p.tone1, 0.9, ctx.gradient), span = 0.5)
    tones.forEachIndexed { index, tone ->
        val left = w * (0.06 + index * 0.2)
        val top = h * (0.1 + index * 0.16)
        ctx.add(drawPolygonFill(rectPolygon(left, top, w * 0.3, h * 0.34), tone, 0.94, ctx.gradient), delay = index * 0.06, span = 0.5, enterDX = -w * 0.2, enterDY = h * 0.12)
    }
}

private val pillarGap: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val gap = w * (0.26 + th01(ctx.seed, 8, 29) * 0.08)
    val side = (w - gap) / 2
    addPanels(ctx, listOf(
        Panel(rectPolygon(-b, -b, side + b, h + b * 2), p.tone4, -w * 0.3, 0.0),
        Panel(rectPolygon(w - side, -b, side + b, h + b * 2), p.tone4, w * 0.3, 0.0),
    ), 1)
}

private val cornerQuad: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val sx = w * (0.6 + th01(ctx.seed, 9, 31) * 0.12)
    val sy = h * (0.62 + th01(ctx.seed, 10, 37) * 0.1)
    addPanels(ctx, listOf(
        Panel(rectPolygon(-b, -b, sx + b, sy + b), p.tone2, 0.0, -h * 0.25),
        Panel(rectPolygon(sx, -b, w - sx + b, sy + b), p.tone4, w * 0.25, 0.0),
        Panel(rectPolygon(-b, sy, sx + b, h - sy + b), p.tone1, -w * 0.25, 0.0),
        Panel(rectPolygon(sx, sy, w - sx + b, h - sy + b), p.tone3, 0.0, h * 0.25),
    ), 0)
}

private val sliverStack: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val fromLeft = th01(ctx.seed, 11, 41) > 0.5
    val slabWidth = w * 0.46
    val left = if (fromLeft) -b else w - slabWidth
    ctx.add(drawPolygonFill(rectPolygon(-b, -b, w + b * 2, h + b * 2), p.tone1, 0.9, ctx.gradient), span = 0.5)
    val count = 9
    val sliver = (h + b * 2) / count
    for (index in 0 until count) {
        if (index % 2 == 1) continue
        ctx.add(drawPolygonFill(rectPolygon(left, -b + sliver * index, slabWidth + b, sliver), p.tone4, 0.92, ctx.gradient),
            delay = index * 0.02, span = 0.5, enterDX = (if (fromLeft) -1 else 1) * w * 0.2)
    }
}

internal val TEMPERA_SPLIT_COMPOSITIONS: Map<String, TpDrawer> = mapOf(
    "duo-split" to duoSplit, "quad-split" to quadSplit, "tri-column" to triColumn, "thirds-stack" to thirdsStack,
    "checker-quad" to checkerQuad, "corner-wedge" to cornerWedge, "diagonal-halves" to diagonalHalves, "cross-axis" to crossAxis,
    "offset-halves" to offsetHalves, "stair-blocks" to stairBlocks, "pillar-gap" to pillarGap, "corner-quad" to cornerQuad,
    "sliver-stack" to sliverStack,
)

// ───────────────────────────── 色带族 ─────────────────────────────

private val bandStrip: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val bandY = h * 0.37; val bandH = h * 0.3
    ctx.add(drawPolygonFill(rectPolygon(-b, bandY, w + b * 2, bandH), p.tone3, 0.96, ctx.gradient), span = 0.55, enterDX = -w * 0.5)
    ctx.add(drawLines(listOf(
        TpLine(-b, bandY - 10, w + b, bandY - 22),
        TpLine(-b, bandY + bandH + 22, w + b, bandY + bandH + 10),
    ), p.tone4, 1.4, 0.75), delay = 0.12, enterDX = w * 0.25)
    if (ctx.showDecor) {
        ctx.add(drawCrossMarks(buildCrossRow(ctx.seed, 17, w * 0.06, bandY - h * 0.16, 4, w * 0.055, 9.0), p.ink, 2.0, 0.8), delay = 0.2, span = 0.5)
        ctx.add(drawSquareMarks(buildDotRow(ctx.seed, 19, w * 0.94, bandY + bandH + h * 0.06, 3, h * 0.05, 6.0), p.ink, 0.75), delay = 0.26, drift = true)
    }
}

private val horizonBand: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val waterline = h * (0.5 + th01(ctx.seed, 1, 23) * 0.12)
    ctx.add(drawPolygonFill(rectPolygon(-b, -b, w + b * 2, waterline + b), p.tone1, 0.94, ctx.gradient), span = 0.55, enterDY = -h * 0.3)
    val water = rectPolygon(-b, waterline, w + b * 2, h - waterline + b)
    ctx.add(drawPolygonFill(water, p.tone3, 0.95, ctx.gradient), delay = 0.05, span = 0.55, enterDY = h * 0.3)
    ctx.add(drawHatchFill(water, buildHatchSpec(ctx.seed, 29).copy(angle = 0.0), p.tone4, 0.55), delay = 0.1, span = 0.55, grow = true)
    ctx.add(drawPolyline(buildWavyPath(ctx.seed, 31, -b, w + b, waterline, h * 0.012, 30), p.ink, 2.2, 0.85), delay = 0.16, span = 0.5)
}

private val deepDive: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val tones = listOf(p.tone1, p.tone2, p.tone3, p.tone4)
    val bandH = (h + b * 2) / tones.size
    tones.forEachIndexed { index, tone ->
        val top = -b + bandH * index
        ctx.add(drawPolygonFill(rectPolygon(-b, top, w + b * 2, bandH + 1), tone, 0.95, ctx.gradient), delay = index * 0.06, span = 0.55, enterDY = h * 0.3)
        if (index == 0) return@forEachIndexed
        ctx.add(drawPolyline(buildWavyPath(ctx.seed, 37 + index, -b, w + b, top, h * 0.008, 24), p.paper, 1.6, 0.5), delay = index * 0.06 + 0.05, span = 0.5)
    }
}

private val toneRamp: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val spec = buildHatchSpec(ctx.seed, 41)
    ctx.add(drawPolygonFill(rectPolygon(-b, -b, w + b * 2, h + b * 2), p.tone1, 0.92, ctx.gradient), span = 0.5)
    val steps = 4
    for (index in 0 until steps) {
        val cw = (w + b * 2) / steps
        val column = rectPolygon(-b + cw * index, -b, cw, h + b * 2)
        ctx.add(drawHatchFill(column, spec.copy(spacing = spec.spacing * (1.6 - index * 0.32)), p.tone4, 0.6), delay = index * 0.06, span = 0.55, grow = true)
    }
}

private val doubleBand: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val slot = h * (0.2 + th01(ctx.seed, 51, 43) * 0.08)
    val rail = h * 0.3
    val top = (h - slot) / 2 - rail
    ctx.add(drawPolygonFill(rectPolygon(-b, top, w + b * 2, rail), p.tone3, 0.95, ctx.gradient), span = 0.55, enterDX = -w * 0.4)
    ctx.add(drawPolygonFill(rectPolygon(-b, top + rail + slot, w + b * 2, rail), p.tone4, 0.95, ctx.gradient), delay = 0.07, span = 0.55, enterDX = w * 0.4)
}

private val tiltBand: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val lean = h * (0.14 + th01(ctx.seed, 53, 47) * 0.14)
    val half = h * 0.17
    val band = doubleArrayOf(
        -b, h / 2 + lean - half,
        w + b, h / 2 - lean - half,
        w + b, h / 2 - lean + half,
        -b, h / 2 + lean + half,
    )
    ctx.add(drawPolygonFill(rectPolygon(-b, -b, w + b * 2, h + b * 2), p.tone1, 0.9, ctx.gradient), span = 0.5)
    ctx.add(drawPolygonFill(band, p.tone4, 0.95, ctx.gradient), delay = 0.06, span = 0.55, enterDX = -w * 0.35)
    ctx.add(drawHatchFill(band, buildHatchSpec(ctx.seed, 59).copy(angle = PI / 2), p.paper, 0.3), delay = 0.12, span = 0.55, grow = true)
}

private val edgeRails: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val rail = h * (0.16 + th01(ctx.seed, 61, 53) * 0.06)
    ctx.add(drawPolygonFill(rectPolygon(-b, -b, w + b * 2, rail + b), p.tone3, 0.94, ctx.gradient), span = 0.55, enterDY = -h * 0.2)
    ctx.add(drawPolygonFill(rectPolygon(-b, h - rail, w + b * 2, rail + b), p.tone3, 0.94, ctx.gradient), delay = 0.06, span = 0.55, enterDY = h * 0.2)
    if (ctx.showDecor) {
        ctx.add(drawLines(listOf(
            TpLine(-b, rail + 8, w + b, rail + 8),
            TpLine(-b, h - rail - 8, w + b, h - rail - 8),
        ), p.tone4, 1.2, 0.6), delay = 0.16, span = 0.5, enterDX = w * 0.2)
    }
}

private val gradientWall: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val spec = buildHatchSpec(ctx.seed, 67)
    val downward = th01(ctx.seed, 71, 59) > 0.5
    ctx.add(drawPolygonFill(rectPolygon(-b, -b, w + b * 2, h + b * 2), p.tone2, 0.94, ctx.gradient), span = 0.5)
    val steps = 5
    val bandH = h / steps
    for (index in 0 until steps) {
        val rank = if (downward) index else steps - 1 - index
        val top = bandH * index - (if (index == 0) b else 0.0)
        val bottom = bandH * (index + 1) + (if (index == steps - 1) b else 0.0)
        ctx.add(drawHatchFill(rectPolygon(-b, top, w + b * 2, bottom - top), spec.copy(spacing = spec.spacing * (1.9 - rank * 0.34)), p.tone4, 0.55),
            delay = index * 0.05, span = 0.55, grow = true)
    }
}

private val terrace: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val tones = listOf(p.tone1, p.tone2, p.tone3, p.tone4)
    val bandH = h / tones.size
    ctx.add(drawPolygonFill(rectPolygon(-b, -b, w + b * 2, h + b * 2), p.tone1, 0.9, ctx.gradient), span = 0.5)
    tones.forEachIndexed { index, tone ->
        val left = w * index * 0.14 - b
        val top = bandH * index - (if (index == 0) b else 0.0)
        val bottom = bandH * (index + 1) + (if (index == tones.size - 1) b else 1.0)
        ctx.add(drawPolygonFill(rectPolygon(left, top, w + b * 2 - left - b, bottom - top), tone, 0.94, ctx.gradient), delay = index * 0.06, span = 0.55, enterDX = -w * 0.25)
    }
}

internal val TEMPERA_BAND_COMPOSITIONS: Map<String, TpDrawer> = mapOf(
    "band-strip" to bandStrip, "horizon-band" to horizonBand, "deep-dive" to deepDive, "tone-ramp" to toneRamp,
    "double-band" to doubleBand, "tilt-band" to tiltBand, "edge-rails" to edgeRails, "gradient-wall" to gradientWall,
    "terrace" to terrace,
)
