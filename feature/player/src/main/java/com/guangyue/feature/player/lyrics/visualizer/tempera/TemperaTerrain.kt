// 凝彩构图：地形族（compositions/temperaTerrainCompositions.ts）与物语留白卡（compositions/temperaMonogatariCompositions.ts），逐函数对译。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

private val ridgeLine: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val lift = th01(ctx.seed, 5, 251) * 0.06
    addGround(ctx, p.tone1)
    addMass(ctx, doubleArrayOf(-b, h * (0.68 + lift), w * 0.34, h * (0.4 + lift), w * 0.62, h * (0.58 + lift), w + b, h * (0.34 + lift), w + b, h + b, -b, h + b), p.tone3, enterDY = h * 0.08)
    addFaceRuling(ctx, doubleArrayOf(w * 0.34, h * (0.42 + lift), w * 0.62, h * (0.6 + lift), w * 0.62, h + b, w * 0.34, h + b), 253, p.tone4, 0.45)
    addSurveyLines(ctx, 3)
    if (ctx.showDecor) {
        addCornerTicks(ctx)
        addWireTrace(ctx, w * 0.1, h * 0.86, min(w, h) * 0.08)
    }
}

private val chasm: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val gap = rectPolygon(w * 0.46, -b, w * 0.1, h + b * 2)
    addCutField(ctx, p.tone1, listOf(gap), alpha = 0.94)
    addMass(ctx, rectPolygon(-b, h * 0.3, w * 0.46 + b, h * 0.7 + b), p.tone3, enterDX = -w * 0.1, edgeWidth = 2.0)
    addMass(ctx, rectPolygon(w * 0.56, h * 0.2, w * 0.44 + b, h * 0.8 + b), p.tone4, delay = 0.08, enterDX = w * 0.1, edgeWidth = 2.0)
    addSurveyLines(ctx, 2)
}

private val overhang: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    addGround(ctx, p.tone1)
    addMass(ctx, rectPolygon(-b, -b, w + b * 2, h * 0.34 + b), p.tone4, enterDY = -h * 0.1, edgeWidth = 3.0)
    addMass(ctx, rectPolygon(w * 0.72, h * 0.34, w * 0.28 + b, h * 0.66 + b), p.tone3, delay = 0.08, enterDX = w * 0.1, edge = false)
    ctx.add(drawPolygonFill(rectPolygon(-b, h * 0.34, w + b * 2, h * 0.14), p.tone2, 0.5, ctx.gradient), delay = 0.16, span = 0.6)
    addSurveyLines(ctx, 3)
    if (ctx.showDecor) addCornerTicks(ctx)
}

private val stepWell: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val shaft = rectPolygon(w * 0.36, h * 0.6, w * 0.28, h * 0.4 + b)
    addCutField(ctx, p.tone2, listOf(shaft), alpha = 0.94)
    listOf(Triple(doubleArrayOf(0.14, 0.3, 0.72), p.tone3, 0), Triple(doubleArrayOf(0.24, 0.44, 0.52), p.tone4, 1)).forEach { (geo, tone, index) ->
        val (x, y, ww) = geo
        ctx.add(drawPolygonFillWithHoles(rectPolygon(w * x, h * y, w * ww, h * (1 - y) + b), listOf(shaft), tone, 0.95, ctx.gradient), delay = 0.06 + index * 0.06, span = 0.6, enterDY = h * 0.06)
    }
    addHoleLip(ctx, shaft, 3.0, 0.85, 0.18)
    addSurveyLines(ctx, 2)
}

private val pierRow: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val water = rectPolygon(-b, h * 0.46, w + b * 2, h * 0.54 + b)
    addCutField(ctx, p.tone1, listOf(water), alpha = 0.94)
    listOf(0.04, 0.28, 0.52, 0.76).forEachIndexed { index, x ->
        addMass(ctx, rectPolygon(w * x, h * 0.46, w * 0.14, h * 0.54 + b), p.tone4, delay = 0.06 + index * 0.04, enterDY = h * 0.1, edge = false)
    }
    addMass(ctx, rectPolygon(-b, h * 0.32, w + b * 2, h * 0.16), p.tone3, delay = 0.02, enterDX = -w * 0.12, edgeWidth = 2.0)
    addSurveyLines(ctx, 2)
}

private val revetment: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    addGround(ctx, p.tone1)
    addMass(ctx, doubleArrayOf(-b, h * 0.72, w * 0.62, h * 0.3, w + b, h * 0.34, w + b, h + b, -b, h + b), p.tone3, enterDY = h * 0.1)
    val ribs = List(9) { index ->
        val x = w * (0.04 + index * 0.11)
        val top = h * (0.72 - (x / (w * 0.62)) * 0.42)
        TpLine(x, max(top, h * 0.3) + h * 0.03, x - w * 0.03, h + b)
    }
    ctx.add(drawLines(ribs, p.tone4, 2.4, 0.5), delay = 0.16, span = 0.65)
    addSurveyLines(ctx, 3)
    if (ctx.showDecor) addCornerTicks(ctx)
}

private val towerCrop: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    addGround(ctx, p.tone1)
    addMass(ctx, rectPolygon(w * 0.58, h * 0.18, w * 0.42 + b, h * 0.82 + b), p.tone4, enterDX = w * 0.08, enterDY = h * 0.06, edgeWidth = 3.0)
    addFaceRuling(ctx, rectPolygon(w * 0.62, h * 0.26, w * 0.12, h * 0.6), 257, p.paper, 0.3)
    addSurveyLines(ctx, 2)
    if (ctx.showDecor) {
        addWireTrace(ctx, w * 0.2, h * 0.7, min(w, h) * 0.1)
        addCornerTicks(ctx)
    }
}

private val lintel: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    addGround(ctx, p.tone1)
    addMass(ctx, rectPolygon(-b, h * 0.4, w + b * 2, h * 0.2), p.tone3, enterDX = -w * 0.16, edgeWidth = 3.0)
    listOf(0.18, 0.66).forEachIndexed { index, x ->
        addMass(ctx, rectPolygon(w * x, h * 0.6, w * 0.16, h * 0.12), p.tone4, delay = 0.12 + index * 0.04, enterDY = h * 0.06, edge = false)
    }
    addSurveyLines(ctx, 3)
    if (ctx.showDecor) addCornerTicks(ctx)
}

private val rubbleFan: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val hubX = -w * 0.12; val hubY = h * 1.12
    addGround(ctx, p.tone1)
    for (index in 0 until 5) {
        val angle = -1.24 + index * 0.19 + th01(ctx.seed, index, 259) * 0.05
        val reach = hypot(w, h) * (0.5 + index * 0.09)
        val slab = rectPolygon(hubX + reach * 0.34, hubY - h * 0.06, reach * 0.5, h * 0.12 + index * 8)
        addMass(ctx, rotatePolygon(slab, hubX, hubY, angle), if (index % 2 == 0) p.tone3 else p.tone4, delay = 0.04 + index * 0.05, enterDX = -w * 0.06, enterDY = h * 0.06, edgeWidth = 2.0)
    }
    addSurveyLines(ctx, 2)
}

private val gnomon: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    addGround(ctx, p.tone1)
    ctx.add(drawPolygonFill(doubleArrayOf(w * 0.42, h * 0.86, w + b, h * 0.6, w + b, h * 0.78, w * 0.46, h * 0.94), p.tone2, 0.6, ctx.gradient), delay = 0.1, span = 0.65, enterDX = w * 0.1)
    addMass(ctx, doubleArrayOf(w * 0.3, h + b, w * 0.36, h * 0.16, w * 0.42, h * 0.16, w * 0.46, h + b), p.tone4, delay = 0.04, enterDY = h * 0.08, edgeWidth = 2.0)
    addSurveyLines(ctx, 3)
    if (ctx.showDecor) {
        addCornerTicks(ctx)
        addWireTrace(ctx, w * 0.8, h * 0.82, min(w, h) * 0.08)
    }
}

internal val TEMPERA_TERRAIN_COMPOSITIONS: Map<String, TpDrawer> = mapOf(
    "ridge-line" to ridgeLine, "chasm" to chasm, "overhang" to overhang, "step-well" to stepWell,
    "pier-row" to pierRow, "revetment" to revetment, "tower-crop" to towerCrop, "lintel" to lintel,
    "rubble-fan" to rubbleFan, "gnomon" to gnomon,
)

// ───────────────────────────── 物语留白卡 ─────────────────────────────

private fun addCardField(ctx: TpCompositionContext, tone: TpTone) {
    ctx.add(drawPolygonFill(rectPolygon(-ctx.bleed, -ctx.bleed, ctx.width + ctx.bleed * 2, ctx.height + ctx.bleed * 2), tone, 1.0, ctx.gradient), span = 0.45)
}

private fun pickField(ctx: TpCompositionContext) = if (th01(ctx.seed, 167, 173) > 0.5) ctx.palette.tone4 else ctx.palette.tone1

private val monogatariCard: TpDrawer = { ctx ->
    addCardField(ctx, pickField(ctx))
    if (ctx.showDecor) ctx.add(drawLines(listOf(TpLine(ctx.width * 0.12, ctx.height * 0.9, ctx.width * 0.88, ctx.height * 0.9)), ctx.palette.paper, 1.2, 0.45), delay = 0.22, span = 0.6, enterDX = ctx.width * 0.15)
}

private val monogatariRule: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    addCardField(ctx, pickField(ctx))
    val y = h * (0.62 + th01(ctx.seed, 179, 181) * 0.08)
    ctx.add(drawPolygonFill(rectPolygon(-b, y, w + b * 2, h * 0.012), p.paper, 0.85, ctx.gradient), delay = 0.12, span = 0.55, enterDX = -w * 0.3)
    if (ctx.showDecor) ctx.add(drawLines(listOf(TpLine(-b, y + h * 0.05, w + b, y + h * 0.05)), p.paper, 1.0, 0.35), delay = 0.2, span = 0.6, enterDX = w * 0.2)
}

private val monogatariEdge: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val field = pickField(ctx)
    addCardField(ctx, field)
    val band = w * (0.1 + th01(ctx.seed, 191, 193) * 0.05)
    val fromLeft = th01(ctx.seed, 197, 199) > 0.5
    val spine = rectPolygon(if (fromLeft) -b else w - band, -b, band + b, h + b * 2)
    ctx.add(drawPolygonFill(spine, if (field == p.tone4) p.tone1 else p.tone4, 0.95, ctx.gradient), delay = 0.1, span = 0.55, enterDX = (if (fromLeft) -1 else 1) * w * 0.2)
    if (ctx.showDecor) ctx.add(drawHatchFill(spine, buildHatchSpec(ctx.seed, 211), p.paper, 0.2), delay = 0.18, span = 0.6, grow = true)
}

private val monogatariStack: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    addCardField(ctx, pickField(ctx))
    val column = w * 0.5
    ctx.add(drawLines(listOf(TpLine((w - column) / 2, -b, (w - column) / 2, h + b), TpLine((w + column) / 2, -b, (w + column) / 2, h + b)), p.paper, 1.1, 0.4), delay = 0.14, span = 0.6, enterDY = h * 0.12)
}

private val monogatariFlash: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    addCardField(ctx, p.tone4)
    if (ctx.showDecor) ctx.add(drawCrossMarks(buildCrossRow(ctx.seed, 223, w * 0.1, h * 0.18, 4, w * 0.05, 10.0), p.paper, 2.4, 0.6), delay = 0.2, span = 0.5, enterDX = -w * 0.1)
}

internal val TEMPERA_MONOGATARI_COMPOSITIONS: Map<String, TpDrawer> = mapOf(
    "monogatari-card" to monogatariCard, "monogatari-rule" to monogatariRule, "monogatari-edge" to monogatariEdge,
    "monogatari-stack" to monogatariStack, "monogatari-flash" to monogatariFlash,
)
