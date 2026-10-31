// 对应 Folia 文件：src/utils/lyrics/graphemeTiming.ts（行 1–126）
//
// 逐字时间：有 syllables 用 syllables，否则词内均分；行级时间线把词级时间映射回整行
// （包含分词器没有吐出的空格与标点）。
// Kotlin 侧没有 Intl.Segmenter，用码点序列近似 grapheme（CJK/拉丁/假名场景等价）；
// 代理对（emoji、扩展 B 区汉字）会各自算一个字符，见 splitLyricGraphemes 的说明。
package com.guangyue.core.lyrics

/**
 * 逐字时间。对应 Folia 的 GraphemeTiming。
 *
 * 名字与 Folia 保持一致；函数装在 [GraphemeTimings] 里
 * （Kotlin 不允许同包内数据类与 object 同名）。
 */
data class GraphemeTiming(
    val char: String,
    val startTime: Double,
    val endTime: Double,
    val wordIndex: Int? = null,
)

/** grapheme 切分与逐字时间线。对应 Folia graphemeTiming.ts。 */
object GraphemeTimings {

    /**
     * 对应 Folia 行 18–27 的 splitLyricGraphemes。
     *
     * Folia 走 Intl.Segmenter(granularity:'grapheme')；Kotlin 用码点序列。
     * 对歌词主体（CJK、拉丁、假名、谚文）两者结果一致；代理对（emoji、扩展 B 区汉字）
     * 会各自算一个字符，与 Folia 的 grapheme 不同——差异已记录在 docs/ui-v0/report.md。
     */
    fun splitLyricGraphemes(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<String>(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val count = Character.charCount(cp)
            out.add(text.substring(i, i + count))
            i += count
        }
        return out
    }

    /** 对应 Folia 行 29–47 的 buildEvenGraphemeTimings：等分。 */
    fun buildEvenGraphemeTimings(
        text: String,
        startTime: Double,
        endTime: Double,
        wordIndex: Int? = null,
    ): List<GraphemeTiming> {
        val graphemes = splitLyricGraphemes(text)
        if (graphemes.isEmpty()) return emptyList()

        val duration = maxOf(endTime - startTime, 0.0)
        val unitDuration = duration / graphemes.size

        return graphemes.mapIndexed { index, char ->
            GraphemeTiming(
                char = char,
                startTime = startTime + unitDuration * index,
                // 最后一个的结束时间直接取 endTime，避免浮点累加误差。
                endTime = if (index == graphemes.size - 1) endTime else startTime + unitDuration * (index + 1),
                wordIndex = wordIndex,
            )
        }
    }

    /** 对应 Folia 行 49–55 的 buildWordGraphemeTimings。 */
    fun buildWordGraphemeTimings(word: Word, wordIndex: Int? = null): List<GraphemeTiming> {
        if (word.syllables.isEmpty()) {
            return buildEvenGraphemeTimings(word.text, word.startTime, word.endTime, wordIndex)
        }
        return word.syllables.flatMap { syllable ->
            buildEvenGraphemeTimings(syllable.text, syllable.startTime, syllable.endTime, wordIndex)
        }
    }

    /** 对应 Folia 行 57–76 的 findGraphemeSequence：找不到返回 -1。 */
    fun findGraphemeSequence(source: List<String>, target: List<String>, fromIndex: Int): Int {
        if (target.isEmpty()) return fromIndex
        var index = fromIndex
        while (index <= source.size - target.size) {
            var matched = true
            for (targetIndex in target.indices) {
                if (source[index + targetIndex] != target[targetIndex]) {
                    matched = false
                    break
                }
            }
            if (matched) return index
            index += 1
        }
        return -1
    }

    /**
     * 对应 Folia 行 78–126 的 buildLineGraphemeTimeline：
     * 以整行 grapheme 为基准，把词级时间贴回去；词与词之间的空隙 grapheme 记成零长，
     * 整行没被覆盖到的尾部 grapheme 统一用「已解析过的最大 endTime」。
     */
    fun buildLineGraphemeTimeline(line: Line): List<GraphemeTiming?> {
        val lineGraphemes = splitLyricGraphemes(line.fullText)
        if (lineGraphemes.isEmpty()) return emptyList()

        if (line.words.isEmpty()) {
            return buildEvenGraphemeTimings(line.fullText, line.startTime, line.endTime)
        }

        val timeline = arrayOfNulls<GraphemeTiming>(lineGraphemes.size)
        var cursor = 0
        var lastResolvedTime = line.startTime

        line.words.forEachIndexed { wordIndex, word ->
            val wordGraphemes = splitLyricGraphemes(word.text)
            if (wordGraphemes.isEmpty()) return@forEachIndexed

            val matchedStart = findGraphemeSequence(lineGraphemes, wordGraphemes, cursor)
            val start = if (matchedStart >= 0) matchedStart else cursor
            val end = minOf(start + wordGraphemes.size, lineGraphemes.size)

            // 词之间/词首的空隙：零长，钉在词的开始时间上。
            for (gapIndex in cursor until start) {
                timeline[gapIndex] = GraphemeTiming(
                    char = lineGraphemes[gapIndex],
                    startTime = word.startTime,
                    endTime = word.startTime,
                )
            }

            val wordTimings = buildWordGraphemeTimings(word, wordIndex)
            for (localIndex in 0 until (end - start)) {
                val timing = wordTimings.getOrNull(localIndex)
                    ?: buildEvenGraphemeTimings(
                        wordGraphemes.getOrNull(localIndex) ?: "",
                        word.startTime,
                        word.endTime,
                        wordIndex,
                    ).firstOrNull()

                if (timing == null) continue

                timeline[start + localIndex] = timing.copy(char = lineGraphemes[start + localIndex])
                lastResolvedTime = maxOf(lastResolvedTime, timing.endTime)
            }

            cursor = maxOf(cursor, end)
        }

        for (index in lineGraphemes.indices) {
            if (timeline[index] != null) continue
            timeline[index] = GraphemeTiming(
                char = lineGraphemes[index],
                startTime = lastResolvedTime,
                endTime = lastResolvedTime,
            )
        }

        return timeline.toList()
    }

    /** 便利重载：保证时间线稠密（每格都有值），供 Compose 层按字渲染。 */
    fun buildDenseLineGraphemeTimeline(line: Line): List<GraphemeTiming> {
        val dense = buildLineGraphemeTimeline(line)
        return dense.mapIndexed { index, timing ->
            timing ?: GraphemeTiming(
                char = splitLyricGraphemes(line.fullText).getOrElse(index) { "" },
                startTime = line.startTime,
                endTime = line.startTime,
            )
        }
    }
}