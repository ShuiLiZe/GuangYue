// 对应 Folia 文件：src/components/visualizer/nebbia/（雾笺）——VisualizerNebbia.tsx（外壳 inset-0、底色 #05070a）、
// nebbiaLayout.ts（整首按时间一次排完：每句挑构图、在几个候选位置里找不与「还没被雾吞掉的句子」重叠的地方，
// 字号 max(26, 0.065H)…0.14H × 歌词字号）、nebbiaConstants.ts（擦痕边缘、珠子、下淌水珠、收笔大水珠、
// 句子被雾吞回 breathFade 8 秒）、nebbiaInk.ts / nebbiaWipe.ts（随唱逐字擦开雾）、nebbiaDrops.ts（雾上凝结水珠）。
//
// P5「雾笺」手机版：不做 WebGL 折射 / 城市夜景建筑缓存 / 雨刷与颠簸 / 哈气。夜色用散景光斑代替，
// 雾是一层半透明冷灰（下浓上淡 + 凝结细点），歌词字形以 DstOut 从雾层里擦掉，露出后面的光斑；
// 擦开处随后挂水珠下淌，句子唱完后雾慢慢把字吞回去。排版保留「几个槽位轮换、句子之间不重叠」。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.guangyue.core.lyrics.Line
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 雾笺的时间与排版常量（Folia nebbiaConstants / nebbiaLayout 的手机取值）。 */
internal object NebbiaTiming {
    /** 一个字从开始擦到全清的秒数。 */
    const val WIPE_S = 0.14
    /** 句子唱完（或下一句开唱）后再清晰保留的秒数。 */
    const val HOLD_S = 1.5
    /** 雾把字吞回去的秒数（Folia breathFade 8）。 */
    const val FOG_S = 8.0

    /** 第 [i] 句开始起雾的时刻：下一句开唱与本句唱完取晚者，再留 [HOLD_S]。 */
    fun fogAt(lines: List<Line>, i: Int): Double {
        val next = lines.getOrNull(i + 1)?.startTime ?: lines[i].endTime
        return max(lines[i].endTime, next) + HOLD_S
    }

    /** 第 [i] 句在 [t] 时的清晰度：开唱前 0，起雾前 1，之后线性退到 0；第 i+3 句开唱时强制归零（槽位要让出来）。 */
    fun clarity(lines: List<Line>, i: Int, t: Double): Float {
        if (t < lines[i].startTime - 0.05) return 0f
        val f = fogAt(lines, i)
        if (t <= f) return 1f
        return (1.0 - (t - f) / FOG_S).coerceIn(0.0, 1.0).toFloat()
    }

    /** 字的擦开进度 0..1。 */
    fun wipe(charStart: Double, t: Double): Float = ((t - charStart) / WIPE_S).coerceIn(0.0, 1.0).toFloat()

    /** 槽位个数：同时最多三句在屏上，第 i 句与第 i+3 句共用一个槽位。 */
    const val SLOTS = 3
    /** 槽位竖向中心（占高比例），顺序上下交错，避免相邻两句挤在一起。 */
    val slotY = floatArrayOf(0.26f, 0.76f, 0.51f)
}

/** 一个擦痕字：位置（中心）、字号、旋转、开始擦的时刻。 */
internal class NebbiaGlyph(
    val text: String,
    val layout: TextLayoutResult,
    val cx: Float,
    val cy: Float,
    val size: Float,
    val rot: Float,
    val start: Double,
    /** 收笔挂珠：0 不挂，否则是下淌速度系数。 */
    val drip: Float,
)

/** 把一句拆成「字」：中日韩逐字，拉丁按词（词内不拆，免得字母乱转），每个字分到它在词里的那段时间。 */
internal fun nebbiaUnits(line: Line): List<Pair<String, Double>> {
    val out = ArrayList<Pair<String, Double>>()
    fun split(text: String, t0: Double, t1: Double) {
        val parts = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val n = Character.charCount(cp)
            val ch = text.substring(i, i + n)
            val cjk = Character.isIdeographic(cp) || Character.UnicodeScript.of(cp).let {
                it == Character.UnicodeScript.HIRAGANA || it == Character.UnicodeScript.KATAKANA || it == Character.UnicodeScript.HANGUL
            }
            when {
                Character.isWhitespace(cp) -> { if (sb.isNotEmpty()) { parts += sb.toString(); sb.clear() } }
                cjk -> { if (sb.isNotEmpty()) { parts += sb.toString(); sb.clear() }; parts += ch }
                else -> sb.append(ch)
            }
            i += n
        }
        if (sb.isNotEmpty()) parts += sb.toString()
        if (parts.isEmpty()) return
        val step = (t1 - t0).coerceAtLeast(0.0) / parts.size
        parts.forEachIndexed { k, p -> out += p to (t0 + step * k) }
    }
    if (line.words.isEmpty()) split(line.fullText, line.startTime, line.endTime)
    else line.words.forEach { w -> split(w.text, w.startTime, w.endTime) }
    return out
}

/** 以像素为单位量字：fontSize 的 sp 数即像素。 */
private val OnePx = androidx.compose.ui.unit.Density(1f, 1f)

/** 简单可复现的随机数（同一首歌每次排版一样，seek / 重建也一样）。 */
private class NebbiaRng(seed: Int) {
    private var s = (seed.toLong() and 0xffffffffL) xor 0x9E3779B9L
    fun next(): Float {
        s = (s * 1103515245L + 12345L) and 0x7fffffffL
        return (s % 100000L) / 100000f
    }
}

/** 整首排版：每句一组字；字号从大往小试，直到多行能放进槽位高度（≤ 0.2H，槽位 0.26/0.51/0.76H 让开顶栏与底部胶囊，相邻句不重叠）。 */
internal fun layoutNebbiaSong(
    lines: List<Line>,
    w: Float,
    h: Float,
    fontScale: Float,
    minPx: Float,
    measurer: TextMeasurer,
    base: TextStyle,
): List<List<NebbiaGlyph>> {
    val mx = w * 0.07f
    val sMin = max(minPx, h * 0.045f) * fontScale
    val sMax = min(h * 0.085f, w * 0.17f) * fontScale
    return lines.mapIndexed { li, line ->
        val units = nebbiaUnits(line)
        if (units.isEmpty()) return@mapIndexed emptyList()
        val rnd = NebbiaRng(line.fullText.hashCode() * 31 + li * 7919)
        val ref = 100f
        val refStyle = base.copy(fontSize = ref.sp)
        val widths = units.map { (u, _) -> measurer.measure(u, refStyle, density = OnePx).size.width.toFloat() }
        val gap = 0.18f
        var s = sMax
        var rows: List<List<Int>>
        while (true) {
            val maxW = w - mx * 2
            val r = ArrayList<MutableList<Int>>()
            var cur = ArrayList<Int>(); var cw = 0f
            units.indices.forEach { k ->
                val uw = widths[k] / ref * s
                val add = if (cur.isEmpty()) uw else uw + s * gap
                if (cur.isNotEmpty() && cw + add > maxW) { r += cur; cur = ArrayList(); cw = 0f }
                cw += if (cur.isEmpty()) uw else uw + s * gap
                cur += k
            }
            if (cur.isNotEmpty()) r += cur
            rows = r
            if (rows.size * s * 1.2f <= h * 0.2f || s <= sMin) break
            s = max(sMin, s * 0.9f)
        }
        // 槽位与横向落点：左 / 中 / 右三种，按句子种子挑。
        val cy = h * NebbiaTiming.slotY[li % NebbiaTiming.SLOTS] + (rnd.next() - 0.5f) * h * 0.02f
        val align = (rnd.next() * 3).toInt().coerceIn(0, 2)
        val style = base.copy(fontSize = s.sp)
        val lineH = s * 1.2f
        val top = cy - rows.size * lineH / 2f
        val out = ArrayList<NebbiaGlyph>()
        rows.forEachIndexed { ri, row ->
            val rowW = row.sumOf { (widths[it] / ref * s).toDouble() }.toFloat() + (row.size - 1) * s * gap
            val left = when (align) {
                0 -> mx + rnd.next() * max(0f, (w - mx * 2 - rowW) * 0.3f)
                2 -> w - mx - rowW - rnd.next() * max(0f, (w - mx * 2 - rowW) * 0.3f)
                else -> (w - rowW) / 2f
            }
            var x = left
            row.forEach { k ->
                val uw = widths[k] / ref * s
                val (txt, t0) = units[k]
                out += NebbiaGlyph(
                    text = txt,
                    layout = measurer.measure(txt, style, density = OnePx),
                    cx = x + uw / 2f,
                    cy = top + lineH * (ri + 0.5f) + (rnd.next() - 0.5f) * s * 0.1f,
                    size = s,
                    rot = (rnd.next() - 0.5f) * 7f,
                    start = t0,
                    drip = if (rnd.next() < 0.28f) 0.5f + rnd.next() else 0f,
                )
                x += uw + s * gap
            }
        }
        out
    }
}

/**
 * B0 雾笺重做：Folia nebbiaLayout 构图排版（主词放大、十字 / 竖柱 / 刊头 / 竖排等构图、整屏找空位），
 * 取代旧的「三槽位整行」。每个字一个擦痕字形，竖排里转 90° 的字带旋转。
 */
internal fun composeNebbiaSong(
    lines: List<Line>,
    w: Float,
    h: Float,
    fontScale: Float,
    minPx: Float,
    measurer: TextMeasurer,
    base: TextStyle,
): List<List<NebbiaGlyph>> {
    val src = NebbiaComposer.buildLines(lines)
    val pos = NebbiaComposer.layoutSong(
        src, w.toDouble(), h.toDouble(), fontScale.toDouble(), minPx.toDouble(), jitter = 1.0,
        visibleUntil = { NebbiaTiming.fogAt(lines, it) + NebbiaTiming.FOG_S },
        fogAt = { NebbiaTiming.fogAt(lines, it) },
    )
    return src.mapIndexed { li, line ->
        val rnd = NebbiaRng(line.chars.size * 131 + li * 7919)
        line.chars.mapIndexedNotNull { k, c ->
            if (c.ch.isBlank()) return@mapIndexedNotNull null
            val p = pos[li][k]
            val size = p.s.toFloat()
            NebbiaGlyph(
                text = c.ch,
                layout = measurer.measure(c.ch, base.copy(fontSize = size.sp), density = OnePx),
                cx = p.x.toFloat(),
                cy = p.y.toFloat(),
                size = size,
                rot = (p.rot * 180.0 / Math.PI).toFloat(),
                start = c.t,
                drip = if (rnd.next() < 0.28f) 0.5f + rnd.next() else 0f,
            )
        }
    }
}

private data class NebbiaLight(val x: Float, val y: Float, val r: Float, val color: Color, val phase: Float)

@Composable
internal fun NebbiaStage(
    lines: List<Line>,
    clock: VisualizerClock,
    lyricsFontScale: Double,
    accentColor: Color,
    fontFamily: FontFamily,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val minPx = with(density) { 26.sp.toPx() }
        val style = remember(fontFamily) { TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Bold) }
        val song = remember(lines, w, h, lyricsFontScale, style) {
            if (w <= 0f || h <= 0f) emptyList() else composeNebbiaSong(lines, w, h, lyricsFontScale.toFloat(), minPx, measurer, style)
        }
        // 夜色光斑：城市灯火的散景（暖黄、钠灯橙、冷青、尾灯红），种子固定。
        val lights = remember(w, h, accentColor) {
            val rnd = NebbiaRng(20261108)
            val palette = listOf(Color(0xFFFFC46B), Color(0xFFFF8A3D), Color(0xFF6FD6E8), Color(0xFFE5484D), Color(0xFFF4F1E8), accentColor)
            List(72) {
                val y = h * (0.15f + rnd.next() * 0.85f)
                NebbiaLight(
                    x = rnd.next() * w,
                    y = y,
                    r = (0.03f + rnd.next() * 0.08f) * min(w, h) * (0.6f + y / h * 0.6f),
                    color = palette[(rnd.next() * palette.size).toInt().coerceIn(0, palette.size - 1)],
                    phase = rnd.next() * 6.283f,
                )
            }
        }
        val speckles = remember(w, h) {
            val rnd = NebbiaRng(7)
            List(220) { Triple(rnd.next() * w, rnd.next() * h, 0.6f + rnd.next() * 2.2f) }
        }
        val erase = remember { Color.Black }
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.readForDraw()
            val drift = if (reduceMotion) 0f else t.toFloat()
            // 1) 夜景
            drawRect(Brush.verticalGradient(listOf(Color(0xFF05070A), Color(0xFF0B1220), Color(0xFF151A24))))
            lights.forEach { l ->
                val a = 0.62f + 0.18f * sin(drift * 0.6f + l.phase)
                val c = Offset(l.x + sin(drift * 0.05f + l.phase) * 6f, l.y)
                drawCircle(Brush.radialGradient(listOf(l.color.copy(alpha = a), l.color.copy(alpha = a * 0.45f), Color.Transparent), c, l.r), l.r, c)
            }
            drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0xFFFF9A4A).copy(alpha = 0.22f)))
            // 2) 雾层：离屏图层里先铺雾，再用字形 DstOut 擦开。
            drawIntoCanvas { it.saveLayer(Rect(0f, 0f, size.width, size.height), Paint()) }
            drawRect(Brush.verticalGradient(listOf(Color(0xFF6F7C8C).copy(alpha = 0.66f), Color(0xFF8D98A6).copy(alpha = 0.8f))))
            speckles.forEach { (x, y, r) -> drawCircle(Color(0xFFDDE4EA).copy(alpha = 0.18f), r, Offset(x, y)) }
            song.forEachIndexed { li, glyphs ->
                if (glyphs.isEmpty()) return@forEachIndexed
                val clarity = NebbiaTiming.clarity(lines, li, t)
                if (clarity <= 0f) return@forEachIndexed
                glyphs.forEach { g ->
                    val p = NebbiaTiming.wipe(g.start, t)
                    if (p <= 0f) return@forEach
                    val tl = Offset(g.cx - g.layout.size.width / 2f, g.cy - g.layout.size.height / 2f)
                    rotate(g.rot, Offset(g.cx, g.cy)) {
                        // 指腹宽的软边：先一层宽描边半透明，再实心字 + 窄描边。
                        drawText(g.layout, erase, tl, alpha = clarity * p * 0.4f, drawStyle = Stroke(g.size * 0.1f, join = StrokeJoin.Round), blendMode = BlendMode.DstOut)
                        drawText(g.layout, erase, tl, alpha = clarity * p, drawStyle = Stroke(g.size * 0.035f, join = StrokeJoin.Round), blendMode = BlendMode.DstOut)
                        drawText(g.layout, erase, tl, alpha = clarity * p, drawStyle = Fill, blendMode = BlendMode.DstOut)
                    }
                }
            }
            drawIntoCanvas { it.restore() }
            // 3) 擦痕边挂的水珠：收笔处凝一颗，过一会儿顺玻璃往下淌，跟着句子一起被雾吞掉。
            song.forEachIndexed { li, glyphs ->
                val clarity = NebbiaTiming.clarity(lines, li, t)
                if (clarity <= 0f) return@forEachIndexed
                glyphs.forEach { g ->
                    if (g.drip <= 0f) return@forEach
                    val age = (t - g.start - NebbiaTiming.WIPE_S).toFloat()
                    if (age <= 0f) return@forEach
                    val fall = if (reduceMotion) 0f else min(max(0f, age - 0.8f), 6f) * g.size * 0.14f * g.drip
                    val r = g.size * 0.055f
                    val c = Offset(g.cx + g.size * 0.18f, g.cy + g.size * 0.42f + fall)
                    if (c.y > size.height + r) return@forEach
                    val a = clarity * min(1f, age / 0.3f)
                    if (fall > r) drawLine(Color(0xFF0B1220).copy(alpha = 0.22f * a), Offset(c.x, g.cy + g.size * 0.42f), c, r * 0.5f)
                    drawCircle(Color(0xFF0E1522).copy(alpha = 0.55f * a), r, c)
                    drawCircle(Color.White.copy(alpha = 0.55f * a), r, c, style = Stroke(r * 0.22f))
                    drawCircle(Color.White.copy(alpha = 0.8f * a), r * 0.28f, c + Offset(-r * 0.35f, -r * 0.35f))
                }
            }
        }
    }
}
