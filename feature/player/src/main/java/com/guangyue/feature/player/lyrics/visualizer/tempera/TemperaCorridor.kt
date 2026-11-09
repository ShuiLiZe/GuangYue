// 凝彩构图：走廊族（compositions/temperaCorridorCompositions.ts），逐函数对译：沿 flow 方向开槽，交接时同一个洞连着走。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private fun channel(ctx: TpCompositionContext, offset: Double, width: Double): DoubleArray {
    val c = acrossPoint(ctx, offset)
    return axisRect(c.x, c.y, flowSpan(ctx), width, channelAxis(ctx))
}

private fun addRails(ctx: TpCompositionContext, offset: Double, width: Double, alpha: Double = 0.55) {
    val angle = channelAxis(ctx)
    val half = flowSpan(ctx) / 2
    listOf(-1, 1).forEachIndexed { index, side ->
        val s = flowPoint(ctx, -half, offset + side * width)
        val e = flowPoint(ctx, half, offset + side * width)
        ctx.add(drawLines(listOf(TpLine(s.x, s.y, e.x, e.y)), ctx.palette.tone4, 1.4, alpha),
            delay = 0.18 + index * 0.03, span = 0.5, enterDX = cos(angle) * ctx.width * 0.08, enterDY = sin(angle) * ctx.height * 0.08)
    }
}

private val flowChannel: TpDrawer = { ctx ->
    val w = ctx.width; val unit = min(w, ctx.height)
    val slot = channel(ctx, w * 0.26, unit * 0.3)
    addCutField(ctx, ctx.palette.tone3, listOf(slot))
    addHoleLip(ctx, slot, 2.6, 0.85, 0.08)
    addRails(ctx, w * 0.26, unit * 0.19)
}

private val twinChannel: TpDrawer = { ctx ->
    val w = ctx.width; val unit = min(w, ctx.height)
    val slots = listOf(channel(ctx, -w * 0.34, unit * 0.16), channel(ctx, w * 0.34, unit * 0.16))
    addCutField(ctx, ctx.palette.tone4, slots)
    slots.forEachIndexed { index, slot -> addHoleLip(ctx, slot, 2.4, 0.8, 0.1 + index * 0.04) }
}

private val reedRun: TpDrawer = { ctx ->
    val w = ctx.width; val unit = min(w, ctx.height)
    val slots = ArrayList<DoubleArray>()
    listOf(-1, 1).forEach { side -> for (index in 0 until 4) slots += channel(ctx, side * w * (0.22 + index * 0.08), unit * 0.035) }
    addCutField(ctx, ctx.palette.tone2, slots, alpha = 0.9)
    addRails(ctx, 0.0, w * 0.17, 0.4)
}

private val taperChannel: TpDrawer = { ctx ->
    val w = ctx.width; val unit = min(w, ctx.height)
    val half = flowSpan(ctx) / 2
    val wide = unit * 0.24; val narrow = unit * 0.06; val offset = w * 0.24
    val corners = listOf(flowPoint(ctx, -half, offset - wide), flowPoint(ctx, -half, offset + wide), flowPoint(ctx, half, offset + narrow), flowPoint(ctx, half, offset - narrow))
    val slot = corners.flatMap { listOf(it.x, it.y) }.toDoubleArray()
    addCutField(ctx, ctx.palette.tone3, listOf(slot))
    addHoleLip(ctx, slot, 2.6, 0.85, 0.08)
}

private val chainPorts: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    val offset = w * 0.28; val spacing = h * 0.26
    val ports = listOf(-1.5, -0.5, 0.5, 1.5).map { flowPoint(ctx, it * spacing, offset) }
    addCutField(ctx, p.tone4, ports.map { circlePolygon(it.x, it.y, unit * 0.11, 40) })
    ctx.add(drawRings(ports.map { TpDisc(it.x, it.y, unit * 0.11) }, p.ink, 2.4, 0.85), delay = 0.1, span = 0.55)
    val head = flowPoint(ctx, -spacing * 2.4, offset)
    val tail = flowPoint(ctx, spacing * 2.4, offset)
    ctx.add(drawLines(listOf(TpLine(head.x, head.y, tail.x, tail.y)), p.tone2, 1.6, 0.6), delay = 0.18, span = 0.5)
}

private val dashChannel: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height
    val unit = min(w, h)
    val offset = w * 0.24; val step = h * 0.32
    val angle = channelAxis(ctx)
    val slots = listOf(-1.5, -0.5, 0.5, 1.5).map { i -> val c = flowPoint(ctx, i * step, offset); axisRect(c.x, c.y, step * 0.72, unit * 0.22, angle) }
    addCutField(ctx, ctx.palette.tone3, slots)
    slots.forEachIndexed { index, slot -> addHoleLip(ctx, slot, 2.2, 0.8, 0.08 + index * 0.03) }
}

private val windowRun: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height
    val unit = min(w, h)
    val offset = w * 0.27; val step = h * 0.36
    val angle = channelAxis(ctx)
    val slots = listOf(-1.5, -0.5, 0.5, 1.5).map { i ->
        val c = flowPoint(ctx, i * step, offset)
        val size = unit * 0.2
        rotatePolygon(roundedRectPolygon(c.x - size, c.y - size * 0.62, size * 2, size * 1.24, size * 0.3), c.x, c.y, angle + PI / 2)
    }
    addCutField(ctx, ctx.palette.tone4, slots)
    slots.forEachIndexed { index, slot -> addHoleLip(ctx, slot, 2.4, 0.85, 0.08 + index * 0.03) }
}

private val bridgeSpan: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    val slot = channel(ctx, 0.0, unit * 0.62)
    addCutField(ctx, p.tone4, listOf(slot))
    addHoleLip(ctx, slot, 2.6, 0.8, 0.08)
    val band = axisRect(w / 2, h / 2, flowSpan(ctx), h * 0.46, channelAxis(ctx) + PI / 2)
    ctx.add(drawPolygonFill(band, p.tone3, 0.95, ctx.gradient), delay = 0.12, span = 0.6, enterDX = -w * 0.16)
    ctx.add(drawPolygonOutline(band, p.ink, 2.0, 0.7), delay = 0.2, span = 0.5, enterDX = w * 0.1)
}

private val braidChannel: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height
    val unit = min(w, h)
    val angle = channelAxis(ctx)
    val pitch = h * 0.42
    val slots = ArrayList<DoubleArray>()
    listOf(-1, 1).forEach { side ->
        for (index in -1..1) {
            val c = flowPoint(ctx, index * pitch + side * pitch * 0.5, side * w * 0.32)
            slots += axisRect(c.x, c.y, pitch * 0.78, unit * 0.16, angle)
        }
    }
    addCutField(ctx, ctx.palette.tone3, slots)
    slots.forEachIndexed { index, slot -> addHoleLip(ctx, slot, 2.2, 0.8, 0.08 + index * 0.025) }
}

private val portLadder: TpDrawer = { ctx ->
    val w = ctx.width; val h = ctx.height; val p = ctx.palette
    val unit = min(w, h)
    val offset = w * 0.3
    val slot = channel(ctx, offset, unit * 0.14)
    addCutField(ctx, p.tone2, listOf(slot))
    addHoleLip(ctx, slot, 2.2, 0.8, 0.08)
    val rungs = List(9) { index ->
        val distance = (index - 4) * h * 0.13
        val long = index % 3 == 0
        val inner = flowPoint(ctx, distance, offset - unit * 0.09)
        val outer = flowPoint(ctx, distance, offset - unit * (if (long) 0.22 else 0.15))
        TpLine(inner.x, inner.y, outer.x, outer.y)
    }
    ctx.add(drawLines(rungs, p.tone4, 2.0, 0.7), delay = 0.14, span = 0.6)
}

internal val TEMPERA_CORRIDOR_COMPOSITIONS: Map<String, TpDrawer> = mapOf(
    "flow-channel" to flowChannel, "twin-channel" to twinChannel, "reed-run" to reedRun, "taper-channel" to taperChannel,
    "chain-ports" to chainPorts, "dash-channel" to dashChannel, "window-run" to windowRun, "bridge-span" to bridgeSpan,
    "braid-channel" to braidChannel, "port-ladder" to portLadder,
)
