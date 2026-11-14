package com.guangyue.music.ui.home

import com.guangyue.core.plugin.AlbumItem
import com.guangyue.feature.library.home.HomeTileKind
import com.guangyue.feature.library.viewmodel.AlbumTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 类名带 CollectionLayout，CI「集合页布局单测」会一起跑。
class CollectionLayoutAlbumTest {
    @Test fun `专辑块 key、年份副标题与取回`() {
        val a = AlbumItem(platform = "qq", id = "003RMaRI1iFoYd", title = "叶惠美", artist = "周杰伦", artwork = "https://x/a.jpg", date = "2003-07-31")
        val t = AlbumTiles.tile("lx-1", a)
        assertEquals("album:lx-1:qq:003RMaRI1iFoYd", t.key)
        assertEquals("叶惠美", t.title)
        assertEquals("2003", t.subtitle)
        assertEquals(HomeTileKind.ALBUM, t.kind)
        assertTrue(AlbumTiles.isAlbum(t.key))
        assertEquals("lx-1" to a, AlbumTiles.albumOf(t.key))
    }

    @Test fun `年份解析`() {
        assertEquals("2001", AlbumTiles.yearOf("2001"))
        assertEquals("2001", AlbumTiles.yearOf("2001-11-01"))
        assertEquals("2003", AlbumTiles.yearOf("1059609600000"))
        assertEquals("2003", AlbumTiles.yearOf("1059609600"))
        assertNull(AlbumTiles.yearOf(""))
        assertNull(AlbumTiles.yearOf("未知"))
        assertEquals("专辑", AlbumTiles.tile("p", AlbumItem("qq", "1", "A", "B", null)).subtitle)
    }

    @Test fun `没登记过按 key 拆、不是专辑 key`() {
        val (pid, a) = AlbumTiles.albumOf("album:p2:kg:12:34", "某专辑")!!
        assertEquals("p2", pid); assertEquals("kg", a.platform); assertEquals("12:34", a.id); assertEquals("某专辑", a.title)
        assertFalse(AlbumTiles.isAlbum("artist:p:qq:1"))
        assertNull(AlbumTiles.albumOf("album:only"))
    }
}
