// 对应 Folia 文件：src/components/visualizer/runtime.ts（行 1–148）
//
// 共享运行时：只回答「现在该关心哪一行」与「要不要预热下一行」，
// 让每个渲染器自己对布局保持主见。
package com.guangyue.core.lyrics

/** 预热窗口（按提前量，不是绝对时间戳）。对应 Folia 的 VisualizerPreheatWindow。 */
data class PrelatureWindow(
    val minLead: Double,
    val maxLead: Double,
) {
    companion object {
        /** 内核默认预热窗口：提前 0.4–2.0 秒开始准备。 */
        val DEFAULT = PrelatureWindow(minLead = 0.4, maxLead = 2.0)
    }
}

/** 运行时视图。对应 Folia useVisualizerRuntime 的返回值。 */
data class VisualizerRuntime(
    val currentTimeValue: Double,
    val activeLine: Line?,
    val recentCompletedLine: Line?,
    val upcomingLine: Line?,
    val nextLines: List<Line>,
)

/** 对应 Folia runtime.ts。 */
object Runtime {

    /** 行结束时间取值器；默认 line.endTime。 */
    fun defaultLineEndTime(line: Line): Double = line.endTime

    /**
     * 对应 Folia 行 33–52 的 getRecentCompletedLine。
     * 只在没有活动行时才有意义（主要是让字幕在两句之间的小空隙里继续显示上一句）。
     */
    fun getRecentCompletedLine(
        lines: List<Line>,
        currentLineIndex: Int,
        currentTime: Double,
        getLineEndTime: (Line) -> Double = ::defaultLineEndTime,
    ): Line? {
        if (currentLineIndex != -1 || lines.isEmpty()) return null
        for (i in lines.indices.reversed()) {
            if (currentTime > getLineEndTime(lines[i])) return lines[i]
        }
        return null
    }

    /** 对应 Folia 行 54–70 的 getUpcomingLine。 */
    fun getUpcomingLine(lines: List<Line>, currentLineIndex: Int, currentTime: Double): Line? {
        val activeLine = lines.getOrNull(currentLineIndex)
        if (activeLine != null) return lines.getOrNull(currentLineIndex + 1)
        return lines.firstOrNull { it.startTime > currentTime }
    }

    /** 对应 Folia 行 72–81 的 getUpcomingLines。 */
    fun getUpcomingLines(lines: List<Line>, currentLineIndex: Int, count: Int = 2): List<Line> {
        if (currentLineIndex < 0) return emptyList()
        return lines.subList(
            (currentLineIndex + 1).coerceAtMost(lines.size),
            (currentLineIndex + 1 + count).coerceAtMost(lines.size),
        )
    }

    /** 对应 Folia 行 83–91 的 getSubtitleNextLines。 */
    fun getSubtitleNextLines(
        lines: List<Line>,
        currentLineIndex: Int,
        recentCompletedLine: Line?,
        count: Int = 2,
    ): List<Line> {
        val base = if (currentLineIndex >= 0 || recentCompletedLine == null) {
            currentLineIndex
        } else {
            lines.indexOf(recentCompletedLine)
        }
        return getUpcomingLines(lines, base, count)
    }

    /** 对应 Folia 行 93–105 的 shouldPreheatLine（窗口按提前量定义）。 */
    fun shouldPreheatLine(line: Line?, currentTime: Double, window: PrelatureWindow): Boolean {
        if (line == null) return false
        val leadTime = line.startTime - currentTime
        return leadTime >= window.minLead && leadTime <= window.maxLead
    }

    /**
     * 对应 Folia 行 107–123 的 prepareActiveAndUpcoming：
     * 有活动行就准备它并顺带预热下一行，返回活动行的准备结果；没有活动行则只预热并返回 null。
     */
    fun <T> prepareActiveAndUpcoming(
        activeLine: Line?,
        upcomingLine: Line?,
        prepareLine: (Line?) -> T?,
    ): T? {
        if (activeLine == null) {
            prepareLine(upcomingLine)
            return null
        }
        val currentState = prepareLine(activeLine)
        prepareLine(upcomingLine)
        return currentState
    }

    /** 当前行下标（没有活动行时 -1）。与 LyricTimeline.indexAt 同规则。 */
    fun currentLineIndex(lines: List<Line>, currentTime: Double): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) / 2
            if (lines[mid].startTime <= currentTime) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /**
     * 对应 Folia useVisualizerRuntime 的返回值计算（不含 React 依赖）。
     * Folia 用 MotionValue.get() 拿当前时间；这里由调用方传入。
     */
    fun computeRuntime(
        lines: List<Line>,
        currentLineIndex: Int,
        currentTimeValue: Double,
        getLineEndTime: (Line) -> Double = ::defaultLineEndTime,
    ): VisualizerRuntime {
        val recentCompletedLine = getRecentCompletedLine(lines, currentLineIndex, currentTimeValue, getLineEndTime)
        val upcomingLine = getUpcomingLine(lines, currentLineIndex, currentTimeValue)
        val nextLines = getSubtitleNextLines(lines, currentLineIndex, recentCompletedLine)
        return VisualizerRuntime(
            currentTimeValue = currentTimeValue,
            activeLine = lines.getOrNull(currentLineIndex),
            recentCompletedLine = recentCompletedLine,
            upcomingLine = upcomingLine,
            nextLines = nextLines,
        )
    }
}