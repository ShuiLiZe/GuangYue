package com.guangyue.core.plugin

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 插件返回值 → 模型对象的映射（0.0.7 从 [MusicPluginBridge] 里抽出来）。
 *
 * 抽出来的唯一原因是**可测**：0.0.4 的体检只断言「search 返回的不是 null」，于是 43/57 被判可用，
 * 装机后大量音源搜不到——真正容易错的是这一步映射（`{isEnd,data:[...]}`、`title/artist/artwork/id`
 * 字段名、`duration` 秒 vs 毫秒）。现在生产代码和端到端测试跑的是同一份实现。
 *
 * 映射规则（与 MusicFree 一致）：
 * - 返回值可以是数组本身，也可以是 `{ isEnd: Boolean, data: [...] }`；
 * - `isEnd` 缺失时按「这一页是不是空的」推断；
 * - type 决定落到 items / albums / artists 哪个桶。
 */
internal object SearchParser {

    fun parse(raw: JsonElement, platform: String, type: String): SearchResult {
        val o = raw.asObj()
        val arr = raw.asArr() ?: o?.get("data").asArr()
            ?: return SearchResult(isEnd = true)
        val isEnd = o?.get("isEnd").asBool() ?: arr.isEmpty()
        return when (type) {
            SearchType.ALBUM -> SearchResult(
                albums = arr.mapNotNull { AlbumItem.from(it.asObj(), platform) },
                isEnd = isEnd,
            )
            SearchType.ARTIST -> SearchResult(
                artists = arr.mapNotNull { ArtistItem.from(it.asObj(), platform) },
                isEnd = isEnd,
            )
            SearchType.SHEET -> SearchResult(
                albums = arr.mapNotNull { AlbumItem.from(it.asObj(), platform) },
                isEnd = isEnd,
            )
            else -> SearchResult(
                items = arr.mapNotNull { MusicItem.from(it.asObj(), platform) },
                isEnd = isEnd,
            )
        }
    }

    /** `getMediaSource` 的返回值 → [MediaSourceResult]；`url` 为空视为「取不到」。 */
    fun mediaSource(raw: JsonElement?): MediaSourceResult? {
        val o: JsonObject = raw?.asObj() ?: return null
        val url = o["url"].asStr()?.takeIf { it.isNotBlank() } ?: return null
        val headers = LinkedHashMap<String, String>()
        o["headers"].asObj()?.forEach { (k, v) -> v.asStr()?.let { headers[k] = it } }
        return MediaSourceResult(url, headers, o["mimeType"].asStr())
    }
}