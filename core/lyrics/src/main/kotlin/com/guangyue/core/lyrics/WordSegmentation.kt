// 对应 Folia 文件：
//   src/utils/lyrics/wordSegmentation.ts（全文）
//   src/utils/lyrics/graphemeTiming.ts（splitLyricGraphemes）
//
// 分词：优先用保存下来的分词（wordSegments），其次自动分词（autoWordSegments），
// 最后兜底。Folia 的兜底是 Intl.Segmenter('word')（异常时退回 grapheme）；
// Kotlin/JVM 没有 ICU 分词器，这里用等价规则的确定性兜底：
//   - 连续拉丁字母/数字（含下划线、撇号）合成一个词，isWordLike = true；
//   - CJK/假名/谚文每个字算一个词，isWordLike = true；
//   - 空白与纯标点各成片段，isWordLike = false。
package com.guangyue.core.lyrics

/** 对应 Folia 的 segmentLyricWords 输出。 */
object WordSegmentation {

    /** 对应 Folia 的 PUNCTUATION_ONLY = /^[\s\p{P}\p{S}]+$/u。 */
    private val PUNCTUATION_ONLY = Regex("^[\\s\\p{P}\\p{S}]+$")

    /** 空白片段。 */
    private val WHITESPACE = Regex("\\s+")

    /** 「词」字符：拉丁字母、数字、下划线、撇号。 */
    private fun isLatinWordChar(ch: Char): Boolean =
        (ch.isLetterOrDigit() && ch.code < 0x3000) || ch == '_' || ch == '\'' || ch == '\u2019'

    /** 对应 Folia 的 segmentLyricWords。 */
    fun segmentLyricWords(line: Line): List<LyricWordSegment> {
        val stored = line.wordSegments
        if (stored != null && isValidWordSegmentation(stored, line.fullText)) {
            return stored.mapIndexed { index, segment -> toSegment(segment, index) }
        }

        val auto = line.autoWordSegments
        if (auto != null && isValidWordSegmentation(auto, line.fullText)) {
            return auto.mapIndexed { index, segment -> toSegment(segment, index) }
        }

        return segmentByRules(line.fullText)
    }

    /** 对应 Folia 的 isValidWordSegmentation：拼回原文才算有效。 */
    fun isValidWordSegmentation(segments: List<String>, fullText: String): Boolean =
        segments.isNotEmpty() && segments.joinToString("") == fullText

    /** 对应 Folia 的 getWordSegmentationKey：用各片段长度串做键。 */
    fun getWordSegmentationKey(segments: List<String>): String =
        segments.joinToString("|") { it.length.toString() }

    private fun toSegment(segment: String, index: Int): LyricWordSegment =
        LyricWordSegment(segment = segment, index = index, isWordLike = !PUNCTUATION_ONLY.matches(segment))

    /** 兜底切分：规则化、可复现。 */
    fun segmentByRules(text: String): List<LyricWordSegment> {
        if (text.isEmpty()) return emptyList()

        val out = ArrayList<LyricWordSegment>()
        val graphemes = GraphemeTimings.splitLyricGraphemes(text)
        var buffer = StringBuilder()
        var bufferWordLike = false
        var index = 0

        fun flush() {
            if (buffer.isEmpty()) return
            out.add(LyricWordSegment(segment = buffer.toString(), index = index++, isWordLike = bufferWordLike))
            buffer = StringBuilder()
        }

        for (char in graphemes) {
            val wordLike = !WHITESPACE.matches(char) && !PUNCTUATION_ONLY.matches(char)
            val latin = char.length == 1 && isLatinWordChar(char[0])
            val continuesLatin = latin && bufferWordLike && buffer.isNotEmpty() && buffer.all { isLatinWordChar(it) }

            // 拉丁词累积；其余（CJK、空白、标点）每段独立。
            if (continuesLatin) {
                buffer.append(char)
            } else if (latin) {
                flush()
                buffer.append(char)
                bufferWordLike = true
            } else {
                flush()
                bufferWordLike = false
                if (wordLike) {
                    out.add(LyricWordSegment(segment = char, index = index++, isWordLike = true))
                } else {
                    // 空白/标点：与相邻同类合并，减少片段数。
                    val prev = out.lastOrNull()
                    if (prev != null && !prev.isWordLike &&
                        ((WHITESPACE.matches(prev.segment) && WHITESPACE.matches(char)) ||
                            (!WHITESPACE.matches(prev.segment) && !WHITESPACE.matches(char)))
                    ) {
                        out[out.size - 1] = prev.copy(segment = prev.segment + char)
                    } else {
                        out.add(LyricWordSegment(segment = char, index = index++, isWordLike = false))
                    }
                }
            }
        }
        flush()
        return out
    }
}