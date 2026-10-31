// 对应 Folia 文件：
//   src/utils/lyrics/graphemeTiming.ts（无逐字时间时的字级均分）
//   src/utils/lyrics/wordSegmentation.ts（词边界来源）
//   src/utils/lyrics/renderHints.ts（渲染提示补齐）
//
// 时间轴接入：把逐行 LRC（广乐 core:data 的 Lyric/LyricLine）转成内核的 Line。
//   - 有逐字时间戳（网易 yrc / QQ qrc 解析出来的 <mm:ss.xx>）：直接做成 Word，不造假；
//   - 没有逐字时间：按字均分（与 Folia 无 syllables 时一致）。
// 本文件不依赖 Android：输入是与 core:data 对应的纯 Kotlin 数据类，
// 由 core:data 侧的适配器填充（见 LyricTimeline.kt）。
package com.guangyue.core.lyrics

/** 时间轴输入的一行（与 core:data 的 LyricLine 一一对应）。 */
data class TimelineLineInput(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
    /** 逐字时间戳（core:data 的 LyricTag）：data 是两段时间戳之间的文本。 */
    val tags: List<TimelineTag> = emptyList(),
)

/** 逐字时间戳（与 core:data 的 LyricTag 一一对应）。 */
data class TimelineTag(val startMs: Long, val endMs: Long, val data: String)

/** 对应 Folia 的时间轴构建（逐行 → Line 列表）。 */
object Timeline {

    /** 毫秒 → 秒。内核所有时间都是秒（与 Folia 一致）。 */
    fun ms(value: Long): Double = value / 1000.0

    /**
     * 构建 Line 列表。
     *
     * @param inputs 逐行歌词（按时间升序）
     * @param durationMs 整曲时长（ms）；最后一行没有下一行时用它夹住结束时间
     * @param fallbackLineMs 无下一行且没有曲长时，最后一行至少留这么久（与 core:data 的 4000ms 节奏一致）
     */
    fun buildLines(
        inputs: List<TimelineLineInput>,
        durationMs: Long? = null,
        fallbackLineMs: Long = 4_000L,
    ): List<Line> = inputs.mapIndexed { index, input ->
        val nextStartMs = inputs.getOrNull(index + 1)?.timeMs
            ?: durationMs
            ?: (input.timeMs + fallbackLineMs)
        // 结束时间：下一行开始（与 core:data 的 fillEnds 同规则），至少 1ms。
        val endMs = maxOf(nextStartMs, input.timeMs + 1L)

        val words = buildWords(input, endMs)
        val line = Line(
            words = words,
            startTime = ms(input.timeMs),
            endTime = ms(endMs),
            fullText = input.text,
            translation = input.translation,
        )
        // 渲染提示预齐（与 Folia ensureLyricLinesRenderHints 一致）。
        line.copy(renderHints = RenderHints.buildLineRenderHints(line))
    }

    /**
     * 一行的 Word 列表。
     * 有逐字时间戳就直接用；没有就按字均分。
     */
    fun buildWords(input: TimelineLineInput, endMs: Long): List<Word> {
        val sentence = input.text
        if (sentence.isEmpty()) return emptyList()

        if (input.tags.isNotEmpty()) {
            // 逐字时间戳：每个 tag 的 data 就是这一段（一个词/一个字）的文本。
            return input.tags.mapNotNull { tag ->
                val text = tag.data
                if (text.isEmpty()) return@mapNotNull null
                Word(
                    text = text,
                    startTime = ms(tag.startMs),
                    endTime = ms(maxOf(tag.endMs, tag.startMs + 1L)),
                )
            }
        }

        // 无逐字时间：先按规则切词，再按「行内 grapheme 均分」给每段定时间。
        val segments = WordSegmentation.segmentByRules(sentence).map { it.segment }
        return distributeWordsEvenly(segments, ms(input.timeMs), ms(endMs))
    }

    /**
     * 与 Folia graphemeTiming.buildEvenGraphemeTimings 等价的分法：
     * 整行 grapheme 等分，每个词覆盖它自己的 grapheme 区间（起止时间取该区间的边界）。
     * 这样词内逐字时间再做一次均分（buildWordGraphemeTimings）就与 Folia 完全一致。
     */
    fun distributeWordsEvenly(segments: List<String>, startTime: Double, endTime: Double): List<Word> {
        val graphemes = segments.map { GraphemeTimings.splitLyricGraphemes(it) }
        val total = graphemes.sumOf { it.size }
        if (total == 0) return emptyList()

        val unit = maxOf(endTime - startTime, 0.0) / total
        var consumed = 0
        val words = ArrayList<Word>(segments.size)

        for (index in segments.indices) {
            val segment = segments[index]
            val count = graphemes[index].size
            if (count == 0) continue
            val wordStart = startTime + unit * consumed
            val wordEnd = if (consumed + count >= total) endTime else startTime + unit * (consumed + count)
            words.add(Word(text = segment, startTime = wordStart, endTime = wordEnd))
            consumed += count
        }

        return words
    }
}