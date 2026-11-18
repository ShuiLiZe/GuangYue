package com.guangyue.core.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 登录态读歌单曲目失败时回落到未登录读法（用户 11-18：登录后点进歌单「就是不加载歌」）。 */
class QqFallbackTest {
    private val p = AccountPlaylist(id = "7777", name = "收藏", trackCount = 2)

    @Test fun credentialedResultUsedWhenItHasSongs() {
        var anon = 0
        val r = QqFallback.firstWithSongs<Int>(true, listOf("cred" to { listOf(1, 2) }, "anon" to { anon++; listOf(9) }))
        assertEquals(listOf(1, 2), r); assertEquals(0, anon)
    }

    @Test fun credentialedErrorFallsBackToAnonymous() {
        val r = QqFallback.firstWithSongs<Int>(true, listOf("cred" to { error("code 2000") }, "anon" to { listOf(3) }))
        assertEquals(listOf(3), r)
    }

    @Test fun expiredCredentialStillFallsBack() {
        val r = QqFallback.firstWithSongs<Int>(true, listOf("cred" to { throw AccountExpiredException() }, "anon" to { listOf(4) }))
        assertEquals(listOf(4), r)
    }

    @Test fun emptyButExpectedSongsKeepsTrying() {
        val r = QqFallback.firstWithSongs<Int>(true, listOf("cred" to { emptyList<Int>() }, "anon" to { listOf(5) }))
        assertEquals(listOf(5), r)
    }

    @Test fun reallyEmptyPlaylistReturnsEmpty() {
        assertTrue(QqFallback.firstWithSongs(false, listOf("cred" to { emptyList<Int>() }, "anon" to { listOf(5) })).isEmpty())
        assertTrue(QqFallback.firstWithSongs(true, listOf("cred" to { emptyList<Int>() }, "anon" to { error("x") })).isEmpty())
    }

    @Test fun allFailThrowsFirstError() {
        try {
            QqFallback.firstWithSongs<Int>(true, listOf("cred" to { throw AccountExpiredException() }, "anon" to { error("x") }))
            fail()
        } catch (e: AccountExpiredException) { }
    }

    @Test fun anonymousSongsBodyCarriesNoCredential() {
        val body = QqAccountApi.songsBody(null, p, 0, 50)
        assertTrue("authst" !in body)
        assertTrue("\"disstid\":7777" in body)
    }

    @Test fun cdInfoUrlOnlyForNumericIds() {
        assertTrue(QqAccountApi.cdInfoUrl(p)!!.contains("disstid=7777"))
        assertNull(QqAccountApi.cdInfoUrl(p.copy(id = "dir5")))
    }

    @Test fun parsesAnonymousCdInfoOldAndNewFormat() {
        val body = """jsonCallback({"code":0,"cdlist":[{"dissname":"收藏","total_song_num":2,"songlist":[
            {"songid":101,"songmid":"m1","songname":"旧格式","singer":[{"name":"甲"}],"albummid":"a1","albumname":"专辑一","strMediaMid":"md1","interval":200},
            {"id":102,"mid":"m2","title":"新格式","singer":[{"name":"乙"}],"album":{"mid":"a2","title":"专辑二"},"file":{"media_mid":"md2"},"interval":180}
        ]}]})"""
        val r = QqAccountApi.parseCdInfo(body) as QqRead.Ok
        assertEquals(2, r.value.songs.size)
        val a = r.value.songs[0]
        assertEquals("m1", a.songMid); assertEquals("a1", a.albumMid); assertEquals("md1", a.mediaMid); assertEquals("专辑一", a.album)
        val b = r.value.songs[1]
        assertEquals("md2", b.mediaMid); assertEquals("a2", b.albumMid)
        assertEquals(2, r.value.total)
    }

    @Test fun anonymousShellIsFailure() {
        assertTrue(QqAccountApi.parseCdInfo("""{"code":0,"cdlist":[{"songlist":[]}]}""") is QqRead.Failed)
        assertTrue(QqAccountApi.parseCdInfo("""{"code":0}""") is QqRead.Failed)
    }
}
