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
    val enabled: Boolean = true,
    val sizeBytes: Long = 0L,
    val error: String? = null,
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
