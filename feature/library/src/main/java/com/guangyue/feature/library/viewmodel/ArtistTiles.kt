package com.guangyue.feature.library.viewmodel

import com.guangyue.core.plugin.ArtistItem
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import java.util.concurrent.ConcurrentHashMap

/**
 * 歌手页（Folia BravaisArtist 的手机子集）：歌手走集合层，块的 key = `artist:<插件id>:<平台>:<歌手id>`。
 * 插件要的原始歌手对象（MusicFree 契约：同一个对象回传给插件）存在这里，按 key 取回；进程重建后丢了就按
 * 平台 / id / 名字重建一个（getArtistWorks 会自己拼 payload）。
 */
object ArtistTiles {
    const val PREFIX = "artist:"
    private val known = ConcurrentHashMap<String, Pair<String, ArtistItem>>()

    fun keyOf(pluginId: String, artist: ArtistItem): String = "$PREFIX$pluginId:${artist.platform}:${artist.id}"

    fun isArtist(key: String): Boolean = key.startsWith(PREFIX)

    fun tile(pluginId: String, artist: ArtistItem): HomeTile {
        val key = keyOf(pluginId, artist)
        known[key] = pluginId to artist
        return HomeTile(
            key = key,
            title = artist.name,
            subtitle = artist.fans?.takeIf { it.isNotBlank() }?.let { "$it 粉丝" } ?: "歌手",
            artwork = artist.avatar,
            kind = HomeTileKind.PLAYLIST,
        )
    }

    /** key → (插件 id, 歌手)。没登记过时从 key 拆出插件 / 平台 / id，名字用 [fallbackName]。 */
    fun artistOf(key: String, fallbackName: String = ""): Pair<String, ArtistItem>? {
        known[key]?.let { return it }
        if (!isArtist(key)) return null
        val parts = key.removePrefix(PREFIX).split(":", limit = 3)
        if (parts.size < 3 || parts.any { it.isEmpty() }) return null
        return parts[0] to ArtistItem(platform = parts[1], id = parts[2], name = fallbackName)
    }
}
