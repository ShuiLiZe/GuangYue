// 对应 Folia 文件：
//   src/utils/lyrics/subtitleOverlayLayout.ts（底部字幕的行布局：解析 / 行数切换 / 改某一行）
//   src/stores/useTypographySettingsStore.ts（subtitle_overlay_layout / 透明度 0.6 / 背景 true / 下一句模糊 true）
//   src/components/visualizer/window/VisPlaygroundSettingsPanel.tsx「字幕」页（隐藏底部字幕层、行数、每行内容、透明度 0.2–1、背景、模糊）
// 广乐歌词模型没有罗马音，行内容只有「当前句 / 下一句 / 翻译」三种。
package com.guangyue.core.lyrics

/** 底部字幕设置；layout 是 Folia 的串（"translation"、"current+next"…）拆成的行。 */
data class SubtitleSettings(
    val hidden: Boolean = false,
    val layout: List<String> = SubtitleLayout.DEFAULT,
    val opacity: Double = 0.6,
    val background: Boolean = true,
    val upcomingBlur: Boolean = true,
) {
    companion object {
        val DEFAULT = SubtitleSettings()
        const val OPACITY_MIN = 0.2
        const val OPACITY_MAX = 1.0
        fun sanitize(s: SubtitleSettings) = s.copy(
            layout = SubtitleLayout.parse(SubtitleLayout.encode(s.layout)) ?: SubtitleLayout.DEFAULT,
            opacity = if (s.opacity.isFinite()) s.opacity.coerceIn(OPACITY_MIN, OPACITY_MAX) else 0.6,
        )
    }
}

object SubtitleLayout {
    const val CURRENT = "current"
    const val NEXT = "next"
    const val TRANSLATION = "translation"

    /** 单行布局那一行可选的内容（Folia SUBTITLE_OVERLAY_SINGLE_ROWS，去掉罗马音）。 */
    val SINGLE_ROWS = listOf(CURRENT, TRANSLATION)
    /** 当前句之后每一行可选的内容（Folia SUBTITLE_OVERLAY_FOLLOW_ROWS，去掉罗马音）。 */
    val FOLLOW_ROWS = listOf(NEXT, TRANSLATION)
    /** Folia DEFAULT_SUBTITLE_OVERLAY_LAYOUT = 'translation'。 */
    val DEFAULT = listOf(TRANSLATION)

    fun encode(rows: List<String>): String = rows.joinToString("+")

    /** parseSubtitleOverlayLayout：单行只能是 SINGLE_ROWS；多行（≤3）第一行必须是当前句，其余是 FOLLOW_ROWS。 */
    fun parse(value: String?): List<String>? {
        if (value.isNullOrEmpty()) return null
        val rows = value.split("+")
        if (rows.size == 1) return if (rows[0] in SINGLE_ROWS) rows else null
        if (rows.size > 3 || rows[0] != CURRENT) return null
        return if (rows.drop(1).all { it in FOLLOW_ROWS }) rows else null
    }

    fun decode(value: String?): List<String> = parse(value) ?: DEFAULT

    /**
     * withSubtitleOverlayRowCount：降到单行时保留第一条翻译行（没有就当前句），所以「单行翻译 → 双行 → 单行」回到翻译；
     * 加行时补还没显示的内容。
     */
    fun withRowCount(layout: List<String>, count: Int): List<String> {
        val n = count.coerceIn(1, 3)
        if (n == 1) {
            if (layout.size == 1) return layout
            return listOf(layout.firstOrNull { it == TRANSLATION } ?: CURRENT)
        }
        val follows = (if (layout.size == 1) listOf(if (layout[0] == CURRENT) NEXT else layout[0]) else layout.drop(1)).toMutableList()
        while (follows.size < n - 1) {
            follows += listOf(TRANSLATION, NEXT).firstOrNull { it !in follows } ?: NEXT
        }
        return parse(encode(listOf(CURRENT) + follows.take(n - 1))) ?: DEFAULT
    }

    /** withSubtitleOverlayRow：多行时第 0 行固定是当前句，只有单行能改第 0 行。 */
    fun withRow(layout: List<String>, index: Int, source: String): List<String> {
        if (index !in layout.indices) return layout
        val next = layout.toMutableList().also { it[index] = source }
        return parse(encode(next)) ?: layout
    }
}
