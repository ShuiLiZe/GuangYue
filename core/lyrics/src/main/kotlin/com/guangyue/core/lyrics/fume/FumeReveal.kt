// 对应 Folia 文件：src/components/visualizer/fume/fumeReveal.ts
//
// 「浮名」的逐字时机：每个词覆盖哪些字素、这一刻已经打印到第几个字素、
// 以及一行什么时候算「已读」并交棒给淡出。
//
// 主入口刻意与 Folia 同形（line + wordRanges + graphemeCount + time），
// 这样 Folia 的 test/unit/visualizer/fumeWordTiming.test.ts 可以几乎原样搬到 JVM 单测。
// [FumeBlock] 版本只是薄封装，给绘制层和镜头用。
package com.guangyue.core.lyrics.fume

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.RenderHints

object FumeReveal {
    /** 对应 resolveWordRevealProgress：一个词自己的打印进度（时长至少 0.08s）。 */
    fun wordRevealProgress(range: FumeWordRange, currentTime: Double): Double {
        if (range.word.endTime <= range.word.startTime) {
            return if (currentTime >= range.word.endTime) 1.0 else 0.0
        }
        val duration = maxOf(range.word.endTime - range.word.startTime, 0.08)
        return FumeMath.clamp((currentTime - range.word.startTime) / duration, 0.0, 1.0)
    }

    /** 对应 resolvePrintedGlyphsInRange：这个词这一刻打出了几个字素。 */
    fun printedGlyphsInRange(range: FumeWordRange, currentTime: Double): Int {
        val length = maxOf(range.end - range.start, 0)
        if (length == 0) return 0
        if (currentTime < range.word.startTime) return 0

        // 有逐音节时间轴时按音节时间数（中文歌大多走这条）；否则退回整词线性铺开。
        val timedGlyphCount = if (range.word.syllables.isNotEmpty()) {
            minOf(range.graphemeTimings.size, length)
        } else {
            0
        }
        if (timedGlyphCount > 0) {
            if (currentTime >= range.word.endTime) return length
            var printed = 0
            for (index in 0 until timedGlyphCount) {
                if (currentTime >= range.graphemeTimings[index].startTime) printed = index + 1
            }
            return FumeMath.clamp(printed.toDouble(), 0.0, length.toDouble()).toInt()
        }

        val progress = wordRevealProgress(range, currentTime)
        if (progress >= 1.0) return length
        return FumeMath.clamp(
            Math.floor(progress * length + 0.2),
            if (progress > 0.0) 1.0 else 0.0,
            length.toDouble(),
        ).toInt()
    }

    /** 对应 hasRevealCompletedByLineEnd：过了行尾时间就算整行打完。 */
    fun hasRevealCompletedByLineEnd(line: Line, currentTime: Double): Boolean = currentTime >= line.endTime

    /** 对应 resolveLinePassCutoffTime：整行「已读」的截止时刻不会被下一行推迟。 */
    fun linePassCutoffTime(line: Line, nextLineStartTime: Double?): Double {
        val renderEndTime = RenderHints.getLineRenderEndTime(line)
        if (nextLineStartTime == null || !nextLineStartTime.isFinite()) return renderEndTime
        return minOf(renderEndTime, nextLineStartTime)
    }

    /**
     * 对应 resolveVisualProgressWithCutoff：给一个「起点 + 名义时长」，
     * 用 cutoff 把实际终点提前，保证换行时进度不会拖过下一行开头。
     */
    fun visualProgressWithCutoff(startedAt: Double, duration: Double, currentTime: Double, cutoffTime: Double): Double {
        val nominalEndTime = startedAt + maxOf(duration, 0.001)
        val effectiveEndTime = maxOf(startedAt + 0.001, minOf(nominalEndTime, cutoffTime))
        return FumeMath.clamp(
            (currentTime - startedAt) / maxOf(effectiveEndTime - startedAt, 0.001),
            0.0,
            1.0,
        )
    }

    /** 对应 resolvePrintedGraphemeCount：整行已打印（离散）字素数，镜头 stepped 模式用它。 */
    fun printedGraphemeCount(
        line: Line,
        wordRanges: List<FumeWordRange>,
        graphemeCount: Int,
        currentTime: Double,
    ): Int {
        if (graphemeCount == 0) return 0
        if (currentTime < line.startTime) return 0
        if (hasRevealCompletedByLineEnd(line, currentTime)) return graphemeCount

        if (wordRanges.isEmpty()) {
            val duration = maxOf(line.endTime - line.startTime, 0.12)
            val progress = FumeMath.clamp((currentTime - line.startTime) / duration, 0.0, 1.0)
            return FumeMath.clamp(
                Math.floor(progress * graphemeCount + if (progress > 0.0) 1.0 else 0.0),
                0.0,
                graphemeCount.toDouble(),
            ).toInt()
        }

        // 取「连续前缀」的最大值：词时间轴可能互相重叠，只认能连成一片的部分。
        var printed = 0
        for (range in wordRanges) {
            val partial = printedGlyphsInRange(range, currentTime)
            printed = range.start + partial
            if (partial < range.end - range.start) {
                return FumeMath.clamp(printed.toDouble(), 0.0, graphemeCount.toDouble()).toInt()
            }
        }
        return FumeMath.clamp(printed.toDouble(), 0.0, graphemeCount.toDouble()).toInt()
    }

    /**
     * 对应 resolvePrintedGraphemeProgress：连续浮点版本，smooth 镜头用它，
     * 于是镜头能跟着笔迹「流」而不是一格一格跳。
     */
    fun printedGraphemeProgress(
        line: Line,
        wordRanges: List<FumeWordRange>,
        graphemeCount: Int,
        currentTime: Double,
    ): Double {
        if (graphemeCount == 0) return 0.0
        if (currentTime < line.startTime) return 0.0
        if (hasRevealCompletedByLineEnd(line, currentTime)) return graphemeCount.toDouble()

        if (wordRanges.isEmpty()) {
            val duration = maxOf(line.endTime - line.startTime, 0.12)
            val progress = FumeMath.clamp((currentTime - line.startTime) / duration, 0.0, 1.0)
            return FumeMath.clamp(progress * graphemeCount, 0.0, graphemeCount.toDouble())
        }

        var printed = 0.0
        for (range in wordRanges) {
            if (currentTime < range.word.startTime) {
                return FumeMath.clamp(printed, 0.0, graphemeCount.toDouble())
            }
            val progress = wordRevealProgress(range, currentTime)
            val length = maxOf(range.end - range.start, 0)
            printed = range.start + progress * length
            if (progress < 1.0) {
                return FumeMath.clamp(printed, 0.0, graphemeCount.toDouble())
            }
        }
        return FumeMath.clamp(printed, 0.0, graphemeCount.toDouble())
    }

    /** 块版本：绘制层与镜头直接拿 block 用。 */
    fun printedGraphemeCount(block: FumeBlock, currentTime: Double): Int =
        printedGraphemeCount(block.line, block.wordRanges, block.graphemes.size, currentTime)

    /** 块版本（连续）。 */
    fun printedGraphemeProgress(block: FumeBlock, currentTime: Double): Double =
        printedGraphemeProgress(block.line, block.wordRanges, block.graphemes.size, currentTime)
}