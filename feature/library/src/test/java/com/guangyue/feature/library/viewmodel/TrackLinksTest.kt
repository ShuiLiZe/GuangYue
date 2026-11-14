package com.guangyue.feature.library.viewmodel

import com.guangyue.core.plugin.ArtistItem
import com.guangyue.core.plugin.MusicItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 曲目上的歌手 / 专辑链接（Folia useBravaisArtistLinks 规则）。 */
class TrackLinksTest {
    private val song = MusicItem(
        platform = "qq", id = "s1", title = "晴天", artist = "周杰伦", album = "叶惠美",
        artistId = "a1", albumId = "b1",
    )
    private val any: (String) -> String? = { p -> if (p == "qq") "plug" else null }

    @Test fun bothLinksWhenIdsAndPluginKnown() {
        val links = TrackLinks.of(song, any, any)
        assertEquals(listOf("查看歌手 · 周杰伦", "查看专辑 · 叶惠美"), links.map { it.label })
        assertEquals("artist:plug:qq:a1", links[0].tile.key)
        assertEquals("album:plug:qq:b1", links[1].tile.key)
    }

    @Test fun noLinkWithoutIdOrPlugin() {
        assertTrue(TrackLinks.of(song.copy(artistId = null, albumId = " "), any, any).isEmpty())
        assertTrue(TrackLinks.of(song, { null }, { null }).isEmpty())
        // 专辑名空：不给专辑链接。
        assertEquals(1, TrackLinks.of(song.copy(album = ""), any, any).size)
    }

    @Test fun ownPageHasNoSelfLink() {
        val onArtist = TrackLinks.of(song, any, any, ownKey = "artist:plug:qq:a1")
        assertEquals(listOf("查看专辑 · 叶惠美"), onArtist.map { it.label })
        val onAlbum = TrackLinks.of(song, any, any, ownKey = "album:plug:qq:b1")
        assertEquals(listOf("查看歌手 · 周杰伦"), onAlbum.map { it.label })
    }

    @Test fun keepsKnownArtistObject() {
        val full = ArtistItem(platform = "qq", id = "a1", name = "周杰伦", avatar = "https://x/a.jpg", description = "简介")
        ArtistTiles.tile("plug", full)
        val link = TrackLinks.of(song, any, any).first()
        assertEquals("https://x/a.jpg", link.tile.artwork)
        assertEquals("简介", ArtistTiles.knownOrNull(link.tile.key)?.description)
    }
}
