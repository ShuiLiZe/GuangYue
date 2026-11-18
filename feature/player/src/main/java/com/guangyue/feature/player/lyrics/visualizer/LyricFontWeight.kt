// 对应 Folia：utils/fontStacks.ts resolveThemeFontWeight(theme, fallback) —— 歌词样式窗口「字体」页的字重，
// 设了就盖过各样式自己的默认字重（自动字重 = null，用各样式的 fallback）。只作用于歌词正文，不管底部字幕（Folia 字幕另有字重）。
package com.guangyue.feature.player.lyrics.visualizer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight

/** 播放页把 LyricCommonSettings.fontWeight 写进来（SideEffect），各舞台读。 */
internal object LyricFontWeight {
    var override by mutableStateOf<Int?>(null)

    /** resolveThemeFontWeight：有覆盖用覆盖，否则用该样式的默认。 */
    fun resolve(fallback: FontWeight): FontWeight = override?.let { FontWeight(it.coerceIn(1, 1000)) } ?: fallback
}
