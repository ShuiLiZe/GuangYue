package com.guangyue.feature.library.viewmodel

import com.guangyue.core.plugin.AlbumItem
import com.guangyue.core.plugin.ArtistItem
import com.guangyue.core.plugin.MusicItem
import com.guangyue.feature.library.home.HomeTile

/**
 * 2026-11-14：曲目上的「歌手 / 专辑」链接（Folia useBravaisArtistLinks + core/shared/trackLinks 的手机子集）。
 * Folia 在聚焦卡上把歌手名、专辑名做成链接，点了推入歌手页 / 专辑页；手机上集合层列表的曲目行长按弹出同样两项。
 * 规则照 Folia：
 *  - 只有能解析出目标 id 的才给链接（歌手要 artistId，专辑要 albumId + 专辑名），没有就不出这一项；
 *  - 要有能接住的音源（同平台、声明了 getArtistWorks / getAlbumInfo 的插件）；
 *  - 当前页自己（歌手页里的本歌手、专辑页里的本专辑）不给链接——点了也是原地，留着就是死链接。
 */
object TrackLinks {
    data class Link(val label: String, val tile: HomeTile)

    /**
     * @param artistPlugin 平台 → 能取歌手作品的插件 id（没有回 null）
     * @param albumPlugin 平台 → 能取专辑曲目的插件 id（没有回 null）
     * @param ownKey 当前集合层的块 key（歌手页 / 专辑页自己），与它相同的链接不出
     */
    fun of(
        item: MusicItem,
        artistPlugin: (String) -> String?,
        albumPlugin: (String) -> String?,
        ownKey: String? = null,
    ): List<Link> {
        val out = ArrayList<Link>(2)
        val artistId = item.artistId?.trim().orEmpty()
        val artistName = item.artist.trim()
        if (artistId.isNotEmpty() && artistName.isNotEmpty()) {
            artistPlugin(item.platform)?.let { pid ->
                val key = ArtistTiles.keyOf(pid, ArtistItem(platform = item.platform, id = artistId, name = artistName))
                if (key != ownKey) {
                    // 已登记过（搜索结果里点过，带头像 / 简介 / 原始对象）就沿用，不拿桩对象覆盖。
                    val known = ArtistTiles.knownOrNull(key)
                        ?: ArtistItem(platform = item.platform, id = artistId, name = artistName)
                    out += Link("查看歌手 · $artistName", ArtistTiles.tile(pid, known))
                }
            }
        }
        val albumId = item.albumId?.trim().orEmpty()
        val albumName = item.album?.trim().orEmpty()
        if (albumId.isNotEmpty() && albumName.isNotEmpty()) {
            albumPlugin(item.platform)?.let { pid ->
                val stub = AlbumItem(platform = item.platform, id = albumId, title = albumName, artist = item.displayArtist, artwork = item.artwork)
                val key = AlbumTiles.keyOf(pid, stub)
                if (key != ownKey) out += Link("查看专辑 · $albumName", AlbumTiles.tile(pid, AlbumTiles.knownOrNull(key) ?: stub))
            }
        }
        return out
    }
}
