package com.guangyue.music.ui.home

import com.guangyue.feature.library.home.HomeTile

/**
 * 集合层过滤（Folia src/library/core/collection/collectionQuery.ts 的手机子集 + 缝里「过滤当前页」）。
 *
 * 只收窄当前墙 / 列表，不发请求。匹配文本 = 歌名 + 副标题（手机块的副标题就是「歌手 · 专辑」那一行），
 * 转小写后做子串包含；过滤词先 trim + 小写。过滤词为空返回 null（= 没有筛选），与「一首都没命中」（空表）区分开。
 * Folia 还拼别名 / 译名（SongResult.aliases / translatedNames），手机块没有这两项，未移。
 */
object CollectionFilter {
    fun normalize(query: String): String = query.trim().lowercase()

    fun searchText(tile: HomeTile): String = listOf(tile.title, tile.subtitle).filter { it.isNotEmpty() }.joinToString(" ").lowercase()

    fun matchIndexes(tiles: List<HomeTile>, query: String): List<Int>? {
        val needle = normalize(query)
        if (needle.isEmpty()) return null
        return tiles.indices.filter { searchText(tiles[it]).contains(needle) }
    }

    fun apply(tiles: List<HomeTile>, query: String): List<HomeTile> = matchIndexes(tiles, query)?.map { tiles[it] } ?: tiles

    /** Folia libraryBravaisCollection.matchCount「{{matches}} / {{total}}」。 */
    fun matchLabel(matches: Int, total: Int): String = "$matches / $total"
}
