package com.guangyue.feature.player.lyrics.visualizer

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/*
 * R7 花火⑥a：逐函数对译 Folia hanabi/text/layout.ts（排版纯函数）。
 * 把逐字（字宽、所属词、词的字号倍率）按 9 种排法排进文字区，原点 = 文字区中心；
 * 超出文字区时先折行 / 换列，再整体缩小；竖排时拉丁字母侧转、中日文直立。
 * 随机量全部取自 createRng("<seed>:layout" / "<seed>:word-scale")，消费顺序与 TS 一致；金标 hanabi_layout_golden.txt。
 */

internal class HbLayoutGlyph(val char: String, val advance: Double, val blank: Boolean, val upright: Boolean, val word: Int)

internal class HbLayoutWord(val start: Int, val end: Int, val blank: Boolean, val space: Boolean, val scale: Double)

internal class HbWordText(val text: String, val blank: Boolean, val space: Boolean)

internal class HbLayoutParams(
    val layout: String,
    val regionWidth: Double,
    val regionHeight: Double,
    val fontPx: Double,
    val tilt: Double,
    val seed: String,
    val tracking: Double,
    val wordGap: Double,
    val lineHeight: Double,
    val minAdvance: Double,
    val stairStep: Double,
    val padding: Double,
)

internal class HbPlacedGlyph(
    val char: String, val x: Double, val y: Double, val size: Double, val advance: Double,
    val angle: Double, val rotation: Double, val boxW: Double, val boxH: Double,
    val row: Int, val word: Int, val blank: Boolean,
)

internal class HbBounds(val x: Double, val y: Double, val width: Double, val height: Double)

internal class HbTextPlacement(
    val glyphs: List<HbPlacedGlyph>,
    val rows: List<List<Int>>,
    val fontPx: Double,
    val fit: Double,
    val bounds: HbBounds,
    val vertical: Boolean,
)

private fun cps(s: String): List<String> {
    val out = ArrayList<String>()
    var i = 0
    while (i < s.length) {
        val n = Character.charCount(s.codePointAt(i))
        out += s.substring(i, i + n)
        i += n
    }
    return out
}

private val HB_FUNCTION_WORDS: Set<String> = cps("的了在把是我你他她它们和与也就都着过吗呢吧啊呀哦被让给从向到这那之而又很のはがをにでともへやねよかな").toSet()
private val HB_MINOR_LATIN = setOf("a", "an", "the", "of", "to", "in", "on", "at", "me", "my", "with", "and", "or", "is", "i", "you", "it", "by", "for")

/** 竖排时即便属于中日文区也要侧转的符号（长音、破折号、省略号、波浪线）。 */
private val HB_ROTATE_IN_VERTICAL: Set<String> = cps("ー—―─～〜…‥－-_=~").toSet()

private fun isKanaOrPunctStart(c: String): Boolean {
    if (c.isEmpty()) return false
    val ch = c[0]
    return ch in '\u3040'..'\u309F' || ch in '\u30A0'..'\u30FF' || ch == 'ー' || ch in "、。，．！？"
}

private fun isKanaOnly(text: String): Boolean =
    text.length == 1 && (text[0] in '\u3040'..'\u309F' || text[0] in '\u30A0'..'\u30FF' || text[0] == 'ー')

/**
 * wordScales：重点词（最长的实词，一样长取靠后的）放大到 1 + 0.8 × emphasis，虚词缩小，标点小一号，其余按种子小幅浮动。
 */
internal fun hanabiWordScales(words: List<HbWordText>, emphasis: Double, seed: String): List<Double> {
    val rng = lumiereRng("$seed:word-scale")
    val e = max(0.0, min(1.0, emphasis))
    fun lengthOf(t: String) = t.codePointCount(0, t.length)
    fun minor(w: HbWordText) = w.blank ||
        (lengthOf(w.text) == 1 && w.text in HB_FUNCTION_WORDS) ||
        w.text.lowercase() in HB_MINOR_LATIN
    var key = -1
    words.forEachIndexed { index, w ->
        if (minor(w)) return@forEachIndexed
        if (key < 0 || lengthOf(w.text) >= lengthOf(words[key].text)) key = index
    }
    val solid = words.count { !it.blank }
    return words.mapIndexed { index, w ->
        val jitter = rng.next()
        when {
            w.space -> 1.0
            w.blank -> 1 - 0.2 * e
            solid <= 1 -> 1.0
            index == key -> 1 + 0.8 * e
            minor(w) -> max(0.6, 1 - 0.32 * e)
            else -> 1 + (jitter - 0.5) * 0.45 * e
        }
    }
}

internal class HbRunUnit(val length: Double, val space: Boolean)

/** partitionRuns：把一串长度按顺序分成 k 段，使最长的一段最短（动态规划）。 */
internal fun hanabiPartitionRuns(units: List<HbRunUnit>, gaps: List<Double>, k: Int, breakPenalty: List<Double>? = null): List<List<Int>> {
    val n = units.size
    if (n == 0) return emptyList()
    val solidCount = units.count { !it.space }
    val parts = max(1, min(k, if (solidCount == 0) 1 else solidCount))
    fun spanLength(from: Int, to: Int): Double {
        var a = from
        var b = to
        while (a <= b && units[a].space) a += 1
        while (b >= a && units[b].space) b -= 1
        var total = 0.0
        for (i in a..b) total += units[i].length + (if (i > a) gaps[i] else 0.0)
        return total
    }
    val inf = Double.POSITIVE_INFINITY
    val best = Array(parts + 1) { DoubleArray(n + 1) { inf } }
    val cut = Array(parts + 1) { IntArray(n + 1) }
    best[0][0] = 0.0
    for (p in 1..parts) {
        for (j in 1..n) {
            for (i in (p - 1) until j) {
                if (best[p - 1][i] == inf) continue
                var solid = false
                for (u in i until j) if (!units[u].space) solid = true
                if (!solid) continue
                val value = max(best[p - 1][i], spanLength(i, j - 1) + (if (i > 0) (breakPenalty?.getOrNull(i) ?: 0.0) else 0.0))
                if (value < best[p][j] - 1e-9) {
                    best[p][j] = value
                    cut[p][j] = i
                }
            }
        }
    }
    var p = parts
    while (p > 1 && best[p][n] == inf) p -= 1
    val runs = ArrayList<List<Int>>()
    var j = n
    for (q in p downTo 1) {
        val i = cut[q][j]
        runs.add(0, List(j - i) { i + it })
        j = i
    }
    return runs
}

private class HbWork(var x: Double, var y: Double, var size: Double, var advance: Double, var angle: Double, var rotation: Double, var boxW: Double, var boxH: Double, var row: Int)

private class HbUnit(val glyphs: List<Int>, var space: Boolean, val length: Double, val thickness: Double)

private fun hbRotate(x: Double, y: Double, angle: Double): Pair<Double, Double> {
    val c = cos(angle)
    val s = sin(angle)
    return (x * c - y * s) to (x * s + y * c)
}

/** 把词切成不可拆分的块：标点粘在前一个词上（行首不放标点）。 */
private fun hbBuildUnits(glyphs: List<HbLayoutGlyph>, words: List<HbLayoutWord>): List<Pair<List<Int>, Boolean>> {
    class U(val words: MutableList<Int>, var space: Boolean)
    val units = ArrayList<U>()
    words.forEachIndexed { index, word ->
        if (word.end <= word.start) return@forEachIndexed
        val last = units.lastOrNull()
        if (word.blank && !word.space && last != null && !last.space) {
            last.words.add(index)
            return@forEachIndexed
        }
        units.add(U(mutableListOf(index), word.space))
    }
    if (units.size > 1 && !units[0].space && units[0].words.all { words[it].blank }) {
        val first = units.removeAt(0)
        units[0].words.addAll(0, first.words)
        units[0].space = false
    }
    return units.map { unit ->
        val indices = ArrayList<Int>()
        unit.words.forEach { index ->
            var g = words[index].start
            while (g < words[index].end) {
                if (g < glyphs.size) indices.add(g)
                g += 1
            }
        }
        indices to unit.space
    }
}

internal fun hanabiLayoutText(glyphInputs: List<HbLayoutGlyph>, words: List<HbLayoutWord>, params: HbLayoutParams): HbTextPlacement {
    val layout = params.layout
    val W = params.regionWidth
    val H = params.regionHeight
    val fontPx = params.fontPx
    val rng = lumiereRng("${params.seed}:layout")
    val vertical = layout == "vertical" || layout == "vertical-split"
    val count = glyphInputs.size
    val sizes = glyphInputs.map { fontPx * (words.getOrNull(it.word)?.scale ?: 1.0) }
    val turned = glyphInputs.map { vertical && (!it.upright || it.char in HB_ROTATE_IN_VERTICAL) }
    val along = glyphInputs.mapIndexed { index, glyph ->
        val size = sizes[index]
        when {
            glyph.blank -> glyph.advance * size
            vertical && !turned[index] -> size * (1 + params.tracking * 0.6)
            else -> (max(glyph.advance, if (vertical) 0.0 else params.minAdvance) + params.tracking) * size
        }
    }
    val work = glyphInputs.indices.map { HbWork(0.0, 0.0, sizes[it], along[it], 0.0, 0.0, 0.0, 0.0, 0) }
    val units = hbBuildUnits(glyphInputs, words).map { (gl, space) ->
        val thick = gl.fold(0.0) { m, i -> if (glyphInputs[i].blank) m else max(m, sizes[i]) }
        HbUnit(gl, space, gl.sumOf { along[it] }, if (thick == 0.0) fontPx else thick)
    }
    val gaps = units.mapIndexed { index, unit -> if (index > 0 && !unit.space && !units[index - 1].space) params.wordGap * fontPx else 0.0 }
    val penalties = units.map { unit ->
        val first = glyphInputs.getOrNull(unit.glyphs.getOrNull(0) ?: 0)?.char ?: ""
        if (isKanaOrPunctStart(first)) fontPx * 1.2 else 0.0
    }
    val rows = sortedMapOf<Int, List<Int>>()

    fun thickOf(unitIndices: List<Int>): Double {
        val t = unitIndices.fold(0.0) { m, u -> max(m, if (units[u].space) 0.0 else units[u].thickness) }
        return if (t == 0.0) fontPx else t
    }

    fun flow(unitIndices: List<Int>, rowIndex: Int, place: (Int, Double, Double) -> Unit) {
        var a = 0
        var b = unitIndices.size - 1
        while (a <= b && units[unitIndices[a]].space) a += 1
        while (b >= a && units[unitIndices[b]].space) b -= 1
        val thick = thickOf(unitIndices)
        var s = 0.0
        val members = ArrayList<Int>()
        for (k in unitIndices.indices) {
            val unit = units[unitIndices[k]]
            val outside = k < a || k > b
            if (k > a && k <= b) s += gaps[unitIndices[k]]
            var wordStart = -1
            var stepIndex = 0
            unit.glyphs.forEach { glyph ->
                val size = sizes[glyph]
                val advance = if (outside) 0.0 else along[glyph]
                val word = glyphInputs[glyph].word
                if (word != wordStart) {
                    wordStart = word
                    stepIndex = 0
                }
                val settle = if (vertical) 0.0 else (thick - size) * 0.42
                val stair = if (glyphInputs[glyph].blank) 0.0 else -params.stairStep * size * (stepIndex % 3)
                if (!glyphInputs[glyph].blank) stepIndex += 1
                work[glyph].row = rowIndex
                work[glyph].advance = advance
                place(glyph, s + advance / 2, settle + stair)
                s += advance
                members.add(glyph)
            }
        }
        rows[rowIndex] = members
    }

    fun runLength(unitIndices: List<Int>): Double {
        var a = 0
        var b = unitIndices.size - 1
        while (a <= b && units[unitIndices[a]].space) a += 1
        while (b >= a && units[unitIndices[b]].space) b -= 1
        var total = 0.0
        for (k in a..b) total += units[unitIndices[k]].length + (if (k > a) gaps[unitIndices[k]] else 0.0)
        return total
    }

    val runUnits = units.map { HbRunUnit(it.length, it.space) }

    fun chooseRuns(kMin: Int, kMax: Int, main: Double, cross: Double, pitch: Double): List<List<Int>> {
        var bestRuns: List<List<Int>> = emptyList()
        var bestFit = -1.0
        for (k in kMin..kMax) {
            val runs = hanabiPartitionRuns(runUnits, gaps, k, penalties)
            if (runs.size < min(k, kMin)) continue
            val longest = max(runs.maxOfOrNull { runLength(it) } ?: Double.NEGATIVE_INFINITY, 1.0)
            val across = runs.sumOf { thickOf(it) * pitch }
            val fit = min(1.0, min(main / longest, cross / across))
            if (fit > bestFit * 1.08 || bestRuns.isEmpty()) {
                bestFit = fit
                bestRuns = runs
            }
            if (fit >= 1) break
        }
        return bestRuns
    }

    val solidUnits = units.count { !it.space }

    fun chunkUnits(): List<List<Int>> {
        val chunks = ArrayList<MutableList<Int>>()
        units.forEachIndexed { index, unit ->
            if (unit.space) return@forEachIndexed
            val last = chunks.lastOrNull()
            val text = unit.glyphs.joinToString("") { glyphInputs[it].char }
            val kana = unit.glyphs.size == 1 && isKanaOnly(text)
            val lastLength = last?.sumOf { units[it].glyphs.size } ?: 0
            if (last != null && kana && lastLength < 5) last.add(index) else chunks.add(mutableListOf(index))
        }
        return chunks
    }

    fun placeStraight(glyph: Int, x: Double, y: Double, rowIndex: Int) {
        val w = work[glyph]
        w.x = x
        w.y = y
        w.row = rowIndex
    }

    when (layout) {
        "center", "left", "right", "stagger", "diagonal" -> {
            var kMin = 1
            var kMax = if (layout == "diagonal") 2 else 3
            if (layout == "stagger") {
                kMin = min(solidUnits, if (solidUnits >= 5) 3 else 2)
                kMax = min(solidUnits, 3)
                kMin = max(1, kMin)
                kMax = max(kMin, kMax)
            }
            val slope = if (layout == "diagonal") -min(0.42, max(0.2, atan2(H * 0.7, W))) else 0.0
            val runs = chooseRuns(kMin, kMax, if (layout == "diagonal") W * 0.95 else W, H, params.lineHeight)
            val pitchFactor = if (layout == "stagger") params.lineHeight * 0.94 else params.lineHeight
            var cursorY = 0.0
            val lengths = runs.map { runLength(it) }
            val longest = max(lengths.maxOrNull() ?: Double.NEGATIVE_INFINITY, 1.0)
            val staggerAmp = fontPx * (0.9 + rng.next() * 0.6)
            runs.forEachIndexed { rowIndex, run ->
                val thick = thickOf(run)
                if (rowIndex > 0) cursorY += thick * pitchFactor * 0.5
                val L = lengths[rowIndex]
                val x0 = when (layout) {
                    "left" -> 0.0
                    "right" -> -L
                    "stagger" -> {
                        val room = max(0.0, (longest - L) / 2)
                        val side = if (rowIndex % 2 == 0) -1 else 1
                        -L / 2 + side * max(staggerAmp, room * 0.9) * (if (runs.size == 1) 0 else 1)
                    }
                    else -> -L / 2
                }
                val rowY = cursorY
                flow(run, rowIndex) { glyph, s, n ->
                    if (layout == "diagonal") {
                        val x = x0 + s
                        placeStraight(glyph, x, rowY + n + (x - (x0 + L / 2)) * tan(slope), rowIndex)
                        val w = work[glyph]
                        w.advance /= cos(slope)
                        w.angle = slope
                        w.rotation = slope * 0.3
                    } else {
                        placeStraight(glyph, x0 + s, rowY + n, rowIndex)
                    }
                }
                cursorY += thick * pitchFactor * 0.5
            }
        }
        "arc" -> {
            val runs = chooseRuns(1, 2, W * 0.95, H, params.lineHeight)
            val lengths = runs.map { runLength(it) }
            val longest = max(lengths.maxOrNull() ?: Double.NEGATIVE_INFINITY, 1.0)
            val radius0 = max(longest / 1.3, W * 0.62)
            var depth = 0.0
            runs.forEachIndexed { rowIndex, run ->
                val thick = thickOf(run)
                if (rowIndex > 0) depth += thick * params.lineHeight * 0.5
                val R = max(radius0 - depth, longest / 2.6)
                val L = lengths[rowIndex]
                val centerY = radius0
                flow(run, rowIndex) { glyph, s, n ->
                    val a = (s - L / 2) / R
                    val r = R - n
                    val w = work[glyph]
                    w.x = r * sin(a)
                    w.y = centerY - r * cos(a)
                    w.angle = a
                    w.rotation = a
                }
                depth += thick * params.lineHeight * 0.5
            }
        }
        "vertical" -> {
            val runs = chooseRuns(1, 4, H, W, params.lineHeight)
            var cursorX = 0.0
            runs.forEachIndexed { colIndex, run ->
                val thick = thickOf(run)
                if (colIndex > 0) cursorX -= thick * params.lineHeight * 0.5
                val colX = cursorX
                flow(run, colIndex) { glyph, s, n ->
                    val w = work[glyph]
                    w.x = colX - n
                    w.y = s
                    w.angle = PI / 2
                    w.rotation = if (turned[glyph]) PI / 2 else 0.0
                }
                cursorX -= thick * params.lineHeight * 0.5
            }
        }
        "vertical-split" -> {
            val chunks = chunkUnits()
            val lengths = chunks.map { runLength(it) }
            fun estimate(groups: Int): Double {
                val per = ceil(chunks.size.toDouble() / groups).toInt()
                var m = 1.0
                for (g in 0 until groups) {
                    val from = min(g * per, lengths.size)
                    val to = min((g + 1) * per, lengths.size)
                    m = max(m, lengths.subList(from, to).fold(0.0) { sum, l -> sum + l * 0.6 })
                }
                return m
            }
            var groups = 1
            while (groups < 3 && estimate(groups) > H && chunks.size > groups * 2) groups += 1
            val per = ceil(chunks.size.toDouble() / groups).toInt().let { if (it == 0) 1 else it }
            var cursorX = 0.0
            var runIndex = 0
            for (g in 0 until groups) {
                val from = min(g * per, chunks.size)
                val to = min((g + 1) * per, chunks.size)
                val members = chunks.subList(from, to)
                val memberLengths = lengths.subList(from, to)
                if (members.isEmpty()) continue
                val thick = members.maxOf { thickOf(it) }
                val pitch = thick * params.lineHeight
                if (g > 0) cursorX -= pitch * 1.1
                val tops = ArrayList<Double>()
                val ends = ArrayList<Double>()
                members.forEachIndexed { k, chunk ->
                    val side = if (k % 2 == 0) 0.5 else -0.5
                    var top = 0.0
                    if (k >= 1) top = max(top, tops[k - 1] + memberLengths[k - 1] * 0.5)
                    if (k >= 2) top = max(top, ends[k - 2] + fontPx * 0.2)
                    tops.add(top)
                    ends.add(top + memberLengths[k])
                    val colX = cursorX + side * pitch * (if (members.size > 1) 1 else 0) - pitch * 0.5
                    val topF = top
                    flow(chunk, runIndex) { glyph, s, n ->
                        val w = work[glyph]
                        w.x = colX - n
                        w.y = topF + s
                        w.angle = PI / 2
                        w.rotation = if (turned[glyph]) PI / 2 else 0.0
                    }
                    runIndex += 1
                }
                cursorX -= pitch
            }
        }
        "scatter" -> {
            val chunks = chunkUnits()
            val pad = params.padding * fontPx
            val extra = chunks.map { 0.8 + rng.next() * 0.5 }
            val rotations = chunks.map { (rng.next() - 0.5) * 0.16 }
            val gapsAfter = chunks.map { fontPx * (0.35 + rng.next() * 0.8) }
            val jitterRand = chunks.map { rng.next() * 2 - 1 }
            class Dim(val w: Double, val h: Double, val aabbW: Double, val aabbH: Double)
            val dims = chunks.mapIndexed { k, chunk ->
                val w = runLength(chunk) * extra[k] + pad * 2
                val h = thickOf(chunk) * extra[k] * (1.12 + params.stairStep * 4) + pad * 2
                val c = abs(cos(rotations[k]))
                val s = abs(sin(rotations[k]))
                Dim(w, h, w * c + h * s, w * s + h * c)
            }
            val n = chunks.size
            var bestRows: List<List<Int>> = emptyList()
            var bestScore = Double.NEGATIVE_INFINITY
            for (r in 1..min(4, n)) {
                val rowsOf = hanabiPartitionRuns(dims.map { HbRunUnit(it.aabbW, false) }, dims.indices.map { k -> if (k > 0) gapsAfter[k - 1] else 0.0 }, r)
                val widths = rowsOf.map { row -> row.foldIndexed(0.0) { i, sum, k -> sum + dims[k].aabbW + (if (i > 0) gapsAfter[k - 1] else 0.0) } }
                val heights = rowsOf.map { row -> row.maxOf { dims[it].aabbH } * 1.5 }
                val fit = min(1.0, min(W / widths.max(), H / heights.sum()))
                val score = fit + (if (n >= 3 && rowsOf.size >= 2) 0.08 else 0.0) + (if (rowsOf.size == min(3, n)) 0.04 else 0.0)
                if (score > bestScore) {
                    bestScore = score
                    bestRows = rowsOf
                }
            }
            val rowHeights = bestRows.map { row -> row.maxOf { dims[it].aabbH } }
            val widths = bestRows.map { row -> row.foldIndexed(0.0) { i, sum, k -> sum + dims[k].aabbW + (if (i > 0) gapsAfter[k - 1] else 0.0) } }
            val widest = max(widths.maxOrNull() ?: Double.NEGATIVE_INFINITY, 1.0)
            var cursorY = 0.0
            bestRows.forEachIndexed { r, row ->
                val rowH = rowHeights[r]
                val jitter = rowH * 0.22
                if (r > 0) cursorY += rowHeights[r - 1] / 2 + rowH / 2 + (rowHeights[r - 1] * 0.22 + jitter) + fontPx * 0.1
                val slack = max(0.0, widest - widths[r])
                var cursorX = -widths[r] / 2 + (rng.next() - 0.5) * (slack + fontPx * 1.2) * (if (bestRows.size > 1) 1 else 0)
                row.forEachIndexed { i, k ->
                    if (i > 0) cursorX += gapsAfter[k - 1]
                    val d = dims[k]
                    val cx = cursorX + d.aabbW / 2
                    val cy = cursorY + jitterRand[k] * (jitter + (rowH - d.aabbH) * 0.5)
                    cursorX += d.aabbW
                    val chunk = chunks[k]
                    val L = runLength(chunk) * extra[k]
                    val angle = rotations[k]
                    flow(chunk, k) { glyph, s, nOffset ->
                        val w = work[glyph]
                        val (lx, ly) = hbRotate(s * extra[k] - L / 2, nOffset * extra[k], angle)
                        w.x = cx + lx
                        w.y = cy + ly
                        w.angle = angle
                        w.rotation = angle
                        w.size = sizes[glyph] * extra[k]
                        w.advance *= extra[k]
                    }
                }
            }
        }
    }
    if (layout == "scatter" || layout == "vertical-split") {
        units.forEach { unit ->
            if (!unit.space) return@forEach
            unit.glyphs.forEach { glyph ->
                val prev = work[max(0, glyph - 1)]
                val w = work[glyph]
                w.x = prev.x
                w.y = prev.y
                w.angle = prev.angle
                w.rotation = prev.rotation
                w.advance = 0.0
                w.row = prev.row
            }
        }
    }

    work.forEachIndexed { index, w ->
        val verticalUpright = vertical && !turned[index]
        w.boxW = if (verticalUpright) w.size * 1.12 else max(w.advance, 0.01)
        w.boxH = if (verticalUpright) max(w.advance, 0.01) else w.size * 1.12
        if (layout == "diagonal") w.boxW = max(w.advance * cos(w.angle), 0.01)
    }

    if (params.tilt != 0.0) {
        work.forEach { w ->
            val (px, py) = hbRotate(w.x, w.y, params.tilt)
            w.x = px
            w.y = py
            w.angle += params.tilt
            w.rotation += params.tilt
        }
    }

    fun measure(): DoubleArray {
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        work.forEachIndexed { index, w ->
            if (glyphInputs[index].blank) return@forEachIndexed
            val pad = params.padding * w.size
            val hw = w.boxW / 2 + pad
            val hh = w.boxH / 2 + pad
            val c = abs(cos(w.rotation))
            val s = abs(sin(w.rotation))
            val ex = hw * c + hh * s
            val ey = hw * s + hh * c
            minX = min(minX, w.x - ex)
            maxX = max(maxX, w.x + ex)
            minY = min(minY, w.y - ey)
            maxY = max(maxY, w.y + ey)
        }
        if (!minX.isFinite()) return doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        return doubleArrayOf(minX, minY, maxX, maxY)
    }

    var box = measure()
    val fit = min(1.0, min(W / max(1e-6, box[2] - box[0]), H / max(1e-6, box[3] - box[1])))
    if (fit < 1) {
        work.forEach { w ->
            w.x *= fit
            w.y *= fit
            w.size *= fit
            w.advance *= fit
            w.boxW *= fit
            w.boxH *= fit
        }
        box = measure()
    }
    val shiftX = when (layout) {
        "left" -> -W / 2 - box[0]
        "right" -> W / 2 - box[2]
        else -> -(box[0] + box[2]) / 2
    }
    val shiftY = -(box[1] + box[3]) / 2
    work.forEach { w ->
        w.x += shiftX
        w.y += shiftY
    }
    val placed = work.mapIndexed { index, w ->
        HbPlacedGlyph(
            glyphInputs[index].char, w.x, w.y, w.size, w.advance, w.angle, w.rotation, w.boxW, w.boxH,
            w.row, glyphInputs[index].word, glyphInputs[index].blank,
        )
    }
    return HbTextPlacement(
        glyphs = placed,
        rows = rows.values.map { run -> run.filter { it < count } },
        fontPx = fontPx * fit,
        fit = fit,
        bounds = HbBounds(box[0] + shiftX, box[1] + shiftY, box[2] - box[0], box[3] - box[1]),
        vertical = vertical,
    )
}
