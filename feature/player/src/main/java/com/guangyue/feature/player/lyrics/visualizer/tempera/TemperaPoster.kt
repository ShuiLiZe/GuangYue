// 凝彩构图：海报族（compositions/temperaPosterCompositions.ts），逐函数对译。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import kotlin.math.PI
import kotlin.math.hypot

private fun TpCompositionContext.fullBleed() = rectPolygon(-bleed, -bleed, width + bleed * 2, height + bleed * 2)

private val posterPanel: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val poster = ctx.createGroup(-0.06, w / 2, h / 2)
    val solid = diamondPolygon(-w * 0.12, 0.0, w * 0.42, h * 0.66)
    val hatched = diamondPolygon(w * 0.2, -h * 0.06, w * 0.3, h * 0.48)
    ctx.add(drawPolygonFill(solid, p.ink, 0.92, ctx.gradient), enterDX = -w * 0.6, span = 0.55, parent = poster)
    ctx.add(drawHatchFill(hatched, buildHatchSpec(ctx.seed, 41), p.tone4, 0.7), delay = 0.08, grow = true, span = 0.55, parent = poster)
    ctx.add(drawPolygonOutline(hatched, p.ink, 2.0, 0.8), delay = 0.12, span = 0.55, parent = poster)
    ctx.add(drawPolyline(buildWavyPath(ctx.seed, 43, -w * 0.6, w * 0.6, h * 0.42, h * 0.02), p.tone4, 2.0, 0.7), delay = 0.2, enterDY = h * 0.1, parent = poster)
}

private val diamondStack: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone1, 0.9, ctx.gradient), span = 0.5)
    listOf(p.ink, p.tone4, p.tone3).forEachIndexed { index, tone ->
        val scale = 1 - index * 0.28
        val cx = w * (0.34 + index * 0.24)
        val cy = h * (0.52 - index * 0.14)
        ctx.add(drawPolygonFill(diamondPolygon(cx, cy, w * 0.3 * scale, h * 0.46 * scale), tone, 0.93, ctx.gradient),
            delay = index * 0.07, span = 0.55, enterDX = w * 0.25, enterDY = -h * 0.12)
    }
}

private val slashPoster: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val lean = h * 0.34
    val band = doubleArrayOf(-b, h * 0.24 + lean, w + b, h * 0.24 - lean, w + b, h * 0.72 - lean, -b, h * 0.72 + lean)
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone1, 0.9, ctx.gradient), span = 0.5)
    ctx.add(drawPolygonFill(band, p.tone4, 0.94, ctx.gradient), delay = 0.05, span = 0.55, enterDX = -w * 0.4)
    ctx.add(drawHatchFill(band, buildHatchSpec(ctx.seed, 67).copy(angle = PI / 3), p.paper, 0.3), delay = 0.1, span = 0.55, grow = true)
    ctx.add(drawPolygonOutline(band, p.ink, 2.2, 0.8), delay = 0.14, span = 0.5)
}

private val arrowWedge: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val apex = h * (0.24 + th01(ctx.seed, 3, 71) * 0.14)
    val thickness = h * 0.2
    val chevron = doubleArrayOf(-b, h + b, w * 0.5, apex, w + b, h + b, w + b, h + b, w * 0.5, apex + thickness, -b, h + b)
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone1, 0.9, ctx.gradient), span = 0.5)
    ctx.add(drawPolygonFill(doubleArrayOf(-b, h + b, w * 0.5, apex, w + b, h + b), p.tone4, 0.94, ctx.gradient), delay = 0.05, span = 0.55, enterDY = h * 0.3)
    ctx.add(drawHatchFill(chevron, buildHatchSpec(ctx.seed, 73).copy(angle = -PI / 4), p.paper, 0.35), delay = 0.1, span = 0.55, grow = true)
    ctx.add(drawPolyline(doubleArrayOf(-b, h * 0.9, w * 0.5, apex - thickness * 0.4, w + b, h * 0.9), p.ink, 2.0, 0.7), delay = 0.16, span = 0.5, enterDY = -h * 0.1)
}

private val edgeBleed: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val fromLeft = th01(ctx.seed, 4, 79) > 0.5
    val cover = w * 0.42
    val mass = if (fromLeft) rectPolygon(-b, -b, cover + b, h + b * 2) else rectPolygon(w - cover, -b, cover + b, h + b * 2)
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone1, 0.9, ctx.gradient), span = 0.5)
    ctx.add(drawPolygonFill(mass, p.ink, 0.93, ctx.gradient), delay = 0.05, span = 0.55, enterDX = (if (fromLeft) -1 else 1) * w * 0.4)
    ctx.add(drawHatchFill(mass, buildHatchSpec(ctx.seed, 83), p.paper, 0.22), delay = 0.12, span = 0.55, grow = true)
}

private val triangleMass: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val fromLeft = th01(ctx.seed, 91, 89) > 0.5
    val apexX = if (fromLeft) w * 1.02 else -w * 0.02
    val edgeX = if (fromLeft) -b else w + b
    val triangle = doubleArrayOf(edgeX, -b, edgeX, h + b, apexX, h * (0.3 + th01(ctx.seed, 93, 97) * 0.3))
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone1, 0.9, ctx.gradient), span = 0.5)
    ctx.add(drawPolygonFill(triangle, p.tone4, 0.94, ctx.gradient), delay = 0.05, span = 0.55, enterDX = (if (fromLeft) -1 else 1) * w * 0.35)
    ctx.add(drawHatchFill(triangle, buildHatchSpec(ctx.seed, 101), p.paper, 0.24), delay = 0.12, span = 0.55, grow = true)
}

private val ribbonCross: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val half = h * 0.14; val lean = h * 0.3
    fun ribbon(sign: Int) = doubleArrayOf(
        -b, h / 2 + sign * lean - half, w + b, h / 2 - sign * lean - half,
        w + b, h / 2 - sign * lean + half, -b, h / 2 + sign * lean + half,
    )
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone1, 0.9, ctx.gradient), span = 0.5)
    ctx.add(drawPolygonFill(ribbon(1), p.tone3, 0.8, ctx.gradient), delay = 0.05, span = 0.55, enterDX = -w * 0.3)
    ctx.add(drawPolygonFill(ribbon(-1), p.tone4, 0.8, ctx.gradient), delay = 0.11, span = 0.55, enterDX = w * 0.3)
}

private val halfDisc: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val fromRight = th01(ctx.seed, 103, 107) > 0.5
    val radius = hypot(w, h) * 0.62
    val disc = circlePolygon(if (fromRight) w + radius * 0.55 else -radius * 0.55, h * 0.5, radius, 64)
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone1, 0.9, ctx.gradient), span = 0.5)
    ctx.add(drawPolygonFill(disc, p.tone4, 0.94, ctx.gradient), delay = 0.05, span = 0.55, enterDX = (if (fromRight) 1 else -1) * w * 0.3)
    ctx.add(drawPolygonOutline(disc, p.ink, 2.0, 0.6), delay = 0.12, span = 0.5)
}

private val stackedSlabs: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone1, 0.9, ctx.gradient), span = 0.5)
    listOf(p.tone2, p.tone3, p.tone4).forEachIndexed { index, tone ->
        val group = ctx.createGroup(-0.09 + index * 0.08, w / 2, h / 2)
        val slab = rectPolygon(-w * (0.42 - index * 0.05), -h * (0.3 - index * 0.03), w * (0.84 - index * 0.1), h * (0.6 - index * 0.06))
        ctx.add(drawPolygonFill(slab, tone, 0.93, ctx.gradient), delay = index * 0.07, span = 0.55, enterDX = w * 0.2, enterDY = h * 0.12, parent = group)
    }
}

private val wedgePair: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val waist = w * (0.2 + th01(ctx.seed, 109, 113) * 0.1)
    ctx.add(drawPolygonFill(ctx.fullBleed(), p.tone1, 0.9, ctx.gradient), span = 0.5)
    ctx.add(drawPolygonFill(doubleArrayOf(-b, -b, (w - waist) / 2, h / 2, -b, h + b), p.tone4, 0.94, ctx.gradient), delay = 0.05, span = 0.55, enterDX = -w * 0.25)
    ctx.add(drawPolygonFill(doubleArrayOf(w + b, -b, (w + waist) / 2, h / 2, w + b, h + b), p.tone4, 0.94, ctx.gradient), delay = 0.11, span = 0.55, enterDX = w * 0.25)
}

internal val TEMPERA_POSTER_COMPOSITIONS: Map<String, TpDrawer> = mapOf(
    "poster-panel" to posterPanel, "diamond-stack" to diamondStack, "slash-poster" to slashPoster, "arrow-wedge" to arrowWedge,
    "edge-bleed" to edgeBleed, "triangle-mass" to triangleMass, "ribbon-cross" to ribbonCross, "half-disc" to halfDisc,
    "stacked-slabs" to stackedSlabs, "wedge-pair" to wedgePair,
)
