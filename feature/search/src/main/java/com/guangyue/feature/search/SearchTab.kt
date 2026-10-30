package com.guangyue.feature.search

import com.guangyue.core.plugin.SearchType

/**
 * 搜索结果的四类。
 *
 * 0.0.7 起「只用用户选中的一个音源」，所以 Tab 只列该音源声明支持的分类
 * （见 `SearchViewModel.tabsOf`）。`label` 曾随旧页面一起放在 `SearchScreen.kt` 里；
 * U3 把旧页面换成 `FoliaSearchScreen.kt` 后，这层枚举被抽到这里，模型与界面都能引。
 *
 * 文案沿用 Folia zh-CN.ts 的 Tab 名（音乐/专辑/歌单/歌手那一组），手机版按「单曲」叫法保留旧页面的措辞。
 */
enum class SearchTab(val label: String, val type: String) {
    TRACKS("单曲", SearchType.MUSIC),
    ALBUMS("专辑", SearchType.ALBUM),
    SHEETS("歌单", SearchType.SHEET),
    ARTISTS("歌手", SearchType.ARTIST),
}
