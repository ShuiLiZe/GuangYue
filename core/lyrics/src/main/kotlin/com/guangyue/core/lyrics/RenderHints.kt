// 对应 Folia 文件：src/utils/lyrics/renderHints.ts（行 1–201）
//
// 行时长档位与渲染结束时间：v0 内核里所有样式共用的时序底座。
// 逐条数值照抄 Folia 源码，改动会破坏与 Folia 的帧级一致性。
package com.guangyue.core.lyrics

/** 行渲染提示的计算。对应 Folia renderHints.ts 的全部导出函数。 */
object RenderHints {

    /** Folia 行 26：MICRO_LINE_DURATION_THRESHOLD。 */
    const val MICRO_LINE_DURATION_THRESHOLD = 0.10

    /** Folia 行 27：SHORT_LINE_DURATION_THRESHOLD。 */
    const val SHORT_LINE_DURATION_THRESHOLD = 0.18

    /** Folia 行 28：MICRO_LINE_RENDER_FLOOR（微行最短渲染时间）。 */
    const val MICRO_LINE_RENDER_FLOOR = 0.067

    /** 对应 Folia 行 31 的私有 clamp。 */
    private fun clamp(value: Double, min: Double, max: Double): Double =
        minOf(maxOf(value, min), max)

    /** 对应 Folia 行 33–36：getLastWordEndTime，词表为空则退回 line.endTime。 */
    fun getLastWordEndTime(line: Line): Double =
        line.words.lastOrNull()?.endTime ?: line.endTime

    /** 对应 Folia 行 43–52。 */
    fun getTimingClass(rawDuration: Double): TimingClass = when {
        rawDuration < MICRO_LINE_DURATION_THRESHOLD -> TimingClass.MICRO
        rawDuration < SHORT_LINE_DURATION_THRESHOLD -> TimingClass.SHORT
        else -> TimingClass.NORMAL
    }

    /** 对应 Folia 行 38–41。 */
    fun getLineTransitionMode(timingClass: TimingClass): LineTransitionMode = when (timingClass) {
        TimingClass.MICRO -> LineTransitionMode.NONE
        TimingClass.SHORT -> LineTransitionMode.FAST
        TimingClass.NORMAL -> LineTransitionMode.NORMAL
    }

    /** 对应 Folia 行 54–57。 */
    fun getWordRevealMode(timingClass: TimingClass): WordRevealMode = when (timingClass) {
        TimingClass.MICRO -> WordRevealMode.INSTANT
        TimingClass.SHORT -> WordRevealMode.FAST
        TimingClass.NORMAL -> WordRevealMode.NORMAL
    }

    /** 对应 Folia 行 43–77：行入离场时序。 */
    fun getLineTransitionTiming(
        rawDuration: Double,
        lineTransitionMode: LineTransitionMode,
        wordRevealMode: WordRevealMode,
    ): LineTransitionTiming = when (lineTransitionMode) {
        LineTransitionMode.NONE -> LineTransitionTiming(0.0, 0.0, 0.0)

        LineTransitionMode.FAST -> LineTransitionTiming(
            enterDuration = clamp(rawDuration * 0.45, 0.045, 0.06),
            exitDuration = clamp(rawDuration * 0.22, 0.03, 0.04),
            linePassHold = if (wordRevealMode == WordRevealMode.INSTANT) 0.0 else 0.03,
        )

        LineTransitionMode.NORMAL -> LineTransitionTiming(
            enterDuration = minOf(0.42, maxOf(0.22, maxOf(rawDuration, 0.12) * 0.34)),
            exitDuration = minOf(0.32, maxOf(0.18, maxOf(rawDuration, 0.12) * 0.18)),
            linePassHold = if (wordRevealMode == WordRevealMode.INSTANT) 0.0 else 0.06,
        )
    }

    /**
     * 对应 Folia 行 97–113：渲染结束时间。
     * 这是视觉上这一行最长可留到屏幕上的时刻（留给 active/passed 的收尾），
     * 不是保证独立的时间线：后面的行可能更早开始，渲染器会把这段窗口截短。
     */
    fun buildLineRenderEndTime(
        line: Line,
        rawDuration: Double,
        lineTransitionMode: LineTransitionMode,
        wordRevealMode: WordRevealMode,
    ): Double {
        if (lineTransitionMode == LineTransitionMode.NONE) {
            return maxOf(line.endTime, line.startTime + MICRO_LINE_RENDER_FLOOR)
        }

        val timing = getLineTransitionTiming(rawDuration, lineTransitionMode, wordRevealMode)
        val linePassStart = maxOf(getLastWordEndTime(line), line.startTime) + timing.linePassHold
        val exitStart = if (lineTransitionMode == LineTransitionMode.FAST) {
            maxOf(line.startTime + timing.enterDuration + 0.01, linePassStart, line.endTime - timing.exitDuration)
        } else {
            maxOf(linePassStart, line.endTime - timing.exitDuration)
        }

        return maxOf(line.endTime, exitStart + timing.exitDuration)
    }

    /** 对应 Folia 行 115–131：默认签名的重载（只有起止时间，没有词）。 */
    fun buildLineRenderHints(startTime: Double, endTime: Double = startTime): LineRenderHints =
        buildLineRenderHints(
            Line(words = emptyList(), startTime = startTime, endTime = endTime, fullText = "")
        )

    /** 对应 Folia 行 128–143：由行现算渲染提示。 */
    fun buildLineRenderHints(line: Line): LineRenderHints {
        val rawDuration = maxOf(line.endTime - line.startTime, 0.0)
        val timingClass = getTimingClass(rawDuration)
        val lineTransitionMode = getLineTransitionMode(timingClass)
        val wordRevealMode = getWordRevealMode(timingClass)

        return LineRenderHints(
            rawDuration = rawDuration,
            timingClass = timingClass,
            renderEndTime = buildLineRenderEndTime(line, rawDuration, lineTransitionMode, wordRevealMode),
            lineTransitionMode = lineTransitionMode,
            wordRevealMode = wordRevealMode,
        )
    }

    /** 对应 Folia 行 145–151：已有 renderHints 就用它。 */
    fun getLineRenderHints(line: Line?): LineRenderHints? {
        if (line == null) return null
        return line.renderHints ?: buildLineRenderHints(line)
    }

    /** 对应 Folia 行 153–159：行渲染结束时间；空行是负无穷。 */
    fun getLineRenderEndTime(line: Line?): Double {
        if (line == null) return Double.NEGATIVE_INFINITY
        return getLineRenderHints(line)?.renderEndTime ?: line.endTime
    }

    /** 对应 Folia 行 161–172：逐字段比对现算的渲染提示是否已存在。 */
    private fun hasExpectedRenderHints(line: Line, expected: LineRenderHints): Boolean {
        val current = line.renderHints ?: return false
        return current.rawDuration == expected.rawDuration &&
            current.timingClass == expected.timingClass &&
            current.renderEndTime == expected.renderEndTime &&
            current.lineTransitionMode == expected.lineTransitionMode &&
            current.wordRevealMode == expected.wordRevealMode
    }

    /** 迁移结果。对应 Folia 的 MigrationResult<T>。 */
    data class MigrationResult<T>(val value: T, val changed: Boolean)

    /** 对应 Folia 行 174–194：补齐每行的 renderHints，未变则原样返回。 */
    fun migrateLyricLinesRenderHints(lines: List<Line>): MigrationResult<List<Line>> {
        var changed = false
        val next = lines.map { line ->
            val hints = buildLineRenderHints(line)
            if (hasExpectedRenderHints(line, hints)) {
                line
            } else {
                changed = true
                line.copy(renderHints = hints)
            }
        }
        return MigrationResult(if (changed) next else lines, changed)
    }

    /** 对应 Folia 行 196–198：annotate/ensure 都是同一迁移的取值。 */
    fun annotateLyricLines(lines: List<Line>): List<Line> = migrateLyricLinesRenderHints(lines).value

    /** 对应 Folia 行 200–201（ensureLyricLinesRenderHints）。 */
    fun ensureLyricLinesRenderHints(lines: List<Line>): List<Line> = migrateLyricLinesRenderHints(lines).value
}