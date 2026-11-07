// 对应 Folia 文件：src/utils/lyrics/cjkSemanticLayout.ts（全文）
//
// 解析之后、视觉布局之前的「布局单元」层：不改 Line / Line.words，只派生出
// 分行与显示用的 LyricLayoutUnit（CJK 语义分组 → 标点 / 缩写粘滞）。
// 云阶（Partita）用它切 chunk；以后的其它样式也可复用。
package com.guangyue.core.lyrics

data class LyricLayoutUnit(
    val text: String,
    val words: List<Word>,
    val startTime: Double,
    val endTime: Double,
    /** 由分词把多个 CJK 解析词合成的语义词组。 */
    val isSemantic: Boolean,
    /** 做过标点 / 缩写粘滞。 */
    val isSticky: Boolean = false,
)

object PostLyricLayout {

    private val CJK = Regex("[\\u4e00-\\u9fa5\\u3040-\\u30ff\\uac00-\\ud7af]")
    private val WHITESPACE = Regex("^\\s+$")
    private val APOSTROPHE_ONLY = Regex("^['’]\\s*$")
    private val CONTRACTION_SUFFIX = Regex("^(s|t|m|d|ll|re|ve|em)\\s*$", RegexOption.IGNORE_CASE)
    private val DIRECT_CONTRACTION = Regex("^['’](s|t|m|d|ll|re|ve|em)\\s*$", RegexOption.IGNORE_CASE)
    private val TRAILING_APOSTROPHE = Regex("['’]\\s*$")
    private val TRAILING_WORD_CHAR = Regex("[\\p{L}\\p{N}]$")
    private val INLINE_CONTRACTION = Regex("[\\p{L}\\p{N}]+['’](s|t|m|d|ll|re|ve|em)", RegexOption.IGNORE_CASE)
    private val STICKY_TRAILING_PUNCTUATION = Regex("^[,.;:!?，。！？、：；）】》」』〉〕］)}\\]\"'’”]+$")

    private fun hasCjk(text: String) = CJK.containsMatchIn(text)

    fun singleWordUnits(words: List<Word>): List<LyricLayoutUnit> =
        words.map { LyricLayoutUnit(it.text, listOf(it), it.startTime, it.endTime, isSemantic = false) }

    /** 对应 hasWordSegmentationOverride：用户保存过这一行的分词。 */
    private fun hasOverride(line: Line): Boolean =
        line.wordSegments?.let { WordSegmentation.isValidWordSegmentation(it, line.fullText) } == true

    /** 对应 mapSegmentsToWords：分词片段对回解析词，任何一段对不上就整行放弃（返回 null）。 */
    private fun mapSegmentsToWords(segments: List<LyricWordSegment>, words: List<Word>): List<LyricLayoutUnit>? {
        val units = ArrayList<LyricLayoutUnit>()
        var wordIndex = 0
        for (segment in segments) {
            val segmentText = segment.segment
            if (segmentText.isEmpty() || WHITESPACE.matches(segmentText)) continue
            val start = wordIndex
            var collected = ""
            while (wordIndex < words.size && collected.length < segmentText.length) {
                collected += words[wordIndex].text
                wordIndex += 1
                if (!segmentText.startsWith(collected)) return null
            }
            if (collected != segmentText) return null
            val segWords = words.subList(start, wordIndex).toList()
            val first = segWords.firstOrNull() ?: return null
            val last = segWords.last()
            if (!segment.isWordLike && units.isNotEmpty()) {
                val prev = units.removeAt(units.size - 1)
                units.add(prev.copy(text = prev.text + segmentText, words = prev.words + segWords, endTime = last.endTime))
                continue
            }
            units.add(
                LyricLayoutUnit(
                    text = segmentText,
                    words = segWords,
                    startTime = first.startTime,
                    endTime = last.endTime,
                    isSemantic = segment.isWordLike && hasCjk(segmentText) && segWords.size > 1,
                ),
            )
        }
        if (wordIndex != words.size || units.isEmpty()) return null
        return units
    }

    /** 对应 buildCjkSemanticLayoutUnits。 */
    fun cjkSemanticUnits(line: Line): List<LyricLayoutUnit> {
        if (line.words.isEmpty()) return emptyList()
        val fallback = singleWordUnits(line.words)
        if (!hasCjk(line.fullText) && !hasOverride(line)) return fallback
        val segments = WordSegmentation.segmentLyricWords(line)
        if (segments.isEmpty()) return fallback
        return mapSegmentsToWords(segments, line.words) ?: fallback
    }

    private fun canAttachToPrevious(text: String) = TRAILING_WORD_CHAR.containsMatchIn(text.trimEnd())
    private fun endsWithApostrophe(text: String) = TRAILING_APOSTROPHE.containsMatchIn(text.trimEnd())
    private fun isApostropheOnly(u: LyricLayoutUnit) = APOSTROPHE_ONLY.matches(u.text.trim())
    private fun isContractionSuffix(u: LyricLayoutUnit) = CONTRACTION_SUFFIX.matches(u.text.trim())
    private fun isDirectContraction(u: LyricLayoutUnit) = DIRECT_CONTRACTION.matches(u.text.trim())
    private fun isStickyPunctuation(text: String) = STICKY_TRAILING_PUNCTUATION.matches(text.trim())

    private fun attach(target: LyricLayoutUnit, unit: LyricLayoutUnit) = target.copy(
        text = target.text + unit.text,
        words = target.words + unit.words,
        endTime = unit.endTime,
        isSticky = true,
    )

    /** 对应 applyStickyPunctuationLayoutUnits。 */
    fun applyStickyPunctuation(units: List<LyricLayoutUnit>): List<LyricLayoutUnit> {
        val merged = ArrayList<LyricLayoutUnit>()
        var index = 0
        while (index < units.size) {
            val current = units[index]
            val previous = merged.lastOrNull()
            if (previous == null) {
                merged.add(current); index += 1; continue
            }
            val next = units.getOrNull(index + 1)
            when {
                isApostropheOnly(current) && next != null && canAttachToPrevious(previous.text) && isContractionSuffix(next) -> {
                    merged[merged.size - 1] = attach(attach(previous, current), next)
                    index += 2
                    continue
                }
                isDirectContraction(current) && canAttachToPrevious(previous.text) -> merged[merged.size - 1] = attach(previous, current)
                isContractionSuffix(current) && endsWithApostrophe(previous.text) -> merged[merged.size - 1] = attach(previous, current)
                isStickyPunctuation(current.text) && canAttachToPrevious(previous.text) -> merged[merged.size - 1] = attach(previous, current)
                else -> merged.add(current)
            }
            index += 1
        }
        return merged.map { unit ->
            val trailing = unit.words.size > 1 && isStickyPunctuation(unit.words.last().text)
            val inline = unit.words.size > 1 && !unit.isSemantic && INLINE_CONTRACTION.containsMatchIn(unit.text)
            if (trailing || inline) unit.copy(isSticky = true) else unit
        }
    }

    /** 对应 buildPostLyricLayoutUnits：先语义分组，再标点粘滞。 */
    fun build(line: Line, semantic: Boolean, sticky: Boolean): List<LyricLayoutUnit> {
        val raw = if (semantic) cjkSemanticUnits(line) else singleWordUnits(line.words)
        return if (sticky) applyStickyPunctuation(raw) else raw
    }

    /** 对应 buildDisplayWordsFromLayoutUnits：粘滞非语义单元合成一个显示词，语义单元保留逐字 timing。 */
    fun displayWords(units: List<LyricLayoutUnit>): List<Word> = units.flatMap { unit ->
        if (!unit.isSticky || unit.isSemantic) unit.words
        else listOf(Word(text = unit.text, startTime = unit.startTime, endTime = unit.endTime))
    }
}
