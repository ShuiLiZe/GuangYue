// 对应 Folia 文件：src/components/visualizer/lumiere/（绘光）——catalog.ts（光位目录 10 族：天光 / 窗隙 / 棱镜 / 焦散 / 光路 /
// 衍射 / 叶脉 / 星象 / 追光 / 萤尘）、lumiereProgram.ts（整首编译：段落 → 每段切镜头、选光位，chain 跨段落不重复；前奏够长单出
// 间奏镜头；段落首尾相接铺满时间轴；与上段空隙够长才重播「星空点亮」开场）、lumiereUnitLayout.ts（运镜：缓慢推近 + 平移，两端
// 缓入缓出，叠手持浮动）、types.ts（文字区 region、横排 / 竖排、崩解 decay、十字爆闪 burst、背景歌词碎片 echo）、
// lumiereDarkField.ts（暗场）。
//
// P9「绘光」手机版：不做 Pixi runtime / 100 种光位 / bloom 后处理 / 粒子着色器 / 片尾卡。保留：暗场 + 十族光位各一种手绘版
// （光柱、百叶光、棱镜分光、焦散光网、透镜光路、干涉环、叶脉线稿生长、星图、追光、极光萤尘），镜头间光位交叉淡化、段首星空
// 点亮，镜头推近 + 浮动；字在每镜的文字区里横排或竖排，未唱暗、唱到点亮带光晕（部分光位加十字爆闪），换句后崩解上漂，背景
// 一层超大淡字碎片。全部只由时刻算出，seek 到哪一刻画面都一样；不用需要回读的混合模式。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ───────────────────────────── 程序（纯函数，可单测） ─────────────────────────────

/** 十族光位（Folia catalog.ts 的族序）。 */
internal enum class LumiereFamily { ZENITH, LATTICE, PRISM, CAUSTIC, OPTICS, WAVE, BOTANY, ASTRAL, STAGE, MOTES }

/** 一个镜头：时间段、光位族、包含的歌词行、是否间奏、是否播放星空点亮开场、横 / 竖排、文字区中心（占比）。 */
internal data class LumiereShot(
    val start: Double,
    val end: Double,
    val family: LumiereFamily,
    val lines: List<Int>,
    val bridge: Boolean,
    val opening: Boolean,
    val vertical: Boolean,
    val seed: Int,
    val cx: Float,
    val cy: Float,
)

internal object LumiereTiming {
    /** 两行之间空隙超过这么多秒就分段（分段规则的手机取值）。 */
    const val PARAGRAPH_GAP_S = 4.0
    /** 前奏 / 段后空隙长过这么多秒出间奏镜头。 */
    const val BRIDGE_GAP_S = 5.0
    /** 与上一段空隙超过这么多秒才重播星空点亮开场（Folia REOPEN_GAP）。 */
    const val REOPEN_GAP_S = 2.5
    /** 字提前多久显现。 */
    const val LEAD_S = 0.35
    /** 光位交叉淡化、开场点亮、崩解时长。 */
    const val FADE_S = 0.8
    const val OPEN_S = 1.8
    const val DECAY_S = 1.5
    /** 每镜最多几行。 */
    const val LINES_PER_SHOT = 2
}

private val BridgeFamilies = listOf(LumiereFamily.MOTES, LumiereFamily.ASTRAL, LumiereFamily.ZENITH, LumiereFamily.CAUSTIC)

private fun isCjkText(s: String): Boolean = s.isNotBlank() && s.all { c ->
    c.isWhitespace() || Character.isIdeographic(c.code) || Character.UnicodeScript.of(c.code).let {
        it == Character.UnicodeScript.HIRAGANA || it == Character.UnicodeScript.KATAKANA
    }
}

/** 整首编译：段落 → 每段每两行一个镜头，镜头首尾相接铺满 [0, 曲终]，光位族相邻不重复。 */
internal fun compileLumiere(lines: List<Line>, songEnd: Double? = null): List<LumiereShot> {
    if (lines.isEmpty()) return emptyList()
    // 1) 分段。
    val paragraphs = ArrayList<MutableList<Int>>()
    lines.indices.forEach { i ->
        if (i == 0 || lines[i].startTime - lines[i - 1].endTime > LumiereTiming.PARAGRAPH_GAP_S) paragraphs += ArrayList<Int>()
        paragraphs.last() += i
    }
    data class Plan(val start: Double, val lines: List<Int>, val bridge: Boolean, val opening: Boolean)
    val plans = ArrayList<Plan>()
    val first = lines.first().startTime
    if (first >= LumiereTiming.BRIDGE_GAP_S) plans += Plan(0.0, emptyList(), bridge = true, opening = true)
    var prevEnd = Double.NEGATIVE_INFINITY
    paragraphs.forEachIndexed { pi, para ->
        val pStart = lines[para.first()].startTime
        val gap = pStart - prevEnd
        // 上一段之后的长空隙：在上一段唱完 1 秒后出间奏镜头。
        if (pi > 0 && gap > LumiereTiming.BRIDGE_GAP_S) plans += Plan(prevEnd + 1.0, emptyList(), bridge = true, opening = false)
        val reopen = pi == 0 || gap > LumiereTiming.REOPEN_GAP_S
        para.chunked(LumiereTiming.LINES_PER_SHOT).forEachIndexed { k, group ->
            val at = if (plans.isEmpty()) 0.0 else lines[group.first()].startTime - LumiereTiming.LEAD_S
            // 段首镜头重播开场：整首第一镜（前面没有间奏开场时），或与上一段空隙够长且中间没有间奏镜头接住。
            val opening = k == 0 && reopen && (plans.isEmpty() || !plans.last().bridge)
            plans += Plan(at, group, bridge = false, opening = opening)
        }
        prevEnd = lines[para.last()].endTime
    }
    val programEnd = max(songEnd ?: 0.0, lines.last().endTime + 6.0)
    // 2) 选光位、排版。
    val out = ArrayList<LumiereShot>()
    var prev: LumiereFamily? = null
    plans.forEachIndexed { i, p ->
        val start = if (i == 0) 0.0 else p.start
        val end = plans.getOrNull(i + 1)?.start ?: programEnd
        val text = p.lines.joinToString("|") { lines[it].fullText }
        val seed = hanabiHash("lum$i@${(start * 100).toInt()}:$text")
        val pool = if (p.bridge) BridgeFamilies else LumiereFamily.values().toList()
        var fam = pool[Math.floorMod(seed, pool.size)]
        if (fam == prev) fam = pool[(pool.indexOf(fam) + 1) % pool.size]
        prev = fam
        val cjk = p.lines.isNotEmpty() && p.lines.all { isCjkText(lines[it].fullText) && lines[it].fullText.count { c -> !c.isWhitespace() } <= 12 }
        val vertical = cjk && fam != LumiereFamily.STAGE && fam != LumiereFamily.PRISM && Math.floorMod(seed ushr 5, 3) == 0
        val cx = if (vertical) (if (Math.floorMod(seed ushr 9, 2) == 0) 0.68f else 0.34f) else 0.5f
        val cy = when (fam) {
            LumiereFamily.ZENITH, LumiereFamily.STAGE, LumiereFamily.OPTICS, LumiereFamily.PRISM -> 0.62f
            LumiereFamily.BOTANY -> 0.44f
            else -> 0.5f
        } + (Math.floorMod(seed ushr 13, 5) - 2) * 0.025f
        out += LumiereShot(start, end, fam, p.lines, p.bridge, p.opening, vertical, seed, cx, cy)
    }
    return out
}

/** 此刻所在的镜头序号（最后一个已开始的）。 */
internal fun lumiereShotAt(shots: List<LumiereShot>, t: Double): Int {
    var idx = 0
    shots.forEachIndexed { i, s -> if (s.start <= t) idx = i }
    return idx
}

/** 此刻的当前行：提前 LEAD_S 显现，最后一个已开始的；都没开始为 -1。 */
internal fun lumiereLineAt(lines: List<Line>, t: Double): Int {
    var idx = -1
    lines.forEachIndexed { i, l -> if (l.startTime - LumiereTiming.LEAD_S <= t) idx = i }
    return idx
}

// ───────────────────────────── 排版 ─────────────────────────────

private class LumiereGlyph(val layout: TextLayoutResult, val cx: Float, val cy: Float, val start: Double, val order: Int, val seed: Int)

private class LumiereLineLayout(val glyphs: List<LumiereGlyph>, val size: Float, val echo: TextLayoutResult?, val echoX: Float, val echoY: Float)

private val LumOnePx = Density(1f, 1f)

private fun layoutLumiereLine(
    line: Line, li: Int, shot: LumiereShot, w: Float, top: Float, bottom: Float,
    sMax: Float, sMin: Float, measurer: TextMeasurer, base: TextStyle,
): LumiereLineLayout {
    val units = nebbiaUnits(line)
    if (units.isEmpty()) return LumiereLineLayout(emptyList(), sMax, null, 0f, 0f)
    val areaH = bottom - top
    val slot = shot.lines.indexOf(li).coerceAtLeast(0)
    val glyphs = ArrayList<LumiereGlyph>()
    val s: Float
    if (shot.vertical) {
        // 竖排：一列从上往下，镜头里第二行在第一行左侧。
        s = min(sMax * 1.1f, areaH * 0.78f / (units.size + 0.5f)).coerceAtLeast(min(sMin, sMax))
        val style = base.copy(fontSize = s.sp)
        val colX = w * shot.cx - slot * s * 1.6f
        val colTop = top + areaH * 0.5f - units.size * s * 1.08f / 2f + slot * s * 1.2f
        units.forEachIndexed { k, (txt, t0) ->
            glyphs += LumiereGlyph(measurer.measure(txt, style, density = LumOnePx), colX, colTop + s * 1.08f * (k + 0.5f), t0, k, hanabiHash("g$li#$k"))
        }
    } else {
        val ref = 100f
        val refStyle = base.copy(fontSize = ref.sp)
        val widths = units.map { (u, _) -> measurer.measure(u, refStyle, density = LumOnePx).size.width.toFloat() }
        val maxW = w * 0.84f
        var size = sMax
        var rows: List<List<Int>>
        val gapK = 0.28f
        while (true) {
            val r = ArrayList<MutableList<Int>>(); var cur = ArrayList<Int>(); var cw = 0f
            units.indices.forEach { k ->
                val uw = widths[k] / ref * size
                val needsGap = cur.isNotEmpty() && (units[k].first.first().isLetterOrDigit() && !isCjkText(units[k].first))
                val add = uw + if (needsGap) size * gapK else 0f
                if (cur.isNotEmpty() && cw + add > maxW) { r += cur; cur = ArrayList(); cw = 0f; cw += uw } else cw += add
                cur += k
            }
            if (cur.isNotEmpty()) r += cur
            rows = r
            if (rows.size <= 2 || size <= sMin) break
            size = max(sMin, size * 0.9f)
        }
        s = size
        val style = base.copy(fontSize = s.sp)
        val lineH = s * 1.42f
        val blockH = rows.size * lineH
        // 镜头里两行上下错开：第一行在文字区中心上方，第二行在下方。
        val cy = top + areaH * shot.cy + (if (shot.lines.size > 1) (slot - 0.5f) * (blockH + s * 0.6f) else 0f)
        val rowTop = cy - blockH / 2f
        var order = 0
        rows.forEachIndexed { ri, row ->
            val ws = row.mapIndexed { k, idx ->
                val gap = if (k > 0 && units[idx].first.first().isLetterOrDigit() && !isCjkText(units[idx].first)) s * gapK else 0f
                widths[idx] / ref * s + gap
            }
            var x = w * shot.cx - ws.sum() / 2f
            val y = rowTop + lineH * (ri + 0.5f)
            row.forEachIndexed { k, idx ->
                val (txt, t0) = units[idx]
                val uw = widths[idx] / ref * s
                val gap = ws[k] - uw
                glyphs += LumiereGlyph(measurer.measure(txt, style, density = LumOnePx), x + gap + uw / 2f, y, t0, order++, hanabiHash("g$li#$idx"))
                x += ws[k]
            }
        }
    }
    // 背景碎片：本行前几个字放大到 3 倍，斜放在文字区上方。
    val frag = units.take(if (isCjkText(line.fullText)) 3 else 1).joinToString("") { it.first }
    val echo = measurer.measure(frag, base.copy(fontSize = (s * 3.2f).sp, fontWeight = FontWeight.Light), density = LumOnePx)
    val ex = if (Math.floorMod(shot.seed + li, 2) == 0) w * 0.06f else w * 0.94f - echo.size.width
    val ey = top + areaH * 0.16f + slot * areaH * 0.42f
    return LumiereLineLayout(glyphs, s, echo, ex, ey)
}

// ───────────────────────────── 画：光位 ─────────────────────────────

private fun lumiereUnit(seed: Int, salt: Int): Float {
    var x = seed * 0x27d4eb2d + salt * 0x165667b1
    x = x xor (x ushr 15); x *= 0x2c1b3c6d; x = x xor (x ushr 12)
    return ((x ushr 8) and 0xffff) / 65535f
}

private val Spectrum = listOf(0xFFFF4B4B, 0xFFFF9F3A, 0xFFFFE45C, 0xFF5CFF8A, 0xFF4FB8FF, 0xFFA77BFF).map { Color(it) }
private val WarmLight = Color(0xFFFFE6BE)
private val LumiereInk = Color(0xFF04050A)

private fun lightOf(f: LumiereFamily, accent: Color): Color = when (f) {
    LumiereFamily.BOTANY -> Color(0xFFFFC866)
    LumiereFamily.ASTRAL -> Color(0xFFBFD6FF)
    LumiereFamily.CAUSTIC -> Color(0xFFA6F0FF)
    LumiereFamily.STAGE -> Color(0xFFFFFFFF)
    LumiereFamily.MOTES -> lerp(Color(0xFF9CFFD6), accent, 0.35f)
    else -> lerp(WarmLight, accent, 0.22f)
}

private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

private fun DrawScope.drawRig(shot: LumiereShot, t: Double, a: Float, accent: Color, motion: Float) {
    if (a <= 0.005f) return
    val w = size.width; val h = size.height
    val u = ((t - shot.start).toFloat()).coerceAtLeast(0f)
    val m = u * motion
    val col = lightOf(shot.family, accent)
    val sd = shot.seed
    val thin = w * 0.0035f
    when (shot.family) {
        LumiereFamily.ZENITH -> {
            val n = 1 + Math.floorMod(sd, 3)
            for (k in 0 until n) {
                val x = w * (0.5f + (k - (n - 1) / 2f) * 0.27f) + sin(m * 0.21f + k * 1.7f) * w * 0.02f
                val tw = w * (0.05f + lumiereUnit(sd, k) * 0.04f)
                val bw = w * (0.32f + lumiereUnit(sd, k + 7) * 0.14f)
                val fl = 0.88f + 0.12f * sin(m * 2.3f + k * 2f)
                val path = Path().apply { moveTo(x - tw, -4f); lineTo(x + tw, -4f); lineTo(x + bw / 2f, h * 0.96f); lineTo(x - bw / 2f, h * 0.96f); close() }
                drawPath(path, Brush.verticalGradient(0f to col.copy(alpha = 0.5f * a * fl), 0.75f to col.copy(alpha = 0.12f * a * fl), 1f to Color.Transparent))
                drawOval(Brush.radialGradient(listOf(col.copy(alpha = 0.32f * a), Color.Transparent), Offset(x, h * 0.9f), bw * 0.6f), Offset(x - bw * 0.6f, h * 0.86f), Size(bw * 1.2f, h * 0.08f))
            }
        }
        LumiereFamily.LATTICE -> {
            val ang = -24f - lumiereUnit(sd, 1) * 14f
            withTransform({ rotate(ang, Offset(w * 0.15f, 0f)) }) {
                for (k in 0 until 7) {
                    val x = w * (0.0f + k * 0.12f) + sin(m * 0.3f + k) * w * 0.006f
                    val bw = w * (0.035f + lumiereUnit(sd, k + 3) * 0.03f)
                    drawRect(Brush.verticalGradient(0f to col.copy(alpha = 0.42f * a), 1f to Color.Transparent, startY = 0f, endY = h * 1.2f), Offset(x, -h * 0.2f), Size(bw, h * 1.4f))
                }
            }
            // 地面上的窗格光斑。
            val gx = w * 0.56f; val gy = h * 0.8f
            for (i in 0 until 3) for (j in 0 until 2) {
                drawRect(col.copy(alpha = 0.1f * a), Offset(gx + i * w * 0.1f - j * w * 0.05f, gy + j * h * 0.045f), Size(w * 0.085f, h * 0.035f))
            }
        }
        LumiereFamily.PRISM -> {
            val px = w * 0.34f; val py = h * 0.3f; val r = w * 0.09f
            val tri = Path().apply { moveTo(px, py - r); lineTo(px + r * 0.87f, py + r * 0.5f); lineTo(px - r * 0.87f, py + r * 0.5f); close() }
            drawLine(Color.White.copy(alpha = 0.7f * a), Offset(-4f, py - h * 0.08f), Offset(px - r * 0.4f, py), strokeWidth = w * 0.01f, cap = StrokeCap.Round)
            val open = smooth(u / 1.4f)
            val exit = Offset(px + r * 0.45f, py)
            Spectrum.forEachIndexed { k, c ->
                val y0 = h * (0.24f + (0.1f + k * 0.075f) * open)
                val y1 = y0 + h * 0.06f * open + 2f
                val beam = Path().apply { moveTo(exit.x, exit.y - 2f); lineTo(w + 4f, y0); lineTo(w + 4f, y1); lineTo(exit.x, exit.y + 2f); close() }
                drawPath(beam, c.copy(alpha = 0.34f * a))
            }
            drawPath(tri, Color.White.copy(alpha = 0.08f * a))
            drawPath(tri, Color.White.copy(alpha = 0.7f * a), style = Stroke(w * 0.004f))
        }
        LumiereFamily.CAUSTIC -> {
            val pathH = Path(); val pathV = Path()
            for (j in 0 until 11) {
                val by = h * (0.04f + j * 0.088f)
                var x = 0f
                pathH.moveTo(0f, by)
                while (x <= w) {
                    x += w / 24f
                    pathH.lineTo(x, by + sin(x / w * 9.4f + m * 0.9f + j) * h * 0.018f + sin(x / w * 21f - m * 1.4f + j * 2f) * h * 0.006f)
                }
            }
            for (j in 0 until 7) {
                val bx = w * (0.05f + j * 0.15f)
                var y = 0f
                pathV.moveTo(bx, 0f)
                while (y <= h) {
                    y += h / 30f
                    pathV.lineTo(bx + sin(y / h * 11f - m * 0.8f + j * 1.3f) * w * 0.035f + sin(y / h * 27f + m * 1.6f) * w * 0.01f, y)
                }
            }
            drawRect(Brush.radialGradient(listOf(col.copy(alpha = 0.16f * a), Color.Transparent), Offset(w * 0.5f, h * 0.45f), max(w, h) * 0.7f))
            drawPath(pathH, col.copy(alpha = 0.2f * a), style = Stroke(w * 0.005f))
            drawPath(pathV, col.copy(alpha = 0.16f * a), style = Stroke(w * 0.004f))
        }
        LumiereFamily.OPTICS -> {
            val lx = w * 0.42f; val ly = h * 0.3f; val lh = h * 0.2f
            val fx = w * 0.8f
            drawLine(col.copy(alpha = 0.35f * a), Offset(0f, ly), Offset(w, ly), thin, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.02f, w * 0.015f)))
            drawOval(col.copy(alpha = 0.1f * a), Offset(lx - w * 0.035f, ly - lh / 2f), Size(w * 0.07f, lh))
            drawOval(col.copy(alpha = 0.75f * a), Offset(lx - w * 0.035f, ly - lh / 2f), Size(w * 0.07f, lh), style = Stroke(thin * 1.2f))
            val travel = smooth(u / 2f)
            for (k in 0 until 5) {
                val y = ly + (k - 2) * lh * 0.2f
                val p1 = Offset(lx, y)
                val far = Offset(w + 4f, ly + (ly - y) * (w - fx) / (fx - lx))
                val inLen = travel.coerceAtMost(0.4f) / 0.4f
                drawLine(col.copy(alpha = 0.6f * a), Offset(0f, y), Offset(lx * inLen, y), thin, cap = StrokeCap.Round)
                if (travel > 0.4f) {
                    val q = (travel - 0.4f) / 0.6f
                    drawLine(col.copy(alpha = 0.6f * a), p1, p1 + (far - p1) * q, thin, cap = StrokeCap.Round)
                }
            }
            if (travel > 0.7f) {
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f * a), col.copy(alpha = 0.2f * a), Color.Transparent), Offset(fx, ly), w * 0.05f), w * 0.05f, Offset(fx, ly))
            }
            // 刻度。
            for (k in 0..8) drawLine(col.copy(alpha = 0.3f * a), Offset(w * k / 8f, ly - h * 0.008f), Offset(w * k / 8f, ly + h * 0.008f), thin)
        }
        LumiereFamily.WAVE -> {
            val sources = listOf(Offset(w * 0.4f, h * 0.32f), Offset(w * 0.6f, h * 0.32f))
            val span = w * 0.95f
            sources.forEachIndexed { si, c ->
                for (k in 0 until 9) {
                    val r = ((m * w * 0.09f + k * span / 9f + si * span / 18f) % span)
                    val al = (1f - r / span) * 0.45f * a
                    drawCircle(col.copy(alpha = al), r, c, style = Stroke(thin * 1.3f))
                }
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.7f * a), Color.Transparent), c, w * 0.03f), w * 0.03f, c)
            }
        }
        LumiereFamily.BOTANY -> {
            val grow = if (motion == 0f) 1f else smooth(u / 4.5f)
            fun stem(s: Float) = Offset(w * (0.12f + 0.62f * s + 0.07f * sin(s * 7f + sd % 10)), h * (0.94f - 0.8f * s))
            val n = 60
            val path = Path(); path.moveTo(stem(0f).x, stem(0f).y)
            val last = (grow * n).toInt()
            for (i in 1..last) { val p = stem(i / n.toFloat()); path.lineTo(p.x, p.y) }
            drawPath(path, col.copy(alpha = 0.8f * a), style = Stroke(w * 0.005f, cap = StrokeCap.Round))
            for (b in 1..7) {
                val s0 = b / 8f
                if (s0 > grow) break
                val g = ((grow - s0) / 0.12f).coerceIn(0f, 1f)
                val p = stem(s0)
                val dir = if (b % 2 == 0) 1f else -1f
                val leafL = w * 0.09f * g
                withTransform({ translate(p.x, p.y); rotate(dir * (40f + lumiereUnit(sd, b) * 30f) - 90f * 0f, Offset.Zero) }) {
                    drawOval(col.copy(alpha = 0.16f * a), Offset(if (dir > 0) 0f else -leafL, -leafL * 0.2f), Size(leafL, leafL * 0.4f))
                    drawOval(col.copy(alpha = 0.7f * a), Offset(if (dir > 0) 0f else -leafL, -leafL * 0.2f), Size(leafL, leafL * 0.4f), style = Stroke(thin))
                    drawLine(col.copy(alpha = 0.5f * a), Offset.Zero, Offset(dir * leafL, 0f), thin * 0.7f)
                }
            }
            drawRect(Brush.radialGradient(listOf(col.copy(alpha = 0.12f * a), Color.Transparent), stem(grow), w * 0.5f))
        }
        LumiereFamily.ASTRAL -> {
            val c = Offset(w * 0.5f, h * 0.3f)
            withTransform({ rotate(-14f, c) }) {
                listOf(0.18f, 0.3f, 0.43f).forEachIndexed { k, rr ->
                    val rx = w * rr; val ry = rx * 0.36f
                    drawOval(col.copy(alpha = 0.32f * a), Offset(c.x - rx, c.y - ry), Size(rx * 2f, ry * 2f), style = Stroke(thin))
                    val ang = m * (0.5f - k * 0.13f) + k * 2.1f
                    drawCircle(col.copy(alpha = 0.9f * a), w * 0.008f, Offset(c.x + cos(ang) * rx, c.y + sin(ang) * ry))
                }
            }
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.8f * a), col.copy(alpha = 0.18f * a), Color.Transparent), c, w * 0.09f), w * 0.09f, c)
            // 星座连线，逐段画出。
            val stars = List(7) { Offset(w * (0.1f + lumiereUnit(sd, it * 2) * 0.8f), h * (0.08f + lumiereUnit(sd, it * 2 + 1) * 0.3f)) }
            val draw = smooth(u / 3f) * (stars.size - 1)
            for (i in 0 until stars.size - 1) {
                val q = (draw - i).coerceIn(0f, 1f)
                if (q <= 0f) break
                drawLine(col.copy(alpha = 0.45f * a), stars[i], stars[i] + (stars[i + 1] - stars[i]) * q, thin)
            }
            stars.forEach { drawCircle(col.copy(alpha = 0.9f * a), w * 0.006f, it) }
        }
        LumiereFamily.STAGE -> {
            listOf(0.06f, 0.94f).forEachIndexed { k, ax ->
                val apex = Offset(w * ax, -h * 0.02f)
                val swing = sin(m * 0.6f + k * PI.toFloat()) * 0.16f
                val tx = w * (0.5f + (if (k == 0) -0.12f else 0.12f) + swing)
                val ty = h * 0.78f
                val half = w * 0.17f
                val cone = Path().apply { moveTo(apex.x, apex.y); lineTo(tx - half, ty); lineTo(tx + half, ty); close() }
                drawPath(cone, Brush.linearGradient(listOf(col.copy(alpha = 0.42f * a), col.copy(alpha = 0.06f * a)), apex, Offset(tx, ty)))
                drawOval(Brush.radialGradient(listOf(col.copy(alpha = 0.45f * a), Color.Transparent), Offset(tx, ty), half), Offset(tx - half, ty - h * 0.03f), Size(half * 2f, h * 0.06f))
            }
            // 浓烟：一条贴地的雾带。
            drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 0.82f to col.copy(alpha = 0.08f * a), 1f to Color.Transparent))
        }
        LumiereFamily.MOTES -> {
            for (k in 0 until 2) {
                val path = Path()
                val base = h * (0.14f + k * 0.1f)
                path.moveTo(0f, base)
                var x = 0f
                while (x <= w) { x += w / 20f; path.lineTo(x, base + sin(x / w * 5f + m * 0.35f + k * 2f) * h * 0.05f) }
                path.lineTo(w, base + h * 0.22f); path.lineTo(0f, base + h * 0.22f); path.close()
                drawPath(path, Brush.verticalGradient(listOf(col.copy(alpha = 0.26f * a), Color.Transparent), startY = base - h * 0.05f, endY = base + h * 0.22f))
            }
        }
    }
}

/** 浮尘：所有光位都有，萤尘族更多更亮。 */
private fun DrawScope.drawMotes(shot: LumiereShot, t: Double, a: Float, accent: Color, motion: Float) {
    val w = size.width; val h = size.height
    val n = if (shot.family == LumiereFamily.MOTES) 70 else 34
    val col = lightOf(shot.family, accent)
    for (k in 0 until n) {
        val sp = 0.008f + lumiereUnit(shot.seed, k * 3) * 0.02f
        val y = ((lumiereUnit(shot.seed, k * 3 + 1) - t.toFloat() * motion * sp) % 1f + 1f) % 1f
        val x = lumiereUnit(shot.seed, k * 3 + 2) + sin(t.toFloat() * motion * 0.4f + k) * 0.01f
        val tw = 0.5f + 0.5f * sin(t.toFloat() * motion * (1f + k % 5) + k * 1.3f)
        drawCircle(col.copy(alpha = (0.15f + 0.45f * tw) * a), w * (0.002f + 0.003f * lumiereUnit(shot.seed, k)), Offset(x * w, y * h))
    }
}

/** 星空点亮：开场时光点从上方倾泻而下。 */
private fun DrawScope.drawStarfall(shot: LumiereShot, t: Double, accent: Color) {
    val u = (t - shot.start).toFloat()
    if (!shot.opening || u < 0f || u > LumiereTiming.OPEN_S.toFloat()) return
    val w = size.width; val h = size.height
    val col = lerp(Color.White, accent, 0.2f)
    for (k in 0 until 26) {
        val delay = lumiereUnit(shot.seed, 300 + k) * 0.7f
        val q = ((u - delay) / 0.9f)
        if (q <= 0f || q >= 1f) continue
        val x = lumiereUnit(shot.seed, 400 + k) * w
        val y = q * q * h * 0.9f
        val al = sin(q * PI.toFloat()) * 0.8f
        drawLine(Brush.verticalGradient(listOf(Color.Transparent, col.copy(alpha = al)), startY = y - h * 0.08f, endY = y), Offset(x, y - h * 0.08f), Offset(x, y), w * 0.003f, cap = StrokeCap.Round)
        drawCircle(col.copy(alpha = al), w * 0.004f, Offset(x, y))
    }
}

// ───────────────────────────── 画：字 ─────────────────────────────

private val LumiereText = Color(0xFFFFF5E3)
private val BurstFamilies = setOf(LumiereFamily.STAGE, LumiereFamily.ASTRAL, LumiereFamily.PRISM, LumiereFamily.ZENITH)

private fun DrawScope.drawLumiereLine(
    l: LumiereLineLayout, line: Line, decayAt: Double, t: Double, light: Color, burst: Boolean, reduceMotion: Boolean,
) {
    if (l.glyphs.isEmpty()) return
    val s = l.size
    val enter0 = line.startTime - LumiereTiming.LEAD_S
    val dq = ((t - decayAt) / LumiereTiming.DECAY_S).toFloat()
    if (dq >= 1.4f) return
    // 背景碎片。
    l.echo?.let { e ->
        val ea = (((t - enter0) / 0.8).toFloat().coerceIn(0f, 1f)) * (1f - dq.coerceIn(0f, 1f)) * 0.07f
        if (ea > 0f) drawText(e, light, Offset(l.echoX, l.echoY), alpha = ea)
    }
    l.glyphs.forEach { g ->
        val e = ((t - enter0 - g.order * 0.025) / 0.4).toFloat().coerceIn(0f, 1f)
        if (e <= 0f) return@forEach
        // 崩解：逐字错开，上漂 + 横向散开 + 淡出。
        val d = if (reduceMotion) (if (dq > 0f) 1f else 0f) else ((dq - lumiereUnit(g.seed, 1) * 0.4f) / 0.6f).coerceIn(0f, 1f)
        val fade = 1f - d
        if (fade <= 0f) return@forEach
        val drift = if (reduceMotion) Offset.Zero else Offset((lumiereUnit(g.seed, 2) - 0.5f) * s * 2f * d, -s * 2.4f * d * d)
        val sung = t >= g.start
        val since = (t - g.start).toFloat()
        val lit = if (sung) min(1f, since / 0.18f) else 0f
        val c = Offset(g.cx, g.cy) + drift
        // 光晕：唱到时先放大再收拢。
        if (lit > 0f) {
            val pulse = if (reduceMotion) 1f else 1f + 0.5f * (1f - min(1f, since / 0.5f))
            val r = s * 0.95f * pulse
            drawCircle(Brush.radialGradient(listOf(light.copy(alpha = 0.34f * lit * fade), Color.Transparent), c, r), r, c)
        }
        if (burst && sung && !reduceMotion && since < 0.4f && Math.floorMod(g.seed, 3) == 0) {
            val q = since / 0.4f
            val al = (1f - q) * fade
            val len = s * 2.2f * (0.4f + 0.6f * q)
            val bc = lerp(light, Color(0xFFFF8A3D), 0.45f)
            drawLine(bc.copy(alpha = al), c - Offset(0f, len), c + Offset(0f, len), s * 0.06f, cap = StrokeCap.Round)
            drawLine(bc.copy(alpha = al), c - Offset(len * 0.45f, 0f), c + Offset(len * 0.45f, 0f), s * 0.05f, cap = StrokeCap.Round)
        }
        val col = lerp(Color.White.copy(alpha = 1f), LumiereText, lit)
        val alpha = (0.26f + 0.74f * lit) * e * fade
        val tl = Offset(c.x - g.layout.size.width / 2f, c.y - g.layout.size.height / 2f + (1f - e) * s * 0.25f)
        drawText(g.layout, col, tl, alpha = alpha)
    }
}

// ───────────────────────────── 舞台 ─────────────────────────────

@Composable
internal fun LumiereStage(
    lines: List<Line>,
    clock: VisualizerClock,
    lyricsFontScale: Double,
    accentColor: Color,
    fontFamily: FontFamily,
    reduceMotion: Boolean,
    topReserve: Dp,
    bottomReserve: Dp,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val shots = remember(lines) { compileLumiere(lines) }
    val shotOfLine = remember(shots) { HashMap<Int, Int>().also { m -> shots.forEachIndexed { si, s -> s.lines.forEach { m[it] = si } } } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val topPx = with(density) { topReserve.toPx() }
        val bottomPx = with(density) { bottomReserve.toPx() }
        val sMin = with(density) { 20.sp.toPx() } * lyricsFontScale.toFloat()
        val sMax = min(w * 0.092f, h * 0.052f) * lyricsFontScale.toFloat()
        val style = remember(fontFamily) { TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Medium) }
        val cache = remember(lines, w, h, lyricsFontScale, style, shots) { HashMap<Int, LumiereLineLayout>() }
        val motion = if (reduceMotion) 0f else 1f
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.readForDraw()
            if (w <= 0f || h <= 0f || shots.isEmpty()) return@Canvas
            // 1) 暗场 + 一抹强调色。
            drawRect(LumiereInk)
            drawRect(Brush.radialGradient(listOf(accentColor.copy(alpha = 0.14f), Color.Transparent), Offset(w * 0.5f, h * 1.05f), max(w, h) * 0.8f))
            val si = lumiereShotAt(shots, t)
            val shot = shots[si]
            val u = (t - shot.start).toFloat()
            // 2) 运镜：镜头内缓入缓出推近 + 手持浮动。
            val prog = smooth(((t - shot.start) / max(0.5, shot.end - shot.start)).toFloat())
            val tf = t.toFloat() * motion
            val sc = 1f + 0.06f * prog * motion + 0.006f * motion * sin(tf * 0.31f + 0.7f)
            val fx = (sin(tf * 0.21f + 0.4f) * 0.6f + sin(tf * 0.53f + 1.9f) * 0.4f) * 0.006f * w
            val fy = (cos(tf * 0.17f + 1.1f) * 0.6f + sin(tf * 0.47f + 0.3f) * 0.4f) * 0.006f * h
            val rot = 0.25f * motion * sin(tf * 0.13f + 2.1f)
            withTransform({ translate(fx, fy); rotate(rot, Offset(w / 2f, h / 2f)); scale(sc, sc, Offset(w / 2f, h / 2f)) }) {
                // 3) 光位：上一镜淡出、这一镜淡入；段首镜头随星空点亮慢慢亮起。
                val inA = if (shot.opening) smooth(u / LumiereTiming.OPEN_S.toFloat()) else smooth(u / LumiereTiming.FADE_S.toFloat())
                if (si > 0 && u < LumiereTiming.FADE_S.toFloat() && !shot.opening) {
                    val p = shots[si - 1]
                    drawRig(p, t, 1f - inA, accentColor, motion)
                }
                drawRig(shot, t, inA, accentColor, motion)
                drawMotes(shot, t, max(0.4f, inA), accentColor, motion)
                drawStarfall(shot, t, accentColor)
                // 4) 字：当前行、同镜上一行，以及正在崩解的旧行。
                val cur = lumiereLineAt(lines, t)
                for (li in max(0, cur - 2)..cur) {
                    if (li < 0) continue
                    val lsi = shotOfLine[li] ?: continue
                    val ls = shots[lsi]
                    val line = lines[li]
                    // 同一镜头里的行一直挂到镜头结束；换镜头或下一行占据同一槽位时崩解。
                    val decayAt = if (lsi == si) ls.end else max(line.endTime, ls.end)
                    val layout = cache.getOrPut(li) {
                        layoutLumiereLine(line, li, ls, w, topPx + h * 0.02f, h - bottomPx, sMax, max(1f, min(sMin, sMax)), measurer, style)
                    }
                    drawLumiereLine(layout, line, decayAt, t, lightOf(ls.family, accentColor).let { lerp(it, Color.White, 0.4f) }, ls.family in BurstFamilies, reduceMotion)
                }
            }
        }
    }
}
