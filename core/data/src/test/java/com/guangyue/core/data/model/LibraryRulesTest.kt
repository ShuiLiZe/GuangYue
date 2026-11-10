package com.guangyue.core.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRulesTest {

    private fun online(key: String = "kw:1", fav: Boolean = false) = TrackEntity(
        key = key, title = "晴天", artist = "周杰伦", album = "叶惠美", artwork = "https://img/1.jpg",
        platform = "kw", songId = "1", rawJson = "{\"id\":\"1\"}", favorite = fav, addedAt = 100L,
    )

    private fun downloaded(key: String = "kw:1", uri: String = "content://media/external/audio/media/42") = TrackEntity(
        key = key, title = "晴天", artist = "周杰伦", artwork = "file:///data/covers/kw_1.jpg",
        platform = "kw", songId = "1", source = TrackEntity.SOURCE_LOCAL, localUri = uri,
        rawJson = "{\"id\":\"1\",\"rawLrc\":\"[00:01]x\"}",
    )

    @Test fun `重新写库不冲掉收藏和播放记录`() {
        val old = online(fav = true).copy(playCount = 3, lastPlayedAt = 900L)
        val merged = LibraryRules.merge(old, online().copy(title = "晴天（Live）", addedAt = 5000L))
        assertTrue(merged.favorite)
        assertEquals(3, merged.playCount)
        assertEquals(900L, merged.lastPlayedAt)
        assertEquals(100L, merged.addedAt)
        assertEquals("晴天（Live）", merged.title)
    }

    @Test fun `新行空字段不覆盖旧值`() {
        val merged = LibraryRules.merge(online(), online().copy(artwork = null, album = "", rawJson = null))
        assertEquals("https://img/1.jpg", merged.artwork)
        assertEquals("叶惠美", merged.album)
        assertEquals("{\"id\":\"1\"}", merged.rawJson)
    }

    @Test fun `在线行写进来不丢本地文件`() {
        val merged = LibraryRules.merge(downloaded().copy(favorite = true), online())
        assertEquals(TrackEntity.SOURCE_LOCAL, merged.source)
        assertEquals("content://media/external/audio/media/42", merged.localUri)
        assertTrue(merged.favorite)
    }

    @Test fun `库里没有这一行时收藏会建行`() {
        val row = LibraryRules.favorite(null, downloaded(), favorite = true, now = 777L)
        assertTrue(row.favorite)
        assertEquals("kw:1", row.key)
        assertEquals(777L, row.addedAt)
        assertEquals(TrackEntity.SOURCE_LOCAL, row.source)
    }

    @Test fun `取消收藏保留其余状态`() {
        val old = online(fav = true).copy(playCount = 2)
        val row = LibraryRules.favorite(old, online(), favorite = false, now = 777L)
        assertFalse(row.favorite)
        assertEquals(2, row.playCount)
        assertEquals(100L, row.addedAt)
    }

    @Test fun `重复收藏不改收藏时间`() {
        val old = online(fav = true).copy(addedAt = 300L)
        assertEquals(300L, LibraryRules.favorite(old, online(), true, 999L).addedAt)
    }

    @Test fun `记播放没有行先建行`() {
        val row = LibraryRules.played(null, online(), now = 123L)
        assertEquals(1, row.playCount)
        assertEquals(123L, row.lastPlayedAt)
        val again = LibraryRules.played(row.copy(favorite = true), online(), now = 456L)
        assertEquals(2, again.playCount)
        assertTrue(again.favorite)
    }

    @Test fun `MediaStore id 解析`() {
        assertEquals("42", LibraryRules.mediaStoreId("content://media/external/audio/media/42"))
        assertEquals("7", LibraryRules.mediaStoreId("content://media/external_primary/audio/media/7"))
        assertNull(LibraryRules.mediaStoreId("file:///sdcard/a.mp3"))
        assertNull(LibraryRules.mediaStoreId(null))
    }

    @Test fun `全部歌曲 下载版和手机里同一个文件只留下载版`() {
        val dl = downloaded()
        val sameFile = TrackEntity(key = "mediastore:42", title = "晴天", source = TrackEntity.SOURCE_LOCAL,
            localUri = "content://media/external_primary/audio/media/42")
        val other = TrackEntity(key = "mediastore:9", title = "七里香", source = TrackEntity.SOURCE_LOCAL,
            localUri = "content://media/external/audio/media/9")
        val all = LibraryRules.allSongs(listOf(dl), listOf(sameFile, other), favoriteKeys = setOf("mediastore:9"))
        assertEquals(listOf("kw:1", "mediastore:9"), all.map { it.key })
        assertTrue(all[1].favorite)
        assertFalse(all[0].favorite)
    }

    @Test fun `我喜欢里下载过的歌换成本地版且保留收藏`() {
        val row = online(fav = true)
        val p = LibraryRules.playable(row, downloaded())
        assertTrue(p.favorite)
        assertEquals("content://media/external/audio/media/42", p.localUri)
        assertTrue(p.rawJson!!.contains("rawLrc"))
        assertEquals("file:///data/covers/kw_1.jpg", p.artwork)
    }

    @Test fun `下载删掉后退回在线取链 手机本地歌不动`() {
        val stale = downloaded().copy(favorite = true)
        val p = LibraryRules.playable(stale, null)
        assertEquals(TrackEntity.SOURCE_PLUGIN, p.source)
        assertNull(p.localUri)
        val device = TrackEntity(key = "mediastore:9", title = "七里香", source = TrackEntity.SOURCE_LOCAL, localUri = "content://media/external/audio/media/9")
        assertEquals(device, LibraryRules.playable(device, null))
    }
}
