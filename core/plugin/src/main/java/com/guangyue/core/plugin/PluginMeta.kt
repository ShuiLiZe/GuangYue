package com.guangyue.core.plugin

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * A plugin as the app sees it.
 *
 * MusicFree plugins are plain JS files with this shape:
 *
 * ```js
 * module.exports = {
 *   platform: 'demo',
 *   version: '0.0.1',
 *   srcUrl: 'https://example.com/plugin.js',
 *   cacheControl: 'no-cache',
 *   supportedSearchType: ['music', 'album', 'artist'],
 *   search(query, page, type) {},
 *   getMediaSource(musicItem, quality) {},
 *   getLyric(musicItem) {},
 *   ...
 * }
 * ```
 *
 * M1 keeps everything the runtime needs to install / enable / update a plugin, plus the list
 * of functions the plugin actually implements so calls can degrade gracefully.
 */
@kotlinx.serialization.Serializable
data class PluginMeta(
    val id: String,
    val platform: String,
    val name: String,
    val version: String,
    val srcUrl: String? = null,
    val author: String? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val supportedSearchType: List<String> = listOf(SearchType.MUSIC),
    val supportedQualities: List<String> = emptyList(),
    val cacheControl: String = "no-cache",
    val functions: Set<String> = emptySet(),
    /** where the code came from: a local file path or an http(s) url */
    val source: String = "",
    val installedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * 「启用」只表示这个音源出现在搜索页的可选列表里；0.0.7 起导入插件不再自动启用，
     * 由用户（或「只启用体检可用的」一键动作）明确打开。
     */
    val enabled: Boolean = false,
    val sizeBytes: Long = 0L,
    val error: String? = null,
    /** 用户在插件管理页拖动排序后的位置，越小越靠前。 */
    val sortOrder: Int = 0,
    /**
     * 体检结论，见 [PluginHealth]：
     * `unknown` 未体检 / `ok` 搜得到且取得到播放地址 / `search-failed` 搜索失败 /
     * `no-result` 搜索成功但没结果 / `no-media` 搜得到但拿不到播放地址。
     */
    val health: String = PluginHealth.UNKNOWN,
    /** 体检失败的一句话原因（成功时为空）。 */
    val healthNote: String? = null,
    /** 上次体检时间戳，0 = 从未体检。 */
    val healthAt: Long = 0L,
) {
    val hasSearch: Boolean get() = functions.isEmpty() || functions.contains("search")
    val hasAlbumSearch: Boolean get() = functions.isEmpty() || functions.contains("searchAlbum")
    val hasArtistSearch: Boolean get() = functions.isEmpty() || functions.contains("searchArtist")
    val hasMediaSource: Boolean get() = functions.contains("getMediaSource")
    val hasLyric: Boolean get() = functions.contains("getLyric")
    val hasMusicInfo: Boolean get() = functions.contains("getMusicInfo")
    val hasAlbumInfo: Boolean get() = functions.contains("getAlbumInfo")
    val hasArtistWorks: Boolean get() = functions.contains("getArtistWorks")
    val hasTopLists: Boolean get() = functions.contains("getTopLists")
    val hasTopListDetail: Boolean get() = functions.contains("getTopListDetail")
    val hasRecommend: Boolean get() = functions.contains("getRecommendSheetTags")
    val hasImportSheet: Boolean get() = functions.contains("importMusicSheet")
    val hasSearchSheet: Boolean get() = functions.contains("searchSheet")
    val hasSheetInfo: Boolean get() = functions.contains("getMusicSheetInfo")

    /** Display label: MusicFree shows `平台名` and the version. */
    val displayName: String get() = name.ifBlank { platform }

    /** 这个音源支持哪些搜索类型（单曲/专辑/歌手/歌单），交给搜索页决定显示哪几个 Tab。 */
    val searchTypes: List<String>
        get() = supportedSearchType
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(SearchType.MUSIC) }

    val supportsMusic: Boolean get() = SearchType.MUSIC in searchTypes
    val supportsAlbum: Boolean get() = SearchType.ALBUM in searchTypes
    val supportsArtist: Boolean get() = SearchType.ARTIST in searchTypes
    val supportsSheet: Boolean get() = SearchType.SHEET in searchTypes

    /** 体检通过（真搜得到且真取得到播放地址）的音源才允许被「只启用可用的」打开。 */
    val isHealthy: Boolean get() = health == PluginHealth.OK

    /** 管理页每行的一句健康状态（0.0.8 起固定四档：可用 / 只能搜索 / 取不到链 / 搜索失败）。 */
    val healthLabel: String
        get() = when (health) {
            PluginHealth.OK -> "可用"
            PluginHealth.NO_RESULT -> "只能搜索"
            PluginHealth.NO_MEDIA -> "取不到链"
            PluginHealth.SEARCH_FAILED -> "搜索失败"
            PluginHealth.UNSUPPORTED -> "只能搜索"
            else -> "未体检"
        }
}

/**
 * 插件体检结论（0.0.7 端到端复检）。
 *
 * 0.0.4 的体检只看「导入成功 + search 返回类型不是 null」，57 个里 43 个标为通过，
 * 但真实装机后大量音源搜不到东西 —— 因为返回可能是空数组、也可能是 Promise 没结算。
 * 0.0.7 起把「搜索出结果」与「getMediaSource 拿到播放地址」两步都跑过才叫 [OK]。
 */
object PluginHealth {
    const val UNKNOWN = "unknown"
    const val OK = "ok"
    const val SEARCH_FAILED = "search-failed"
    const val NO_RESULT = "no-result"
    const val NO_MEDIA = "no-media"
    const val UNSUPPORTED = "unsupported"
}

/** Every function name the runtime probes on install, in MusicFree order. */
object PluginFunctions {
    val ALL = listOf(
        // search
        "search", "searchAlbum", "searchArtist", "searchSheet",
        // playback / details
        "getMediaSource", "getLyric", "getMusicInfo", "getAlbumInfo", "getArtistWorks",
        "getMusicSheetInfo", "importMusicSheet",
        // lists & discovery
        "getTopLists", "getTopListDetail", "getRecommendSheetTags", "getRecommendSheetsByTag",
        // misc
        "getMusicComments", "getAlbumComments",
    )

    /** Functions that must exist for a plugin to be considered functional. */
    val REQUIRED = listOf("search")
}
