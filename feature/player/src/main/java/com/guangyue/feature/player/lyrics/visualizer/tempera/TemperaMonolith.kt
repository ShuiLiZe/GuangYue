// 凝彩构图：巨构公共件（compositions/temperaMonolithKit.ts）与巨构族（compositions/temperaMonolithCompositions.ts），逐函数对译。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import kotlin.math.max
import kotlin.math.min

internal fun addMass(
    ctx: TpCompositionContext, polygon: DoubleArray, tone: TpTone,
    alpha: Double? = null, delay: Double? = null, span: Double? = null, enterDX: Double? = null, enterDY: Double? = null,
    edge: Boolean? = null, edgeWidth: Double? = null,
) {
    ctx.add(drawPolygonFill(polygon, tone, alpha ?: 0.95, ctx.gradient), delay = delay ?: 0.04, span = span ?: 0.6, enterDX = enterDX ?: 0.0, enterDY = enterDY ?: 0.0)
    if (edge == false) return
    ctx.add(drawPolygonOutline(polygon, ctx.palette.ink, edgeWidth ?: 2.4, 0.8), delay = (delay ?: 0.04) + 0.06, span = 0.5, enterDX = (enterDX ?: 0.0) * 0.6, enterDY = (enterDY ?: 0.0) * 0.6)
}

internal fun addGround(ctx: TpCompositionContext, tone: TpTone, alpha: Double = 0.94) {
    ctx.add(drawPolygonFill(rectPolygon(-ctx.bleed, -ctx.bleed, ctx.width + ctx.bleed * 2, ctx.height + ctx.bleed * 2), tone, alpha, ctx.gradient), span = 0.5)
}

internal fun addFaceRuling(ctx: TpCompositionContext, face: DoubleArray, salt: Int, tone: TpTone = ctx.palette.tone4, alpha: Double = 0.5) {
    ctx.add(drawHatchFill(face, buildHatchSpec(ctx.seed, salt, 0.7), tone, alpha), delay = 0.16, span = 0.65, grow = true)
}

internal fun addSurveyLines(ctx: TpCompositionContext, count: Int = 3, salt: Int = 211) {
    val w = ctx.width; val h = ctx.height; val b = ctx.bleed
    val lines = List(max(1, count)) { index ->
        val anchor = h * (0.16 + ((index * 0.31 + (ctx.seed % 7) * 0.04) % 0.7))
        val lean = h * (if (index % 2 == 0) 0.22 else -0.16)
        TpLine(-b, anchor - lean, w + b, anchor + lean)
    }
    ctx.add(drawLines(lines, ctx.palette.paper, 1.0, 0.45), delay = 0.2 + (salt % 3) * 0.02, span = 0.7, enterDX = w * 0.2)
}

internal fun addCornerTicks(ctx: TpCompositionContext) {
    val w = ctx.width; val h = ctx.height
    val inset = min(w, h) * 0.06; val arm = min(w, h) * 0.05
    listOf(1 to 1, -1 to -1).forEachIndexed { index, (sx, sy) ->
        val x = if (sx > 0) inset else w - inset
        val y = if (sy > 0) inset else h - inset
        ctx.add(drawLines(listOf(TpLine(x, y, x + sx * arm, y), TpLine(x, y, x, y + sy * arm)), ctx.palette.paper, 1.6, 0.6), delay = 0.26 + index * 0.04, span = 0.5)
    }
}

internal fun addWireTrace(ctx: TpCompositionContext, cx: Double, cy: Double, radius: Double) {
    val group = ctx.createGroup(0.0, cx, cy)
    ctx.add(drawPolyline(buildScribblePath(ctx.decor.scribbleSeed, 199, 0.0, 0.0, radius, 1.0), ctx.palette.paper, 1.2, 0.4), delay = 0.3, span = 0.6, drift = true, parent = group)
}

private val apexMass: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val apexX = w * (0.46 + th01(ctx.seed, 3, 227) * 0.12)
    val apexY = h * 0.38
    addGround(ctx, p.tone1)
    addMass(ctx, doubleArrayOf(-b, h + b, apexX, apexY, w + b, h + b), p.tone3, enterDY = h * 0.1)
    addFaceRuling(ctx, doubleArrayOf(apexX, apexY, w + b, h + b, apexX + w * 0.24, h + b), 229)
    addSurveyLines(ctx, 3)
    if (ctx.showDecor) {
        addCornerTicks(ctx)
        addWireTrace(ctx, w * 0.12, h * 0.84, min(w, h) * 0.09)
    }
}

private val ziggurat: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    addGround(ctx, p.tone1)
    val tones = listOf(p.tone4, p.tone3, p.tone3, p.tone2)
    for (course in 0 until 4) {
        val half = w * (0.44 - course * 0.09)
        val top = h * (0.9 - course * 0.13) - h * 0.13
        addMass(ctx, rectPolygon(w / 2 - half, top, half * 2, h * 0.13 + b), tones[course], delay = 0.04 + course * 0.05, enterDY = h * 0.08, edgeWidth = 2.0)
    }
    addSurveyLines(ctx, 2)
    if (ctx.showDecor) addCornerTicks(ctx)
}

private val slabWall: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val edge = w * 0.36
    addGround(ctx, p.tone1)
    addMass(ctx, rectPolygon(edge, -b, w - edge + b, h + b * 2), p.tone3, edge = false)
    ctx.add(drawLines(listOf(TpLine(edge, -b, edge, h + b)), p.ink, 3.5, 0.9), delay = 0.1, span = 0.55, enterDX = w * 0.06)
    addFaceRuling(ctx, rectPolygon(w * 0.62, h * 0.1, w * 0.3, h * 0.8), 233)
    addSurveyLines(ctx, 3)
    if (ctx.showDecor) addWireTrace(ctx, w * 0.16, h * 0.24, min(w, h) * 0.08)
}

private val cantilever: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    addGround(ctx, p.tone1)
    addMass(ctx, rectPolygon(0.16 * w, h * 0.5, w * 0.1, h * 0.5 + b), p.tone4, delay = 0.02, enterDY = h * 0.1, edge = false)
    addMass(ctx, rectPolygon(-b, h * 0.34, w * 0.78 + b, h * 0.16), p.tone3, delay = 0.08, enterDX = -w * 0.14)
    addFaceRuling(ctx, rectPolygon(w * 0.3, h * 0.36, w * 0.44, h * 0.12), 239, p.paper, 0.35)
    addSurveyLines(ctx, 2)
    if (ctx.showDecor) addCornerTicks(ctx)
}

private val pylonPair: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val bay = rectPolygon(w * 0.26, h * 0.3, w * 0.48, h * 0.7 + b)
    addCutField(ctx, p.tone1, listOf(bay), alpha = 0.94)
    addHoleLip(ctx, bay, 2.0, 0.6, 0.12)
    listOf(0.1, 0.74).forEachIndexed { index, x ->
        addMass(ctx, rectPolygon(w * x, h * 0.28, w * 0.16, h * 0.72 + b), p.tone4, delay = 0.04 + index * 0.05, enterDY = h * 0.08, edgeWidth = 2.0)
    }
    addMass(ctx, rectPolygon(w * 0.04, h * 0.14, w * 0.92, h * 0.16), p.tone3, delay = 0.14, enterDY = -h * 0.1)
    addSurveyLines(ctx, 2)
}

private val bunkerSlit: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val slit = rectPolygon(-b, h * 0.62, w + b * 2, h * 0.06)
    addCutField(ctx, p.tone1, listOf(slit), alpha = 0.94)
    ctx.add(drawPolygonFill(rectPolygon(-b, h * 0.34, w + b * 2, h * 0.28), p.tone4, 0.95, ctx.gradient), delay = 0.05, span = 0.6, enterDY = -h * 0.08)
    ctx.add(drawPolygonFill(rectPolygon(-b, h * 0.68, w + b * 2, h * 0.32 + b), p.tone4, 0.95, ctx.gradient), delay = 0.08, span = 0.6, enterDY = h * 0.1)
    ctx.add(drawLines(listOf(TpLine(-b, h * 0.34, w + b, h * 0.34)), p.ink, 3.0, 0.85), delay = 0.14, span = 0.5)
    addFaceRuling(ctx, rectPolygon(w * 0.08, h * 0.72, w * 0.36, h * 0.2), 241, p.paper, 0.3)
    addSurveyLines(ctx, 2)
}

private val plinthStack: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    addGround(ctx, p.tone1)
    addMass(ctx, rectPolygon(w * 0.06, h * 0.72, w * 0.88, h * 0.28 + b), p.tone4, edgeWidth = 2.0)
    addMass(ctx, rectPolygon(w * 0.16, h * 0.52, w * 0.68, h * 0.2), p.tone3, delay = 0.08, edgeWidth = 2.0)
    addMass(ctx, rectPolygon(w * 0.28, h * 0.34, w * 0.44, h * 0.18), p.tone2, delay = 0.14, enterDY = h * 0.06)
    addFaceRuling(ctx, rectPolygon(w * 0.2, h * 0.76, w * 0.6, h * 0.16), 243)
    addSurveyLines(ctx, 3)
    if (ctx.showDecor) addCornerTicks(ctx)
}

private val buttressRun: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val band = rectPolygon(-b, h * 0.42, w + b * 2, h * 0.58 + b)
    addCutField(ctx, p.tone1, listOf(band), alpha = 0.94)
    for (index in 0 until 5) {
        val left = w * (index * 0.2 + 0.01)
        addMass(ctx, doubleArrayOf(left, h + b, left + w * 0.09, h * 0.42, left + w * 0.17, h + b), if (index % 2 == 0) p.tone4 else p.tone3,
            delay = 0.04 + index * 0.04, enterDY = h * 0.12, edgeWidth = 2.0)
    }
    ctx.add(drawLines(listOf(TpLine(-b, h * 0.42, w + b, h * 0.42)), p.ink, 2.4, 0.8), delay = 0.2, span = 0.5, enterDX = -w * 0.1)
    addSurveyLines(ctx, 2)
}

private val voidCore: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val core = rectPolygon(w * 0.22, h * 0.34, w * 0.56, h * 0.46)
    addCutField(ctx, p.tone3, listOf(core), alpha = 0.95)
    addHoleLip(ctx, core, 3.5, 0.9, 0.08)
    addFaceRuling(ctx, rectPolygon(w * 0.04, h * 0.36, w * 0.14, h * 0.42), 247, p.tone4, 0.45)
    addSurveyLines(ctx, 3)
    if (ctx.showDecor) {
        addCornerTicks(ctx)
        addWireTrace(ctx, w * 0.86, h * 0.86, min(w, h) * 0.08)
    }
}

private val shearBlock: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val left = w * 0.14; val right = w * 0.86; val top = h * 0.2; val bottom = h * 0.8
    val cutTop = w * 0.56; val cutBottom = w * 0.44
    addGround(ctx, p.tone1)
    addMass(ctx, doubleArrayOf(left, top, cutTop, top, cutBottom, bottom, left, bottom), p.tone3, enterDX = -w * 0.08, enterDY = -h * 0.03, edgeWidth = 2.0)
    addMass(ctx, doubleArrayOf(cutTop + w * 0.03, top + h * 0.04, right, top + h * 0.04, right, bottom + h * 0.04, cutBottom + w * 0.03, bottom + h * 0.04), p.tone4,
        delay = 0.1, enterDX = w * 0.08, enterDY = h * 0.03, edgeWidth = 2.0)
    addSurveyLines(ctx, 2)
    if (ctx.showDecor) ctx.add(drawPolygonOutline(rectPolygon(w * 0.1, h * 0.16, w * 0.8, h * 0.68), p.paper, 1.2, 0.35), delay = 0.28, span = 0.55)
}

internal val TEMPERA_MONOLITH_COMPOSITIONS: Map<String, TpDrawer> = mapOf(
    "apex-mass" to apexMass, "ziggurat" to ziggurat, "slab-wall" to slabWall, "cantilever" to cantilever,
    "pylon-pair" to pylonPair, "bunker-slit" to bunkerSlit, "plinth-stack" to plinthStack, "buttress-run" to buttressRun,
    "void-core" to voidCore, "shear-block" to shearBlock,
)
