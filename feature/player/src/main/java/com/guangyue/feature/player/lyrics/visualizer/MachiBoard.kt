// 町景屋顶广告塔字面的排版与逐字点亮：逐函数对译 Folia v0.7.25 machi/engine/boardLayout.ts。
// 把一行歌词按字面宽高排成 1–3 行（取字最大的那种，行数多了略扣分），记下每个字的 [x0, x1] 和它的唱起、唱完时间；
// 每帧按时间求每一行亮到哪个 x。字的时间来自 GraphemeTimings.buildLineGraphemeTimeline。测量函数由调用方给。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.GraphemeTimings
import com.guangyue.core.lyrics.Line
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 按参考字号量一段字的宽度（像素）。 */
internal typealias MachiMeasure = (String) -> Double

internal data class MachiBoardGlyph(
    val text: String,
    /** 在 fullText 里的 UTF-16 起止下标。 */
    val offset: Int,
    val length: Int,
    val x0: Double,
    val x1: Double,
    val startTime: Double,
    val endTime: Double,
)

internal data class MachiBoardRow(
    val glyphs: List<MachiBoardGlyph>,
    val fontPx: Double,
    /** 基线中心 y（像素，字面半张图里从上往下）。 */
    val centerY: Double,
    val top: Double,
    val bottom: Double,
)

internal data class MachiBoardLayout(val fontPx: Double, val rows: List<MachiBoardRow>, val width: Double, val height: Double)

internal data class MachiBoardBox(val width: Double, val height: Double, val paddingX: Double, val referencePx: Double)

private const val MAX_ROWS = 3
private const val SINGLE_ROW_FILL = 0.56
private const val LINE_GAP = 1.22
private val ROW_PENALTY = doubleArrayOf(1.0, 0.94, 0.84)

private val CJK = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]")
private val STICKY_PUNCTUATION = Regex("^[，。、！？；：,.!?;:」』）)\\]】〉》…~～ー]+$")
/** JS /^\s+$/u（Android ICU 不认 (?U)，开机就抛 PatternSyntaxException；改成逐字判空白）。 */
private fun isMachiSpace(text: String) = text.isNotEmpty() && text.all { it.isWhitespace() || it == '\uFEFF' }

private class MbGlyph(val text: String, val offset: Int, val startTime: Double, val endTime: Double)
private class MbToken(val glyphs: MutableList<MbGlyph> = ArrayList())

/** 一行歌词 → 断行单位：中日韩字一个字一个单位，拉丁词连同后面的空格一个单位，行首禁则标点并到前面。 */
private fun tokenize(line: Line): List<MbToken> {
    val timeline = GraphemeTimings.buildLineGraphemeTimeline(line)
    val tokens = ArrayList<MbToken>()
    var current: MbToken? = null
    var offset = 0
    for (entry in timeline) {
        if (entry == null) continue
        val glyph = MbGlyph(entry.char, offset, entry.startTime, entry.endTime)
        offset += entry.char.length
        val isSpace = isMachiSpace(entry.char)
        val isCjk = CJK.containsMatchIn(entry.char)
        if (STICKY_PUNCTUATION.matches(entry.char) && tokens.isNotEmpty() && current == null) {
            tokens.last().glyphs += glyph
            continue
        }
        if (isCjk) {
            current?.let { tokens += it }
            current = null
            tokens += MbToken(mutableListOf(glyph))
            continue
        }
        var cur = current
        if (cur == null) { cur = MbToken(); current = cur }
        cur.glyphs += glyph
        if (isSpace) {
            tokens += cur
            current = null
        }
    }
    current?.let { tokens += it }
    return tokens.filter { it.glyphs.isNotEmpty() }
}

private fun tokenText(t: MbToken) = t.glyphs.joinToString("") { it.text }

/** 去掉行尾空格后的宽度（行尾空格不占排版）。 */
private fun rowWidth(tokens: List<MbToken>, measure: MachiMeasure) = measure(tokens.joinToString("") { tokenText(it) }.trimEnd())

/** 把单位分成 rows 行，尽量等宽（贪心：每行装到接近总宽 / 行数）。 */
private fun splitRows(tokens: List<MbToken>, rows: Int, measure: MachiMeasure): List<List<MbToken>> {
    if (rows <= 1 || tokens.size <= 1) return listOf(tokens)
    val total = rowWidth(tokens, measure)
    val target = total / rows
    val result = ArrayList<List<MbToken>>()
    var current: List<MbToken> = emptyList()
    tokens.forEachIndexed { index, token ->
        val remainingRows = rows - result.size
        val remainingTokens = tokens.size - index
        val next = current + token
        if (current.isNotEmpty() && remainingRows > 1 && rowWidth(next, measure) > target * 1.04 && remainingTokens >= remainingRows - 1) {
            val without = rowWidth(current, measure)
            val withIt = rowWidth(next, measure)
            if (abs(withIt - target) < abs(without - target) && remainingTokens > remainingRows - 1) {
                result += next
                current = emptyList()
                return@forEachIndexed
            }
            result += current
            current = listOf(token)
            return@forEachIndexed
        }
        current = next
    }
    if (current.isNotEmpty()) result += current
    return result
}

/** 一行歌词在字面上的排版：1–3 行里挑字最大的（行数多扣分）。 */
internal fun layoutMachiBoardLine(line: Line, box: MachiBoardBox, measure: MachiMeasure): MachiBoardLayout {
    val tokens = tokenize(line)
    val usableWidth = box.width * (1 - box.paddingX * 2)
    var bestRows: List<List<MbToken>>? = null
    var bestFont = 0.0
    var bestScore = 0.0
    for (rows in 1..MAX_ROWS) {
        if (rows > tokens.size) break
        val split = splitRows(tokens, rows, measure)
        val widest = max(1.0, split.maxOf { rowWidth(it, measure) })
        val byWidth = (usableWidth / widest) * box.referencePx
        val byHeight = if (rows == 1) box.height * SINGLE_ROW_FILL else box.height * 0.86 / (split.size * LINE_GAP)
        val fontPx = min(byWidth, byHeight)
        val score = fontPx * ROW_PENALTY[split.size - 1]
        if (bestRows == null || score > bestScore) { bestRows = split; bestFont = fontPx; bestScore = score }
    }
    if (bestRows == null) return MachiBoardLayout(box.height * 0.4, emptyList(), box.width, box.height)
    return placeRows(bestRows, bestFont, box, measure)
}

/** 按字号把各行居中摆好，量出每个字的 x 区间。 */
private fun placeRows(rows: List<List<MbToken>>, fontPx: Double, box: MachiBoardBox, measure: MachiMeasure): MachiBoardLayout {
    val scale = fontPx / box.referencePx
    val lineHeight = fontPx * LINE_GAP
    val blockHeight = lineHeight * rows.size
    val firstCenter = box.height / 2 - blockHeight / 2 + lineHeight / 2
    return MachiBoardLayout(fontPx, rows.mapIndexed { rowIndex, tokens ->
        val glyphs = tokens.flatMap { it.glyphs }
        val text = glyphs.joinToString("") { it.text }
        val width = measure(text.trimEnd()) * scale
        val left = (box.width - width) / 2
        val prefix = StringBuilder()
        val placed = glyphs.map { g ->
            val x0 = left + measure(prefix.toString()) * scale
            prefix.append(g.text)
            val x1 = left + measure(prefix.toString()) * scale
            MachiBoardGlyph(g.text, g.offset, g.text.length, x0, x1, g.startTime, g.endTime)
        }
        val centerY = firstCenter + rowIndex * lineHeight
        MachiBoardRow(placed, fontPx, centerY, centerY - lineHeight / 2, centerY + lineHeight / 2)
    }, box.width, box.height)
}

/** 曲名卡（间奏、纯音乐、没开显示歌词时）：主标题一行（放不下缩字），副标题小一号。 */
internal fun layoutMachiBoardCard(title: String, subtitle: String, box: MachiBoardBox, measure: MachiMeasure): MachiBoardLayout {
    val usableWidth = box.width * (1 - box.paddingX * 2)
    val hasSub = subtitle.isNotEmpty()
    val titleFont = min(box.height * (if (hasSub) 0.4 else 0.5), (usableWidth / max(1.0, measure(title))) * box.referencePx)
    val subtitleFont = if (hasSub) min(titleFont * 0.52, (usableWidth / max(1.0, measure(subtitle))) * box.referencePx) else 0.0
    val rows = ArrayList<MachiBoardRow>()
    val totalHeight = titleFont * 1.15 + (if (hasSub) subtitleFont * 1.4 else 0.0)
    var y = box.height / 2 - totalHeight / 2
    fun row(text: String, fontPx: Double) {
        val scale = fontPx / box.referencePx
        val width = measure(text) * scale
        val left = (box.width - width) / 2
        val height = fontPx * (if (rows.isEmpty()) 1.15 else 1.4)
        val centerY = y + height / 2
        rows += MachiBoardRow(
            listOf(MachiBoardGlyph(text, 0, text.length, left, left + width, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY)),
            fontPx, centerY, y, y + height,
        )
        y += height
    }
    row(title, titleFont)
    if (hasSub) row(subtitle, subtitleFont)
    return MachiBoardLayout(titleFont, rows, box.width, box.height)
}

/**
 * 时刻 time 每一行亮到的 x（像素）。字在 [startTime, endTime] 里从左往右扫亮，唱完的字全亮；
 * 一行里按字的顺序走，遇到还没开始的字就停。结果对时间单调不减。
 */
internal fun resolveMachiBoardReveal(layout: MachiBoardLayout, time: Double): DoubleArray = DoubleArray(layout.rows.size) { r ->
    val row = layout.rows[r]
    var lit = row.glyphs.firstOrNull()?.x0 ?: 0.0
    for (g in row.glyphs) {
        if (time >= g.endTime) { lit = g.x1; continue }
        if (time > g.startTime) {
            val span = max(1e-3, g.endTime - g.startTime)
            lit = g.x0 + (g.x1 - g.x0) * min(1.0, (time - g.startTime) / span)
        }
        break
    }
    lit
}
