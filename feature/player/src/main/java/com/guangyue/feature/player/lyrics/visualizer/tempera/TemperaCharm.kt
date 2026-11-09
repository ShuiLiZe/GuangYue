// 凝彩构图：圆滑族（compositions/temperaCharmCompositions.ts），逐函数对译。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private fun addField(ctx: TpCompositionContext, tone: TpTone, alpha: Double = 0.92) {
    ctx.add(drawPolygonFill(rectPolygon(-ctx.bleed, -ctx.bleed, ctx.width + ctx.bleed * 2, ctx.height + ctx.bleed * 2), tone, alpha, ctx.gradient), span = 0.5)
}

private val bubbleDrift: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    addField(ctx, p.tone1)
    val big = buildDiscField(ctx.seed, 11, w, h, 8.0, unit * 0.115)
    val small = buildDiscField(ctx.seed, 17, w, h, 13.0, unit * 0.042)
    ctx.add(drawDiscs(big, p.tone3, 0.88, ctx.gradient), delay = 0.05, span = 0.6, enterDY = h * 0.16, drift = true)
    ctx.add(drawRings(big, p.ink, 2.0, 0.55), delay = 0.12, span = 0.55, enterDY = h * 0.12)
    ctx.add(drawDiscs(small, p.tone4, 0.7), delay = 0.18, span = 0.6, enterDY = h * 0.22, drift = true)
    if (ctx.showDecor) {
        ctx.add(drawDiscs(big.take(2).map { TpDisc(it.x - it.radius * 0.36, it.y - it.radius * 0.36, it.radius * 0.16) }, p.paper, 0.85), delay = 0.28, drift = true)
    }
}

private val cloudWindow: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    val cx = w / 2; val cy = h * 0.52; val rx = w * 0.3; val ry = h * 0.3
    addField(ctx, p.tone3)
    val cloud = lobedPolygon(cx, cy, rx, ry, 9.0, 0.1)
    ctx.add(drawPolygonFill(lobedPolygon(cx, cy + h * 0.025, rx * 1.05, ry * 1.05, 9.0, 0.1), p.tone4, 0.5), delay = 0.04, span = 0.6)
    ctx.add(drawPolygonFill(cloud, p.paper, 0.96, ctx.gradient), delay = 0.08, span = 0.55)
    ctx.add(drawPolygonOutline(cloud, p.ink, 2.4, 0.85), delay = 0.12, span = 0.5)
    if (ctx.showDecor) {
        ctx.add(drawDiscs(listOf(TpDisc(cx - rx * 1.16, cy + ry * 0.66, unit * 0.035), TpDisc(cx - rx * 1.32, cy + ry * 0.9, unit * 0.02)), p.paper, 0.92, ctx.gradient), delay = 0.22, drift = true)
    }
}

private val heartBurst: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val lean = if (th01(ctx.seed, 3, 83) > 0.5) 1 else -1
    val cx = w * 0.5; val cy = h * 0.52; val rx = w * 0.32; val ry = h * 0.4
    addField(ctx, p.tone4, 0.94)
    val main = rotatePolygon(heartPolygon(cx, cy, rx, ry), cx, cy, lean * 0.06)
    ctx.add(drawPolygonFill(rotatePolygon(heartPolygon(cx + lean * w * 0.022, cy + h * 0.025, rx, ry), cx, cy, lean * 0.06), p.tone2, 0.6), delay = 0.04, span = 0.6)
    ctx.add(drawPolygonFill(main, p.paper, 0.95, ctx.gradient), delay = 0.08, span = 0.55, enterDY = h * 0.1)
    ctx.add(drawPolygonOutline(main, p.ink, 3.0, 0.9), delay = 0.14, span = 0.5, enterDY = h * 0.08)
    listOf(0.4, 0.24).forEachIndexed { index, scale ->
        val x = if (lean > 0) w * (0.88 + index * 0.08) else w * (0.12 - index * 0.08)
        val y = h * (0.24 + index * 0.42)
        val group = ctx.createGroup(0.0, x, y)
        ctx.add(drawPolygonFill(rotatePolygon(heartPolygon(0.0, 0.0, rx * scale, ry * scale), 0.0, 0.0, -lean * 0.22), p.tone2, 0.9, ctx.gradient),
            delay = 0.2 + index * 0.06, span = 0.5, enterDX = lean * w * 0.12, drift = true, parent = group)
    }
}

private val sparkleField: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    addField(ctx, p.tone1, 0.88)
    val seeds = buildDiscField(ctx.seed, 23, w, h, 9.0, unit * 0.07)
    seeds.take(5).forEachIndexed { index, disc ->
        val star = starPolygon(0.0, 0.0, disc.radius, disc.radius * 0.16, 4.0, th01(ctx.seed, index, 29) * 0.9)
        val group = ctx.createGroup(0.0, disc.x, disc.y)
        ctx.add(drawPolygonFill(star, p.tone4, 0.9, ctx.gradient), delay = 0.06 + index * 0.05, span = 0.5, drift = true, parent = group)
    }
    ctx.add(drawDiscs(seeds.drop(5).map { TpDisc(it.x, it.y, it.radius * 0.22) }, p.tone4, 0.7), delay = 0.24, span = 0.55, drift = true)
    ctx.add(drawLines(listOf(TpLine(w * 0.08, h * 0.74, w * 0.92, h * 0.72)), p.tone4, 1.2, 0.5), delay = 0.3, span = 0.5, enterDX = -w * 0.12)
}

private val petalArc: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val fromLeft = th01(ctx.seed, 5, 89) > 0.5
    val hubX = if (fromLeft) -w * 0.1 else w * 1.1
    val hubY = h * 0.12
    val rx = w * 0.26; val ry = h * 0.085
    addField(ctx, p.tone2)
    for (index in 0 until 5) {
        val base = ellipsePolygon(hubX + (if (fromLeft) rx else -rx), hubY, rx, ry, 28.0)
        val petal = rotatePolygon(base, hubX, hubY, (if (fromLeft) 1 else -1) * (0.18 + index * 0.3))
        ctx.add(drawPolygonFill(petal, if (index % 2 == 0) p.tone3 else p.tone1, 0.9, ctx.gradient), delay = index * 0.05, span = 0.55, enterDY = -h * 0.1)
        ctx.add(drawPolygonOutline(petal, p.ink, 1.6, 0.5), delay = 0.06 + index * 0.05, span = 0.5)
    }
    val hub = ctx.createGroup(0.0, hubX, hubY)
    ctx.add(drawPolygonFill(lobedPolygon(0.0, 0.0, w * 0.11, w * 0.11, 6.0, 0.34), p.tone4, 0.9, ctx.gradient), delay = 0.28, span = 0.5, drift = true, parent = hub)
    if (ctx.showDecor) {
        ctx.add(drawDiscs(List(3) { i ->
            TpDisc(if (fromLeft) w * (0.78 + i * 0.07) else w * (0.22 - i * 0.07), h * (0.7 + i * 0.1), h * (0.03 - i * 0.006))
        }, p.tone4, 0.75, ctx.gradient), delay = 0.32, span = 0.5, drift = true)
    }
}

private val scallopBand: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    addField(ctx, p.tone1, 0.9)
    val top = scallopBandPolygon(-b, w + b, -b, h * 0.28, 9.0)
    val bottom = scallopBandPolygon(-b, w + b, h + b, h * 0.72, 9.0)
    ctx.add(drawPolygonFill(top, p.tone3, 0.92, ctx.gradient), delay = 0.04, span = 0.55, enterDY = -h * 0.18)
    ctx.add(drawPolygonFill(bottom, p.tone4, 0.92, ctx.gradient), delay = 0.1, span = 0.55, enterDY = h * 0.18)
    ctx.add(drawPolygonOutline(top, p.ink, 2.0, 0.75), delay = 0.14, span = 0.5, enterDY = -h * 0.12)
    ctx.add(drawPolygonOutline(bottom, p.ink, 2.0, 0.75), delay = 0.18, span = 0.5, enterDY = h * 0.12)
    if (ctx.showDecor) {
        val pitch = (w + b * 2) / 9
        ctx.add(drawDiscs(List(5) { i -> TpDisc(-b + pitch * (i + 2), h * 0.28, min(w, h) * 0.018) }, p.ink, 0.7), delay = 0.26, drift = true)
    }
}

private val ribbonLoop: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette; val b = ctx.bleed
    val sag = h * (0.06 + th01(ctx.seed, 7, 97) * 0.05) * (if (th01(ctx.seed, 9, 97) > 0.5) 1 else -1)
    addField(ctx, p.tone2)
    val band = arcRibbonPolygon(-b, w + b, h * 0.5, h * 0.3, sag)
    val echo = arcRibbonPolygon(-b, w + b, h * 0.68, h * 0.06, -sag)
    ctx.add(drawPolygonFill(echo, p.tone4, 0.7, ctx.gradient), delay = 0.04, span = 0.6, enterDX = -w * 0.16)
    ctx.add(drawPolygonFill(band, p.paper, 0.95, ctx.gradient), delay = 0.08, span = 0.55, enterDX = w * 0.18)
    ctx.add(drawPolygonOutline(band, p.ink, 2.6, 0.85), delay = 0.14, span = 0.5, enterDX = w * 0.14)
    val knotX = w * (if (th01(ctx.seed, 11, 97) > 0.5) 0.24 else 0.76)
    val knotY = h * 0.5 + sin((knotX + b) / (w + b * 2) * PI) * sag
    val bow = ctx.createGroup(0.0, knotX, knotY)
    listOf(-1, 1).forEachIndexed { index, side ->
        val loop = rotatePolygon(ellipsePolygon(side * w * 0.055, 0.0, w * 0.05, h * 0.07, 26.0), 0.0, 0.0, side * 0.3)
        ctx.add(drawPolygonFill(loop, p.tone4, 0.92, ctx.gradient), delay = 0.2 + index * 0.04, span = 0.5, enterDX = side * w * 0.06, parent = bow)
        ctx.add(drawPolygonOutline(loop, p.ink, 2.0, 0.8), delay = 0.24 + index * 0.04, span = 0.5, parent = bow)
    }
    val knot = roundedRectPolygon(-w * 0.018, -h * 0.035, w * 0.036, h * 0.07, h * 0.02)
    ctx.add(drawPolygonFill(knot, p.tone4, 0.95, ctx.gradient), delay = 0.28, span = 0.5, parent = bow)
    ctx.add(drawPolygonOutline(knot, p.ink, 2.0, 0.85), delay = 0.3, span = 0.5, parent = bow)
}

private val roundPlate: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    addField(ctx, p.tone3)
    val plate = roundedRectPolygon(w * 0.16, h * 0.24, w * 0.68, h * 0.52, unit * 0.11)
    val inner = roundedRectPolygon(w * 0.19, h * 0.29, w * 0.62, h * 0.42, unit * 0.08)
    ctx.add(drawPolygonFill(plate, p.paper, 0.95, ctx.gradient), delay = 0.05, span = 0.55, enterDY = h * 0.06)
    ctx.add(drawHatchFill(inner, buildHatchSpec(ctx.seed, 101, 1.3), p.tone2, 0.35), delay = 0.1, span = 0.6, grow = true)
    ctx.add(drawPolygonOutline(plate, p.ink, 3.0, 0.9), delay = 0.12, span = 0.5)
    ctx.add(drawPolygonOutline(inner, p.tone4, 1.2, 0.55), delay = 0.16, span = 0.5)
    if (ctx.showDecor) {
        ctx.add(drawDiscs(listOf(
            TpDisc(w * 0.22, h * 0.3, unit * 0.016), TpDisc(w * 0.78, h * 0.3, unit * 0.016),
            TpDisc(w * 0.22, h * 0.7, unit * 0.016), TpDisc(w * 0.78, h * 0.7, unit * 0.016),
        ), p.ink, 0.75), delay = 0.22, span = 0.5)
    }
}

private val haloBurst: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    val cx = w * (0.44 + th01(ctx.seed, 13, 103) * 0.12)
    val cy = h * 0.5
    val reach = hypot(w, h) * 0.7
    val wedges = 14
    addField(ctx, p.tone1)
    for (index in 0 until wedges step 2) {
        val angle = (index.toDouble() / wedges) * PI * 2 + th01(ctx.seed, index, 107) * 0.09
        val spread = 0.11
        ctx.add(drawPolygonFill(doubleArrayOf(
            cx, cy,
            cx + cos(angle - spread) * reach, cy + sin(angle - spread) * reach,
            cx + cos(angle + spread) * reach, cy + sin(angle + spread) * reach,
        ), p.tone3, 0.55, ctx.gradient), delay = 0.04 + index * 0.012, span = 0.6)
    }
    ctx.add(drawPolygonFill(ellipsePolygon(cx, cy, unit * 0.34, unit * 0.34, 48.0), p.paper, 0.94, ctx.gradient), delay = 0.14, span = 0.55)
    listOf(0.34 to 3.0, 0.44 to 1.4).forEachIndexed { index, (scale, stroke) ->
        ctx.add(drawRings(listOf(TpDisc(cx, cy, unit * scale)), p.ink, stroke, 0.75), delay = 0.2 + index * 0.06, span = 0.5, drift = true)
    }
}

internal val TEMPERA_CHARM_COMPOSITIONS: Map<String, TpDrawer> = mapOf(
    "bubble-drift" to bubbleDrift, "cloud-window" to cloudWindow, "heart-burst" to heartBurst, "sparkle-field" to sparkleField,
    "petal-arc" to petalArc, "scallop-band" to scallopBand, "ribbon-loop" to ribbonLoop, "round-plate" to roundPlate,
    "halo-burst" to haloBurst,
)
