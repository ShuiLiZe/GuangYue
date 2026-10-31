// 对应 Folia 文件：src/components/visualizer/wordColoring.ts（行 1–218）
//
// 关键词着色：按区间（range）匹配，一行只算一次区间表，逐 token 单趟有序指针查色。
// 优先级高者先占位，区间不重叠。
package com.guangyue.core.lyrics

/** 待着色的 token。对应 Folia 的 WordColorToken。 */
data class WordColorToken(
    val key: String,
    val timed: Boolean,
    val startOffset: Int,
    val endOffset: Int,
)

/** 着色区间。对应 Folia 的 WordColorRange。 */
data class WordColorRange(
    val startOffset: Int,
    val endOffset: Int,
    val color: String,
    val priority: Int,
)

/** 关键词匹配器。对应 Folia 的 WordColorMatcher。 */
data class WordColorMatcher(
    val color: String,
    val cjkPhrases: List<String>,
    val englishWords: List<String>,
    val priority: Int,
)

/** CJK 匹配模式。对应 Folia 的 WordColorCjkMatchMode。 */
enum class WordColorCjkMatchMode { TARGET_CONTAINS_TOKEN, BIDIRECTIONAL_CONTAINS, EXACT }

/** 主题里的关键词着色条。对应 Folia Theme['wordColors'] 的一项。 */
data class WordColorEntry(val word: String, val color: String)

/** 对应 Folia wordColoring.ts。 */
object WordColoring {

    /** Folia 行 17：CJK_REGEX（含中日韩与谚文区）。 */
    private val CJK_REGEX = Regex("[\\u4e00-\\u9fa5\\u3040-\\u30ff\\uac00-\\ud7af]")

    /** 对应 Folia 行 19 的 isCJK。 */
    fun isCjk(text: String): Boolean = CJK_REGEX.containsMatchIn(text)

    /** 对应 Folia 行 21：normalizeWordColorToken。 */
    fun normalizeWordColorToken(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() || it == '_' }

    /** 对应 Folia 行 63–66：rangesOverlap（半开区间）。 */
    fun rangesOverlap(a: WordColorRange, b: WordColorRange): Boolean =
        a.startOffset < b.endOffset && b.startOffset < a.endOffset

    /** token 与 range 的重叠判定（token 只有偏移，见 resolveTokenColorMap）。 */
    private fun overlaps(startOffset: Int, endOffset: Int, range: WordColorRange): Boolean =
        startOffset < range.endOffset && range.startOffset < endOffset

    /** 对应 Folia 行 68–83：按优先级取不重叠区间，最后按偏移升序返回。 */
    fun selectNonOverlappingRanges(ranges: List<WordColorRange>): List<WordColorRange> {
        val selected = ArrayList<WordColorRange>()
        val prioritySorted = ranges.sortedWith(
            compareByDescending<WordColorRange> { it.priority }
                .thenBy { it.startOffset }
                .thenBy { it.endOffset }
        )
        prioritySorted.forEach { range ->
            if (selected.none { rangesOverlap(it, range) }) selected.add(range)
        }
        return selected.sortedWith(compareBy({ it.startOffset }, { it.endOffset }))
    }

    /** 对应 Folia 行 85–131：prepareWordColorMatchers。 */
    fun prepareWordColorMatchers(
        wordColors: List<WordColorEntry>,
        keywordColoringEnabled: Boolean = true,
    ): List<WordColorMatcher> {
        if (!keywordColoringEnabled || wordColors.isEmpty()) return emptyList()

        return wordColors.mapNotNull { entry ->
            val target = entry.word.trim()
            if (target.isEmpty()) return@mapNotNull null
            val color = entry.color
            if (color.isEmpty()) return@mapNotNull null

            if (isCjk(target)) {
                WordColorMatcher(color = color, cjkPhrases = listOf(target), englishWords = emptyList(), priority = target.length)
            } else {
                val englishWords = target.split(WHITESPACE).map { normalizeWordColorToken(it) }.filter { it.isNotEmpty() }
                if (englishWords.isEmpty()) return@mapNotNull null
                WordColorMatcher(
                    color = color,
                    cjkPhrases = emptyList(),
                    englishWords = englishWords,
                    priority = englishWords.maxOf { it.length },
                )
            }
        }
    }

    private val WHITESPACE = Regex("\\s+")

    /** 英文词扫描用：Folia 的 /\w+/g。 */
    private val WORD_REGEX = Regex("\\w+")

    /** 对应 Folia 行 134–193：buildWordColorRangesFromMatchers。 */
    fun buildWordColorRangesFromMatchers(lineText: String, matchers: List<WordColorMatcher>): List<WordColorRange> {
        if (lineText.isEmpty() || matchers.isEmpty()) return emptyList()

        val ranges = ArrayList<WordColorRange>()
        // 同词取优先级更高者的颜色。
        val englishColorByWord = HashMap<String, Pair<String, Int>>()

        matchers.forEach { matcher ->
            matcher.cjkPhrases.forEach { target ->
                var cursor = 0
                while (cursor < lineText.length) {
                    val at = lineText.indexOf(target, cursor)
                    if (at < 0) break
                    ranges.add(
                        WordColorRange(
                            startOffset = at,
                            endOffset = at + target.length,
                            color = matcher.color,
                            priority = matcher.priority,
                        )
                    )
                    cursor = at + maxOf(target.length, 1)
                }
            }

            matcher.englishWords.forEach { word ->
                val current = englishColorByWord[word]
                if (current == null || matcher.priority > current.second) {
                    englishColorByWord[word] = matcher.color to matcher.priority
                }
            }
        }

        if (englishColorByWord.isNotEmpty()) {
            WORD_REGEX.findAll(lineText).forEach { match ->
                val found = englishColorByWord[normalizeWordColorToken(match.value)]
                if (found != null) {
                    ranges.add(
                        WordColorRange(
                            startOffset = match.range.first,
                            endOffset = match.range.last + 1,
                            color = found.first,
                            priority = found.second,
                        )
                    )
                }
            }
        }

        return selectNonOverlappingRanges(ranges)
    }

    /** 对应 Folia 行 195–206：buildWordColorRanges。 */
    fun buildWordColorRanges(
        lineText: String,
        wordColors: List<WordColorEntry>,
        keywordColoringEnabled: Boolean = true,
    ): List<WordColorRange> =
        buildWordColorRangesFromMatchers(lineText, prepareWordColorMatchers(wordColors, keywordColoringEnabled))

    /** 对应 Folia 行 208–251：resolveWordColor（单个词直接查色）。 */
    fun resolveWordColor(
        wordText: String,
        wordColors: List<WordColorEntry>,
        fallbackColor: String,
        keywordColoringEnabled: Boolean = true,
        cjkMatchMode: WordColorCjkMatchMode = WordColorCjkMatchMode.TARGET_CONTAINS_TOKEN,
    ): String {
        if (!keywordColoringEnabled || wordColors.isEmpty()) return fallbackColor
        val clean = wordText.trim()
        if (clean.isEmpty()) return fallbackColor

        val matched = wordColors.firstOrNull { entry ->
            val target = entry.word.trim()
            if (target.isEmpty()) return@firstOrNull false

            if (isCjk(clean)) {
                when (cjkMatchMode) {
                    WordColorCjkMatchMode.EXACT -> target == clean
                    WordColorCjkMatchMode.BIDIRECTIONAL_CONTAINS -> target.contains(clean) || clean.contains(target)
                    WordColorCjkMatchMode.TARGET_CONTAINS_TOKEN -> target.contains(clean)
                }
            } else {
                val targetWords = target.split(WHITESPACE).map { normalizeWordColorToken(it) }.filter { it.isNotEmpty() }
                val normalized = normalizeWordColorToken(clean)
                normalized.isNotEmpty() && targetWords.contains(normalized)
            }
        }

        return matched?.color?.takeIf { it.isNotEmpty() } ?: fallbackColor
    }

    /** 对应 Folia 行 253–273：resolveTokenColorMap（单趟有序指针）。 */
    fun resolveTokenColorMap(tokens: List<WordColorToken>, ranges: List<WordColorRange>): Map<String, String> {
        val colors = LinkedHashMap<String, String>()
        var rangeIndex = 0

        tokens.forEach { token ->
            if (!token.timed) return@forEach

            while (rangeIndex < ranges.size && ranges[rangeIndex].endOffset <= token.startOffset) {
                rangeIndex += 1
            }

            val range = ranges.getOrNull(rangeIndex)
            if (range != null && overlaps(token.startOffset, token.endOffset, range)) {
                colors[token.key] = range.color
            }
        }

        return colors
    }
}