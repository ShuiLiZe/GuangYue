// 对应 Folia 文件：src/components/visualizer/pendolo/VisualizerPendolo.tsx（歌词沿钟轮弧线排布、擒纵弹簧
// 一格格推进）、pendoloGeometry.ts（calculatePendoloWheelLayout：轮心 (0, 0.5)、半径 0.42·min(w,h)、
// 100° 弧上 9 行一窗、按行高加宽角距、透明度 cos(0.75a)^2.5·(1-0.18d)、当前句放大 1.25）、
// pendoloClockworkDraw2d.ts + pendoloClockworkScene.ts（subtle 档：中心暗渐变、同心导环、60 刻度、
// 主擒纵齿轮 36 齿、六辐内轮、斜面双环、扭索纹 48 道、12 颗铆钉、中心小齿轮）、PendoloActiveLyricSweep.tsx
// （当前句底色 primary@0.52，扫过处填 mix(primary, accent, 0.32)）。
//
// P4「时计」手机版：不做 WebGL 钟芯（摆轮/游丝/行星齿轮/日内瓦机构）、智能动感冲击波、副歌光晕、表盘封面
// （Folia 默认关）。行内扫光改为逐词着色（同莫奈）。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Folia DEFAULT_PENDOLO_TUNING 与几何常量。 */
internal object PendoloGeometry {
    const val ARC_RADIUS = 0.42f
    const val ARC_ANGLE_DEG = 100f
    const val CENTER_X = 0f
    const val CENTER_Y = 0.5f
    const val ACTIVE_SCALE = 1.25f
    const val WINDOW = 9
    const val REACH = 8
    val angleStepRad: Float get() = (ARC_ANGLE_DEG * PI.toFloat() / 180f) / (WINDOW - 1)

    /** 透明度：max(0.12, cos(0.75a)^2.5 · (1 − 0.18d))。 */
    fun alpha(angleRad: Float, distance: Float): Float =
        max(0.12f, cos(angleRad * 0.75f).coerceAtLeast(0f).pow(2.5f) * (1f - distance * 0.18f))

    /** pendoloTimeline：可见弧 110°，边缘 28° 内渐隐。 */
    fun edgeFade(angleDeg: Float): Float {
        val a = abs(angleDeg)
        if (a >= 110f) return 0f
        return min(1f, (110f - a) / 28f)
    }

    fun scale(active: Boolean, distance: Float): Float =
        if (active) ACTIVE_SCALE else max(0.7f, 1f - distance * 0.08f)

    /**
     * 各行相对当前句的角度（弧度）：相邻两行至少一格，行块太高时按 (h1+h2+24)/(2R) 加宽。
     * [heights] 为行块高度（px，按当前句尺寸预留）。
     */
    fun angles(anchor: Int, first: Int, last: Int, radiusPx: Float, gapPx: Float = 24f, heights: (Int) -> Float): Map<Int, Float> {
        val out = HashMap<Int, Float>()
        out[anchor] = 0f
        val step = angleStepRad
        for (i in anchor + 1..last) {
            val s = max(step, (heights(i - 1) + heights(i) + gapPx) / (2f * radiusPx))
            out[i] = out.getValue(i - 1) + s
        }
        for (i in anchor - 1 downTo first) {
            val s = max(step, (heights(i + 1) + heights(i) + gapPx) / (2f * radiusPx))
            out[i] = out.getValue(i + 1) - s
        }
        return out
    }
}

@Composable
internal fun PendoloStage(
    lines: List<Line>,
    currentIndex: Int,
    clock: VisualizerClock,
    lyricsFontScale: Double,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    backgroundColor: Color,
    fontFamily: FontFamily,
    reduceMotion: Boolean,
    onSeekToLine: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val anchor = currentIndex.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
    // 擒纵：tick 弹簧追当前句下标（stiffness 180·snappiness 2，damping 18+4/2，mass 0.8 → 阻尼比约 0.6）。
    val tick = remember { Animatable(anchor.toFloat()) }
    LaunchedEffect(anchor, reduceMotion) {
        if (reduceMotion) tick.snapTo(anchor.toFloat())
        else tick.animateTo(anchor.toFloat(), spring(dampingRatio = 0.6f, stiffness = 450f))
    }
    val step = PendoloGeometry.angleStepRad
    val fill = lerp(primaryColor, accentColor, 0.32f)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val cx = wPx * PendoloGeometry.CENTER_X
        val cy = hPx * PendoloGeometry.CENTER_Y
        val radius = min(wPx, hPx) * PendoloGeometry.ARC_RADIUS
        // Folia：歌词环 = 钟轮半径 + 0.06·min(w,h)（lyricRadiusOffset），文字可用宽度仍按钟轮半径算。
        val ringR = radius + min(wPx, hPx) * 0.06f
        val availPx = max(with(density) { 140.dp.toPx() }, min(wPx * 0.46f, wPx - (cx + radius) - with(density) { 48.dp.toPx() }))
        val fs = lyricsFontScale.toFloat()

        // ---------- 钟芯线框 ----------
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.readForDraw()
            drawPendoloClockwork(
                cx, cy, radius, ringR, tick.value * step,
                PendoloMotion.phase(t), PendoloMotion.secondGearAngle(t),
                primaryColor, accentColor, backgroundColor,
            )
        }

        // ---------- 弧上歌词 ----------
        val first = (anchor - PendoloGeometry.REACH).coerceAtLeast(0)
        val last = (anchor + PendoloGeometry.REACH).coerceAtMost(lines.size - 1)
        // 行块高度（px）按当前句尺寸量：未量到时先按一行估。
        val heights = remember(lines) { mutableStateMapOf<Int, Int>() }
        val focalLinePx = with(density) { (28f * fs * 1.2f).dp.toPx() }
        // Folia 的 24 是 CSS px（≈dp）；行块按当前句放大后的高度预留。
        val angles = PendoloGeometry.angles(anchor, first, last, ringR, with(density) { 24.dp.toPx() }) { i ->
            (heights[i]?.toFloat() ?: focalLinePx) * PendoloGeometry.ACTIVE_SCALE
        }
        for (i in first..last) {
            val line = lines[i]
            val base = angles[i] ?: continue
            key(i) {
                val active = i == anchor
                val distance = abs(i - anchor).toFloat()
                val sc = PendoloGeometry.scale(active, distance)
                val fontDp = (if (active) 28f else 22f) * fs
                val maxW = with(density) { (availPx / sc).toDp() }
                Column(
                    Modifier
                        .offset {
                            val a = base - (tick.value - anchor) * step
                            val x = cx + ringR * cos(a)
                            val y = cy + ringR * sin(a)
                            IntOffset(x.roundToInt(), (y - (heights[i] ?: 0) / 2f).roundToInt())
                        }
                        .onSizeChanged { heights[i] = it.height }
                        .graphicsLayer {
                            val a = base - (tick.value - anchor) * step
                            val deg = a * 180f / PI.toFloat()
                            if (abs(a) >= PI.toFloat() / 2f) {
                                alpha = 0f
                            } else {
                                alpha = PendoloGeometry.alpha(a, distance) * PendoloGeometry.edgeFade(deg)
                            }
                            rotationZ = deg * 0.35f
                            scaleX = sc
                            scaleY = sc
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .widthIn(max = maxW)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSeekToLine(i) },
                ) {
                    val style = TextStyle(
                        fontFamily = fontFamily,
                        fontSize = with(density) { fontDp.dp.toSp() },
                        lineHeight = with(density) { (fontDp * 1.2f).dp.toSp() },
                        letterSpacing = 0.01.em,
                    )
                    if (active) {
                        val text by remember(line, primaryColor, fill) {
                            derivedStateOf { pendoloActiveText(line, clock.seconds(), primaryColor.copy(alpha = 0.52f), fill) }
                        }
                        Text(text = text, style = style)
                    } else {
                        Text(text = line.fullText, style = style.copy(color = primaryColor.copy(alpha = 0.75f)))
                    }
                    val tr = line.translation
                    if (!tr.isNullOrBlank()) {
                        Text(
                            text = tr,
                            modifier = Modifier.padding(top = 4.dp),
                            style = TextStyle(
                                color = if (active) secondaryColor else secondaryColor.copy(alpha = 0.6f),
                                fontSize = with(density) { (fontDp * 0.58f).dp.toSp() },
                                letterSpacing = 0.01.em,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** 当前句逐词着色：已唱 = fill，未唱 = base，正在唱按进度插值。 */
internal fun pendoloActiveText(line: Line, nowS: Double, base: Color, fill: Color): AnnotatedString {
    if (line.words.isEmpty()) {
        return AnnotatedString(line.fullText, SpanStyle(color = lerp(base, fill, MonetRail.wordFill(line.startTime, line.endTime, nowS))))
    }
    return buildAnnotatedString {
        append(line.fullText)
        var cursor = 0
        var last = base
        for (w in line.words) {
            val t = w.text.trim()
            if (t.isEmpty()) continue
            val at = line.fullText.indexOf(t, cursor)
            if (at < 0) continue
            if (at > cursor) addStyle(SpanStyle(color = last), cursor, at)
            last = lerp(base, fill, MonetRail.wordFill(w.startTime, w.endTime, nowS))
            addStyle(SpanStyle(color = last), at, at + t.length)
            cursor = at + t.length
        }
        if (cursor < line.fullText.length) addStyle(SpanStyle(color = if (nowS >= line.endTime) fill else base), cursor, line.fullText.length)
    }
}

/** subtle 档钟芯（m = 0.6）。 */
private fun DrawScope.drawPendoloClockwork(
    cx: Float,
    cy: Float,
    r: Float,
    ringR: Float,
    esc: Float,
    phase: Float,
    secondAngle: Float,
    primary: Color,
    accent: Color,
    background: Color,
) {
    val m = 0.6f
    val center = Offset(cx, cy)
    // 0. 中心暗渐变（背景色 0.72 → 0）。
    val gr = r * 1.65f
    drawCircle(
        brush = Brush.radialGradient(
            0f to background.copy(alpha = 0.72f),
            0.35f to background.copy(alpha = 0.52f),
            0.7f to background.copy(alpha = 0.20f),
            1f to background.copy(alpha = 0f),
            center = center,
            radius = gr,
        ),
        radius = gr,
        center = center,
    )
    val p15 = primary.copy(alpha = 0.15f * m)
    // 1. 同心导环 + 60 刻度。
    for (k in floatArrayOf(0.3f, 0.6f, 0.85f, 1.15f, 1.4f)) drawCircle(p15, r * k, center, style = Stroke(1.dp.toPx()))
    val tickR = r * 1.15f
    for (i in 0 until 60) {
        val a = i * 2f * PI.toFloat() / 60f + esc * 0.2f
        val major = i % 5 == 0
        val len = (if (major) 12 else 6).dp.toPx()
        drawLine(
            if (major) accent.copy(alpha = 0.35f * m) else p15,
            Offset(cx + tickR * cos(a), cy + tickR * sin(a)),
            Offset(cx + (tickR + len) * cos(a), cy + (tickR + len) * sin(a)),
            strokeWidth = (if (major) 1.5f else 1f).dp.toPx(),
        )
    }
    // 2. 主擒纵齿轮（36 齿）+ 六辐内轮 + 斜面双环。
    gearTeeth(center, r + 8.dp.toPx(), 36, 10.dp.toPx(), esc, accent.copy(alpha = 0.58f * m), 2.2f, accent.copy(alpha = 0.08f * m))
    spokedWheel(center, r * 0.2f, r * 0.85f, 6, esc, primary.copy(alpha = 0.42f * m), 1.8f)
    val bevel = primary.copy(alpha = 0.18f * m)
    drawCircle(bevel, r * 0.88f, center, style = Stroke(0.8.dp.toPx()))
    drawCircle(bevel, r * 0.92f, center, style = Stroke(0.8.dp.toPx()))
    // 扭索纹 48 道（长短相间）。
    val gi = r * 0.62f
    val go = r * 0.83f
    for (i in 0 until 48) {
        val a = i * 2f * PI.toFloat() / 48f + esc
        val inner = if (i % 2 == 0) gi else gi + (go - gi) * 0.3f
        drawLine(
            primary.copy(alpha = (if (i % 2 == 0) 0.10f else 0.07f) * m),
            Offset(cx + inner * cos(a), cy + inner * sin(a)),
            Offset(cx + go * cos(a), cy + go * sin(a)),
            strokeWidth = 0.7.dp.toPx(),
        )
    }
    // 铆钉 12 颗。
    val rv = r * 0.96f
    for (i in 0 until 12) {
        val a = i * 2f * PI.toFloat() / 12f + esc
        drawCircle(primary.copy(alpha = 0.25f * m), 2.2.dp.toPx(), Offset(cx + rv * cos(a), cy + rv * sin(a)), style = Stroke(0.8.dp.toPx()))
    }
    // 3. 中心小齿轮（反转 2.5 倍）+ 宝石轴承。
    val strong = accent.copy(alpha = 0.72f * m)
    val subtle = primary.copy(alpha = 0.32f * m)
    gearTeeth(center, r * 0.22f, 12, 6.dp.toPx(), -esc * 2.5f, strong, 2.1f, null)
    jewel(center, 4.5f, 2.5f, accent, m, 1.2f, 0.8f)
    // 4. 行星轮三颗（绕 0.52R 公转 0.4 倍）。
    for (k in 0 until 3) {
        val pa = esc * 0.4f + k * 2f * PI.toFloat() / 3f
        val pc = Offset(cx + r * 0.52f * cos(pa), cy + r * 0.52f * sin(pa))
        gearTeeth(pc, r * 0.16f, 14, 5.dp.toPx(), -esc * 3f + k * 0.5f, primary.copy(alpha = 0.24f * m), 1.7f, null)
        jewel(pc, 3.5f, 1.8f, accent, m, 1f, 0.7f)
    }
    // 5. 左上摆轮：20 齿 + 图标座圈 + 游丝 + 星标（Folia 用主题 lucide 图标，缺省 Star）。
    val bc = Offset(cx + r * 0.2f, cy - r * 0.75f)
    val br = r * 0.28f
    val bAngle = phase * 0.1f
    gearTeeth(bc, br, 20, 7.dp.toPx(), bAngle, strong, 2.1f, accent.copy(alpha = 0.10f * m))
    drawCircle(subtle, br * 0.52f, bc, style = Stroke(1.8.dp.toPx()))
    hairspring(bc, br * 0.56f, br * 0.88f, 3.5f, PendoloMotion.bassOscillation(phase) * 0.6f + bAngle * 0.3f, accent.copy(alpha = 0.30f * m))
    jewel(bc, 3.5f, 1.8f, accent, m, 1f, 0.7f)
    star(bc, max(14.dp.toPx(), r * 0.13f) / 2f, accent.copy(alpha = 0.62f))
    // 传动轮（左下）：24 齿 + 五辐 + 日内瓦条纹。
    val tc = Offset(cx + r * 0.32f, cy + r * 0.78f)
    val tr = r * 0.34f
    val tAngle = -esc * 1.4f
    gearTeeth(tc, tr, 24, 7.dp.toPx(), tAngle, primary.copy(alpha = 0.42f * m), 1.8f, null)
    spokedWheel(tc, tr * 0.25f, tr * 0.85f, 5, tAngle, subtle, 1.5f)
    translate(tc.x, tc.y) {
        rotateRad(tAngle, pivot = Offset.Zero) {
            val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, tr * 0.8f)) }
            androidx.compose.ui.graphics.drawscope.clipPath(clip) {
                val span = tr * 1.6f
                for (i in 1..9) {
                    val y = -span * 0.5f + i * span / 10f
                    drawLine(
                        primary.copy(alpha = (if (i % 2 == 0) 0.10f else 0.06f) * m),
                        Offset(-tr, y), Offset(tr, y), strokeWidth = 0.7.dp.toPx(),
                    )
                }
            }
        }
    }
    jewel(tc, 3.5f, 1.8f, accent, m, 1f, 0.7f)
    // 秒轮：每播放一秒进一齿（15 齿）。
    val sc = Offset(cx + r * 0.68f, cy + r * 0.76f)
    val sr = r * 0.16f
    gearTeeth(sc, sr, 15, 5.dp.toPx(), secondAngle, accent.copy(alpha = 0.58f * m), 1.8f, accent.copy(alpha = 0.06f * m))
    spokedWheel(sc, sr * 0.28f, sr * 0.76f, 4, -secondAngle, subtle, 1.3f)
    jewel(sc, 2.8f, 1.5f, accent, m, 0.8f, 0.6f)
    // 惰轮（传动轮与秒轮之间）。
    val ic = Offset((tc.x + sc.x) * 0.5f + r * 0.04f, (tc.y + sc.y) * 0.5f - r * 0.02f)
    val ir = r * 0.08f
    gearTeeth(ic, ir, 10, 3.5.dp.toPx(), esc * 2.2f, primary.copy(alpha = 0.28f * m), 1.3f, null)
    drawCircle(primary.copy(alpha = 0.18f * m), ir * 0.45f, ic, style = Stroke(0.8.dp.toPx()))
    jewel(ic, 2.2f, 1.2f, accent, m, 0.7f, 0.5f)
    // 6. 焦点轴线 + 箭头（指向当前句）。
    val axisEnd = ringR - max(14.dp.toPx(), r * 0.025f)
    drawLine(strong, Offset(cx + r * 0.8f, cy), Offset(cx + axisEnd, cy), strokeWidth = 2.dp.toPx())
    val ax = cx + axisEnd
    drawPath(
        Path().apply {
            moveTo(ax, cy - 4.dp.toPx()); lineTo(ax + 8.dp.toPx(), cy); lineTo(ax, cy + 4.dp.toPx()); close()
        },
        strong,
    )
}

/** pendoloClockworkMotion 无音频档：摆轮相位 2.8 rad/s 累积，摆幅 0.15；秒轮每秒一齿、弹簧追齿。 */
internal object PendoloMotion {
    fun phase(t: Double): Float = (t * 2.8).toFloat()
    fun bassOscillation(phase: Float): Float = sin(phase) * 0.15f

    /** 每整秒进一齿（2π/15）；齿间用欠阻尼弹簧（k=92、c=13）的阶跃响应近似。 */
    fun secondGearAngle(t: Double): Float {
        val stepAngle = 2f * PI.toFloat() / 15f
        val whole = kotlin.math.floor(t).toFloat()
        val dt = (t - kotlin.math.floor(t)).toFloat()
        val w0 = kotlin.math.sqrt(92f)
        val z = 13f / (2f * w0)
        val wd = w0 * kotlin.math.sqrt((1f - z * z).coerceAtLeast(1e-4f))
        val resp = 1f - kotlin.math.exp(-z * w0 * dt) * (cos(wd * dt) + z * w0 / wd * sin(wd * dt))
        return (whole - 1f + resp) * stepAngle
    }
}

private fun DrawScope.jewel(c: Offset, outerDp: Float, innerDp: Float, accent: Color, m: Float, ow: Float, iw: Float) {
    drawCircle(accent.copy(alpha = 0.65f * m), outerDp.dp.toPx(), c, style = Stroke(ow.dp.toPx()))
    drawCircle(accent.copy(alpha = 0.28f * m), innerDp.dp.toPx(), c, style = Stroke(iw.dp.toPx()))
}

private fun DrawScope.hairspring(c: Offset, startR: Float, endR: Float, coils: Float, rot: Float, color: Color) {
    val steps = (coils * 60).toInt()
    val total = coils * 2f * PI.toFloat()
    val path = Path()
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val a = t * total
        val rr = startR + (endR - startR) * t.pow(0.9f)
        if (i == 0) path.moveTo(rr * cos(a), rr * sin(a)) else path.lineTo(rr * cos(a), rr * sin(a))
    }
    translate(c.x, c.y) { rotateRad(rot, pivot = Offset.Zero) { drawPath(path, color, style = Stroke(0.8.dp.toPx())) } }
}

/** lucide Star 线稿（不随摆轮转）。 */
private fun DrawScope.star(c: Offset, radius: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
        val rr = if (i % 2 == 0) radius else radius * 0.45f
        val x = c.x + rr * cos(a)
        val y = c.y + rr * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Stroke(1.2.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

private fun DrawScope.gearTeeth(
    c: Offset, radius: Float, teeth: Int, depth: Float, rot: Float, stroke: Color, widthDp: Float, fill: Color?,
) {
    val inner = radius - depth
    val per = 2f * PI.toFloat() / teeth
    val path = Path()
    for (i in 0 until teeth) {
        val b = i * per
        val pts = listOf(inner to b - per * 0.22f, radius to b - per * 0.12f, radius to b + per * 0.12f, inner to b + per * 0.22f)
        pts.forEachIndexed { k, (rr, a) ->
            val x = rr * cos(a)
            val y = rr * sin(a)
            if (i == 0 && k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }
    path.close()
    translate(c.x, c.y) {
        rotateRad(rot, pivot = Offset.Zero) {
            if (fill != null) drawPath(path, fill)
            drawPath(path, stroke, style = Stroke(widthDp.dp.toPx()))
        }
    }
}

private fun DrawScope.spokedWheel(c: Offset, hubR: Float, rimR: Float, spokes: Int, rot: Float, color: Color, widthDp: Float) {
    val w = widthDp.dp.toPx()
    translate(c.x, c.y) {
        rotateRad(rot, pivot = Offset.Zero) {
            drawCircle(color, hubR, Offset.Zero, style = Stroke(w))
            drawCircle(color, rimR, Offset.Zero, style = Stroke(w))
            val st = 2f * PI.toFloat() / spokes
            for (i in 0 until spokes) {
                val a = i * st
                drawLine(color, Offset(hubR * cos(a), hubR * sin(a)), Offset(rimR * cos(a), rimR * sin(a)), strokeWidth = w)
            }
            val mid = (hubR + rimR) * 0.5f
            val hole = (rimR - hubR) * 0.22f
            for (i in 0 until spokes) {
                val a = i * st + st * 0.5f
                drawCircle(color, hole, Offset(mid * cos(a), mid * sin(a)), style = Stroke(w))
            }
        }
    }
}
