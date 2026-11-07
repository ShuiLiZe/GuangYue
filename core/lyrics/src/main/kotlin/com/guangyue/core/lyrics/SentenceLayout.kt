// 对应 Folia 文件：src/utils/lyrics/sentenceLayout.ts（全文，逐级拆句）
//
// 把一句歌词按「标点 → 成对括号引号 → 中文里夹的西文词组 → CJK 后空格 → 特殊符号」逐级拆，
// 不够目标段数再二次拆（就近词边界），多了就合并最短相邻两段。倾诉（tilt）用它断行。
// 手机化：Folia 的二次拆用 Intl.Segmenter('word')；这里用 [WordSegmentation.segmentByRules]
// （CJK 逐字算词、拉丁按词），对纯中文会更贴近正中断开。
package com.guangyue.core.lyrics

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

object SentenceLayout {

    private val CJK = Regex("[\\u4e00-\\u9fa5\\u3040-\\u30ff\\uac00-\\ud7af]")
    private fun hasCjk(t: String) = CJK.containsMatchIn(t)

    fun splitIntoSentences(text: String, targetCount: Int, timeSeed: Double? = null): List<String> {
        if (targetCount in 0..1) return listOf(text)
        val maxLevel = if (targetCount in -5..-1) -targetCount else 5
        var sentences = listOf(text)
        for (level in 1..maxLevel) {
            if (sentences.size >= abs(targetCount) && targetCount > 0) break
            sentences = sentences.flatMap { splitByLevel(it, level) }
        }
        var result = sentences.toMutableList()
        if (targetCount > 0 && result.size < targetCount) result = secondarySplit(result, targetCount, timeSeed)
        if (result.size > 1 && result.size > targetCount && targetCount > 0) result = merge(result, targetCount)
        return result
    }

    private fun splitByLevel(text: String, level: Int): List<String> = when (level) {
        1 -> splitByPunctuation(text)
        2 -> splitByBracketsQuotes(text)
        3 -> splitByWesternWords(text)
        4 -> splitCjkBySpace(text)
        5 -> splitBySpecialChars(text)
        else -> listOf(text)
    }

    private val PUNCT = Regex("[，。；！？、…·.,;!?]+")
    private val LEADING_WS = Regex("^\\s+")

    private fun splitByPunctuation(text: String): List<String> {
        val matches = PUNCT.findAll(text).toList()
        if (matches.isEmpty()) return listOf(text)
        val result = ArrayList<String>()
        var last = 0
        for (m in matches) {
            val ps = m.range.first
            val pe = m.range.last + 1
            val trailing = LEADING_WS.find(text.substring(pe))?.value ?: ""
            val before = text.substring(last, ps)
            val punct = m.value + trailing
            when {
                before.isNotEmpty() -> result.add(before + punct)
                result.isNotEmpty() -> result[result.size - 1] = result.last() + punct
                else -> result.add(punct)
            }
            last = pe + trailing.length
        }
        if (last < text.length) result.add(text.substring(last))
        return result.filter { it.isNotEmpty() }
    }

    private val WESTERN_BLOCK = Regex("[a-zA-Z0-9]+(?:[a-zA-Z0-9'\\-]*[a-zA-Z0-9]+)?(?:\\s+[a-zA-Z0-9]+(?:[a-zA-Z0-9'\\-]*[a-zA-Z0-9]+)?)+[.,;:!?。，；：！？]?\\s*")
    private val WORD = Regex("[a-zA-Z0-9]+")

    private fun splitByWesternWords(text: String): List<String> {
        if (!hasCjk(text)) return listOf(text)
        val blocks = WESTERN_BLOCK.findAll(text).toList()
        if (blocks.none { WORD.findAll(it.value).count() > 1 }) return listOf(text)
        val parts = ArrayList<String>()
        var last = 0
        for (m in blocks) {
            val bs = m.range.first
            val be = m.range.last + 1
            val before = text.substring(last, bs)
            when {
                shouldSkipBoundary(text, before) -> parts.add(before + m.value)
                bs > last -> { parts.add(before); parts.add(m.value) }
                else -> parts.add(m.value)
            }
            last = be
        }
        if (last < text.length) parts.add(text.substring(last))
        return if (parts.size > 1) parts else listOf(text)
    }

    private fun shouldSkipBoundary(full: String, before: String): Boolean {
        if (before.isEmpty()) return false
        val sep = before.last()
        if (sep == '-') return true
        if (sep in ":：/／|｜") return full.count { it == sep } >= 2
        return false
    }

    private val PAIRS = listOf("「" to "」", "『" to "』", "《" to "》", "【" to "】", "｛" to "｝", "［" to "］", "[" to "]", "（" to "）", "(" to ")", "\"" to "\"", "'" to "'")

    private fun splitByBracketsQuotes(text: String): List<String> {
        val r = extractPairs(text)
        return if (r.size > 1) r else listOf(text)
    }

    private fun extractPairs(text: String): List<String> {
        var bestOpen = -1
        var bestEnd = -1
        for ((o, c) in PAIRS) {
            val os = text.indexOf(o)
            if (os < 0) continue
            val cs = text.indexOf(c, os + o.length)
            if (cs < 0) continue
            val ce = cs + c.length
            if (bestOpen < 0 || os < bestOpen || (os == bestOpen && ce > bestEnd)) {
                bestOpen = os; bestEnd = ce
            }
        }
        if (bestOpen < 0) return listOf(text)
        val out = ArrayList<String>()
        val before = text.substring(0, bestOpen)
        val rest = text.substring(bestEnd)
        if (before.isNotEmpty()) out.addAll(extractPairs(before))
        out.add(text.substring(bestOpen, bestEnd))
        if (rest.isNotEmpty()) out.addAll(extractPairs(rest))
        return out.filter { it.isNotEmpty() }
    }

    private fun splitCjkBySpace(text: String): List<String> {
        if (!hasCjk(text)) return listOf(text)
        val segs = ArrayList<String>()
        val cur = StringBuilder()
        var inCjk = false
        for (ch in text) {
            val s = ch.toString()
            val isC = CJK.matches(s)
            val isW = ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9'
            when {
                isC -> { cur.append(ch); inCjk = true }
                isW -> cur.append(ch)
                ch.isWhitespace() -> when {
                    ch == '\u3000' && cur.isNotEmpty() -> { cur.append(ch); segs.add(cur.toString()); cur.clear(); inCjk = false }
                    inCjk && cur.isNotEmpty() -> {
                        if (CJK.matches(cur.last().toString())) {
                            cur.append(ch); segs.add(cur.toString()); cur.clear(); inCjk = false
                        } else cur.append(ch)
                    }
                    cur.isEmpty() && segs.isNotEmpty() -> segs[segs.size - 1] = segs.last() + ch
                    else -> cur.append(ch)
                }
                else -> if (cur.isEmpty() && segs.isNotEmpty()) segs[segs.size - 1] = segs.last() + ch else cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) {
            val c = cur.toString()
            if (segs.isNotEmpty() && c.isBlank()) segs[segs.size - 1] = segs.last() + c else segs.add(c)
        }
        return if (segs.size > 1) segs else listOf(text)
    }

    private val SPECIAL = Regex("[：:/／\\\\|｜~～]+")

    private fun splitBySpecialChars(text: String): List<String> {
        val parts = text.split(SPECIAL)
        if (parts.count { it.isNotEmpty() } <= 1) return listOf(text)
        val result = ArrayList<String>()
        var last = 0
        for (part in parts) {
            val idx = text.indexOf(part, last)
            if (idx > last) {
                val sp = text.substring(last, idx)
                if (result.isNotEmpty()) result[result.size - 1] = result.last() + sp else result.add(sp)
            }
            if (part.isNotEmpty()) {
                result.add(part); last = idx + part.length
            } else if (idx != -1) last = idx
        }
        if (last < text.length) {
            val tail = text.substring(last)
            if (result.isNotEmpty()) result[result.size - 1] = result.last() + tail else result.add(tail)
        }
        return result.filter { it.isNotEmpty() }
    }

    private fun secondarySplit(sentences: MutableList<String>, target: Int, timeSeed: Double?): MutableList<String> {
        while (sentences.size < target) {
            val candidates = sentences.filter { it.length > 2 }
            if (candidates.isEmpty()) break
            data class Cand(val index: Int, val split: Int, val score: Double)
            val semantic = ArrayList<Cand>()
            for (s in candidates) {
                val positions = ArrayList<Int>()
                var off = 0
                for (seg in WordSegmentation.segmentByRules(s)) {
                    if (seg.isWordLike) positions.add(off)
                    off += seg.segment.length
                }
                if (positions.size >= 2) {
                    val mid = s.length / 2.0
                    var best = 1
                    var bestD = Double.POSITIVE_INFINITY
                    for (g in 1 until positions.size) {
                        val d = abs(positions[g] - mid)
                        if (d < bestD) { bestD = d; best = g }
                    }
                    val sp = positions[best]
                    semantic.add(Cand(sentences.indexOf(s), sp, (1 - abs(sp - mid) / mid) * 10 + positions.size))
                }
            }
            if (semantic.isNotEmpty()) {
                val b = semantic.sortedByDescending { it.score }.first()
                val t = sentences[b.index]
                sentences.removeAt(b.index)
                sentences.addAll(b.index, listOf(t.substring(0, b.split), t.substring(b.split)))
                continue
            }
            val hash = sentences.sumOf { s -> s.sumOf { it.code } }
            val seed = hash + sentences.size + (timeSeed ?: 0.0)
            val x = sin(seed) * 10000
            val pick = candidates[floor((x - floor(x)) * candidates.size).toInt()]
            val i = sentences.indexOf(pick)
            val mid = pick.length / 2
            sentences.removeAt(i)
            sentences.addAll(i, listOf(pick.substring(0, mid), pick.substring(mid)))
        }
        return sentences
    }

    private fun merge(sentences: MutableList<String>, target: Int): MutableList<String> {
        while (sentences.size > target) {
            var bi = 0
            var shortest = Int.MAX_VALUE
            for (i in 0 until sentences.size - 1) {
                val l = sentences[i].length + sentences[i + 1].length
                if (l < shortest) { shortest = l; bi = i }
            }
            val m = sentences[bi] + sentences[bi + 1]
            sentences.removeAt(bi); sentences.removeAt(bi)
            sentences.add(bi, m)
        }
        return sentences
    }
}
