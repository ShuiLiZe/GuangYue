// 凝彩拼贴排版：逐函数对译 Folia temperaLayout.ts + temperaMeasure.ts。
// 分词只定字号层级（每行一个 hero 词 1.34~1.6×、其余 0.7~0.86×），词间只有原文真有空白才给空格宽，CJK 词界留 0.035em；
// 行按 seed 定的宽度预算装箱，放不下就整体缩字号（最多 4 轮）；每字算好入场向量、入场样式、落位 / 释放时刻。
package com.guangyue.feature.player.lyrics.visualizer.tempera

import com.guangyue.feature.player.lyrics.visualizer.SonnetSegment
import com.guangyue.feature.player.lyrics.visualizer.temperaShotProfile
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** 量字：返回 text 在 fontSize（与排版同一单位）下的宽度。 */
internal fun interface TpMeasure {
    fun width(text: String, fontSize: Double): Double
}

internal class TpGlyphPlacement(
    val char: String,
    val lineIndex: Int,
    val segmentIndex: Int,
    val x: Double,
    val y: Double,
    val rotation: Double,
    val startTime: Double,
    val endTime: Double,
    var settleTime: Double,
    val fontSize: Double,
    val enterX: Double,
    val enterY: Double,
    val enterRotation: Double,
    val enterScale: Double,
    val enterStyle: TpEnterStyle,
    var releaseTime: Double = 0.0,
    var trackingX: Double = 0.0,
    var trackingY: Double = 0.0,
) {
    fun motion() = TpGlyphMotion(startTime, settleTime, endTime, enterX, enterY, enterRotation, enterScale, rotation, enterStyle, releaseTime, trackingX, trackingY)
}

private class TpWordGlyph(val char: String, val startTime: Double, val endTime: Double, val offset: Double, val width: Double)
private class TpWordUnit(val lineIndex: Int, val segmentIndex: Int, val startOffset: Int, val endOffset: Int, var leadingGap: Double, val scale: Double, val width: Double, val glyphs: List<TpWordGlyph>)
private class TpRow(val words: List<TpWordUnit>, val width: Double, var height: Double = 0.0)

internal fun isTemperaLayoutSegment(s: SonnetSegment) = s.text.trim().isNotEmpty()

private fun measureText(m: TpMeasure, text: String, fontSize: Double): Double {
    val measured = try { m.width(text, fontSize) } catch (e: Exception) { text.length * fontSize * 0.6 }
    return max(fontSize * 0.08, measured)
}

private fun measureGrapheme(m: TpMeasure, char: String, fontSize: Double) =
    if (char.trim().isEmpty()) fontSize * 0.3 else measureText(m, char, fontSize)

private fun buildWordUnit(m: TpMeasure, segment: SonnetSegment, lineIndex: Int, segmentIndex: Int, fontSize: Double, scale: Double): TpWordUnit? {
    val glyphs = segment.graphemes.filter { it.char.isNotEmpty() }
    if (glyphs.isEmpty()) return null
    val scaled = fontSize * scale
    val raw = glyphs.map { measureGrapheme(m, it.char, scaled) }
    val rawTotal = raw.sum()
    val shaped = measureText(m, segment.text.replace(Regex("\\s+$"), ""), scaled)
    val correction = if (rawTotal > 0) shaped / rawTotal else 1.0
    var offset = 0.0
    val placed = glyphs.mapIndexed { i, g ->
        val w = raw[i] * correction
        TpWordGlyph(g.char, g.startTime, g.endTime, offset, w).also { offset += w }
    }
    return TpWordUnit(lineIndex, segmentIndex, segment.startOffset, segment.endOffset, 0.0, scale, offset, placed)
}

private fun resolveWordScales(segments: List<SonnetSegment>, seed: Long, lineIndex: Int): List<Double> {
    val lengths = segments.map { if (it.isWordLike) it.graphemes.size else 0 }
    val maxLength = (lengths + 0).max()
    val heroCandidates = lengths.withIndex().filter { it.value > 0 && it.value >= maxLength - 1 }.map { it.index }
    val heroIndex = if (heroCandidates.isNotEmpty())
        heroCandidates[floor(th01(seed, lineIndex, 101) * heroCandidates.size).toInt() % heroCandidates.size] else -1
    return segments.indices.map { i ->
        if (i == heroIndex) 1.34 + th01(seed, lineIndex * 31 + i, 103) * 0.26 else 0.7 + th01(seed, lineIndex * 31 + i, 107) * 0.16
    }
}

private fun packRows(words: List<TpWordUnit>, maxWidth: Double, seed: Long): List<TpRow> {
    val rows = ArrayList<TpRow>()
    var current = ArrayList<TpWordUnit>()
    var currentWidth = 0.0
    fun budgetFor(rowIndex: Int) = maxWidth * (0.72 + th01(seed, rowIndex, 109) * 0.28)
    fun flush() {
        if (current.isEmpty()) return
        rows += TpRow(current, currentWidth)
        current = ArrayList(); currentWidth = 0.0
    }
    words.forEach { word ->
        val newLine = current.isNotEmpty() && word.lineIndex != current[0].lineIndex
        val advance = if (current.isEmpty()) word.width else word.width + word.leadingGap
        if (newLine || (current.isNotEmpty() && currentWidth + advance > budgetFor(rows.size))) flush()
        currentWidth += if (current.isEmpty()) word.width else advance
        current += word
    }
    flush()
    return rows
}

private fun buildWordUnits(m: TpMeasure, lines: List<List<SonnetSegment>>, fontSize: Double, seed: Long): List<TpWordUnit> =
    lines.flatMapIndexed { lineIndex, segments ->
        val scales = resolveWordScales(segments, seed, lineIndex)
        var previousEnd: Int? = null
        segments.mapIndexedNotNull { segmentIndex, segment ->
            val unit = buildWordUnit(m, segment, lineIndex, segmentIndex, fontSize, scales[segmentIndex]) ?: return@mapIndexedNotNull null
            val spaced = previousEnd != null && segment.startOffset > previousEnd!!
            unit.leadingGap = if (previousEnd == null) 0.0 else fontSize * (if (spaced) 0.26 else 0.035)
            previousEnd = segment.endOffset
            unit
        }
    }

private fun rotateAbout(x: Double, y: Double, cx: Double, cy: Double, angle: Double): Pair<Double, Double> {
    if (angle == 0.0) return x to y
    val c = cos(angle); val s = sin(angle)
    val dx = x - cx; val dy = y - cy
    return (cx + dx * c - dy * s) to (cy + dx * s + dy * c)
}

private const val MIN_SETTLE_SECONDS = 0.34

private fun applyGlyphTiming(placements: List<TpGlyphPlacement>, settleStretch: Double): List<TpGlyphPlacement> {
    if (placements.isEmpty()) return placements
    val stretch = if (settleStretch.isFinite()) min(1.0, max(0.0, settleStretch)) else 0.5
    val lineSpans = HashMap<Int, Double>(); val lineStarts = HashMap<Int, Double>()
    placements.forEach { p ->
        lineSpans[p.lineIndex] = max(lineSpans[p.lineIndex] ?: 0.0, p.endTime)
        lineStarts[p.lineIndex] = min(lineStarts[p.lineIndex] ?: Double.POSITIVE_INFINITY, p.startTime)
    }
    val shotLyricEnd = placements.fold(0.0) { acc, p -> max(acc, p.endTime) }
    placements.forEach { p ->
        val reach = max(0.0, shotLyricEnd - p.startTime - MIN_SETTLE_SECONDS)
        p.settleTime = p.startTime + MIN_SETTLE_SECONDS + reach * stretch
    }
    val cx = placements.sumOf { it.x } / placements.size
    val cy = placements.sumOf { it.y } / placements.size
    placements.forEach { p ->
        val span = max(0.5, (lineSpans[p.lineIndex] ?: p.endTime) - (lineStarts[p.lineIndex] ?: p.startTime))
        p.releaseTime = max(p.endTime, p.settleTime) + span
        p.trackingX = p.x - cx
        p.trackingY = p.y - cy
    }
    return placements
}

internal fun resolveTemperaLayout(
    lines: List<List<SonnetSegment>>,
    shotKind: String,
    width: Double,
    height: Double,
    baseFontSize: Double,
    seed: Long,
    measure: TpMeasure,
    settleStretch: Double = 0.5,
): List<TpGlyphPlacement> {
    val profile = temperaShotProfile(shotKind)
    val r = profile.region
    val centerX = r.cx * width; val centerY = r.cy * height
    val regionW = r.w * width; val regionH = r.h * height
    var fontSize = max(14.0, baseFontSize * r.fontScale)
    var rows: List<TpRow> = emptyList()
    var blockHeight = 0.0
    for (attempt in 0 until 4) {
        rows = packRows(buildWordUnits(measure, lines, fontSize, seed), regionW, seed)
        rows.forEachIndexed { rowIndex, row ->
            val tallest = row.words.maxOf { it.scale }
            row.height = fontSize * tallest * (1.02 + th01(seed, rowIndex, 113) * 0.1)
        }
        blockHeight = rows.sumOf { it.height }
        val maxRowWidth = max(1.0, rows.maxOfOrNull { it.width } ?: 0.0)
        val fit = minOf(1.0, if (blockHeight > regionH) regionH / blockHeight else 1.0, if (maxRowWidth > regionW) regionW / maxRowWidth else 1.0)
        if (fit >= 0.999) break
        fontSize *= fit
    }
    val baseX = profile.enterX * fontSize; val baseY = profile.enterY * fontSize
    val baseAngle = atan2(baseY, baseX)
    val baseMagnitude = hypot(baseX, baseY)
    val placements = ArrayList<TpGlyphPlacement>()
    var cursorY = centerY - blockHeight / 2
    val styles = TpEnterStyle.values()
    rows.forEachIndexed { rowIndex, row ->
        val rowCenterY = cursorY + row.height / 2
        cursorY += row.height
        val drift = (th01(seed, rowIndex, 127) - 0.5) * regionW * 0.06
        val rowRotation = (th01(seed, rowIndex, 131) - 0.5) * 0.06
        val rowLeft = when (r.align) {
            TpAlignLocal.CENTER -> centerX - row.width / 2 + drift
            TpAlignLocal.LEFT -> centerX - regionW / 2 + abs(drift) * 0.6
            TpAlignLocal.RIGHT -> centerX + regionW / 2 - row.width - abs(drift) * 0.6
        }
        val rowCenterX = rowLeft + row.width / 2
        var cursorX = rowLeft
        row.words.forEachIndexed { wordIndex, word ->
            val salt = rowIndex * 37 + wordIndex
            val enterStyle = styles[floor(th01(seed, salt, 193) * styles.size).toInt() % styles.size]
            val wordRotation = (th01(seed, salt, 137) - 0.5) * 0.07
            val wordShiftY = (th01(seed, salt, 139) - 0.5) * fontSize * 0.09
            val wordLeft = cursorX + (if (wordIndex == 0) 0.0 else word.leadingGap)
            cursorX = wordLeft + word.width
            val wordCenterX = wordLeft + word.width / 2
            val wordCenterY = rowCenterY + wordShiftY
            val glyphSize = fontSize * word.scale
            word.glyphs.forEachIndexed { glyphIndex, glyph ->
                if (glyph.char.trim().isEmpty()) return@forEachIndexed
                val localX = wordLeft + glyph.offset + glyph.width / 2
                val (wx, wy) = rotateAbout(localX, wordCenterY, wordCenterX, wordCenterY, wordRotation)
                val (rx, ry) = rotateAbout(wx, wy, rowCenterX, rowCenterY, rowRotation)
                val (fx, fy) = rotateAbout(rx, ry, centerX, centerY, r.rotation)
                val glyphSalt = salt * 53 + glyphIndex
                val spread = (th01(seed, glyphSalt, 149) - 0.5) * 1.7
                val magnitude = baseMagnitude * (0.55 + th01(seed, glyphSalt, 151) * 1.05)
                val angle = baseAngle + spread
                placements += TpGlyphPlacement(
                    char = glyph.char, lineIndex = word.lineIndex, segmentIndex = word.segmentIndex,
                    x = fx, y = fy, rotation = r.rotation + rowRotation + wordRotation,
                    startTime = glyph.startTime, endTime = glyph.endTime, settleTime = glyph.endTime,
                    fontSize = glyphSize,
                    enterX = cos(angle) * magnitude, enterY = sin(angle) * magnitude,
                    enterRotation = (th01(seed, glyphSalt, 157) - 0.5) * 0.7,
                    enterScale = 0.6 + th01(seed, glyphSalt, 163) * 0.3,
                    enterStyle = enterStyle,
                )
            }
        }
    }
    return applyGlyphTiming(placements, settleStretch)
}

private typealias TpAlignLocal = com.guangyue.feature.player.lyrics.visualizer.TpAlign
