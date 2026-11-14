package com.guangyue.music.ui.home

import com.guangyue.core.plugin.ArtistItem
import com.guangyue.feature.library.viewmodel.ArtistTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 类名带 CollectionLayout，CI「集合页布局单测」会一起跑。
class CollectionLayoutArtistTest {
    @Test fun `歌手块 key 与取回`() {
        val a = ArtistItem(platform = "qq", id = "0025NhlN2yWrP4", name = "周杰伦", avatar = "https://x/y.jpg", fans = "1000万")
        val t = ArtistTiles.tile("lx-1", a)
        assertEquals("artist:lx-1:qq:0025NhlN2yWrP4", t.key)
        assertEquals("周杰伦", t.title)
        assertEquals("1000万 粉丝", t.subtitle)
        assertEquals("https://x/y.jpg", t.artwork)
        assertTrue(ArtistTiles.isArtist(t.key))
        assertEquals("lx-1" to a, ArtistTiles.artistOf(t.key))
    }

    @Test fun `没登记过按 key 拆（歌手 id 里可以有冒号）`() {
        val (pid, a) = ArtistTiles.artistOf("artist:p2:kg:12:34", "某歌手")!!
        assertEquals("p2", pid)
        assertEquals("kg", a.platform)
        assertEquals("12:34", a.id)
        assertEquals("某歌手", a.name)
    }

    @Test fun `不是歌手 key`() {
        assertFalse(ArtistTiles.isArtist("playlist:3"))
        assertNull(ArtistTiles.artistOf("playlist:3"))
        assertNull(ArtistTiles.artistOf("artist:only"))
    }

    @Test fun `无粉丝数时副标题是歌手`() {
        assertEquals("歌手", ArtistTiles.tile("p", ArtistItem("qq", "1", "A")).subtitle)
    }
}
