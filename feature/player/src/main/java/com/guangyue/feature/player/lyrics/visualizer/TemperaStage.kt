// 对应 Folia 文件：src/components/visualizer/tempera/（凝彩）——README.md（网点图形 screentone MG：段落→shot→slice 编译、
// 每行按词切成 2~4 词或 ~2.2s 的 slice，单字中文粘连最多撑到 3.2s；≥1.2s 空隙编成无字 bridge shot，≤5s 一个）、
// temperaProgram.ts（编译）、temperaShotProfiles.ts（构图族 / 入场向量 / mood）、compositions/*（分割 / 色带 / 框窗 / 海报 /
// 稀疏 / 物语留白卡）、temperaLayout.ts（拼贴排版：每 slice 一个 hero 词放大 1.34~1.6×、其余压到 0.7~0.86×，行高 1.02~1.12）、
// temperaEnterStyles.ts（7 种入场：slide / from-left / from-right / from-above / from-below / swing / stamp，整词同一种、相邻词不同，
// 有位移的拖 2 层浮影）、temperaDifferenceFilter.ts（文字反色：压过色块的笔画翻色）、temperaHatch.ts（网点 / 斜线）、
// createTemperaPixiRuntime.ts（shot 之间沿 flowAngle 纵向交接：上一个继续推出画面、下一个从上游推进来，同屏重叠 0.3×shot 钳 0.4~1.1s）。
//
// P8「凝彩」手机版：不做 Pixi runtime / 121 种镜头 / 后处理 filter / 封面取色渐变 / 用户图片层 / 墨染奥行。保留：纸色 + 墨色 + 强调色的
// duo 调色（取自主题），六族构图（分割、色带、框窗、海报、稀疏、物语留白卡）带色块入场；slice 切分与 bridge shot；hero 词放大；
// 七种逐词入场与浮影；文字用 Difference 混合直接反色；超大水印词垫在字下；shot 间纵向推镜交接。全由时刻算出，seek 到哪都一样。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// ───────────────────────────── 程序（纯函数，可单测） ─────────────────────────────

/** 构图族（Folia 十三族里手机画得动的六族）。 */
internal enum class TemperaComp {
    /** 分割：一道斜切把画面分成纸 / 墨两半，切口压一条强调色细带。 */
    SPLIT,
    /** 色带：三条横带（墨 / 强调色 / 网点）。 */
    BANDS,
    /** 框窗：遮幅窗口，四周墨色、中间镂空（1:1 / 4:3 / 2.39:1）。 */
    FRAME,
    /** 海报：一轮强调色大圆 + 放射网点 + 一根竖墨条。 */
    POSTER,
    /** 稀疏：细线十字 + 小方标 + 斜线块。 */
    SPARSE,
    /** 物语留白卡：整屏强调色平涂 + 大字，不叠装饰。只给有字的 shot。 */
    BLANK,
}

/** 入场方式（Folia TEMPERA_ENTER_STYLES）。 */
internal enum class TemperaEnter { SLIDE, FROM_LEFT, FROM_RIGHT, FROM_ABOVE, FROM_BELOW, SWING, STAMP }

internal data class TemperaUnit(val text: String, val t0: Double, val enter: TemperaEnter)

/** 一个 shot：[start, end) 是它独占的时段，交接在 end 前后各半个 handoff 内与下一个重叠。 */
internal data class TemperaShot(
    val start: Double,
    val end: Double,
    val units: List<TemperaUnit>,
    val line: Int,
    val comp: TemperaComp,
    /** 交接方向角（度）：0 = 内容向上推出，以纵向为主轴，左右偏不超过 ±18°。 */
    val flow: Float,
    val hero: Int,
    val seed: Int,
) {
    val bridge: Boolean get() = units.isEmpty()
}

internal object TemperaTiming {
    /** 字比开唱早这么久开始入场；shot 比首字再早这么久搭构图。 */
    const val UNIT_LEAD_S = 0.1
    const val SHOT_LEAD_S = 0.45
    const val ENTER_S = 0.34
    /** slice 目标时长、单字粘连上限、空隙阈值、bridge 上限（Folia 原值）。 */
    const val SLICE_S = 2.2
    const val SLICE_MAX_S = 3.2
    const val GAP_S = 1.2
    const val BRIDGE_MAX_S = 5.0
    /** 色块入场时长。 */
    const val BLOCK_S = 0.55

    /** 交接时长：shot 时长的 0.3，钳在 0.4~1.1s。 */
    fun handoff(dur: Double) = (dur * 0.3).coerceIn(0.4, 1.1)
}

internal fun temperaHash(s: String): Int {
    var h = 0x811C9DC5.toInt()
    s.forEach { h = (h xor it.code) * 0x01000193 }
    return h
}

private fun tu01(seed: Int, salt: Int): Float {
    var x = seed * 0x27d4eb2d + salt * 0x165667b1
    x = x xor (x ushr 15); x *= 0x2c1b3c6d; x = x xor (x ushr 12)
    return ((x ushr 8) and 0xffff) / 65535f
}

private fun isCjkUnit(s: String) = s.codePointCount(0, s.length) == 1 && s.codePointAt(0).let { cp ->
    Character.isIdeographic(cp) || Character.UnicodeScript.of(cp).let {
        it == Character.UnicodeScript.HIRAGANA || it == Character.UnicodeScript.KATAKANA || it == Character.UnicodeScript.HANGUL
    }
}

/** 一行切成 slice：2~4 词（中文单字算半个词）或 ~2.2s 一刀；尾巴不足一词并回上一刀；单个 slice 不超过 3.2s（单字词除外）。 */
internal fun temperaSlices(units: List<Pair<String, Double>>): List<List<Pair<String, Double>>> {
    if (units.isEmpty()) return emptyList()
    val out = ArrayList<MutableList<Pair<String, Double>>>()
    var cur = ArrayList<Pair<String, Double>>()
    var weight = 0f
    units.forEach { u ->
        if (cur.isNotEmpty()) {
            val dur = u.second - cur.first().second
            val full = (weight >= 2f && dur >= TemperaTiming.SLICE_S) || weight >= 4f || dur >= TemperaTiming.SLICE_MAX_S
            if (full) { out += cur; cur = ArrayList(); weight = 0f }
        }
        cur += u
        weight += if (isCjkUnit(u.first)) 0.5f else 1f
    }
    if (cur.isNotEmpty()) {
        if (out.isNotEmpty() && weight < 1f) out.last().addAll(cur) else out += cur
    }
    return out
}

/** 整首编译：每行切 slice 成 shot，行间 ≥1.2s 的空隙（含前奏、尾奏）编成 1~3 个无字 bridge shot。相邻构图不重复。 */
internal fun compileTempera(lines: List<Line>, songEnd: Double? = null): List<TemperaShot> {
    if (lines.isEmpty()) return emptyList()
    // 先排出每个 shot 的起点和内容，end 统一取下一个的起点。
    data class Draft(val start: Double, val units: List<Pair<String, Double>>, val line: Int, val tag: String)
    val drafts = ArrayList<Draft>()
    fun bridges(from: Double, to: Double, li: Int) {
        val gap = to - from
        if (gap < TemperaTiming.GAP_S) return
        val n = min(3, max(1, kotlin.math.ceil(gap / TemperaTiming.BRIDGE_MAX_S).toInt()))
        for (k in 0 until n) drafts += Draft(from + gap * k / n, emptyList(), li, "bridge$li#$k")
    }
    val firstStart = lines.first().startTime - TemperaTiming.SHOT_LEAD_S
    if (firstStart > TemperaTiming.GAP_S) bridges(0.0, firstStart, -1)
    lines.forEachIndexed { li, line ->
        val slices = temperaSlices(nebbiaUnits(line))
        slices.forEachIndexed { si, sl ->
            val s = sl.first().second - TemperaTiming.SHOT_LEAD_S
            // 段首 shot 不越过上一个 shot 的起点（防短句挤在一起时倒序）。
            val floor = drafts.lastOrNull()?.start?.plus(0.2) ?: Double.NEGATIVE_INFINITY
            drafts += Draft(max(s, floor), sl, li, "l$li.$si:${sl.joinToString("") { it.first }}")
        }
        val nextStart = lines.getOrNull(li + 1)?.startTime?.minus(TemperaTiming.SHOT_LEAD_S)
            ?: max(songEnd ?: 0.0, line.endTime + 8.0)
        if (slices.isNotEmpty()) bridges(line.endTime + 0.3, nextStart, li)
    }
    val tail = max(songEnd ?: 0.0, lines.last().endTime + 8.0)
    val shots = ArrayList<TemperaShot>()
    var prev: TemperaComp? = null
    var prev2: TemperaComp? = null
    var lyricCount = 0
    drafts.forEachIndexed { i, d ->
        val seed = temperaHash(d.tag)
        val end = drafts.getOrNull(i + 1)?.start ?: tail
        val pool = TemperaComp.values().filter { it != TemperaComp.BLANK }
        var comp = pool[Math.floorMod(seed, pool.size)]
        // 物语留白卡：有字的 shot 每六个里给一个。
        if (d.units.isNotEmpty()) { lyricCount++; if (lyricCount % 6 == 4) comp = TemperaComp.BLANK }
        while (comp == prev || comp == prev2) comp = pool[(pool.indexOf(comp).coerceAtLeast(0) + 1) % pool.size]
        prev2 = prev; prev = comp
        val enters = TemperaEnter.values()
        var lastEnter: TemperaEnter? = null
        val units = d.units.mapIndexed { k, (txt, t0) ->
            var e = enters[Math.floorMod(temperaHash("$txt#$k") xor seed, enters.size)]
            if (e == lastEnter) e = enters[(e.ordinal + 1) % enters.size]
            lastEnter = e
            TemperaUnit(txt, t0, e)
        }
        // hero 词：最长的那个；一样长（中文单字）就按种子挑。
        val hero = if (units.isEmpty()) -1 else {
            val maxLen = units.maxOf { it.text.length }
            val cands = units.indices.filter { units[it].text.length == maxLen }
            cands[Math.floorMod(seed ushr 5, cands.size)]
        }
        shots += TemperaShot(d.start, end, units, d.line, comp, (tu01(seed, 3) - 0.5f) * 36f, hero, seed)
    }
    return shots
}

/** 此刻画面上的 shot：自己的时段，加上交接窗口里正在进 / 出的那个。返回（序号，进出量）：负 = 正在推进来，正 = 正在推出去。 */
internal fun temperaShotsAt(shots: List<TemperaShot>, t: Double): List<Pair<Int, Float>> {
    if (shots.isEmpty()) return emptyList()
    var cur = -1
    for (i in shots.indices) if (shots[i].start <= t) cur = i else break
    if (cur < 0) return listOf(0 to -1f + ((t - (shots[0].start - TemperaTiming.handoff(shots[0].end - shots[0].start))) /
        TemperaTiming.handoff(shots[0].end - shots[0].start)).toFloat().coerceIn(0f, 1f))
    val out = ArrayList<Pair<Int, Float>>()
    val s = shots[cur]
    // 刚开始：上一个还在推出、自己还在推进（前半个窗口在 start 之前已开始，这里只算 start 之后的半段）。
    if (cur > 0 && ((t - s.start) / (TemperaTiming.handoff(s.start - shots[cur - 1].start) / 2)).toFloat().let { it < 1f }) {
        val inQ = ((t - s.start) / (TemperaTiming.handoff(s.start - shots[cur - 1].start) / 2)).toFloat()
        out += (cur - 1) to (0.5f + inQ * 0.5f)
        out += cur to (-0.5f + inQ * 0.5f)
        return out
    }
    val nxt = shots.getOrNull(cur + 1)
    if (nxt != null) {
        val h = TemperaTiming.handoff(s.end - s.start) / 2
        val outP = ((t - (nxt.start - h)) / h).toFloat()
        if (outP > 0f) {
            out += cur to (outP * 0.5f)
            out += (cur + 1) to (-1f + outP * 0.5f)
            return out
        }
    }
    out += cur to 0f
    return out
}

// ───────────────────────────── 排版 ─────────────────────────────

private val OnePxT = androidx.compose.ui.unit.Density(1f, 1f)

private class TemperaGlyph(val layout: TextLayoutResult, val x: Float, val y: Float, val unit: TemperaUnit, val order: Int)
private class TemperaShotLayout(val glyphs: List<TemperaGlyph>, val size: Float, val watermark: TextLayoutResult?)

private enum class TemperaAlign { CENTER, START }

/** 每个构图把字摆在哪：中心（占宽 / 可用高的比例）、宽度、对齐。 */
private fun textSlot(c: TemperaComp, seed: Int): Triple<Offset, Float, TemperaAlign> = when (c) {
    TemperaComp.SPLIT -> Triple(Offset(0.5f, 0.48f), 0.84f, TemperaAlign.CENTER)
    TemperaComp.BANDS -> Triple(Offset(0.5f, 0.3f + tu01(seed, 21) * 0.4f), 0.86f, TemperaAlign.CENTER)
    TemperaComp.FRAME -> Triple(Offset(0.5f, 0.5f), 0.78f, TemperaAlign.CENTER)
    TemperaComp.POSTER -> Triple(Offset(0.1f, 0.6f), 0.8f, TemperaAlign.START)
    TemperaComp.SPARSE -> Triple(Offset(0.1f, 0.36f + tu01(seed, 22) * 0.2f), 0.8f, TemperaAlign.START)
    TemperaComp.BLANK -> Triple(Offset(0.5f, 0.46f), 0.84f, TemperaAlign.CENTER)
}

private fun layoutTemperaShot(
    shot: TemperaShot, lines: List<Line>, w: Float, top: Float, bottom: Float,
    sMax: Float, sMin: Float, measurer: TextMeasurer, base: TextStyle,
): TemperaShotLayout {
    // 水印：取全曲另一行里没在本 shot 出现的两三个字，超大号垫在字下面。
    val wmText = if (lines.size < 2) null else {
        val other = lines[Math.floorMod(shot.line + 3 + (shot.seed ushr 9) % 5, lines.size)].takeIf { it !== lines.getOrNull(shot.line) }
        val here = shot.units.joinToString("") { it.text }
        other?.fullText?.filter { !it.isWhitespace() && !here.contains(it) }?.take(if (other.fullText.any { it.code > 0x2E80 }) 2 else 5)
            ?.takeIf { it.isNotBlank() }
    }
    val usableH = bottom - top
    val wm = if (wmText == null || shot.comp == TemperaComp.BLANK) null
        else measurer.measure(wmText, base.copy(fontSize = (usableH * 0.22f).sp), density = OnePxT)
    if (shot.units.isEmpty()) return TemperaShotLayout(emptyList(), sMax, wm)
    val (slot, widthF, align) = textSlot(shot.comp, shot.seed)
    val maxW = w * widthF
    val heroK = 1.34f + tu01(shot.seed, 7) * 0.26f
    val restK = 0.7f + tu01(shot.seed, 8) * 0.16f
    val ref = 100f
    val refStyle = base.copy(fontSize = ref.sp)
    val widths = shot.units.map { measurer.measure(it.text, refStyle, density = OnePxT).size.width.toFloat() }
    fun kOf(i: Int) = if (i == shot.hero) heroK else restK
    // 词间只有原文是西文（有空白）时给一个空格宽，中文只留 0.035em。
    fun gapOf(i: Int, s: Float) = if (isCjkUnit(shot.units[i].text)) s * 0.035f else s * 0.28f
    var s = sMax
    var rows: List<List<Int>>
    while (true) {
        val r = ArrayList<MutableList<Int>>(); var cur = ArrayList<Int>(); var cw = 0f
        shot.units.indices.forEach { k ->
            val uw = widths[k] / ref * s * kOf(k)
            val add = if (cur.isEmpty()) uw else uw + gapOf(k, s)
            if (cur.isNotEmpty() && cw + add > maxW) { r += cur; cur = ArrayList(); cw = 0f }
            cw += if (cur.isEmpty()) uw else uw + gapOf(k, s)
            cur += k
        }
        if (cur.isNotEmpty()) r += cur
        rows = r
        if ((rows.size <= 3 && rows.none { row -> row.sumOf { (widths[it] / ref * s * kOf(it)).toDouble() } > maxW }) || s <= sMin) break
        s = max(sMin, s * 0.9f)
    }
    val rowH = rows.map { row -> row.maxOf { kOf(it) } * s * 1.06f }
    val totalH = rowH.sum()
    val cy = top + usableH * slot.y
    var y = (cy - totalH / 2f).coerceIn(top, max(top, bottom - totalH))
    val glyphs = ArrayList<TemperaGlyph>()
    var order = 0
    rows.forEachIndexed { ri, row ->
        val ws = row.map { widths[it] / ref * s * kOf(it) }
        val rowW = ws.sum() + row.drop(1).sumOf { gapOf(it, s).toDouble() }.toFloat()
        // 行与行左右错开一点（拼贴感），START 对齐时往右阶梯。
        var x = when (align) {
            TemperaAlign.CENTER -> (w - rowW) / 2f + (if (rows.size > 1) (ri - (rows.size - 1) / 2f) * s * 0.4f else 0f)
            TemperaAlign.START -> w * slot.x + ri * s * 0.5f
        }.coerceIn(w * 0.04f, max(w * 0.04f, w * 0.96f - rowW))
        val baseY = y + rowH[ri]
        row.forEachIndexed { k, idx ->
            val st = base.copy(fontSize = (s * kOf(idx)).sp)
            val lay = measurer.measure(shot.units[idx].text, st, density = OnePxT)
            // 各词按基线对齐：hero 大字和小字底边齐平。
            glyphs += TemperaGlyph(lay, x, baseY - lay.firstBaseline, shot.units[idx], order++)
            x += ws[k] + (row.getOrNull(k + 1)?.let { gapOf(it, s) } ?: 0f)
        }
        y += rowH[ri]
    }
    return TemperaShotLayout(glyphs, s, wm)
}

// ───────────────────────────── 画 ─────────────────────────────

private fun easeOut3(x: Float) = 1f - (1f - x.coerceIn(0f, 1f)).pow(3)
private fun softBack(x: Float): Float { val c1 = 1.2f; val c3 = c1 + 1f; val y = x.coerceIn(0f, 1f) - 1f; return 1f + c3 * y * y * y + c1 * y * y }
private fun smooth(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

/** 网点：一格一个圆点，半径沿 [dir] 方向从 0 渐到满格（screentone 渐变）。 */
private fun DrawScope.halftone(rect: Rect, cell: Float, color: Color, dirX: Float, dirY: Float, alpha: Float) {
    if (alpha <= 0f || rect.width <= 0f || rect.height <= 0f) return
    val nx = (rect.width / cell).toInt() + 1
    val ny = (rect.height / cell).toInt() + 1
    if (nx * ny > 1600) return
    for (j in 0 until ny) for (i in 0 until nx) {
        val px = rect.left + (i + if (j % 2 == 0) 0.25f else 0.75f) * cell
        val py = rect.top + (j + 0.5f) * cell
        val u = (((px - rect.left) / rect.width) * dirX + ((py - rect.top) / rect.height) * dirY).let { if (dirX + dirY < 0) 1f + it else it }
        val r = cell * 0.5f * u.coerceIn(0f, 1f)
        if (r > 0.4f) drawCircle(color.copy(alpha = alpha), r, Offset(px, py))
    }
}

/** 斜线块（temperaHatch 的 45° 斜纹）。 */
private fun DrawScope.hatch(rect: Rect, gap: Float, width: Float, color: Color) {
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        var x = rect.left - rect.height
        while (x < rect.right) {
            drawLine(color, Offset(x, rect.bottom), Offset(x + rect.height, rect.top), width)
            x += gap
        }
    }
}

private fun DrawScope.drawComp(shot: TemperaShot, a: Float, w: Float, h: Float, top: Float, bottom: Float, ink: Color, accent: Color) {
    val sd = shot.seed
    val mid = top + (bottom - top) * 0.5f
    val cell = w / 34f
    when (shot.comp) {
        TemperaComp.SPLIT -> {
            // 斜切从一侧扫进来。
            val up = tu01(sd, 31) < 0.5f
            val y0 = mid + (tu01(sd, 32) - 0.5f) * h * 0.2f
            val tilt = h * (0.08f + tu01(sd, 33) * 0.1f) * (if (tu01(sd, 34) < 0.5f) 1f else -1f)
            val sweep = (1f - easeOut3(a)) * h * 0.6f * (if (up) -1f else 1f)
            val l = y0 - tilt + sweep; val r = y0 + tilt + sweep
            val far = if (up) -h else h * 2f
            val p = Path().apply { moveTo(0f, l); lineTo(w, r); lineTo(w, far); lineTo(0f, far); close() }
            drawPath(p, ink)
            val band = h * 0.012f
            drawLine(accent, Offset(0f, l + (if (up) band * 2 else -band * 2)), Offset(w * easeOut3(a), r + (if (up) band * 2 else -band * 2) * 1f), band * 2f)
            halftone(Rect(0f, if (up) max(l, r) + cell else top, w * 0.42f, if (up) bottom else min(l, r) - cell), cell, ink, 1f, 0f, 0.5f * a)
        }
        TemperaComp.BANDS -> {
            val ys = listOf(0.18f + tu01(sd, 41) * 0.1f, 0.48f + tu01(sd, 42) * 0.08f, 0.74f + tu01(sd, 43) * 0.08f)
            ys.forEachIndexed { k, f ->
                val y = top + (bottom - top) * f
                val bh = h * (if (k == 1) 0.12f else 0.06f + tu01(sd, 44 + k) * 0.05f)
                val e = easeOut3((a * 1.4f - k * 0.2f))
                val fromLeft = (k + sd) % 2 == 0
                val len = w * e
                val rect = if (fromLeft) Rect(0f, y - bh / 2, len, y + bh / 2) else Rect(w - len, y - bh / 2, w, y + bh / 2)
                when (k) {
                    0 -> drawRect(ink, rect.topLeft, rect.size)
                    1 -> drawRect(accent, rect.topLeft, rect.size)
                    else -> halftone(rect, cell * 0.8f, ink, if (fromLeft) 1f else -1f, 0f, 0.9f)
                }
            }
        }
        TemperaComp.FRAME -> {
            val aspect = listOf(1f, 4f / 3f, 2.39f)[Math.floorMod(sd ushr 3, 3)]
            val ww = w * 0.86f
            val wh = min(ww / aspect, (bottom - top) * 0.62f)
            val cx = w / 2f; val cy = mid
            // 遮幅从屏外合拢进来。
            val k = 1f + (1f - easeOut3(a)) * 1.2f
            val win = Rect(cx - ww * k / 2, cy - wh * k / 2, cx + ww * k / 2, cy + wh * k / 2)
            val p = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(-w, -h, w * 2, h * 2)); addRect(win)
            }
            drawPath(p, ink)
            drawRect(accent, Offset(win.left, win.bottom + h * 0.012f), Size(win.width * 0.22f * a, h * 0.008f))
        }
        TemperaComp.POSTER -> {
            val r = w * (0.34f + tu01(sd, 51) * 0.12f) * softBack(a)
            val c = Offset(w * (0.58f + tu01(sd, 52) * 0.2f), top + (bottom - top) * (0.26f + tu01(sd, 53) * 0.1f))
            drawCircle(accent, max(0f, r), c)
            halftone(Rect(0f, c.y + r * 0.4f, w, min(bottom, c.y + r * 2.2f)), cell, ink, 0f, -1f, 0.55f * a)
            val bx = w * (0.06f + tu01(sd, 54) * 0.06f)
            drawRect(ink, Offset(bx, top), Size(w * 0.025f, (bottom - top) * easeOut3(a)))
        }
        TemperaComp.SPARSE -> {
            val e = easeOut3(a)
            val hx = w * (0.62f + tu01(sd, 61) * 0.2f)
            val vy = top + (bottom - top) * (0.68f + tu01(sd, 62) * 0.14f)
            drawLine(ink, Offset(hx, top), Offset(hx, top + (bottom - top) * e), 2.5f)
            drawLine(ink, Offset(0f, vy), Offset(w * e, vy), 2.5f)
            drawRect(accent, Offset(hx - cell * 0.6f, vy - cell * 0.6f), Size(cell * 1.2f, cell * 1.2f))
            val hr = Rect(hx + cell, vy + cell, w - cell, min(bottom, vy + cell + (bottom - top) * 0.2f))
            hatch(Rect(hr.left, hr.top, hr.left + hr.width * e, hr.bottom), cell * 0.6f, 2f, ink)
            drawRect(ink, Offset(w * 0.08f, vy - cell * 3f), Size(cell * 3f, cell * 0.6f), style = Stroke(2f))
        }
        TemperaComp.BLANK -> {
            // 整屏平涂从下往上铺满。
            val fy = h * (1f - easeOut3(a))
            drawRect(accent, Offset(0f, fy), Size(w, h - fy + h))
        }
    }
}

private fun enterFrame(e: TemperaEnter, p: Float, dist: Float, flow: Float): Triple<Offset, Float, Float> {
    // 返回（位移、旋转、等比缩放）；p 是 0→1 的入场进度。
    val travel = 1f - easeOut3(p)
    val rad = flow / 180f * PI.toFloat()
    return when (e) {
        TemperaEnter.SLIDE -> Triple(Offset(sin(rad), cos(rad)) * dist * travel, 0f, 1f)
        TemperaEnter.FROM_LEFT -> Triple(Offset(-dist * travel, dist * 0.12f * travel), 0f, 1f)
        TemperaEnter.FROM_RIGHT -> Triple(Offset(dist * travel, -dist * 0.12f * travel), 0f, 1f)
        TemperaEnter.FROM_ABOVE -> Triple(Offset(dist * 0.12f * travel, -dist * travel), 0f, 1f)
        TemperaEnter.FROM_BELOW -> Triple(Offset(-dist * 0.12f * travel, dist * travel), 0f, 1f)
        TemperaEnter.SWING -> Triple(Offset(-dist * 0.7f * travel, dist * 0.5f * travel), -24f * travel, 1f)
        TemperaEnter.STAMP -> Triple(Offset.Zero, 0f, 1f + (1f - softBack(p)) * 0.8f)
    }
}

@Composable
internal fun TemperaStage(
    lines: List<Line>,
    clock: VisualizerClock,
    lyricsFontScale: Double,
    paperColor: Color,
    inkColor: Color,
    accentColor: Color,
    fontFamily: FontFamily,
    reduceMotion: Boolean,
    topReserve: Dp,
    bottomReserve: Dp,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val shots = remember(lines) { compileTempera(lines) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val top = with(density) { topReserve.toPx() }
        val bottom = h - with(density) { bottomReserve.toPx() }
        val sMin = with(density) { 20.sp.toPx() } * lyricsFontScale.toFloat()
        val sMax = min(w * 0.16f, h * 0.086f) * lyricsFontScale.toFloat()
        val style = remember(fontFamily) { TextStyle(fontFamily = fontFamily, fontWeight = FontWeight.Black) }
        val cache = remember(lines, w, h, lyricsFontScale, style) { HashMap<Int, TemperaShotLayout>() }
        // 强调色和纸色太近（亮度差小）时往墨色推一点，免得色块糊进纸里。
        val accent = remember(accentColor, paperColor, inkColor) {
            val dl = abs(lum(accentColor) - lum(paperColor))
            if (dl < 0.18f) lerp(accentColor, inkColor, 0.35f) else accentColor
        }
        val ink = inkColor.copy(alpha = 1f)
        val paper = paperColor.copy(alpha = 1f)
        val layerPaint = remember { Paint().apply { blendMode = BlendMode.Difference } }
        Canvas(Modifier.fillMaxSize()) {
            if (w <= 0f || h <= 0f || bottom <= top) return@Canvas
            val t = clock.readForDraw()
            drawRect(paper)
            // 先算每个在场 shot 的姿态；色块 / 水印 / 浮影直接画，正文统一放进一层 Difference 里一次合成
            // （逐字 Difference 会让每次绘制都回读整屏，模拟器上卡到 ANR）。
            class Pose(val i: Int, val off: Offset, val zoom: Float, val a: Float, val layout: TemperaShotLayout)
            val poses = ArrayList<Pose>(2)
            temperaShotsAt(shots, t).forEach { (i, phase) ->
                val shot = shots[i]
                val rad = shot.flow / 180f * PI.toFloat()
                // phase：-1 在上游（下方）待命 → 0 就位 → +1 推出到上方。纵向长镜头。
                val push = if (reduceMotion) (if (phase >= -0.5f && phase < 0.5f) 0f else 1e6f) else smooth(abs(phase)) * (if (phase < 0f) -1f else 1f)
                if (push > 1e5f) return@forEach
                val dist = h * 1.05f
                val off = Offset(-sin(rad) * dist * push, -cos(rad) * dist * push)
                val life = (t - shot.start).toFloat()
                val a = if (reduceMotion) 1f else (life / TemperaTiming.BLOCK_S.toFloat() + 0.35f).coerceIn(0f, 1f)
                // 镜头：shot 内缓慢推近（不追逐字）。
                val zoom = if (reduceMotion) 1f else 1f + 0.035f * ((t - shot.start) / max(0.5, shot.end - shot.start)).toFloat().coerceIn(0f, 1.3f)
                val layout = cache.getOrPut(i) { layoutTemperaShot(shot, lines, w, top, bottom, sMax, max(1f, min(sMin, sMax)), measurer, style) }
                poses += Pose(i, off, zoom, a, layout)
            }
            val pivotCam = Offset(w / 2f, (top + bottom) / 2f)
            fun glyphPass(pz: Pose, difference: Boolean) {
                val shot = shots[pz.i]
                val s = pz.layout.size
                pz.layout.glyphs.forEach { g ->
                    val p = if (reduceMotion) (if (t >= g.unit.t0 - TemperaTiming.UNIT_LEAD_S) 1f else 0f)
                        else ((t - (g.unit.t0 - TemperaTiming.UNIT_LEAD_S)) / TemperaTiming.ENTER_S).toFloat()
                    if (p <= 0f) return@forEach
                    val pc = p.coerceIn(0f, 1f)
                    val pivot = Offset(g.x + g.layout.size.width / 2f, g.y + g.layout.size.height / 2f)
                    fun at(q: Float, alpha: Float, color: Color) {
                        val (d, rot, sc) = enterFrame(g.unit.enter, q, s * 1.3f, shot.flow)
                        withTransform({ translate(d.x, d.y); rotate(rot, pivot); scale(sc, sc, pivot) }) {
                            drawText(g.layout, color, Offset(g.x, g.y), alpha = alpha)
                        }
                    }
                    if (difference) at(pc, min(1f, pc * 3f), Color.White)
                    else if (pc < 1f && g.unit.enter != TemperaEnter.STAMP && !reduceMotion) {
                        at(max(0f, pc - 0.22f), 0.14f * (1f - pc), accent)
                        at(max(0f, pc - 0.11f), 0.26f * (1f - pc), accent)
                    }
                }
            }
            poses.forEach { pz ->
                val shot = shots[pz.i]
                withTransform({ translate(pz.off.x, pz.off.y); scale(pz.zoom, pz.zoom, pivotCam) }) {
                    drawComp(shot, pz.a, w, h, top, bottom, ink, accent)
                    // 水印：超大号低透明墨字，贴一侧边缘、可出血。
                    pz.layout.watermark?.let { wm ->
                        val right = tu01(shot.seed, 71) < 0.5f
                        val wx = if (right) w - wm.size.width * 0.78f else -wm.size.width * 0.18f
                        val wy = top + (bottom - top) * (0.08f + tu01(shot.seed, 72) * 0.5f)
                        drawText(wm, ink, Offset(wx, wy), alpha = 0.1f * pz.a)
                    }
                    glyphPass(pz, difference = false)
                }
            }
            // 正文 + 角标：一层白字，整层以 Difference 合成到色块上，压过墨块的笔画自然翻色。
            drawIntoCanvas { it.saveLayer(Rect(0f, 0f, w, h), layerPaint) }
            poses.forEach { pz ->
                withTransform({ translate(pz.off.x, pz.off.y); scale(pz.zoom, pz.zoom, pivotCam) }) {
                    val tag = measurer.measure("No.%02d".format(pz.i + 1), TextStyle(fontFamily = FontFamily.Monospace, fontSize = (w / 34f).sp), density = OnePxT)
                    drawText(tag, Color.White, Offset(w * 0.94f - tag.size.width, top - tag.size.height - 6f), alpha = 0.9f)
                    glyphPass(pz, difference = true)
                }
            }
            drawIntoCanvas { it.restore() }
        }
    }
}

private fun lum(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
