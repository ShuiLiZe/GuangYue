package com.guangyue.core.data.meta

import com.guangyue.core.data.meta.MetaMatch.Candidate
import com.guangyue.core.data.meta.MetaMatch.Query
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetaMatchTest {

    @Test fun `歌名歌手时长全对满分`() {
        val q = Query("晴天", "周杰伦", 269_000L)
        assertEquals(1.0, MetaMatch.score(q, Candidate("晴天", "周杰伦", 270_000L)), 1e-9)
    }

    @Test fun `时长正负 3 秒内都算对`() {
        val q = Query("晴天", "周杰伦", 269_000L)
        assertEquals(1.0, MetaMatch.durationScore(269_000L, 272_000L)!!, 1e-9)
        assertEquals(1.0, MetaMatch.durationScore(269_000L, 266_000L)!!, 1e-9)
        assertTrue(MetaMatch.durationScore(269_000L, 275_000L)!! < 1.0)
        assertEquals(0.0, MetaMatch.durationScore(269_000L, 300_000L)!!, 1e-9)
        assertNull(MetaMatch.durationScore(0L, 300_000L))
        assertNull(MetaMatch.durationScore(269_000L, null))
        // 时长差太多（Live / 剪辑版）不过线
        assertTrue(MetaMatch.score(q, Candidate("晴天 (Live)", "周杰伦", 300_000L)) < MetaMatch.THRESHOLD)
    }

    @Test fun `同名不同歌手不过线`() {
        val q = Query("晴天", "周杰伦", 269_000L)
        assertTrue(MetaMatch.score(q, Candidate("晴天", "孙燕姿", 269_000L)) < MetaMatch.THRESHOLD)
        assertTrue(MetaMatch.score(Query("晴天", "周杰伦"), Candidate("晴天", "孙燕姿")) < MetaMatch.THRESHOLD)
    }

    @Test fun `歌名对不上直接 0 分`() {
        assertEquals(0.0, MetaMatch.score(Query("晴天", "周杰伦"), Candidate("七里香", "周杰伦")), 1e-9)
    }

    @Test fun `括号版本说明和全角不影响歌名`() {
        assertEquals(1.0, MetaMatch.similarity("发如雪（Live）", "发如雪"), 1e-9)
        assertEquals(1.0, MetaMatch.similarity("Ｈｅｌｌｏ", "hello"), 1e-9)
        assertEquals(1.0, MetaMatch.similarity("《七里香》", "七里香"), 1e-9)
        assertTrue(MetaMatch.similarity("Hotel California", "Hotel California - 2013 Remaster") >= 0.7)
    }

    @Test fun `多歌手任一对上就算对`() {
        assertEquals(1.0, MetaMatch.artistSimilarity("周杰伦", "周杰伦/费玉清"), 1e-9)
        assertEquals(1.0, MetaMatch.artistSimilarity("Ed Sheeran", "Ed Sheeran feat. Justin Bieber"), 1e-9)
        assertEquals(0.0, MetaMatch.artistSimilarity("未知艺术家", "周杰伦"), 1e-9)
    }

    @Test fun `歌手不知道时只看歌名和时长`() {
        val q = Query("发如雪", "", 299_000L)
        assertEquals(1.0, MetaMatch.score(q, Candidate("发如雪", "周杰伦", 300_000L)), 1e-9)
    }

    @Test fun `文件名兜底解析 歌手 - 歌名`() {
        val q = MetaMatch.queryOf("周杰伦 - 晴天", "<unknown>", 269_000L)
        assertEquals("晴天", q.title)
        assertEquals("周杰伦", q.artist)
        assertEquals("晴天 周杰伦", q.keyword)
        val q2 = MetaMatch.queryOf("03. 周杰伦-晴天.mp3", "未知艺术家")
        assertEquals("晴天", q2.title)
        assertEquals("周杰伦", q2.artist)
        // 有歌手标签时不拆
        val q3 = MetaMatch.queryOf("Rock-n-Roll", "Someone")
        assertEquals("Rock-n-Roll", q3.title)
        assertTrue(q3.alternatives.isEmpty())
    }

    @Test fun `文件名是 歌名 - 歌手 时反向猜测也能对上`() {
        val q = MetaMatch.queryOf("Hotel California - Eagles", "")
        val i = MetaMatch.pick(q, listOf(Candidate("Desperado", "Eagles"), Candidate("Hotel California", "Eagles")))
        assertEquals(1, i)
    }

    @Test fun `挑最高分且过线的那条`() {
        val q = Query("晴天", "周杰伦", 269_000L)
        val cands = listOf(
            Candidate("晴天", "孙燕姿", 260_000L),
            Candidate("晴天 (Live)", "周杰伦", 330_000L),
            Candidate("晴天", "周杰伦", 270_000L),
            Candidate("晴天", "周杰伦", 271_000L),
        )
        assertEquals(2, MetaMatch.pick(q, cands))
        assertNull(MetaMatch.pick(q, listOf(Candidate("七里香", "周杰伦"), Candidate("晴天", "孙燕姿", 200_000L))))
        assertNull(MetaMatch.pick(q, emptyList()))
    }

    @Test fun `未知歌手判断`() {
        assertTrue(MetaMatch.isUnknownArtist(null))
        assertTrue(MetaMatch.isUnknownArtist("<unknown>"))
        assertTrue(MetaMatch.isUnknownArtist(" 未知艺术家 "))
        assertFalse(MetaMatch.isUnknownArtist("周杰伦"))
    }

    @Test fun `封面文件头`() {
        fun b(vararg x: Int) = ByteArray(16) { i -> (x.getOrElse(i) { 0 }).toByte() }
        assertTrue(MetaFiles.looksLikeImage(b(0xFF, 0xD8, 0xFF)))
        assertTrue(MetaFiles.looksLikeImage(b(0x89, 0x50, 0x4E, 0x47)))
        assertTrue(MetaFiles.looksLikeImage("RIFF\u0000\u0000\u0000\u0000WEBPxxxx".toByteArray()))
        assertFalse(MetaFiles.looksLikeImage("<html>not image</html>".toByteArray()))
        assertFalse(MetaFiles.looksLikeImage(ByteArray(4)))
    }
}
