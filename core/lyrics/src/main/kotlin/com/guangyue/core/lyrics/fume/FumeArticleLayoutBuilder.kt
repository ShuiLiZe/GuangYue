// 对应 Folia 文件：src/components/visualizer/fume/fumeArticleLayout.ts、fumeTextMeasure.ts
//
// 「浮名」的整首歌排版：把每一行歌词当成文章的「块」，正文块逐列堆叠、hero 块跨列，
// 在若干套 列数 × 密度缩放 里挑一套最接近目标高度的方案（这就是它看起来「被排版过」的原因）。
//
// 与 Folia 的差异（详见 docs/ui-v0/report.md）：
//  - Folia 用 `pretext` 的 layoutWithLines 做断行与逐字宽度测量；Kotlin 侧走 [FumeTextMeasurer]
//    （绘制层用 android.graphics.Paint 实现），断行为贪心按字素换行，宽度按字素推进累加。
//  - Folia 的排版跑在 MessageChannel 分片里（LAYOUT_SLICE_MS=8）；Kotlin 侧同步一次算完。
package com.guangyue.core.lyrics.fume

import com.guangyue.core.lyrics.GraphemeTimings
import com.guangyue.core.lyrics.Line

/** 排版只关心这几个主题字段。对应 Folia `Pick<Theme,'name'|'fontStyle'|'fontFamily'|'fontFamilyStack'|'fontWeight'>`。 */
data class FumeLayoutTheme(
    val name: String,
    val fontStyle: String,
    val fontFamily: String = "",
    val fontWeight: Int? = null,
)

/** 对应 Folia fumeArticleLayout.ts 的 chooseNaturalBlockVariant / chooseFallbackHeroBlockIndex。 */
object FumeArticleLayoutBuilder {

    /** 对应 LAYOUT_REBUILD_DEBOUNCE_MS：重排前的去抖时长（ms）。 */
    const val LAYOUT_REBUILD_DEBOUNCE_MS = 96

    /**
     * 对应 Folia fumeArticleLayout.ts 行 499：文章纸面宽度。
     *
     * 手机化（2026-11-08，修「浮名太大、句首被切、一行只放 6 字」）：Folia 的纸宽下限 920 是桌面口径，
     * 竖屏手机（宽 < 600dp）上一句会排成 900 多 dp 的单行，镜头再放大 1.3–2.2 倍，屏上只剩 6~7 个字，
     * 句首被推出屏外。手机竖屏改为纸宽 = 视口宽 − 两侧 24dp 安全区，让句子在屏宽内折行。
     */
    fun paperWidthFor(viewportWidth: Double): Double =
        if (viewportWidth < PHONE_PAPER_MAX_VIEWPORT) {
            maxOf(viewportWidth - FumeCamera.FOCUS_SAFE_INSET_DP * 2.0, PHONE_PAPER_MIN)
        } else {
            FumeMath.clamp(maxOf(viewportWidth * 1.95, viewportWidth + 520.0), 920.0, 2400.0)
        }

    /** 视口宽小于它按手机竖屏排纸。 */
    const val PHONE_PAPER_MAX_VIEWPORT = 600.0

    /** 手机纸宽下限。 */
    const val PHONE_PAPER_MIN = 240.0

    /** 对应 Folia 行 501：纸够宽就多排几列。 */
    fun maxColumnsFor(paperWidth: Double): Int = when {
        paperWidth >= 1120.0 -> 4
        paperWidth >= 760.0 -> 3
        paperWidth >= 500.0 -> 2
        else -> 1
    }

    /** 对应 Folia 行 521：列间距。 */
    fun gapFor(paperWidth: Double, columns: Int): Double = FumeMath.clamp(
        Math.round(paperWidth * if (columns >= 4) 0.0065 else if (columns == 3) 0.0085 else 0.0115).toDouble(),
        6.0,
        14.0,
    )

    /** 对应 countRenderableGraphemes(text)：非空白字素数量。 */
    fun countRenderableGraphemes(text: String): Int =
        FumeMath.splitGraphemes(text).count { it.trim().isNotEmpty() }

    /** 对应 chooseNaturalBlockVariant(line, index, total)。 */
    fun chooseNaturalBlockVariant(line: Line, index: Int, total: Int): FumeVariant {
        val graphemeCount = countRenderableGraphemes(line.fullText)
        if (graphemeCount == 0) return FumeVariant.BODY
        if (line.isChorus && graphemeCount <= 22) return FumeVariant.HERO
        val shortEnough = graphemeCount in 4..28
        val centered = Math.abs(index - total / 2.0) / maxOf(total, 1).toDouble()
        val random = FumeMath.seeded("${line.fullText}:$index")
        return if (shortEnough && centered < 0.72 && ((index + 1) % 6 == 0 || random > 0.965)) {
            FumeVariant.HERO
        } else {
            FumeVariant.BODY
        }
    }

    /**
     * 对应 chooseFallbackHeroBlockIndex(entries)：如果整篇没有自然 hero 块，
     * 就按「居中 + 长度 + 副歌 + 稳定抖动」挑一个块强制做 hero；没有合格的返回 -1。
     */
    fun chooseFallbackHeroBlockIndex(entries: List<Pair<Line, Int>>): Int {
        if (entries.isEmpty()) return -1
        for ((line, index) in entries) {
            if (chooseNaturalBlockVariant(line, index, entries.size) == FumeVariant.HERO) return -1
        }

        var bestIndex = -1
        var bestScore = Double.NEGATIVE_INFINITY
        var bestCount = 0
        val total = entries.size
        entries.forEachIndexed { blockIndex, (line, _) ->
            val count = countRenderableGraphemes(line.fullText)
            if (count == 0) return@forEachIndexed
            val centerScore = 1.0 - Math.abs(blockIndex - total / 2.0) / maxOf(total, 1).toDouble()
            val lengthScore = if (count in 6..22) 1.0 else if (count <= 28) 0.72 else 0.36
            val acceptable = count <= 36
            if (!acceptable) return@forEachIndexed
            val chorusScore = if (line.isChorus) 0.28 else 0.0
            val stableJitter = FumeMath.seeded("${line.fullText}:$blockIndex:fallback-hero") * 0.04
            val score = centerScore * 0.62 + lengthScore * 0.34 + chorusScore + stableJitter
            if (score > bestScore) {
                bestScore = score
                bestIndex = blockIndex
                bestCount = count
            }
        }
        if (bestIndex >= 0 && bestCount > 0) return bestIndex

        // 全不合格：取最短的非空行。
        var shortest = -1
        var shortestLength = Int.MAX_VALUE
        entries.forEachIndexed { blockIndex, (line, _) ->
            val count = countRenderableGraphemes(line.fullText)
            if (count in 1 until shortestLength) {
                shortestLength = count
                shortest = blockIndex
            }
        }
        return shortest
    }

    /** 对应 buildLayoutCacheKey：只有影响几何的输入能触发整篇重排。 */
    fun buildLayoutCacheKey(
        lines: List<Line>,
        viewport: FumeViewport,
        theme: FumeLayoutTheme,
        lyricsFontScale: Double,
        tuning: FumeTuning,
    ): String {
        var linesHash = 2_166_136_261L
        for (line in lines) {
            val lineKey = "${line.startTime}:${line.endTime}:${line.fullText}:${line.words.size}:${if (line.isChorus) 1 else 0}"
            linesHash = linesHash xor FumeMath.hashString(lineKey)
            linesHash = (linesHash * 16_777_619L) and 0xFFFFFFFFL
        }
        return listOf(
            Math.round(viewport.width).toString(),
            Math.round(viewport.height).toString(),
            theme.fontStyle,
            theme.fontFamily,
            theme.fontWeight?.toString() ?: "auto",
            theme.name,
            String.format("%.4f", lyricsFontScale),
            String.format("%.4f", tuning.heroScale),
            lines.size.toString(),
            linesHash.toString(),
        ).joinToString("|")
    }

    /** 对应 chooseSingleLineFontPx：8 次二分，找一个还塞得进 [width] 的字号。 */
    internal fun chooseSingleLineFontPx(
        text: String,
        variant: FumeVariant,
        width: Double,
        lyricsFontScale: Double,
        densityScale: Double,
        heroScale: Double,
        measurer: FumeTextMeasurer,
    ): Double {
        var low = if (variant == FumeVariant.HERO) 18.0 else 10.0
        var high = if (variant == FumeVariant.HERO) 58.0 else 30.0
        var best: Double? = null

        repeat(8) {
            val candidate = (low + high) / 2.0 * lyricsFontScale * densityScale *
                (if (variant == FumeVariant.HERO) heroScale else 1.0)
            if (measurer.measureWidth(text, candidate, variant) <= width) {
                best = candidate
                low = (low + high) / 2.0
            } else {
                high = (low + high) / 2.0
            }
        }

        return best ?: ((if (variant == FumeVariant.HERO) 18.0 else 10.0) * lyricsFontScale * densityScale *
            (if (variant == FumeVariant.HERO) heroScale else 1.0))
    }

    /**
     * 贪心按字素断行（对应 Folia `layoutWithLines(prepared, innerWidth, lineHeight)`）。
     * 返回每行覆盖的块内字素区间 [start, end)。
     */
    internal fun wrapGraphemes(
        graphemes: List<String>,
        advances: List<Double>,
        innerWidth: Double,
    ): List<Pair<Int, Int>> {
        if (graphemes.isEmpty()) return listOf(0 to 0)
        val ranges = ArrayList<Pair<Int, Int>>()
        var lineStart = 0
        var lineWidth = 0.0
        for (index in graphemes.indices) {
            val advance = advances[index]
            if (index > lineStart && lineWidth + advance > innerWidth) {
                ranges.add(lineStart to index)
                lineStart = index
                lineWidth = 0.0
            }
            lineWidth += advance
        }
        ranges.add(lineStart to graphemes.size)
        return ranges
    }

    /** 对应 Folia 行 289–295：块字号、行高与行数。 */
    private data class BlockMetrics(val fontPx: Double, val lineHeight: Double, val lineCount: Int)

    private fun resolveBlockMetrics(
        text: String,
        variant: FumeVariant,
        innerWidth: Double,
        lyricsFontScale: Double,
        densityScale: Double,
        tuning: FumeTuning,
        measurer: FumeTextMeasurer,
        graphemes: List<String>,
    ): Pair<BlockMetrics, List<Double>> {
        val fontPx = chooseSingleLineFontPx(
            text, variant, innerWidth, lyricsFontScale, densityScale, tuning.heroScale, measurer,
        )
        val advances = graphemes.map { measurer.measureWidth(it, fontPx, variant) }
        val lineCount = wrapGraphemes(graphemes, advances, innerWidth).size
        val lineHeight = Math.round(fontPx * (if (variant == FumeVariant.HERO) 1.02 else 1.06)).toDouble()
        return BlockMetrics(fontPx, lineHeight, lineCount) to advances
    }

    /** 对应 buildWordRangeIndexByOffset(graphemeCount, wordRanges, 'timing' | 'color')。 */
    internal fun buildWordRangeIndexByOffset(
        graphemeCount: Int,
        wordRanges: List<FumeWordRange>,
        useColorRange: Boolean,
    ): List<Int> {
        val index = IntArray(graphemeCount) { -1 }
        wordRanges.forEachIndexed { rangeIndex, range ->
            val start = if (useColorRange) range.colorStart else range.start
            val end = if (useColorRange) range.colorEnd else range.end
            for (offset in start until minOf(end, graphemeCount)) {
                if (offset >= 0) index[offset] = rangeIndex
            }
        }
        return index.toList()
    }

    /** 对应 fumeReveal.ts 的 buildWordRangesFromWords。 */
    internal fun buildWordRangesFromWords(line: Line, graphemes: List<String>): List<FumeWordRange> {
        if (line.words.isEmpty() || graphemes.isEmpty()) return emptyList()
        val ranged = line.words.filter { FumeMath.splitGraphemes(it.text).isNotEmpty() }
        if (ranged.isEmpty()) return emptyList()

        val ranges = ArrayList<FumeWordRange>(ranged.size)
        var cursor = 0
        ranged.forEachIndexed { wordIndex, word ->
            val wordGraphemes = FumeMath.splitGraphemes(word.text)
            val start = FumeMath.clamp(cursor.toDouble(), 0.0, graphemes.size.toDouble()).toInt()
            var end = FumeMath.clamp(
                (start + wordGraphemes.size).toDouble(), start.toDouble(), graphemes.size.toDouble(),
            ).toInt()
            // 有些歌词把词间空格留在 fullText 里而没留在 word.text 里：把紧随的空白并进当前词，
            // 免得后面每个词都往左挪一格（对应 Folia 行 28–33 的注释）。
            while (end < graphemes.size && graphemes[end].isBlank()) end += 1
            ranges.add(
                FumeWordRange(
                    wordIndex = wordIndex,
                    word = word,
                    start = start,
                    end = end,
                    colorStart = start,
                    colorEnd = end,
                    graphemeTimings = GraphemeTimings.buildWordGraphemeTimings(word),
                ),
            )
            cursor = end
        }
        return ranges
    }

    /**
     * 对应 buildRenderSegments：把一段行文本切成若干「画一段就够」的片段。
     * Folia 按 pretext 的内部分段切；这里按「空白边界 + CJK↔拉丁切换」切，
     * 于是 CJK 每字一段（浮名本来就逐字打印），拉丁词整词一段。
     */
    internal fun buildRenderSegments(
        graphemes: List<String>,
        advances: List<Double>,
        lineStart: Int,
        lineEnd: Int,
    ): List<FumeRenderSegment> {
        val segments = ArrayList<FumeRenderSegment>()
        var index = lineStart
        while (index < lineEnd) {
            var end = index + 1
            val cjk = FumeMath.isCjk(graphemes[index])
            val blank = graphemes[index].isBlank()
            while (end < lineEnd) {
                val next = graphemes[end]
                if (FumeMath.isCjk(next) != cjk || next.isBlank() != blank) break
                end += 1
            }
            val offsets = ArrayList<Double>(end - index)
            var running = 0.0
            for (offset in index until end) {
                offsets.add(running)
                running += advances[offset]
            }
            segments.add(
                FumeRenderSegment(
                    text = graphemes.subList(index, end).joinToString(""),
                    start = index,
                    end = end,
                    localStart = index,
                    localEnd = end,
                    x = advances.take(index).sum(),
                    width = running,
                    isFullSegment = true,
                    measuredGlyphOffsets = offsets,
                ),
            )
            index = end
        }
        return segments
    }

    /**
     * 对应 buildArticleLayout：整首歌排成一篇文章。
     * 返回 null 表示视口无效或没有可排的行。
     */
    fun build(
        lines: List<Line>,
        viewport: FumeViewport,
        theme: FumeLayoutTheme,
        lyricsFontScale: Double,
        tuning: FumeTuning = FumeTuning.DEFAULT,
        measurer: FumeTextMeasurer,
        /**
         * 手机化参数：列数上限。Folia 由纸宽推列数（竖屏手机上会推成 1 栏整屏高），
         * 调用方可以按舞台宽高比另给一个上限；null = 按 Folia 规则。
         */
        maxColumns: Int? = null,
    ): FumeArticleLayout? {
        if (viewport.width <= 0.0 || viewport.height <= 0.0 || lines.isEmpty()) return null

        val paperWidth = paperWidthFor(viewport.width)
        val viewportHeight = maxOf(viewport.height, 240.0)
        val maxColumns = (maxColumns ?: maxColumnsFor(paperWidth)).coerceIn(1, 4)
        val targetHeight = viewportHeight * 2.45
        val horizontalMargin = maxOf(viewport.width * 0.86, 280.0)
        val verticalMargin = maxOf(viewport.height * 0.82, 220.0)

        var bestMetadata: LayoutOptions? = null
        var bestScore = Double.POSITIVE_INFINITY

        // 试几套「列数 × 密度缩放」，保留最贴近目标高度的那套（对应 Folia 行 516–574）。
        for (columns in maxColumns downTo 1) {
            var low = 0.82
            var high = 1.42
            val gap = gapFor(paperWidth, columns)
            // 对应 Folia：`${layoutSeedKey}:${columns}:${paperWidth}`（paperWidth 已取整）。
            val seedKey = "${theme.name}:$columns:${Math.round(paperWidth)}"

            repeat(8) {
                val densityScale = (low + high) / 2.0
                val options = LayoutOptions(
                    paperWidth = paperWidth,
                    viewportHeight = viewportHeight,
                    columns = columns,
                    gap = gap,
                    densityScale = densityScale,
                    seedKey = seedKey,
                    measure = true,
                )
                val metrics = attempt(
                    lines, viewport, theme, lyricsFontScale, tuning, measurer,
                    options, horizontalMargin, verticalMargin,
                ) ?: return@repeat

                val coveragePenalty = Math.abs(metrics.height - targetHeight)
                val overflowPenalty =
                    if (metrics.height < targetHeight) 0.0 else (metrics.height - targetHeight) * 0.14
                val score = coveragePenalty + overflowPenalty
                if (score < bestScore) {
                    bestScore = score
                    bestMetadata = options.copy(measure = false)
                }
                if (metrics.height < targetHeight) low = densityScale else high = densityScale
            }
        }

        val finalOptions = bestMetadata ?: return null
        return attempt(
            lines, viewport, theme, lyricsFontScale, tuning, measurer,
            finalOptions, horizontalMargin, verticalMargin,
        )
    }

    /** 一次排版尝试：mode='measure' 只算高度，mode='render' 才建绘制所需的全部结构。 */
    private data class LayoutOptions(
        val paperWidth: Double,
        val viewportHeight: Double,
        val columns: Int,
        val gap: Double,
        val densityScale: Double,
        val seedKey: String,
        val measure: Boolean,
    )

    private data class AttemptMetrics(val height: Double)

    private fun attempt(
        lines: List<Line>,
        viewport: FumeViewport,
        theme: FumeLayoutTheme,
        lyricsFontScale: Double,
        tuning: FumeTuning,
        measurer: FumeTextMeasurer,
        options: LayoutOptions,
        horizontalMargin: Double,
        verticalMargin: Double,
    ): FumeArticleLayout? {
        val columns = options.columns
        val paperWidth = options.paperWidth
        val gap = options.gap
        val columnWidth = (paperWidth - gap * (columns - 1)) / columns

        // 空行对文章排版没帮助；同时按确定性种子打乱落位顺序，让纸面像「编过版」而不是严格按时间堆。
        val filteredLines = lines
            .mapIndexed { index, line -> line to index }
            .filter { (line, _) -> line.fullText.trim().isNotEmpty() }
            .sortedBy { (line, index) -> FumeMath.seeded("${options.seedKey}:$index:${line.fullText}") }

        if (filteredLines.isEmpty()) return null

        val columnHeights = DoubleArray(columns) { verticalMargin }
        var bodyColumnTieCursor = 0
        var heroPlacementTieCursor = 0
        val forcedHeroIndex = chooseFallbackHeroBlockIndex(filteredLines)
        val blocks = ArrayList<FumeBlock>(filteredLines.size)

        filteredLines.forEachIndexed { blockIndex, (line, sourceIndex) ->
            val variant = if (blockIndex == forcedHeroIndex) {
                FumeVariant.HERO
            } else {
                chooseNaturalBlockVariant(line, blockIndex, filteredLines.size)
            }

            val heroSpanColumns =
                if (variant == FumeVariant.HERO) minOf(columns, if (columns <= 1) 1 else 2) else 1
            val heroSpanWidth = if (heroSpanColumns > 1) {
                columnWidth * heroSpanColumns + gap * (heroSpanColumns - 1)
            } else {
                paperWidth
            }
            val blockWidth = if (variant == FumeVariant.HERO) {
                when {
                    heroSpanColumns == 1 -> paperWidth
                    columns == 2 -> columnWidth * 1.5 + gap * 0.5
                    else -> heroSpanWidth
                }
            } else {
                columnWidth
            }

            val innerWidth = maxOf(blockWidth - 0.0, 120.0)
            val graphemes = FumeMath.splitGraphemes(line.fullText)
            val (metrics, advances) = resolveBlockMetrics(
                text = line.fullText,
                variant = variant,
                innerWidth = innerWidth,
                lyricsFontScale = lyricsFontScale,
                densityScale = options.densityScale,
                tuning = tuning,
                measurer = measurer,
                graphemes = graphemes,
            )
            val blockGap = if (variant == FumeVariant.HERO) {
                maxOf(Math.round(metrics.lineHeight * 0.2), 6L).toDouble()
            } else {
                maxOf(Math.round(metrics.lineHeight * 0.08), 2L).toDouble()
            }
            val blockHeight = metrics.lineCount * metrics.lineHeight

            var x: Double
            var y: Double
            if (variant == FumeVariant.HERO) {
                // hero 块去找「最空的大位」：跨列时取占用高度最小的起始列。
                if (heroSpanColumns == 1) {
                    y = columnHeights.max()
                    x = horizontalMargin
                    columnHeights[0] = y + blockHeight + blockGap
                } else {
                    var bestHeight = Double.POSITIVE_INFINITY
                    var candidateStarts = ArrayList<Int>()
                    for (startColumn in 0..(columns - heroSpanColumns)) {
                        var coveredHeight = 0.0
                        for (columnIndex in startColumn until startColumn + heroSpanColumns) {
                            coveredHeight = maxOf(coveredHeight, columnHeights[columnIndex])
                        }
                        if (coveredHeight < bestHeight) {
                            bestHeight = coveredHeight
                            candidateStarts = arrayListOf(startColumn)
                        } else if (coveredHeight == bestHeight) {
                            candidateStarts.add(startColumn)
                        }
                    }
                    val targetStart = if (candidateStarts.isNotEmpty()) {
                        candidateStarts[heroPlacementTieCursor % candidateStarts.size]
                    } else {
                        0
                    }
                    heroPlacementTieCursor += 1
                    y = bestHeight
                    x = horizontalMargin + targetStart * (columnWidth + gap) +
                        maxOf((heroSpanWidth - blockWidth) * 0.5, 0.0)
                    for (columnIndex in targetStart until targetStart + heroSpanColumns) {
                        columnHeights[columnIndex] = y + blockHeight + blockGap
                    }
                }
            } else {
                // 正文块简单些：丢进当前最矮的列。
                var minHeight = columnHeights[0]
                var candidateColumns = ArrayList<Int>()
                candidateColumns.add(0)
                for (columnIndex in 1 until columns) {
                    val height = columnHeights[columnIndex]
                    if (height < minHeight) {
                        minHeight = height
                        candidateColumns = arrayListOf(columnIndex)
                    } else if (height == minHeight) {
                        candidateColumns.add(columnIndex)
                    }
                }
                val targetColumn = candidateColumns[bodyColumnTieCursor % candidateColumns.size]
                bodyColumnTieCursor += 1
                x = horizontalMargin + targetColumn * (columnWidth + gap)
                y = columnHeights[targetColumn]
                columnHeights[targetColumn] = y + blockHeight + blockGap
            }

            blocks.add(
                FumeBlock(
                    id = "fume-${line.startTime}-$sourceIndex",
                    sourceLineIndex = sourceIndex,
                    line = line,
                    variant = variant,
                    x = x,
                    y = y,
                    width = blockWidth,
                    height = blockHeight,
                    innerWidth = innerWidth,
                    fontPx = metrics.fontPx,
                    lineHeight = metrics.lineHeight,
                    graphemes = graphemes,
                    advances = advances,
                    segmentMetas = buildSegmentMetas(graphemes),
                    wordRanges = if (options.measure) emptyList() else buildWordRangesFromWords(line, graphemes),
                    wordRangeIndexByOffset = emptyList(),
                    colorRangeIndexByOffset = emptyList(),
                    renderLines = if (options.measure) {
                        emptyList()
                    } else {
                        buildRenderLines(
                            line = line,
                            variant = variant,
                            blockWidth = blockWidth,
                            graphemes = graphemes,
                            advances = advances,
                            innerWidth = innerWidth,
                            lineHeight = metrics.lineHeight,
                        )
                    },
                ),
            )
        }

        val articleHeight = maxOf(0.0, columnHeights.max()) + verticalMargin
        val paperBounds = FumePaperBounds(
            left = horizontalMargin,
            top = verticalMargin,
            right = horizontalMargin + paperWidth,
            bottom = maxOf(articleHeight - verticalMargin, verticalMargin),
        )

        if (options.measure) return FumeArticleLayout(
            width = paperWidth + horizontalMargin * 2,
            height = articleHeight,
            viewportHeight = options.viewportHeight,
            columns = columns,
            gap = gap,
            paperBounds = paperBounds,
            blocks = blocks,
            blockBySourceLineIndex = emptyMap(),
            chronologicalBlocks = emptyList(),
            firstRenderableStartTime = Double.POSITIVE_INFINITY,
            lastChronologicalRenderEndTime = Double.NEGATIVE_INFINITY,
        )

        val chronological = blocks.sortedBy { it.sourceLineIndex }
        val bySourceLine = chronological.associateBy { it.sourceLineIndex }
        val withRanges = chronological.map { block ->
            val ranges = buildWordRangesFromWords(block.line, block.graphemes)
            block.copy(
                wordRanges = ranges,
                wordRangeIndexByOffset = buildWordRangeIndexByOffset(block.graphemes.size, ranges, false),
                colorRangeIndexByOffset = buildWordRangeIndexByOffset(block.graphemes.size, ranges, true),
            )
        }
        val byId = withRanges.associateBy { it.id }

        return FumeArticleLayout(
            width = paperWidth + horizontalMargin * 2,
            height = articleHeight,
            viewportHeight = options.viewportHeight,
            columns = columns,
            gap = gap,
            paperBounds = paperBounds,
            blocks = withRanges,
            blockBySourceLineIndex = bySourceLine.mapValues { (_, block) -> byId.getValue(block.id) },
            chronologicalBlocks = withRanges,
            firstRenderableStartTime = withRanges.firstOrNull()?.line?.startTime ?: Double.POSITIVE_INFINITY,
            lastChronologicalRenderEndTime = withRanges.lastOrNull()
                ?.let { com.guangyue.core.lyrics.RenderHints.getLineRenderEndTime(it.line) }
                ?: Double.NEGATIVE_INFINITY,
        )
    }

    /** 对应 Folia buildSegmentMetas：整块文本连续像素段的字素区间。这里一段到底。 */
    private fun buildSegmentMetas(graphemes: List<String>): List<FumeSegmentMeta> =
        if (graphemes.isEmpty()) {
            emptyList()
        } else {
            listOf(FumeSegmentMeta(graphemeStart = 0, graphemeEnd = graphemes.size, graphemeCount = graphemes.size))
        }

    /** 对应 Folia renderLines 的构造（行内全局偏移、逐字素推进、片段坐标）。 */
    private fun buildRenderLines(
        line: Line,
        variant: FumeVariant,
        blockWidth: Double,
        graphemes: List<String>,
        advances: List<Double>,
        innerWidth: Double,
        lineHeight: Double,
    ): List<FumeRenderLine> {
        val wraps = wrapGraphemes(graphemes, advances, innerWidth)
        return wraps.mapIndexed { lineIndex, (start, end) ->
            val lineGraphemes = graphemes.subList(start, end)
            val glyphOffsets = ArrayList<Double>(lineGraphemes.size)
            var running = 0.0
            for (offset in start until end) {
                glyphOffsets.add(running)
                running += advances[offset]
            }
            FumeRenderLine(
                id = "${line.startTime}-$lineIndex",
                text = lineGraphemes.joinToString(""),
                start = start,
                end = end,
                graphemes = lineGraphemes,
                glyphOffsets = glyphOffsets,
                segments = buildRenderSegments(graphemes, advances, start, end),
                left = if (variant == FumeVariant.HERO) maxOf((blockWidth - running) * 0.08, 0.0) else 0.0,
                top = lineIndex * lineHeight,
                width = running,
            )
        }
    }
}