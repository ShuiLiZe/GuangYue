// 对应 Folia 文件：src/components/visualizer/paragraphStructure.ts（行 1–125）
//
// 歌词分段：空隙中位数 × 2.5 定阈值（钳 [1.25, 3.5] 秒）、元数据变化切段、
// 超限段落在最大空隙处切开（recursiveSplit 时左半也继续切）、按副歌/时长/词数/标点分类。
package com.guangyue.core.lyrics

/** 段落性质。对应 Folia 的 ParagraphKind。 */
enum class ParagraphKind { BREATH, VERSE, LIFT, CHORUS, BREAK, OUTRO }

/** 段落从哪切开的。对应 Folia 的 ParagraphBoundary。 */
enum class ParagraphBoundary { SONG_START, TIME_GAP, METADATA, DURATION_CAP, LINE_CAP }

/** 编译用的一行：全曲行序 + 行 + 视觉结束时间（不越过下一行开始）。 */
data class StructureLine(
    val sourceIndex: Int,
    val line: Line,
    val renderEndTime: Double,
)

/** 分段参数。对应 Folia 的 ParagraphStructureParams。 */
data class ParagraphStructureParams(
    val gapMultiplier: Double = 2.5,
    val gapMin: Double = 1.25,
    val gapMax: Double = 3.5,
    val maxLines: Int = 6,
    val maxDuration: Double = 18.0,
    val recursiveSplit: Boolean = true,
) {
    companion object {
        /** 对应 Folia 的 DEFAULT_PARAGRAPH_STRUCTURE_PARAMS。 */
        val DEFAULT = ParagraphStructureParams()
    }
}

/** 一段草稿。对应 Folia 的 ParagraphDraft。 */
data class ParagraphDraft(
    val lines: List<StructureLine>,
    val boundary: ParagraphBoundary,
)

/** 对应 Folia paragraphStructure.ts。 */
object ParagraphStructure {

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.5
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }

    /** 对应 Folia 行 52–60 的 buildStructureLines。 */
    fun buildStructureLines(lines: List<Line>): List<StructureLine> =
        lines.mapIndexed { sourceIndex, line ->
            StructureLine(
                sourceIndex = sourceIndex,
                line = line,
                renderEndTime = maxOf(
                    line.startTime,
                    minOf(
                        RenderHints.getLineRenderEndTime(line),
                        lines.getOrNull(sourceIndex + 1)?.startTime ?: Double.POSITIVE_INFINITY,
                    ),
                ),
            )
        }

    /** 对应 Folia 行 62–68：段落切分阈值。 */
    fun resolveParagraphGapThreshold(
        lines: List<Line>,
        params: ParagraphStructureParams = ParagraphStructureParams.DEFAULT,
    ): Double {
        val gaps = lines.drop(1).mapIndexed { index, line ->
            line.startTime - minOf(RenderHints.getLineRenderEndTime(lines[index]), line.startTime)
        }.filter { it > 0 }
        return minOf(maxOf(median(gaps) * params.gapMultiplier, params.gapMin), params.gapMax)
    }

    /** 对应 Folia 行 70–73：元数据变化（blockIndex 或 songPart 都非空且不同）。 */
    fun metadataChanged(previous: Line, next: Line): Boolean {
        val blockChanged = previous.blockIndex != null && next.blockIndex != null &&
            previous.blockIndex != next.blockIndex
        val partChanged = previous.songPart != null && next.songPart != null &&
            previous.songPart != next.songPart
        return blockChanged || partChanged
    }

    /** 对应 Folia 行 75–99：超限段落在最大空隙处切开。 */
    fun splitOversizedDraft(draft: ParagraphDraft, params: ParagraphStructureParams): List<ParagraphDraft> {
        val output = ArrayList<ParagraphDraft>()
        var remaining = draft.lines
        var boundary = draft.boundary
        var loopGuard = 0

        while (remaining.size > params.maxLines ||
            (remaining.size > 1 && (remaining.last().renderEndTime - remaining.first().line.startTime) > params.maxDuration)
        ) {
            if (loopGuard++ > 1000) break

            val candidates = remaining.subList(2, maxOf(2, remaining.size - 1)).mapIndexed { offset, line ->
                // 注意：Folia 的 slice(2,-1) 会砍掉最后一项，这里用 subList(2, size-1) 等价。
                val splitIndex = offset + 2
                splitIndex to (line.line.startTime - remaining[offset + 1].renderEndTime)
            }
            val valid = candidates.filter { !it.second.isNaN() }
            val rawSplitIndex = valid.maxByOrNull { it.second }?.first
                ?: minOf(4, remaining.size - 1)
            val splitIndex = maxOf(1, rawSplitIndex)

            val head = ParagraphDraft(lines = remaining.subList(0, splitIndex).toList(), boundary = boundary)
            if (params.recursiveSplit) {
                output.addAll(splitOversizedDraft(head, params))
            } else {
                output.add(head)
            }
            remaining = remaining.subList(splitIndex, remaining.size).toList()
            boundary = if (output.last().lines.size >= params.maxLines) {
                ParagraphBoundary.LINE_CAP
            } else {
                ParagraphBoundary.DURATION_CAP
            }
        }

        output.add(ParagraphDraft(lines = remaining, boundary = boundary))
        return output
    }

    /** 对应 Folia 行 101–124：自动分段。 */
    fun draftParagraphs(
        lines: List<StructureLine>,
        threshold: Double,
        params: ParagraphStructureParams = ParagraphStructureParams.DEFAULT,
    ): List<ParagraphDraft> {
        val drafts = ArrayList<ParagraphDraft>()
        var current = ParagraphDraft(lines = emptyList(), boundary = ParagraphBoundary.SONG_START)

        lines.forEachIndexed { index, line ->
            val previous = lines.getOrNull(index - 1)
            val gap = if (previous != null) line.line.startTime - previous.renderEndTime else 0.0
            val boundary = when {
                previous != null && metadataChanged(previous.line, line.line) -> ParagraphBoundary.METADATA
                previous != null && gap >= threshold -> ParagraphBoundary.TIME_GAP
                else -> null
            }
            if (boundary != null && current.lines.isNotEmpty()) {
                drafts.addAll(splitOversizedDraft(current, params))
                current = ParagraphDraft(lines = emptyList(), boundary = boundary)
            }
            current = current.copy(lines = current.lines + line)
        }

        if (current.lines.isNotEmpty()) drafts.addAll(splitOversizedDraft(current, params))
        return drafts
    }

    /** 对应 Folia 行 126–127：一行里「像词」的片段数。 */
    fun countWordLike(line: Line): Int =
        WordSegmentation.segmentLyricWords(line).count { it.isWordLike }

    private val CHORUS_REGEX = Regex("chorus|副歌", RegexOption.IGNORE_CASE)
    private val BREAK_REGEX = Regex("bridge|break|間奏|ブリッジ", RegexOption.IGNORE_CASE)
    private val PUNCTUATION_REGEX = Regex("[!?！？…]")

    /** 对应 Folia 行 129–141：段落性质分类。 */
    fun classifyParagraph(lines: List<StructureLine>, index: Int, total: Int): ParagraphKind {
        if (lines.any { it.line.isChorus || CHORUS_REGEX.containsMatchIn(it.line.songPart ?: "") }) {
            return ParagraphKind.CHORUS
        }
        if (lines.any { BREAK_REGEX.containsMatchIn(it.line.songPart ?: "") }) {
            return ParagraphKind.BREAK
        }
        if (index == total - 1) {
            return ParagraphKind.OUTRO
        }

        val duration = lines.last().renderEndTime - lines.first().line.startTime
        val segmentCount = lines.sumOf { countWordLike(it.line) }
        val punctuationCount = lines.sumOf { PUNCTUATION_REGEX.findAll(it.line.fullText).count() }

        if (duration <= 3.5 || segmentCount <= 3) return ParagraphKind.BREATH
        if (punctuationCount >= 2 || segmentCount / maxOf(duration, 1.0) > 2.5) return ParagraphKind.LIFT
        return ParagraphKind.VERSE
    }
}