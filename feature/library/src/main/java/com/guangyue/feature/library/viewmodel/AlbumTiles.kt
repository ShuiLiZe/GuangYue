package com.guangyue.feature.library.viewmodel

import com.guangyue.core.plugin.AlbumItem
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import java.util.concurrent.ConcurrentHashMap

/**
 * 歌手页的专辑块（Folia bravaisArtistModel.projectArtistItems：热门歌曲在前、专辑在后，混排成一面墙）。
 * 块的 key = `album:<插件id>:<平台>:<专辑id>`；点进去走集合层，曲目用插件 getAlbumInfo 取。
 * 插件要的原始专辑对象存在这里按 key 取回；进程重建后丢了就按平台 / id / 名字重建一个。
 */
object AlbumTiles {
    const val PREFIX = "album:"
    private val known = ConcurrentHashMap<String, Pair<String, AlbumItem>>()

    fun keyOf(pluginId: String, album: AlbumItem): String = "$PREFIX$pluginId:${album.platform}:${album.id}"

    fun isAlbum(key: String): Boolean = key.startsWith(PREFIX)

    /** 副标题照 Folia 写年份（albumYear）；不知道年份时写「专辑」。 */
    fun tile(pluginId: String, album: AlbumItem): HomeTile {
        val key = keyOf(pluginId, album)
        known[key] = pluginId to album
        return HomeTile(
            key = key,
            title = album.title,
            subtitle = yearOf(album.date) ?: "专辑",
            artwork = album.artwork,
            kind = HomeTileKind.ALBUM,
        )
    }

    /** 插件的 date 五花八门（2001-11-01、2001、毫秒时间戳）：取得出四位年份才用。 */
    fun yearOf(date: String?): String? {
        val s = date?.trim().orEmpty()
        if (s.isEmpty()) return null
        s.toLongOrNull()?.let { n ->
            if (n in 1000..2999) return n.toString()
            if (n > 100_000_000_000L) return java.time.Instant.ofEpochMilli(n).atZone(java.time.ZoneOffset.UTC).year.toString()
            if (n > 100_000_000L) return java.time.Instant.ofEpochSecond(n).atZone(java.time.ZoneOffset.UTC).year.toString()
            return null
        }
        return Regex("""^(1\d{3}|2\d{3})""").find(s)?.value
    }

    fun albumOf(key: String, fallbackName: String = ""): Pair<String, AlbumItem>? {
        known[key]?.let { return it }
        if (!isAlbum(key)) return null
        val parts = key.removePrefix(PREFIX).split(":", limit = 3)
        if (parts.size < 3 || parts.any { it.isEmpty() }) return null
        return parts[0] to AlbumItem(platform = parts[1], id = parts[2], title = fallbackName, artist = "", artwork = null)
    }
}
