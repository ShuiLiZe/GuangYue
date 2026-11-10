// 绘光（Folia lumiere）线稿：lineart/ 目录逐函数对译。
// 对应 Folia 文件：src/components/visualizer/lumiere/
//   lineart/lineArt.ts（LineArtSpec 数据结构、按进度描线、节点闪点、受光变亮）、
//   lineart/recipes.ts（弧、贝塞尔、量角器光环、取景框、萌芽、散落闪点、百叶窗、三棱镜、双缝挡板）、
//   lineart/diagrams.ts（L3 各族图解：窗、棱镜、焦散、光路、干涉、植物、天体、舞台、通用）、
//   rigs 目录各文件 里每个光位的 lineArt(context)（含 rigs/base.ts 的 standardArt / frameArt / ellipse，各族缺省）、
//   scene.ts 458–482 行的线稿节奏（每个镜头提前 0.6 秒开始描、3.6 秒描完、镜头结束后 0.8 秒淡出）。
// 坐标一律高度单位（x 在 0..aspect），画的时候乘舞台高度（dp）。随机数顺序与 Folia 逐项一致（金标单测 LumiereLineartTest）。
//
// 偏离：
//   1. lineart/themeIcons.ts + iconPaths.ts（主题图标，theme.lyricsIcons）没有译：广乐主题没有 lyricsIcons，
//      Folia 在没有图标时也是一组都不画（不退回默认图标），所以结果相同。
//   2. Pixi 每条线一个 Graphics、描线进度变化才重建几何；这里每条线缓存一个 android.graphics.Path，进度变了才重建，
//      受光强弱只改画笔透明度（同 Folia 只改 alpha）。Folia 写了两道描边（3.2 倍宽 0.12 透明的光晕 + 本体），但 Pixi 8 的
//      stroke() 会清掉路径，第二道实际不出图；这里照实际出图只画第一道（见 LumiereLineartDraw.kt）。
//   3. GPU 数据的装卸（idle / shouldUnloadLineArt）是 Pixi 的显存管理，Canvas 上没有对应物；藏起来的镜头只是不画。
package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// ───────────────────────────── 数据结构（lineArt.ts） ─────────────────────────────

internal class LPt(@JvmField val x: Double, @JvmField val y: Double)

internal class LumiereLinePath(
    val points: List<LPt>,
    /** 线宽（高度单位）。 */
    val width: Double,
    /** 基础亮度 0..1。 */
    val alpha: Double,
    /** 描线开始的相对时刻 0..1。 */
    val delay: Double,
    /** 描完这一条占整组时长的比例。 */
    val span: Double,
    /** 虚线：实段与空段长度（高度单位）。 */
    val dash: DoubleArray? = null,
)

internal class LumiereLineNode(val at: LPt, val size: Double, val delay: Double, val twinklePhase: Double)

internal class LumiereLineArtSpec(val paths: List<LumiereLinePath> = emptyList(), val nodes: List<LumiereLineNode> = emptyList())

private fun pt(x: Double, y: Double) = LPt(x, y)
private const val TAU = PI * 2

internal fun lumiereMergeSpecs(vararg specs: LumiereLineArtSpec) = LumiereLineArtSpec(specs.flatMap { it.paths }, specs.flatMap { it.nodes })
internal fun lumiereMergeSpecs(specs: List<LumiereLineArtSpec>) = LumiereLineArtSpec(specs.flatMap { it.paths }, specs.flatMap { it.nodes })

// ───────────────────────────── recipes.ts ─────────────────────────────

internal fun lumiereArcPoints(cx: Double, cy: Double, r: Double, from: Double, to: Double, steps: Int = 64): List<LPt> =
    List(steps + 1) { i -> val a = from + ((to - from) * i) / steps; pt(cx + cos(a) * r, cy + sin(a) * r) }

internal fun lumiereCubicPoints(p0: LPt, p1: LPt, p2: LPt, p3: LPt, steps: Int = 40): List<LPt> = List(steps + 1) { i ->
    val t = i.toDouble() / steps
    val u = 1 - t
    val a = u * u * u; val b = 3 * u * u * t; val c = 3 * u * t * t; val d = t * t * t
    pt(a * p0.x + b * p1.x + c * p2.x + d * p3.x, a * p0.y + b * p1.y + c * p2.y + d * p3.y)
}

private fun line2(a: LPt, b: LPt) = listOf(a, b)

/** 量角器似的光环：以光源为圆心的下半圆（外弧 + 内弧），辐条，外弧上的细刻度。 */
internal fun lumiereProtractorHalo(cx: Double, cy: Double, radius: Double, delay: Double = 0.0, alpha: Double = 0.5, spokeStep: Double = PI / 12): LumiereLineArtSpec {
    val from = PI * 0.04
    val to = PI * 0.96
    val inner = radius * 0.62
    val paths = mutableListOf(
        LumiereLinePath(lumiereArcPoints(cx, cy, radius, to, from, 96), 0.0016, alpha, delay, 0.45),
        LumiereLinePath(lumiereArcPoints(cx, cy, inner, from, to, 72), 0.0013, alpha * 0.8, delay + 0.08, 0.4),
        LumiereLinePath(lumiereArcPoints(cx, cy, radius * 1.18, from + 0.1, to - 0.1, 80), 0.0011, alpha * 0.45, delay + 0.2, 0.4, doubleArrayOf(0.004, 0.009)),
    )
    val nodes = mutableListOf<LumiereLineNode>()
    var index = 0
    var a = PI / 2 - spokeStep * 5
    while (a <= PI / 2 + spokeStep * 5 + 1e-6) {
        val long = index % 2 == 0
        val r0 = inner * 0.2
        val r1 = radius * (if (long) 1.12 else 1.02)
        paths.add(LumiereLinePath(line2(pt(cx + cos(a) * r0, cy + sin(a) * r0), pt(cx + cos(a) * r1, cy + sin(a) * r1)),
            if (long) 0.0013 else 0.0009, alpha * (if (long) 0.7 else 0.45), delay + 0.12 + abs(a - PI / 2) * 0.12, 0.3))
        if (long) nodes.add(LumiereLineNode(pt(cx + cos(a) * radius, cy + sin(a) * radius), 0.022, delay + 0.45, index * 1.7))
        index += 1
        a += spokeStep
    }
    // 外弧上的细刻度。
    a = from
    while (a <= to) {
        paths.add(LumiereLinePath(line2(pt(cx + cos(a) * radius, cy + sin(a) * radius), pt(cx + cos(a) * radius * 0.975, cy + sin(a) * radius * 0.975)),
            0.0008, alpha * 0.4, delay + 0.25 + (a - from) * 0.05, 0.15))
        a += PI / 72
    }
    return LumiereLineArtSpec(paths, nodes)
}

/** 取景框：一个大矩形、四角加粗的角标、边上的刻度、两侧竖直的点线。 */
internal fun lumiereViewfinderFrame(aspect: Double, left0: Double, top: Double, right0: Double, bottom: Double, delay: Double = 0.0, alpha: Double = 0.3): LumiereLineArtSpec {
    val left = left0 * aspect
    val right = right0 * aspect
    val corner = 0.03
    val paths = mutableListOf(
        LumiereLinePath(listOf(pt(left, top), pt(right, top), pt(right, bottom), pt(left, bottom), pt(left, top)), 0.0009, alpha * 0.55, delay, 0.6),
    )
    val corners = listOf(
        listOf(pt(left, top + corner), pt(left, top), pt(left + corner, top)),
        listOf(pt(right - corner, top), pt(right, top), pt(right, top + corner)),
        listOf(pt(right, bottom - corner), pt(right, bottom), pt(right - corner, bottom)),
        listOf(pt(left + corner, bottom), pt(left, bottom), pt(left, bottom - corner)),
    )
    corners.forEachIndexed { i, points -> paths.add(LumiereLinePath(points, 0.0018, alpha, delay + 0.1 + i * 0.05, 0.2)) }
    val ticks = 12
    for (i in 1 until ticks) {
        val x = left + ((right - left) * i) / ticks
        val y = top + ((bottom - top) * i) / ticks
        val len = if (i % 3 == 0) 0.014 else 0.007
        val d = delay + 0.3 + i * 0.01
        paths.add(LumiereLinePath(line2(pt(x, top), pt(x, top + len)), 0.0008, alpha * 0.6, d, 0.1))
        paths.add(LumiereLinePath(line2(pt(x, bottom), pt(x, bottom - len)), 0.0008, alpha * 0.6, d, 0.1))
        paths.add(LumiereLinePath(line2(pt(left, y), pt(left + len, y)), 0.0008, alpha * 0.6, d, 0.1))
        paths.add(LumiereLinePath(line2(pt(right, y), pt(right - len, y)), 0.0008, alpha * 0.6, d, 0.1))
    }
    val guideX = listOf(left + (right - left) * 0.14, right - (right - left) * 0.14)
    guideX.forEachIndexed { i, x ->
        paths.add(LumiereLinePath(line2(pt(x, top + 0.06), pt(x, bottom - 0.06)), 0.001, alpha * 0.5, delay + 0.35 + i * 0.05, 0.4, doubleArrayOf(0.003, 0.012)))
    }
    return LumiereLineArtSpec(paths, emptyList())
}

/** 萌芽：从画面底部升起的茎，顶端两片叶（外轮廓 + 主脉 + 侧脉），叶尖与叶缘有闪点。 */
internal fun lumiereSprout(cx: Double, baseY: Double, size: Double, delay: Double = 0.0, alpha: Double = 0.55, open: Double = 1.0): LumiereLineArtSpec {
    val paths = mutableListOf(
        LumiereLinePath(lumiereCubicPoints(pt(cx, 1.04), pt(cx + 0.01, 0.94), pt(cx - 0.008, baseY + 0.08), pt(cx, baseY)), 0.0016, alpha, delay, 0.3),
    )
    val nodes = mutableListOf<LumiereLineNode>()
    fun leaf(side: Int, index: Int) {
        val lean = (0.2 + 0.2 * open) * PI
        val dx = sin(lean) * side; val dy = -cos(lean)
        val nx = -dy; val ny = dx
        val base = pt(cx, baseY)
        val tip = pt(base.x + dx * size, base.y + dy * size - size * 0.12)
        val bulge = size * 0.34
        fun at(along: Double, across: Double) = pt(
            base.x + dx * size * along + nx * across,
            base.y + dy * size * along + ny * across - size * 0.12 * along * along,
        )
        val leafDelay = delay + 0.22 + index * 0.08
        paths.add(LumiereLinePath(lumiereCubicPoints(base, at(0.3, bulge), at(0.78, bulge * 0.9), tip, 48), 0.0026, alpha, leafDelay, 0.35))
        paths.add(LumiereLinePath(lumiereCubicPoints(base, at(0.3, -bulge * 0.95), at(0.8, -bulge * 0.6), tip, 48), 0.0026, alpha, leafDelay + 0.04, 0.35))
        paths.add(LumiereLinePath(lumiereCubicPoints(base, at(0.35, bulge * 0.08), at(0.7, bulge * 0.05), tip, 32), 0.0011, alpha * 0.7, leafDelay + 0.12, 0.3))
        for (v in 1..4) {
            val f = v / 5.2
            val start = at(f, bulge * 0.04)
            for (s in intArrayOf(1, -1)) {
                val end = at(f + 0.14, s * bulge * (0.72 - f * 0.35))
                paths.add(LumiereLinePath(lumiereCubicPoints(start, at(f + 0.04, s * bulge * 0.25), at(f + 0.1, s * bulge * 0.5), end, 16), 0.0008, alpha * 0.45, leafDelay + 0.2 + v * 0.03, 0.18))
            }
        }
        nodes.add(LumiereLineNode(tip, 0.03, leafDelay + 0.3, index * 2.1))
        nodes.add(LumiereLineNode(at(0.45, bulge * 0.86), 0.014, leafDelay + 0.33, index * 2.1 + 1))
        nodes.add(LumiereLineNode(at(0.6, -bulge * 0.8), 0.012, leafDelay + 0.36, index * 2.1 + 2))
    }
    leaf(-1, 0)
    leaf(1, 1)
    nodes.add(LumiereLineNode(pt(cx, baseY), 0.02, delay + 0.3, 0.5))
    return LumiereLineArtSpec(paths, nodes)
}

/** 散落的小闪点。 */
internal fun lumiereScatteredSparks(aspect: Double, count: Int, random: () -> Double, delay: Double = 0.0, region: DoubleArray? = null): LumiereLineArtSpec {
    val r = region ?: doubleArrayOf(0.05, 0.08, 0.95, 0.92)
    val (x0, y0, x1, y1) = listOf(r[0], r[1], r[2], r[3])
    val nodes = List(count) { i ->
        val x = (x0 + (x1 - x0) * random()) * aspect
        val y = y0 + (y1 - y0) * random()
        val size = 0.006 + random() * 0.012
        val d = delay + random() * 0.5
        LumiereLineNode(pt(x, y), size, d, i * 2.399)
    }
    return LumiereLineArtSpec(emptyList(), nodes)
}

/** 百叶窗：窗框 + 一排横向叶片。 */
internal fun lumiereBlindsWindow(x: Double, y: Double, w: Double, h: Double, slats: Int, delay: Double = 0.0, alpha: Double = 0.5): LumiereLineArtSpec {
    val paths = mutableListOf(LumiereLinePath(listOf(pt(x, y), pt(x + w, y), pt(x + w, y + h), pt(x, y + h), pt(x, y)), 0.0018, alpha, delay, 0.5))
    for (i in 1 until slats) {
        val sy = y + (h * i) / slats
        paths.add(LumiereLinePath(line2(pt(x, sy), pt(x + w, sy)), 0.001, alpha * 0.6, delay + 0.1 + i * 0.03, 0.2))
    }
    return LumiereLineArtSpec(paths, listOf(LumiereLineNode(pt(x + w, y + h), 0.018, delay + 0.4, 1.0)))
}

/** 三棱镜：正三角形轮廓。 */
internal fun lumierePrismTriangle(cx: Double, cy: Double, size: Double, delay: Double = 0.0, alpha: Double = 0.7): LumiereLineArtSpec {
    val h = (size * sqrt(3.0)) / 2
    val top = pt(cx, cy - (h * 2) / 3)
    val left = pt(cx - size / 2, cy + h / 3)
    val right = pt(cx + size / 2, cy + h / 3)
    return LumiereLineArtSpec(
        listOf(
            LumiereLinePath(listOf(top, right, left, top), 0.0022, alpha, delay, 0.5),
            LumiereLinePath(listOf(top, pt(cx, cy + h / 3)), 0.0008, alpha * 0.4, delay + 0.3, 0.3, doubleArrayOf(0.004, 0.008)),
        ),
        listOf(top, left, right).mapIndexed { i, at -> LumiereLineNode(at, 0.02, delay + 0.4 + i * 0.05, i * 1.3) },
    )
}

/** 双缝挡板：一条横线中间开两个缝。 */
internal fun lumiereSlitBarrier(cx: Double, y: Double, width: Double, gap: Double, separation: Double, delay: Double = 0.0, alpha: Double = 0.6): LumiereLineArtSpec {
    val a = cx - separation / 2
    val b = cx + separation / 2
    val segments = listOf(cx - width / 2 to a - gap / 2, a + gap / 2 to b - gap / 2, b + gap / 2 to cx + width / 2)
    return LumiereLineArtSpec(
        segments.mapIndexed { i, (from, to) -> LumiereLinePath(line2(pt(from, y), pt(to, y)), 0.0026, alpha, delay + i * 0.08, 0.3) },
        listOf(a, b).mapIndexed { i, x -> LumiereLineNode(pt(x, y), 0.022, delay + 0.35 + i * 0.05, i * 2.0) },
    )
}

// ───────────────────────────── diagrams.ts ─────────────────────────────

/** 样式：未给的项用各函数的缺省（同 TS 的 { 缺省, ...style, 覆盖 } 展开）。 */
internal class LStyle(val width: Double? = null, val alpha: Double? = null, val delay: Double? = null, val span: Double? = null, val dash: DoubleArray? = null) {
    val d: Double get() = delay ?: 0.0
}

private val S0 = LStyle()

/** { width, alpha, ...style }：缺省在前、style 覆盖。 */
private fun LStyle.under(width: Double? = null, alpha: Double? = null, delay: Double? = null, span: Double? = null) =
    LStyle(this.width ?: width, this.alpha ?: alpha, this.delay ?: delay, this.span ?: span, this.dash)

/** { ...style, delay, span }：style 在前、这些覆盖（传 null 的保持 style）。 */
private fun LStyle.over(width: Double? = null, alpha: Double? = null, delay: Double? = null, span: Double? = null) =
    LStyle(width ?: this.width, alpha ?: this.alpha, delay ?: this.delay, span ?: this.span, this.dash)

private fun dpath(points: List<LPt>, style: LStyle = S0) = LumiereLinePath(points, style.width ?: 0.0016, style.alpha ?: 0.55, style.delay ?: 0.0, style.span ?: 0.4, style.dash)
private fun dnode(at: LPt, size: Double = 0.018, delay: Double = 0.4, twinklePhase: Double = 0.0) = LumiereLineNode(at, size, delay, twinklePhase)
internal fun lumiereSegment(a: LPt, b: LPt, style: LStyle = S0) = dpath(listOf(a, b), style)
private fun seg(a: LPt, b: LPt, width: Double? = null, alpha: Double? = null, delay: Double? = null, span: Double? = null, dash: DoubleArray? = null) =
    dpath(listOf(a, b), LStyle(width, alpha, delay, span, dash))
private fun st(width: Double? = null, alpha: Double? = null, delay: Double? = null, span: Double? = null, dash: DoubleArray? = null) = LStyle(width, alpha, delay, span, dash)

/** 圆 / 椭圆（可旋转）。 */
internal fun lumiereEllipsePath(cx: Double, cy: Double, rx: Double, ry: Double, rotation: Double = 0.0, from: Double = 0.0, to: Double = TAU, steps: Int = 96): List<LPt> {
    val c = cos(rotation)
    val s = sin(rotation)
    return lumiereArcPoints(0.0, 0.0, 1.0, from, to, steps).map { p -> pt(cx + p.x * rx * c - p.y * ry * s, cy + p.x * rx * s + p.y * ry * c) }
}

internal fun lumiereCircle(cx: Double, cy: Double, r: Double, style: LStyle = S0) = dpath(lumiereEllipsePath(cx, cy, r, r), style)

/** 放射线：从 r0 到 r1、在 from..to 角度之间均匀 count 条。 */
internal fun lumiereRays(cx: Double, cy: Double, r0: Double, r1: Double, count: Int, from: Double = 0.0, to: Double = TAU, style: LStyle = S0) = LumiereLineArtSpec(
    List(count) { i ->
        val a = from + ((to - from) * (i + (if (to - from >= TAU - 1e-6) 0.0 else 0.5))) / count
        lumiereSegment(pt(cx + cos(a) * r0, cy + sin(a) * r0), pt(cx + cos(a) * r1, cy + sin(a) * r1),
            style.under(width = 0.001, alpha = 0.4).over(delay = style.d + (i.toDouble() / count) * 0.3, span = style.span ?: 0.2))
    },
    emptyList(),
)

private fun concentric(cx: Double, cy: Double, radii: List<Double>, style: LStyle = S0) = LumiereLineArtSpec(
    radii.mapIndexed { i, r -> lumiereCircle(cx, cy, r, style.under(width = 0.0011, alpha = 0.45).over(delay = style.d + i * 0.06)) },
    emptyList(),
)

// 窗隙

internal fun lumiereCrossWindow(x: Double, y: Double, w: Double, h: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(
        dpath(listOf(pt(x, y), pt(x + w, y), pt(x + w, y + h), pt(x, y + h), pt(x, y)), style.under(width = 0.002, alpha = 0.55)),
        seg(pt(x + w / 2, y), pt(x + w / 2, y + h), 0.0014, 0.45, style.d + 0.15),
        seg(pt(x, y + h / 2), pt(x + w, y + h / 2), 0.0014, 0.45, style.d + 0.2),
    ),
    listOf(dnode(pt(x + w / 2, y + h / 2), 0.02, style.d + 0.4)),
)

internal fun lumiereArchWindow(x: Double, y: Double, w: Double, h: Double, style: LStyle = S0): LumiereLineArtSpec {
    val r = w / 2
    val top = y + r
    return LumiereLineArtSpec(
        listOf(
            dpath(listOf(pt(x, y + h), pt(x, top)) + lumiereArcPoints(x + r, top, r, PI, TAU, 40) + listOf(pt(x + w, y + h), pt(x, y + h)), style.under(width = 0.0018, alpha = 0.55)),
            seg(pt(x + r, y), pt(x + r, y + h), 0.001, 0.35, style.d + 0.2),
        ),
        listOf(dnode(pt(x + r, y + 0.01), 0.018, style.d + 0.4)),
    )
}

internal fun lumiereRoseWindow(cx: Double, cy: Double, r: Double, style: LStyle = S0) = lumiereMergeSpecs(
    concentric(cx, cy, listOf(r, r * 0.62, r * 0.22), style.under(width = 0.0016, alpha = 0.55)),
    lumiereRays(cx, cy, r * 0.22, r, 12, 0.0, TAU, st(delay = style.d + 0.2)),
    LumiereLineArtSpec(
        List(12) { i ->
            val a = (i / 12.0) * TAU
            lumiereCircle(cx + cos(a) * r * 0.8, cy + sin(a) * r * 0.8, r * 0.14, st(0.0009, 0.35, style.d + 0.35 + i * 0.02))
        },
        listOf(dnode(pt(cx, cy), 0.028, style.d + 0.5)),
    ),
)

internal fun lumiereDoorSlit(cx: Double, top: Double, bottom: Double, gap: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(
        lumiereSegment(pt(cx - gap / 2, top), pt(cx - gap / 2, bottom), style.under(width = 0.0018, alpha = 0.55)),
        lumiereSegment(pt(cx + gap / 2, top), pt(cx + gap / 2, bottom), style.under(width = 0.0018, alpha = 0.55).over(delay = style.d + 0.05)),
        seg(pt(cx - gap / 2 - 0.25, bottom), pt(cx + gap / 2 + 0.25, bottom), 0.001, 0.35, style.d + 0.2),
    ),
    listOf(dnode(pt(cx, top + 0.02), 0.02, style.d + 0.4)),
)

internal fun lumiereGridPanel(x: Double, y: Double, w: Double, h: Double, cols: Int, rows: Int, diagonal: Boolean = false, style: LStyle = S0): LumiereLineArtSpec {
    val paths = mutableListOf(dpath(listOf(pt(x, y), pt(x + w, y), pt(x + w, y + h), pt(x, y + h), pt(x, y)), style.under(width = 0.0018, alpha = 0.5)))
    val inner = st(0.0009, 0.32, style.d + 0.15, 0.25)
    if (diagonal) {
        val n = cols + rows
        for (i in 1 until n) {
            val t = i.toDouble() / n
            paths.add(lumiereSegment(pt(x + w * min(1.0, t * 2), y + h * max(0.0, t * 2 - 1)), pt(x + w * max(0.0, t * 2 - 1), y + h * min(1.0, t * 2)), inner))
            paths.add(lumiereSegment(pt(x + w * (1 - min(1.0, t * 2)), y + h * max(0.0, t * 2 - 1)), pt(x + w * (1 - max(0.0, t * 2 - 1)), y + h * min(1.0, t * 2)), inner))
        }
    } else {
        for (i in 1 until cols) paths.add(lumiereSegment(pt(x + (w * i) / cols, y), pt(x + (w * i) / cols, y + h), inner))
        for (j in 1 until rows) paths.add(lumiereSegment(pt(x, y + (h * j) / rows), pt(x + w, y + (h * j) / rows), inner))
    }
    return LumiereLineArtSpec(paths, emptyList())
}

internal fun lumiereTallWindows(x0: Double, x1: Double, y: Double, h: Double, count: Int, style: LStyle = S0) = lumiereMergeSpecs(
    List(count) { i ->
        val w = ((x1 - x0) / count) * 0.45
        val x = x0 + ((x1 - x0) * (i + 0.5)) / count - w / 2
        lumiereArchWindow(x, y, w, h, style.under(alpha = 0.45).over(delay = style.d + i * 0.07))
    },
)

// 棱镜

internal fun lumiereShards(aspect: Double, random: () -> Double, count: Int, style: LStyle = S0) = LumiereLineArtSpec(
    List(count) { i ->
        val cx = (0.1 + random() * 0.8) * aspect
        val cy = 0.12 + random() * 0.76
        val r = 0.025 + random() * 0.05
        val a = random() * TAU
        val points = listOf(0, 1, 2, 0).map { k -> pt(cx + cos(a + (k * TAU) / 3) * r, cy + sin(a + (k * TAU) / 3) * r * (0.7 + random() * 0.6)) }
        dpath(points, style.under(width = 0.0014, alpha = 0.5).over(delay = style.d + i * 0.04, span = 0.25))
    },
    emptyList(),
)

internal fun lumiereSpectrumLines(x0: Double, x1: Double, y: Double, h: Double, random: () -> Double, count: Int, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(lumiereSegment(pt(x0, y + h / 2), pt(x1, y + h / 2), style.under(width = 0.001, alpha = 0.35))) + List(count) { i ->
        val x = x0 + (x1 - x0) * (0.05 + 0.9 * random())
        val k = 0.3 + random() * 0.7
        seg(pt(x, y + h / 2), pt(x, y + h / 2 - h * k), 0.0012 + random() * 0.0012, 0.4 + k * 0.3, style.d + 0.1 + i * 0.03, 0.2)
    },
    emptyList(),
)

internal fun lumiereCubeSplitter(cx: Double, cy: Double, size: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(
        dpath(listOf(pt(cx - size / 2, cy - size / 2), pt(cx + size / 2, cy - size / 2), pt(cx + size / 2, cy + size / 2), pt(cx - size / 2, cy + size / 2), pt(cx - size / 2, cy - size / 2)), style.under(width = 0.002, alpha = 0.6)),
        seg(pt(cx - size / 2, cy + size / 2), pt(cx + size / 2, cy - size / 2), 0.0012, 0.45, style.d + 0.2),
    ),
    listOf(dnode(pt(cx, cy), 0.022, style.d + 0.35)),
)

internal fun lumiereRainbowArcs(cx: Double, cy: Double, r: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(
        dpath(lumiereArcPoints(cx, cy, r, PI * 1.08, PI * 1.92, 72), style.under(width = 0.0018, alpha = 0.5)),
        dpath(lumiereArcPoints(cx, cy, r * 1.18, PI * 1.1, PI * 1.9, 72), st(0.001, 0.3, style.d + 0.15, dash = doubleArrayOf(0.006, 0.01))),
        lumiereCircle(cx, cy - r * 0.4, 0.03, st(0.0012, 0.45, style.d + 0.3)),
    ),
    emptyList(),
)

// 焦散

internal fun lumiereGlassCup(cx: Double, top: Double, w: Double, h: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(
        dpath(lumiereEllipsePath(cx, top, w / 2, w * 0.12), style.under(width = 0.0016, alpha = 0.5)),
        dpath(listOf(pt(cx - w / 2, top), pt(cx - w * 0.4, top + h), pt(cx + w * 0.4, top + h), pt(cx + w / 2, top)), st(0.0016, 0.5, style.d + 0.1)),
        dpath(lumiereEllipsePath(cx, top + h, w * 0.4, w * 0.09, 0.0, 0.0, PI), st(0.001, 0.35, style.d + 0.2)),
    ),
    listOf(dnode(pt(cx - w * 0.3, top + h * 0.3), 0.016, style.d + 0.35)),
)

internal fun lumiereRipples(cx: Double, cy: Double, r: Double, count: Int, style: LStyle = S0) = LumiereLineArtSpec(
    List(count) { i ->
        dpath(lumiereEllipsePath(cx, cy, r * (i + 1) / count, r * 0.3 * (i + 1) / count), style.under(width = 0.0012, alpha = 0.5 - i * 0.07).over(delay = style.d + i * 0.08))
    },
    listOf(dnode(pt(cx, cy), 0.02, style.d + 0.3)),
)

internal fun lumiereWaterline(aspect: Double, y: Double, amplitude: Double, waves: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(dpath(List(121) { i -> pt((i / 120.0) * aspect, y + sin((i / 120.0) * waves * TAU) * amplitude) }, style.under(width = 0.0014, alpha = 0.45).over(span = 0.6))),
    emptyList(),
)

internal fun lumiereMagnifier(cx: Double, cy: Double, r: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(
        lumiereCircle(cx, cy, r, style.under(width = 0.0022, alpha = 0.6)),
        lumiereCircle(cx, cy, r * 0.9, st(0.0009, 0.3, style.d + 0.1)),
        seg(pt(cx + r * 0.7, cy + r * 0.7), pt(cx + r * 1.6, cy + r * 1.6), 0.004, 0.5, style.d + 0.2),
    ),
    emptyList(),
)

internal fun lumiereCrystal(cx: Double, cy: Double, r: Double, style: LStyle = S0): LumiereLineArtSpec {
    val outer = List(7) { i -> pt(cx + cos((i * TAU) / 6) * r, cy + sin((i * TAU) / 6) * r * 1.3) }
    return LumiereLineArtSpec(
        listOf(dpath(outer, style.under(width = 0.002, alpha = 0.55))) + listOf(0, 1, 2).map { i -> seg(outer[i], outer[i + 3], 0.0009, 0.3, style.d + 0.15 + i * 0.05) },
        outer.subList(0, 6).filterIndexed { i, _ -> i % 2 == 0 }.mapIndexed { i, at -> dnode(at, 0.016, style.d + 0.4, i.toDouble()) },
    )
}

// 光路

internal fun lumiereLens(cx: Double, cy: Double, h: Double, convex: Boolean, aspect: Double, style: LStyle = S0): LumiereLineArtSpec {
    val bulge = h * 0.18
    val top = pt(cx, cy - h / 2)
    val bottom = pt(cx, cy + h / 2)
    fun side(sign: Double): List<LPt> {
        val ox = cx + sign * (if (convex) bulge else -bulge * 0.2) + (if (convex) 0.0 else sign * bulge * 0.6)
        return lumiereCubicPoints(top, pt(ox, cy - h / 4), pt(ox, cy + h / 4), bottom, 32)
    }
    val outline: List<LPt> = if (convex) side(1.0) + side(-1.0).reversed() else
        listOf(pt(cx - bulge * 0.7, cy - h / 2), pt(cx + bulge * 0.7, cy - h / 2)) +
            lumiereCubicPoints(pt(cx + bulge * 0.7, cy - h / 2), pt(cx + bulge * 0.1, cy - h / 4), pt(cx + bulge * 0.1, cy + h / 4), pt(cx + bulge * 0.7, cy + h / 2), 24) +
            listOf(pt(cx - bulge * 0.7, cy + h / 2)) +
            lumiereCubicPoints(pt(cx - bulge * 0.7, cy + h / 2), pt(cx - bulge * 0.1, cy + h / 4), pt(cx - bulge * 0.1, cy - h / 4), pt(cx - bulge * 0.7, cy - h / 2), 24)
    return LumiereLineArtSpec(
        listOf(
            dpath(outline, style.under(width = 0.002, alpha = 0.6)),
            seg(pt(aspect * 0.05, cy), pt(aspect * 0.95, cy), 0.0009, 0.3, style.d + 0.1, 0.5, doubleArrayOf(0.008, 0.008)),
        ),
        emptyList(),
    )
}

internal fun lumiereRayPath(points: List<LPt>, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(dpath(points, style.under(width = 0.0014, alpha = 0.6, span = 0.5))),
    listOf(dnode(points.last(), 0.016, style.d + (style.span ?: 0.5), points.size.toDouble())),
)

internal fun lumiereFocusMark(x: Double, y: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(
        lumiereSegment(pt(x - 0.012, y), pt(x + 0.012, y), style.under(width = 0.0012, alpha = 0.55)),
        lumiereSegment(pt(x, y - 0.012), pt(x, y + 0.012), style.under(width = 0.0012, alpha = 0.55)),
    ),
    listOf(dnode(pt(x, y), 0.024, style.d + 0.3)),
)

internal fun lumiereMirror(cx: Double, cy: Double, length: Double, angle: Double, style: LStyle = S0): LumiereLineArtSpec {
    val dx = cos(angle) * length / 2
    val dy = sin(angle) * length / 2
    val nx = -sin(angle)
    val ny = cos(angle)
    val hatches = List(10) { i ->
        val t = -0.45 + (i / 9.0) * 0.9
        val bx = cx + dx * 2 * t
        val by = cy + dy * 2 * t
        seg(pt(bx, by), pt(bx + nx * 0.015 - dx * 0.04, by + ny * 0.015 - dy * 0.04), 0.0008, 0.3, style.d + 0.2 + i * 0.01, 0.1)
    }
    val na = atan2(-ny, -nx)
    return LumiereLineArtSpec(
        listOf(lumiereSegment(pt(cx - dx, cy - dy), pt(cx + dx, cy + dy), style.under(width = 0.003, alpha = 0.6))) + hatches + listOf(
            seg(pt(cx, cy), pt(cx - nx * length * 0.5, cy - ny * length * 0.5), 0.0009, 0.35, style.d + 0.25, dash = doubleArrayOf(0.006, 0.006)),
            dpath(lumiereArcPoints(cx, cy, length * 0.12, na - 0.5, na + 0.5, 20), st(0.0009, 0.4, style.d + 0.35)),
        ),
        emptyList(),
    )
}

internal fun lumiereInterfaceLine(aspect: Double, y: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(lumiereSegment(pt(aspect * 0.06, y), pt(aspect * 0.94, y), style.under(width = 0.002, alpha = 0.55).over(span = 0.5))) + List(24) { i ->
        val x = aspect * (0.08 + (i / 23.0) * 0.84)
        seg(pt(x, y + 0.01), pt(x - 0.02, y + 0.04), 0.0008, 0.22, style.d + 0.2 + i * 0.01, 0.1)
    },
    emptyList(),
)

internal fun lumiereRuler(x0: Double, x1: Double, y: Double, ticks: Int, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(lumiereSegment(pt(x0, y), pt(x1, y), style.under(width = 0.0016, alpha = 0.5).over(span = 0.5))) + List(ticks + 1) { i ->
        val x = x0 + ((x1 - x0) * i) / ticks
        val long = i % 5 == 0
        seg(pt(x, y), pt(x, y + (if (long) 0.02 else 0.01)), 0.0008, 0.4, style.d + 0.2 + i * 0.005, 0.08)
    },
    emptyList(),
)

internal fun lumiereFiber(p0: LPt, p1: LPt, p2: LPt, p3: LPt, thickness: Double, style: LStyle = S0): LumiereLineArtSpec {
    val center = lumiereCubicPoints(p0, p1, p2, p3, 60)
    fun offset(sign: Double) = center.mapIndexed { i, p ->
        val a = center[max(0, i - 1)]
        val b = center[min(center.size - 1, i + 1)]
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len = hypot(dx, dy).let { if (it == 0.0) 1.0 else it }
        pt(p.x - (dy / len) * thickness * sign, p.y + (dx / len) * thickness * sign)
    }
    return LumiereLineArtSpec(
        listOf(
            dpath(offset(1.0), style.under(width = 0.0014, alpha = 0.5).over(span = 0.6)),
            dpath(offset(-1.0), style.under(width = 0.0014, alpha = 0.5).over(delay = style.d + 0.05, span = 0.6)),
        ),
        listOf(dnode(center.last(), 0.024, style.d + 0.6)),
    )
}

internal fun lumierePinholeBox(cx: Double, cy: Double, w: Double, h: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(
        dpath(listOf(pt(cx - w / 2, cy - h / 2), pt(cx + w / 2, cy - h / 2), pt(cx + w / 2, cy + h / 2), pt(cx - w / 2, cy + h / 2), pt(cx - w / 2, cy - h / 2)), style.under(width = 0.0018, alpha = 0.5)),
        seg(pt(cx - w / 2 - 0.25, cy - h * 0.35), pt(cx - w / 2 - 0.25, cy + h * 0.35), 0.0016, 0.45, style.d + 0.1),
        seg(pt(cx + w / 2 - 0.02, cy + h * 0.28), pt(cx + w / 2 - 0.02, cy - h * 0.28), 0.0014, 0.4, style.d + 0.3, dash = doubleArrayOf(0.005, 0.005)),
        seg(pt(cx - w / 2 - 0.25, cy - h * 0.35), pt(cx + w / 2 - 0.02, cy + h * 0.28), 0.0008, 0.3, style.d + 0.2),
        seg(pt(cx - w / 2 - 0.25, cy + h * 0.35), pt(cx + w / 2 - 0.02, cy - h * 0.28), 0.0008, 0.3, style.d + 0.2),
    ),
    listOf(dnode(pt(cx - w / 2, cy), 0.02, style.d + 0.3)),
)

// 干涉

internal fun lumiereRings(cx: Double, cy: Double, r: Double, count: Int, style: LStyle = S0) = concentric(cx, cy, List(count) { i -> r * sqrt((i + 1).toDouble() / count) }, style)

internal fun lumiereGrating(cx: Double, y: Double, width: Double, slits: Int, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(lumiereSegment(pt(cx - width / 2, y), pt(cx + width / 2, y), style.under(width = 0.0022, alpha = 0.55))) + List(slits) { i ->
        val x = cx - width * 0.3 + (width * 0.6 * i) / max(1, slits - 1)
        seg(pt(x, y - 0.01), pt(x, y + 0.01), 0.0008, 0.4, style.d + 0.15 + i * 0.01, 0.1)
    },
    emptyList(),
)

internal fun lumiereTwoSources(cx: Double, cy: Double, separation: Double, r: Double, style: LStyle = S0) = lumiereMergeSpecs(
    concentric(cx - separation / 2, cy, listOf(r * 0.25, r * 0.5, r * 0.75, r), style.under(alpha = 0.3)),
    concentric(cx + separation / 2, cy, listOf(r * 0.25, r * 0.5, r * 0.75, r), style.under(alpha = 0.3).over(delay = style.d + 0.1)),
    LumiereLineArtSpec(emptyList(), listOf(dnode(pt(cx - separation / 2, cy), 0.024, 0.3), dnode(pt(cx + separation / 2, cy), 0.024, 0.35, 1.0))),
)

internal fun lumiereMoire(cx: Double, cy: Double, size: Double, count: Int, rotation: Double, style: LStyle = S0): LumiereLineArtSpec {
    fun set(angle: Double, delay: Double) = List(count) { i ->
        val t = -0.5 + i.toDouble() / (count - 1)
        val ox = cos(angle + PI / 2) * t * size
        val oy = sin(angle + PI / 2) * t * size
        lumiereSegment(pt(cx + ox - cos(angle) * size / 2, cy + oy - sin(angle) * size / 2), pt(cx + ox + cos(angle) * size / 2, cy + oy + sin(angle) * size / 2),
            style.under(width = 0.0008, alpha = 0.3).over(delay = delay + i * 0.008, span = 0.15))
    }
    return LumiereLineArtSpec(set(0.0, style.d) + set(rotation, style.d + 0.2), emptyList())
}

internal fun lumiereStandingWave(x0: Double, x1: Double, y: Double, amplitude: Double, loops: Int, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(-1.0, -0.5, 0.5, 1.0).mapIndexed { j, k ->
        dpath(List(121) { i -> val t = i / 120.0; pt(x0 + (x1 - x0) * t, y + sin(t * loops * PI) * amplitude * k) },
            style.under(width = 0.0012, alpha = 0.35 + abs(k) * 0.2).over(delay = style.d + j * 0.06, span = 0.5))
    },
    List(loops + 1) { i -> dnode(pt(x0 + ((x1 - x0) * i) / loops, y), 0.014, style.d + 0.5, i.toDouble()) },
)

internal fun lumierePolarizers(cx: Double, cy: Double, r: Double, gap: Double, rotation: Double, style: LStyle = S0): LumiereLineArtSpec {
    fun disc(x: Double, angle: Double, delay: Double): List<LumiereLinePath> =
        listOf(lumiereCircle(x, cy, r, style.under(width = 0.0016, alpha = 0.5).over(delay = delay))) + List(7) { i ->
            val t = -0.75 + (i / 6.0) * 1.5
            val half = sqrt(max(0.0, 1 - t * t)) * r
            val ox = -sin(angle) * t * r
            val oy = cos(angle) * t * r
            seg(pt(x + ox - cos(angle) * half, cy + oy - sin(angle) * half), pt(x + ox + cos(angle) * half, cy + oy + sin(angle) * half), 0.0008, 0.3, delay + 0.15 + i * 0.02, 0.15)
        }
    return LumiereLineArtSpec(disc(cx - gap / 2, 0.0, style.d) + disc(cx + gap / 2, rotation, style.d + 0.2), emptyList())
}

// 叶脉

internal fun lumiereBigLeaf(cx: Double, cy: Double, length: Double, angle: Double, style: LStyle = S0): LumiereLineArtSpec {
    val dx = cos(angle); val dy = sin(angle)
    val nx = -dy; val ny = dx
    fun at(along: Double, across: Double) = pt(cx + dx * length * (along - 0.5) + nx * across, cy + dy * length * (along - 0.5) + ny * across)
    val bulge = length * 0.3
    val paths = mutableListOf(
        dpath(lumiereCubicPoints(at(0.0, 0.0), at(0.3, bulge), at(0.75, bulge * 0.8), at(1.0, 0.0), 48), style.under(width = 0.002, alpha = 0.6).over(span = 0.4)),
        dpath(lumiereCubicPoints(at(0.0, 0.0), at(0.3, -bulge), at(0.75, -bulge * 0.8), at(1.0, 0.0), 48), style.under(width = 0.002, alpha = 0.6).over(delay = style.d + 0.05, span = 0.4)),
        dpath(listOf(at(-0.12, 0.0), at(1.0, 0.0)), st(0.0014, 0.5, style.d + 0.15, 0.4)),
    )
    for (v in 1..6) {
        val f = v / 7.5
        for (s in doubleArrayOf(1.0, -1.0)) {
            paths.add(dpath(lumiereCubicPoints(at(f, 0.0), at(f + 0.05, s * bulge * 0.3), at(f + 0.1, s * bulge * 0.55), at(f + 0.15, s * bulge * (0.8 - f * 0.35)), 16),
                st(0.0009, 0.4, style.d + 0.25 + v * 0.05, 0.18)))
        }
    }
    return LumiereLineArtSpec(paths, listOf(dnode(at(1.0, 0.0), 0.026, style.d + 0.6)))
}

internal fun lumiereCanopy(aspect: Double, random: () -> Double, style: LStyle = S0): LumiereLineArtSpec {
    val paths = mutableListOf<LumiereLinePath>()
    fun grow(x: Double, y: Double, angle: Double, length: Double, depth: Int, delay: Double) {
        val x1 = x + cos(angle) * length
        val y1 = y + sin(angle) * length
        paths.add(seg(pt(x, y), pt(x1, y1), 0.0008 + depth * 0.0005, 0.3 + depth * 0.08, delay, 0.2))
        if (depth <= 0) return
        grow(x1, y1, angle - 0.35 - random() * 0.3, length * 0.72, depth - 1, delay + 0.08)
        grow(x1, y1, angle + 0.35 + random() * 0.3, length * 0.72, depth - 1, delay + 0.08)
    }
    listOf(0.08, 0.35, 0.65, 0.92).forEachIndexed { i, fx -> grow(fx * aspect, -0.02, PI / 2 + (random() - 0.5) * 0.6, 0.14, 4, style.d + i * 0.05) }
    return LumiereLineArtSpec(paths, emptyList())
}

internal fun lumiereFernCurl(cx: Double, cy: Double, r: Double, style: LStyle = S0): LumiereLineArtSpec {
    val spiral = List(140) { i ->
        val t = i / 139.0
        val a = t * TAU * 2.2
        val rr = r * exp(-t * 2.4)
        pt(cx + cos(a) * rr, cy + sin(a) * rr)
    }
    val stem = listOf(pt(cx + r, cy), pt(cx + r, cy + r * 2.2))
    val leaflets = List(9) { i ->
        val p = spiral[floor((i / 9.0) * 90).toInt()]
        lumiereCircle(p.x, p.y, 0.008 + (1 - i / 9.0) * 0.01, st(0.0008, 0.35, style.d + 0.4 + i * 0.03, 0.1))
    }
    return LumiereLineArtSpec(listOf(dpath(stem, style.under(width = 0.0016, alpha = 0.5)), dpath(spiral, style.under(width = 0.0016, alpha = 0.55).over(delay = style.d + 0.1, span = 0.5))) + leaflets, emptyList())
}

internal fun lumiereSeedRoots(cx: Double, cy: Double, random: () -> Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(dpath(lumiereEllipsePath(cx, cy, 0.025, 0.035, 0.3), style.under(width = 0.002, alpha = 0.6))) + List(6) { i ->
        val spread = (i - 2.5) * 0.05
        val ex = cx + spread * 1.4 + (random() - 0.5) * 0.04
        val ey = cy + 0.2 + random() * 0.06
        dpath(lumiereCubicPoints(pt(cx, cy + 0.03), pt(cx + spread * 0.4, cy + 0.08), pt(cx + spread, cy + 0.12), pt(ex, ey), 24), st(0.0009, 0.4, style.d + 0.2 + i * 0.05, 0.35))
    },
    listOf(dnode(pt(cx, cy - 0.04), 0.03, style.d + 0.2)),
)

internal fun lumiereBloom(cx: Double, cy: Double, r: Double, petals: Int, style: LStyle = S0) = LumiereLineArtSpec(
    List(petals) { i ->
        val a = (i.toDouble() / petals) * TAU
        val dx = cos(a); val dy = sin(a)
        val nx = -dy; val ny = dx
        val tip = pt(cx + dx * r, cy + dy * r)
        val w = r * 0.35
        dpath(
            lumiereCubicPoints(pt(cx, cy), pt(cx + dx * r * 0.4 + nx * w, cy + dy * r * 0.4 + ny * w), pt(cx + dx * r * 0.9 + nx * w * 0.6, cy + dy * r * 0.9 + ny * w * 0.6), tip, 20) +
                lumiereCubicPoints(tip, pt(cx + dx * r * 0.9 - nx * w * 0.6, cy + dy * r * 0.9 - ny * w * 0.6), pt(cx + dx * r * 0.4 - nx * w, cy + dy * r * 0.4 - ny * w), pt(cx, cy), 20),
            style.under(width = 0.0016, alpha = 0.5).over(delay = style.d + i * 0.05, span = 0.3),
        )
    } + listOf(lumiereCircle(cx, cy, r * 0.12, st(0.0012, 0.5, style.d + 0.4))),
    listOf(dnode(pt(cx, cy), 0.03, style.d + 0.5)),
)

internal fun lumiereVine(x: Double, bottom: Double, top: Double, amplitude: Double, turns: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(
        dpath(List(161) { i -> val t = i / 160.0; pt(x + sin(t * turns * TAU) * amplitude * (1 - t * 0.4), bottom + (top - bottom) * t) }, style.under(width = 0.0016, alpha = 0.55).over(span = 0.7)),
        seg(pt(x, bottom), pt(x, top), 0.0008, 0.25, style.d + 0.1, 0.5, doubleArrayOf(0.005, 0.008)),
    ),
    listOf(dnode(pt(x, top), 0.024, style.d + 0.7)),
)

internal fun lumiereCells(cx: Double, cy: Double, r: Double, random: () -> Double, count: Int, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(lumiereCircle(cx, cy, r, style.under(width = 0.0022, alpha = 0.5))) + List(count) { i ->
        val a = random() * TAU
        val d = sqrt(random()) * r * 0.8
        val rx = 0.012 + random() * 0.012
        val ry = 0.008 + random() * 0.008
        val rot = random() * PI
        dpath(lumiereEllipsePath(cx + cos(a) * d, cy + sin(a) * d, rx, ry, rot), st(0.0009, 0.4, style.d + 0.15 + i * 0.02, 0.15))
    },
    emptyList(),
)

internal fun lumiereMolecule(cx: Double, cy: Double, r: Double, style: LStyle = S0): LumiereLineArtSpec {
    val hex = List(7) { i -> pt(cx + cos((i * TAU) / 6 + PI / 6) * r, cy + sin((i * TAU) / 6 + PI / 6) * r) }
    return LumiereLineArtSpec(
        listOf(dpath(hex, style.under(width = 0.0018, alpha = 0.55)), lumiereCircle(cx, cy, r * 0.55, st(0.001, 0.35, style.d + 0.2))) +
            listOf(0, 2, 4).mapIndexed { i, k -> val p = hex[k]; seg(p, pt(p.x + (p.x - cx) * 0.8, p.y + (p.y - cy) * 0.8), 0.0012, 0.45, style.d + 0.3 + i * 0.05) },
        listOf(0, 2, 4).mapIndexed { i, k -> val p = hex[k]; dnode(pt(p.x + (p.x - cx) * 0.8, p.y + (p.y - cy) * 0.8), 0.02, style.d + 0.45, i.toDouble()) },
    )
}

// 星象

internal fun lumiereOrbits(cx: Double, cy: Double, radii: List<Double>, tilt: Double, flatten: Double, random: () -> Double, style: LStyle = S0): LumiereLineArtSpec {
    val paths = radii.mapIndexed { i, r -> dpath(lumiereEllipsePath(cx, cy, r, r * flatten, tilt), style.under(width = 0.0012, alpha = 0.45).over(delay = style.d + i * 0.07, span = 0.5)) }
    val nodes = listOf(dnode(pt(cx, cy), 0.04, style.d + 0.2)) + radii.mapIndexed { i, r ->
        val a = random() * TAU
        val p = lumiereEllipsePath(cx, cy, r, r * flatten, tilt, a, a, 1)[0]
        dnode(p, 0.018 + random() * 0.012, style.d + 0.5 + i * 0.05, i.toDouble())
    }
    return LumiereLineArtSpec(paths, nodes)
}

internal fun lumiereStarTrails(cx: Double, cy: Double, random: () -> Double, count: Int, maxR: Double, style: LStyle = S0) = LumiereLineArtSpec(
    List(count) { i ->
        val r = maxR * (0.1 + random() * 0.9)
        val a = random() * TAU
        val span = 0.4 + random() * 0.8
        val pts = lumiereArcPoints(cx, cy, r, a, a + span, 24)
        val w = 0.0008 + random() * 0.0008
        val al = 0.25 + random() * 0.3
        dpath(pts, style.under(width = w, alpha = al).over(delay = style.d + (i.toDouble() / count) * 0.4, span = 0.3))
    },
    listOf(dnode(pt(cx, cy), 0.03, style.d + 0.1)),
)

internal fun lumiereSextant(cx: Double, cy: Double, r: Double, style: LStyle = S0) = lumiereMergeSpecs(
    LumiereLineArtSpec(
        listOf(
            dpath(lumiereArcPoints(cx, cy, r, PI * 0.33, PI * 0.67, 48), style.under(width = 0.002, alpha = 0.6)),
            seg(pt(cx, cy), pt(cx + cos(PI * 0.33) * r, cy + sin(PI * 0.33) * r), 0.0014, 0.5, style.d + 0.1),
            seg(pt(cx, cy), pt(cx + cos(PI * 0.67) * r, cy + sin(PI * 0.67) * r), 0.0014, 0.5, style.d + 0.12),
            seg(pt(cx, cy), pt(cx + cos(PI * 0.45) * r * 1.1, cy + sin(PI * 0.45) * r * 1.1), 0.001, 0.45, style.d + 0.3, dash = doubleArrayOf(0.006, 0.006)),
            seg(pt(cx - r * 0.9, cy - r * 0.2), pt(cx + r * 0.2, cy - r * 0.05), 0.0009, 0.35, style.d + 0.35, dash = doubleArrayOf(0.004, 0.006)),
        ),
        listOf(dnode(pt(cx, cy), 0.022, style.d + 0.3)),
    ),
    lumiereRays(cx, cy, r * 0.94, r, 30, PI * 0.33, PI * 0.67, st(delay = style.d + 0.2, alpha = 0.4)),
)

internal fun lumiereConstellation(aspect: Double, random: () -> Double, count: Int, region: DoubleArray, style: LStyle = S0): LumiereLineArtSpec {
    val x0 = region[0]; val y0 = region[1]; val x1 = region[2]; val y1 = region[3]
    val stars = List(count) { val x = (x0 + random() * (x1 - x0)) * aspect; val y = y0 + random() * (y1 - y0); pt(x, y) }.sortedBy { it.x }
    val paths = stars.drop(1).mapIndexed { i, s -> lumiereSegment(stars[i], s, style.under(width = 0.001, alpha = 0.4).over(delay = style.d + 0.2 + i * 0.06, span = 0.2)) }
    val nodes = stars.mapIndexed { i, at -> dnode(at, 0.018 + random() * 0.016, style.d + i * 0.05, i.toDouble()) }
    return LumiereLineArtSpec(paths, nodes)
}

internal fun lumiereCorona(cx: Double, cy: Double, r: Double, random: () -> Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(lumiereCircle(cx, cy, r, style.under(width = 0.0024, alpha = 0.65))) + List(40) { i ->
        val a = (i / 40.0) * TAU + random() * 0.05
        val len = r * (0.25 + random() * 0.9)
        val al = 0.25 + random() * 0.3
        seg(pt(cx + cos(a) * r * 1.05, cy + sin(a) * r * 1.05), pt(cx + cos(a) * (r + len), cy + sin(a) * (r + len)), 0.0008, al, style.d + 0.2 + (i / 40.0) * 0.3, 0.15)
    },
    listOf(dnode(pt(cx + r * 0.7, cy - r * 0.7), 0.03, style.d + 0.5)),
)

internal fun lumiereCrescent(cx: Double, cy: Double, r: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(
        dpath(lumiereArcPoints(cx, cy, r, PI * 0.35, PI * 1.65, 60), style.under(width = 0.002, alpha = 0.6)),
        dpath(lumiereArcPoints(cx + r * 0.45, cy, r * 0.85, PI * 0.58, PI * 1.42, 60), st(0.0012, 0.4, style.d + 0.15)),
    ),
    emptyList(),
)

internal fun lumiereArmillary(cx: Double, cy: Double, r: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(
        lumiereCircle(cx, cy, r, style.under(width = 0.002, alpha = 0.55)),
        dpath(lumiereEllipsePath(cx, cy, r, r * 0.3, 0.0), st(0.0014, 0.45, style.d + 0.1)),
        dpath(lumiereEllipsePath(cx, cy, r, r * 0.3, 0.41), st(0.0014, 0.45, style.d + 0.15)),
        dpath(lumiereEllipsePath(cx, cy, r * 0.3, r, 0.0), st(0.0012, 0.4, style.d + 0.2)),
        seg(pt(cx - sin(0.41) * r * 1.25, cy - cos(0.41) * r * 1.25), pt(cx + sin(0.41) * r * 1.25, cy + cos(0.41) * r * 1.25), 0.0012, 0.45, style.d + 0.3),
    ),
    listOf(dnode(pt(cx, cy), 0.03, style.d + 0.3)),
)

internal fun lumiereMeteors(aspect: Double, random: () -> Double, count: Int, style: LStyle = S0): LumiereLineArtSpec {
    val paths = mutableListOf<LumiereLinePath>()
    val nodes = mutableListOf<LumiereLineNode>()
    for (i in 0 until count) {
        val x = (0.2 + random() * 0.8) * aspect
        val y = random() * 0.5
        val len = 0.12 + random() * 0.25
        val head = pt(x - len * 0.8, y + len * 0.6)
        paths.add(lumiereSegment(pt(x, y), head, style.under(width = 0.0012 + random() * 0.001, alpha = 0.45).over(delay = style.d + i * 0.08, span = 0.2)))
        nodes.add(dnode(head, 0.02, style.d + i * 0.08 + 0.2, i.toDouble()))
    }
    return LumiereLineArtSpec(paths, nodes)
}

// 追光

internal fun lumiereLampHead(x: Double, y: Double, angle: Double, size: Double, style: LStyle = S0): LumiereLineArtSpec {
    val dx = cos(angle); val dy = sin(angle)
    val nx = -dy; val ny = dx
    val bx = x - dx * size; val by = y - dy * size
    val corners = listOf(
        pt(bx + nx * size * 0.35, by + ny * size * 0.35),
        pt(x + nx * size * 0.6, y + ny * size * 0.6),
        pt(x - nx * size * 0.6, y - ny * size * 0.6),
        pt(bx - nx * size * 0.35, by - ny * size * 0.35),
    )
    return LumiereLineArtSpec(listOf(dpath(corners + corners[0], style.under(width = 0.0018, alpha = 0.55, span = 0.3))), emptyList())
}

internal fun lumiereCurtains(aspect: Double, gap: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(-1.0, 1.0).flatMap { side ->
        List(6) { i ->
            val base = aspect / 2 + side * (gap / 2 + i * 0.05)
            dpath(List(41) { k -> val t = k / 40.0; pt(base + sin(t * 5 + i) * 0.008 * side, -0.02 + t * 1.04) },
                style.under(width = 0.001 + (5 - i) * 0.0002, alpha = 0.2 + (5 - i) * 0.05).over(delay = style.d + i * 0.05, span = 0.5))
        }
    },
    emptyList(),
)

internal fun lumiereProjector(x: Double, y: Double, size: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(
        dpath(listOf(pt(x - size, y - size * 0.3), pt(x, y - size * 0.3), pt(x, y + size * 0.3), pt(x - size, y + size * 0.3), pt(x - size, y - size * 0.3)), style.under(width = 0.0018, alpha = 0.55)),
        lumiereCircle(x - size * 0.75, y - size * 0.65, size * 0.33, st(0.0014, 0.5, style.d + 0.1)),
        lumiereCircle(x - size * 0.25, y - size * 0.65, size * 0.33, st(0.0014, 0.5, style.d + 0.15)),
        dpath(listOf(pt(x, y - size * 0.12), pt(x + size * 0.18, y - size * 0.18), pt(x + size * 0.18, y + size * 0.18), pt(x, y + size * 0.12)), st(0.0014, 0.5, style.d + 0.2)),
    ),
    listOf(dnode(pt(x + size * 0.18, y), 0.024, style.d + 0.3)),
)

// 通用

internal fun lumiereHorizon(aspect: Double, y: Double, style: LStyle = S0) = LumiereLineArtSpec(
    listOf(lumiereSegment(pt(0.0, y), pt(aspect, y), style.under(width = 0.0014, alpha = 0.4).over(span = 0.6))) +
        List(16) { i -> seg(pt(aspect * (i + 0.5) / 16, y), pt(aspect * (i + 0.5) / 16, y + 0.012), 0.0008, 0.3, style.d + 0.3 + i * 0.01, 0.08) },
    emptyList(),
)

// ───────────────────────────── rigs/base.ts + 各光位的 lineArt(context) ─────────────────────────────

/** 量角器光环 + 取景框 + 散落闪点（参考图的线稿）。 */
internal fun lumiereStandardArt(aspect: Double, random: () -> Double, haloRadius: Double = 0.3) = lumiereMergeSpecs(
    lumiereProtractorHalo(aspect * 0.5, 0.03, haloRadius, alpha = 0.55),
    lumiereViewfinderFrame(aspect, 0.07, 0.21, 0.93, 0.8, delay = 0.1, alpha = 0.32),
    lumiereScatteredSparks(aspect, 16, random, 0.3),
)

/** 只有取景框与闪点。 */
internal fun lumiereFrameArt(aspect: Double, random: () -> Double) = lumiereMergeSpecs(
    lumiereViewfinderFrame(aspect, 0.07, 0.21, 0.93, 0.8, delay = 0.1, alpha = 0.28),
    lumiereScatteredSparks(aspect, 14, random, 0.3),
)

internal fun lumiereBaseEllipse(cx: Double, cy: Double, rx: Double, ry: Double, steps: Int = 96): List<LPt> =
    lumiereArcPoints(0.0, 0.0, 1.0, 0.0, PI * 2, steps).map { p -> pt(cx + p.x * rx, cy + p.y * ry) }

private fun reg(a: Double, b: Double, c: Double, d: Double) = doubleArrayOf(a, b, c, d)
private const val OPTICS_AXIS = 0.4

/** rigs/stage.ts 的 lamp()：从 a 指向 b（画面比例）的一盏灯；线稿只用到 x、y、angle。 */
private fun stageLamp(aspect: Double, ax: Double, ay: Double, bx: Double, by: Double) = Triple(ax, ay, atan2(by - ay, (bx - ax) * aspect))

/** rigs/stage.ts 的 lampsArt：每盏灯的灯头线稿 + 闪点。 */
private fun stageLampsArt(aspect: Double, random: () -> Double, beams: List<Triple<Double, Double, Double>>) = lumiereMergeSpecs(
    beams.mapIndexed { i, (x, y, angle) -> lumiereLampHead(x * aspect, y, angle, 0.035, st(delay = i * 0.05)) } + listOf(lumiereScatteredSparks(aspect, 10, random, 0.3)),
)

/** rigs/optics.ts 的 rayArt：画面比例的点列 → 光线（x 乘画幅比）。 */
private fun opticsRayArt(aspect: Double, points: List<DoubleArray>, delay: Double = 0.2) = lumiereRayPath(points.map { pt(it[0] * aspect, it[1]) }, st(delay = delay))
private fun fr(x: Double, y: Double) = doubleArrayOf(x, y)

/**
 * 每个光位的 lineArt(context)（rigs 目录各文件）。未知的 kind 走 familyOf 的缺省 standardArt。
 * 各族里的 sparks 助手按族定义：lattice（14 个、可给区域，缺省 [0.3,0.3,0.95,0.9]）、prism 16、caustic 可给数量（缺省 10）、
 * optics 10 个（区域 [0.05,0.05,0.95,0.6]）、wave 12、botany 12、astral 20、motes 族缺省 10。
 */
internal fun lumiereProfileLineArt(kind: String, aspect: Double, rng: LumiereRng): LumiereLineArtSpec {
    val random: () -> Double = { rng.next() }
    val a = aspect
    fun latticeSparks(region: DoubleArray? = null) = lumiereScatteredSparks(a, 14, random, 0.3, region ?: reg(0.3, 0.3, 0.95, 0.9))
    fun prismSparks() = lumiereScatteredSparks(a, 16, random, 0.3)
    fun causticSparks(count: Int = 10) = lumiereScatteredSparks(a, count, random, 0.3)
    fun opticsSparks() = lumiereScatteredSparks(a, 10, random, 0.3, reg(0.05, 0.05, 0.95, 0.6))
    fun waveSparks() = lumiereScatteredSparks(a, 12, random, 0.3)
    fun botanySparks() = lumiereScatteredSparks(a, 12, random, 0.3)
    fun astralSparks() = lumiereScatteredSparks(a, 20, random, 0.3)
    val axis = OPTICS_AXIS
    return when (kind) {
        // 天光（zenith）
        "zenith-halo" -> lumiereMergeSpecs(
            lumiereProtractorHalo(a * 0.5, 0.03, 0.42, alpha = 0.7, spokeStep = PI / 18),
            lumiereProtractorHalo(a * 0.5, 0.03, 0.24, alpha = 0.4, delay = 0.2),
            lumiereScatteredSparks(a, 20, random, 0.3),
        )
        "zenith-twin" -> lumiereMergeSpecs(
            lumiereViewfinderFrame(a, 0.07, 0.21, 0.93, 0.8, alpha = 0.32),
            lumiereScatteredSparks(a, 18, random, 0.2),
        )
        "zenith-well" -> LumiereLineArtSpec(
            listOf(
                LumiereLinePath(lumiereBaseEllipse(a * 0.5, 0.8, 0.26, 0.05), 0.0018, 0.7, 0.1, 0.5),
                LumiereLinePath(lumiereBaseEllipse(a * 0.5, 0.8, 0.17, 0.032), 0.0012, 0.45, 0.2, 0.4, doubleArrayOf(0.004, 0.008)),
            ),
            listOf(LumiereLineNode(pt(a * 0.5, 0.8), 0.03, 0.3, 0.0)),
        )
        // 窗隙（lattice）
        "lattice-blinds" -> lumiereMergeSpecs(lumiereBlindsWindow(a * 0.03, 0.02, a * 0.16, 0.26, 9, alpha = 0.5), latticeSparks())
        "lattice-cross" -> lumiereMergeSpecs(lumiereCrossWindow(a * 0.8, 0.01, a * 0.16, 0.26), latticeSparks(reg(0.05, 0.3, 0.7, 0.9)))
        "lattice-arch" -> lumiereMergeSpecs(lumiereArchWindow(a * 0.08, -0.06, a * 0.12, 0.32), latticeSparks())
        "lattice-rose" -> lumiereMergeSpecs(lumiereRoseWindow(a * 0.5, 0.08, 0.12), lumiereScatteredSparks(a, 16, random, 0.3))
        "lattice-slit" -> lumiereMergeSpecs(lumiereDoorSlit(a * 0.1, 0.08, 0.86, 0.012), lumiereScatteredSparks(a, 10, random, 0.3, reg(0.2, 0.2, 0.95, 0.9)))
        "lattice-shoji" -> lumiereMergeSpecs(lumiereGridPanel(a * 0.02, 0.06, a * 0.18, 0.4, 3, 5), lumiereScatteredSparks(a, 10, random, 0.3))
        "lattice-grille" -> lumiereMergeSpecs(lumiereGridPanel(a * 0.02, 0.02, a * 0.16, 0.26, 4, 4, true), latticeSparks())
        "lattice-sweep" -> lumiereMergeSpecs(lumiereBlindsWindow(a * 0.03, 0.02, a * 0.16, 0.26, 8, alpha = 0.45), latticeSparks())
        "lattice-komorebi" -> lumiereMergeSpecs(lumiereCanopy(a, random), lumiereScatteredSparks(a, 12, random, 0.3))
        "lattice-nave" -> lumiereMergeSpecs(lumiereTallWindows(a * 0.05, a * 0.95, -0.08, 0.26, 4), lumiereScatteredSparks(a, 18, random, 0.3))
        // 棱镜（prism）
        "prism-split" -> lumiereMergeSpecs(lumierePrismTriangle(a * 0.42, 0.42, 0.2, alpha = 0.8), prismSparks())
        "prism-fringe" -> lumiereStandardArt(a, random)
        "prism-shard" -> lumiereMergeSpecs(lumiereShards(a, random, 8), lumiereScatteredSparks(a, 18, random, 0.3))
        "prism-lines" -> lumiereMergeSpecs(lumiereSpectrumLines(a * 0.12, a * 0.88, 0.84, 0.16, random, 18), lumiereScatteredSparks(a, 10, random, 0.3))
        "prism-merge" -> lumiereMergeSpecs(lumierePrismTriangle(a * 0.34, 0.46, 0.12, alpha = 0.6), prismSparks())
        "prism-cube" -> lumiereMergeSpecs(lumiereCubeSplitter(a * 0.42, 0.34, 0.08), lumiereScatteredSparks(a, 12, random, 0.3))
        "prism-wheel" -> lumiereMergeSpecs(lumierePrismTriangle(a * 0.5, 0.1, 0.1, alpha = 0.6), prismSparks())
        "prism-droplet" -> lumiereMergeSpecs(lumiereRainbowArcs(a * 0.5, 1.02, 0.62), lumiereScatteredSparks(a, 12, random, 0.3))
        // 焦散（caustic）
        "caustic-pool" -> LumiereLineArtSpec(
            listOf(LumiereLinePath(lumiereBaseEllipse(a * 0.5, 0.82, a * 0.42, 0.14), 0.0014, 0.4, 0.1, 0.6)),
            causticSparks().nodes,
        )
        "caustic-cup" -> lumiereMergeSpecs(lumiereGlassCup(a * 0.24, 0.52, 0.1, 0.2), causticSparks())
        "caustic-ripple" -> lumiereMergeSpecs(lumiereRipples(a * 0.5, 0.8, 0.34, 5), causticSparks())
        "caustic-surface" -> lumiereMergeSpecs(lumiereWaterline(a, 0.1, 0.012, 7.0), causticSparks(14))
        "caustic-ring" -> lumiereMergeSpecs(
            LumiereLineArtSpec(listOf(lumiereCircle(a * 0.5, 0.5, 0.36, st(0.0024, 0.6)), lumiereCircle(a * 0.5, 0.5, 0.32, st(0.0009, 0.3, 0.1))), emptyList()),
            causticSparks(),
        )
        "caustic-burn" -> lumiereMergeSpecs(lumiereMagnifier(a * 0.5, 0.2, 0.1), causticSparks(12))
        "caustic-shimmer" -> lumiereMergeSpecs(lumiereWaterline(a, 0.86, 0.008, 9.0), causticSparks())
        "caustic-crystal" -> lumiereMergeSpecs(lumiereCrystal(a * 0.5, 0.26, 0.05), causticSparks(14))
        "caustic-drift" -> causticSparks(8)
        "caustic-storm" -> lumiereMergeSpecs(lumiereRipples(a * 0.5, 0.82, 0.5, 4), causticSparks(14))
        // 光路（optics）
        "optics-convex" -> lumiereMergeSpecs(
            listOf(lumiereLens(a * 0.42, axis, 0.3, true, a)) +
                listOf(0.3, 0.4, 0.5).mapIndexed { i, y -> opticsRayArt(a, listOf(fr(0.02, y), fr(0.42, y), fr(0.7, axis), fr(0.92, axis + (axis - y) * 0.8)), 0.2 + i * 0.08) } +
                listOf(lumiereFocusMark(a * 0.7, axis, st(delay = 0.5)), opticsSparks()),
        )
        "optics-concave" -> lumiereMergeSpecs(
            listOf(lumiereLens(a * 0.42, axis, 0.28, false, a)) +
                listOf(0.32, 0.48).mapIndexed { i, y ->
                    lumiereMergeSpecs(
                        opticsRayArt(a, listOf(fr(0.02, y), fr(0.42, y), fr(0.62, y + (y - axis) * 2.2)), 0.2 + i * 0.1),
                        LumiereLineArtSpec(listOf(lumiereSegment(pt(a * 0.42, y), pt(a * 0.26, axis), st(0.0009, 0.35, 0.5, dash = doubleArrayOf(0.005, 0.006)))), emptyList()),
                    )
                } +
                listOf(lumiereFocusMark(a * 0.26, axis, st(delay = 0.6)), opticsSparks()),
        )
        "optics-mirror" -> lumiereMergeSpecs(
            lumiereMirror(a * 0.5, 0.6, 0.6, 0.0),
            opticsRayArt(a, listOf(fr(0.12, 0.08), fr(0.5, 0.6), fr(0.88, 0.08))),
            opticsSparks(),
        )
        "optics-refract" -> lumiereMergeSpecs(
            lumiereInterfaceLine(a, 0.5),
            opticsRayArt(a, listOf(fr(0.18, 0.05), fr(0.46, 0.5), fr(0.58, 0.98))),
            LumiereLineArtSpec(listOf(lumiereSegment(pt(a * 0.46, 0.18), pt(a * 0.46, 0.82), st(0.0009, 0.35, 0.4, dash = doubleArrayOf(0.006, 0.006)))), emptyList()),
            opticsSparks(),
        )
        "optics-bench" -> lumiereMergeSpecs(
            listOf(0.3, 0.5, 0.7).mapIndexed { i, x -> lumiereLens(a * x, axis, 0.16 + (i % 2) * 0.04, i != 1, a, st(delay = i * 0.1)) } +
                listOf(lumiereRuler(a * 0.06, a * 0.94, 0.58, 40, st(delay = 0.3)), opticsSparks()),
        )
        "optics-periscope" -> lumiereMergeSpecs(
            lumiereMirror(a * 0.3, 0.14, 0.14, PI / 4),
            lumiereMirror(a * 0.3, 0.84, 0.14, PI / 4, st(delay = 0.1)),
            opticsRayArt(a, listOf(fr(0.98, 0.14), fr(0.3, 0.14), fr(0.3, 0.84), fr(0.98, 0.84))),
            opticsSparks(),
        )
        "optics-fiber" -> lumiereMergeSpecs(
            lumiereFiber(pt(a * 0.04, 0.86), pt(a * 0.4, 0.95), pt(a * 0.5, 0.1), pt(a * 0.84, 0.2), 0.012),
            opticsSparks(),
        )
        "optics-pinhole" -> lumiereMergeSpecs(lumierePinholeBox(a * 0.45 + 0.15, axis, 0.3, 0.3), opticsSparks())
        "optics-telescope" -> lumiereMergeSpecs(
            listOf(lumiereLens(a * 0.28, axis, 0.36, true, a), lumiereLens(a * 0.72, axis, 0.12, true, a, st(delay = 0.1))) +
                listOf(0.3, 0.5).mapIndexed { i, y ->
                    opticsRayArt(a, listOf(fr(0.02, y), fr(0.28, y), fr(0.62, axis), fr(0.72, axis - (y - axis) * 0.3), fr(0.98, axis - (y - axis) * 0.3)), 0.2 + i * 0.1)
                } +
                listOf(lumiereFocusMark(a * 0.62, axis, st(delay = 0.5)), opticsSparks()),
        )
        "optics-raytrace" -> {
            val art = mutableListOf<LumiereLineArtSpec>()
            for (k in 0 until 4) {
                val points = List(6) { i -> fr(0.05 + (i / 5.0) * 0.9, 0.08 + random() * 0.6) }
                art.add(opticsRayArt(a, points, 0.1 + k * 0.1))
            }
            art.add(lumiereScatteredSparks(a, 16, random, 0.3))
            lumiereMergeSpecs(art)
        }
        // 衍射（wave）
        "wave-slits" -> lumiereMergeSpecs(
            lumiereSlitBarrier(a * 0.5, 0.3, a * 0.5, 0.012, 0.08),
            lumiereViewfinderFrame(a, 0.2, 0.52, 0.8, 0.92, delay = 0.2, alpha = 0.28),
            waveSparks(),
        )
        "wave-newton" -> lumiereMergeSpecs(lumiereRings(a * 0.5, 0.5, 0.4, 6), waveSparks())
        "wave-airy" -> lumiereMergeSpecs(lumiereRings(a * 0.5, 0.4, 0.3, 4, st(alpha = 0.3)), waveSparks())
        "wave-grating" -> lumiereMergeSpecs(lumiereGrating(a * 0.5, 0.3, a * 0.4, 16), waveSparks())
        "wave-sources" -> lumiereMergeSpecs(lumiereTwoSources(a * 0.5, 0.5, 0.14, 0.3), waveSparks())
        "wave-moire" -> lumiereMergeSpecs(lumiereMoire(a * 0.5, 0.32, 0.4, 24, 0.08), waveSparks())
        "wave-film" -> lumiereMergeSpecs(
            LumiereLineArtSpec(listOf(lumiereCircle(a * 0.5, 0.4, 0.3, st(0.0016, 0.45)), lumiereCircle(a * 0.5 - 0.08, 0.3, 0.06, st(0.001, 0.3, 0.2))), emptyList()),
            waveSparks(),
        )
        "wave-polar" -> lumiereMergeSpecs(lumierePolarizers(a * 0.5, 0.36, 0.1, 0.24, PI / 3), waveSparks())
        "wave-standing" -> lumiereMergeSpecs(lumiereStandingWave(a * 0.08, a * 0.92, 0.34, 0.07, 6), waveSparks())
        "wave-holo" -> lumiereMergeSpecs(lumiereGridPanel(a * 0.3, 0.15, a * 0.4, 0.7, 6, 8, true, st(alpha = 0.3)), waveSparks())
        // 叶脉（botany）
        "botany-sprout" -> lumiereMergeSpecs(
            lumiereSprout(a * 0.5, 0.78, 0.22, alpha = 1.0),
            lumiereProtractorHalo(a * 0.5, 0.02, 0.22, alpha = 0.35, delay = 0.2),
            lumiereViewfinderFrame(a, 0.07, 0.22, 0.93, 0.8, delay = 0.25, alpha = 0.22),
            lumiereScatteredSparks(a, 12, random, 0.35, reg(0.2, 0.3, 0.8, 0.9)),
        )
        "botany-veins" -> lumiereMergeSpecs(lumiereBigLeaf(a * 0.52, 0.42, 0.62, -0.45), botanySparks())
        "botany-canopy" -> lumiereMergeSpecs(lumiereCanopy(a, random), lumiereScatteredSparks(a, 16, random, 0.3))
        "botany-fern" -> lumiereMergeSpecs(lumiereFernCurl(a * 0.28, 0.42, 0.16), botanySparks())
        "botany-seed" -> lumiereMergeSpecs(lumiereSeedRoots(a * 0.5, 0.7, random), lumiereScatteredSparks(a, 10, random, 0.3))
        "botany-bloom" -> lumiereMergeSpecs(lumiereBloom(a * 0.5, 0.42, 0.2, 8), botanySparks())
        "botany-vine" -> lumiereMergeSpecs(lumiereVine(a * 0.72, 1.02, 0.08, 0.05, 4.0), botanySparks())
        "botany-chloro" -> lumiereMergeSpecs(lumiereCells(a * 0.5, 0.45, 0.36, random, 22), lumiereScatteredSparks(a, 8, random, 0.3))
        "botany-formula" -> lumiereMergeSpecs(
            lumiereMolecule(a * 0.24, 0.3, 0.06),
            lumiereMolecule(a * 0.78, 0.74, 0.05, st(delay = 0.2)),
            lumiereProtractorHalo(a * 0.5, 0.03, 0.28, alpha = 0.5),
            botanySparks(),
        )
        "botany-pollen" -> botanySparks()
        // 星象（astral）
        "astral-eclipse" -> lumiereMergeSpecs(lumiereCorona(a * 0.5, 0.34, 0.13, random), astralSparks())
        "astral-orbit" -> lumiereMergeSpecs(lumiereOrbits(a * 0.5, 0.42, listOf(0.12, 0.2, 0.3, 0.42), -0.18, 0.34, random), astralSparks())
        "astral-trail" -> lumiereMergeSpecs(lumiereStarTrails(a * 0.72, 0.12, random, 60, 0.9))
        "astral-sextant" -> lumiereMergeSpecs(lumiereSextant(a * 0.4, 0.14, 0.32), astralSparks())
        "astral-constellation" -> lumiereMergeSpecs(
            lumiereConstellation(a, random, 8, reg(0.1, 0.1, 0.9, 0.42)),
            lumiereConstellation(a, random, 5, reg(0.6, 0.62, 0.95, 0.9), st(delay = 0.3)),
        )
        "astral-corona" -> lumiereMergeSpecs(lumiereCorona(a * 0.5, 0.12, 0.1, random), astralSparks())
        "astral-moon" -> lumiereMergeSpecs(lumiereCrescent(a * 0.82, 0.12, 0.07), astralSparks())
        "astral-nebula" -> astralSparks()
        "astral-armillary" -> lumiereMergeSpecs(lumiereArmillary(a * 0.5, 0.42, 0.26), astralSparks())
        "astral-meteor" -> lumiereMergeSpecs(lumiereMeteors(a, random, 6), astralSparks())
        // 追光（stage）：withLamps 的灯头按各自光束的 x、y、angle。
        "stage-spot" -> stageLampsArt(a, random, listOf(stageLamp(a, 0.14, 0.02, 0.5, 0.56)))
        "stage-cross" -> stageLampsArt(a, random, listOf(stageLamp(a, 0.06, 0.02, 0.5, 0.56), stageLamp(a, 0.94, 0.02, 0.5, 0.56)))
        "stage-search" -> stageLampsArt(a, random, listOf(0.15, 0.38, 0.62, 0.85).map { Triple(it, 1.02, -PI / 2) })
        "stage-footlight" -> stageLampsArt(a, random, listOf(0.25, 0.5, 0.75).map { Triple(it, 1.03, -PI / 2) })
        "stage-backlight" -> lumiereScatteredSparks(a, 18, random, 0.3)
        "stage-pinspot" -> stageLampsArt(a, random, listOf(stageLamp(a, 0.5, 0.02, 0.5, 0.56)))
        "stage-strobe" -> stageLampsArt(a, random, listOf(0.1, 0.36, 0.64, 0.9).map { stageLamp(a, it, 0.02, 0.5, 0.6) })
        "stage-curtain" -> lumiereMergeSpecs(lumiereCurtains(a, 0.06), lumiereScatteredSparks(a, 8, random, 0.3, reg(0.3, 0.2, 0.7, 0.9)))
        "stage-laser" -> stageLampsArt(a, random, List(6) { k -> Triple(0.5, 1.02, -PI / 2 + (k - 2.5) * 0.22) })
        "stage-projector" -> lumiereMergeSpecs(lumiereProjector(a * 0.12, 0.4, 0.1), lumiereScatteredSparks(a, 10, random, 0.3))
        // 萤尘（motes）：族缺省 10 个闪点。
        "motes-mist" -> lumiereMergeSpecs(lumiereHorizon(a, 0.64), lumiereScatteredSparks(a, 8, random, 0.3, reg(0.05, 0.05, 0.95, 0.55)))
        else -> if (kind.startsWith("motes-")) lumiereScatteredSparks(a, 10, random, 0.3) else lumiereStandardArt(a, random)
    }
}

/**
 * scene.ts 458–482 行：线稿每个镜头提前 0.6 秒开始描（第一个镜头从开场点亮前 0.6 秒），3.6 秒描完，
 * 镜头结束后 0.8 秒淡出（最后一个镜头不淡出，跟单元的 exit 走）。返回 (draw, fade)；fade ≤ 0.003 不画。
 */
internal fun lumiereLineArtTiming(shotIndex: Int, shotCount: Int, shotStart: Double, shotEnd: Double, unitStart: Double, ignition: Double, time: Double, exit: Double, artGain: Double): Pair<Double, Double> {
    val begin = if (shotIndex == 0) unitStart + ignition - 0.6 else shotStart - 0.6
    val out = if (shotIndex == shotCount - 1) 1.0 else 1 - lumiereSmooth((time - shotEnd) / 0.8)
    val base = lumiereSmooth((time - begin) / 1.2) * out * exit
    val draw = (time - begin) / 3.6
    return draw to base * artGain
}

