// 对应 Folia 文件：
//   src/components/visualizer/window/VisPlaygroundSettingsPanel.tsx「字体」页（previewCommonSettings）
//     字号 SliderRow min 0.85 / max 1.4 / step 0.05，刻度 90% 100% 110% 125%（VisPlayground FONT_SCALE_OPTIONS）；
//     整体透明度 SliderRow min 0.2 / max 1 / step 0.05（visualizer_opacity，VisualizerBackdrop 整层 opacity）。
//   字体 FontTiles（theme.fontStyle：sans / serif / mono）。✋ 广乐多一项「跟随样式」（空串，默认）：各舞台原本按样式定的字体；
//   ✋ 系统字体（custom）与字重未移植。
package com.guangyue.core.lyrics

import kotlin.math.roundToInt

/** 歌词样式窗口「字体」页里各样式共用的设置。 */
data class LyricCommonSettings(
    /** 歌词字号倍率（乘在系统字号倍率上）。 */
    val fontScale: Double = 1.0,
    /** 歌词画面（背景 + 舞台）整体不透明度。 */
    val opacity: Double = 1.0,
    /** 歌词字体：""（跟随样式）/ "sans" / "serif" / "mono"。 */
    val fontStyle: String = "",
) {
    companion object {
        val DEFAULT = LyricCommonSettings()
        const val FONT_SCALE_MIN = 0.85
        const val FONT_SCALE_MAX = 1.4
        const val OPACITY_MIN = 0.2
        const val OPACITY_MAX = 1.0
        /** Folia FONT_SCALE_OPTIONS：滑杆上的常用刻度。 */
        val FONT_SCALE_MARKS = listOf(0.9, 1.0, 1.1, 1.25)
        val FONT_STYLES = listOf("", "sans", "serif", "mono")

        /** 夹范围并按 0.05 取整（Folia step 0.05）；非数回默认。 */
        fun sanitize(s: LyricCommonSettings) = LyricCommonSettings(
            fontScale = snap(if (s.fontScale.isFinite()) s.fontScale.coerceIn(FONT_SCALE_MIN, FONT_SCALE_MAX) else 1.0),
            opacity = snap(if (s.opacity.isFinite()) s.opacity.coerceIn(OPACITY_MIN, OPACITY_MAX) else 1.0),
            fontStyle = if (s.fontStyle in FONT_STYLES) s.fontStyle else "",
        )

        private fun snap(v: Double) = (v / 0.05).roundToInt() * 0.05
    }
}
