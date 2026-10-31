// 对应 Folia 源码：src/components/visualizer/songHandover.ts
package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongHandoverTest {

    private fun line(text: String, segments: List<String>? = null, auto: List<String>? = null) =
        Line(
            words = emptyList(), startTime = 0.0, endTime = 1.0, fullText = text,
            wordSegments = segments, autoWordSegments = auto,
        )

    @Test
    fun `分词哈希对相同输入稳定、对不同输入不同`() {
        val a = listOf(line("今天天气", segments = listOf("今天", "天气")))
        val b = listOf(line("今天天气", segments = listOf("今天", "天气")))
        val c = listOf(line("今天天气", segments = listOf("今", "天", "天", "气")))
        assertEquals(SongHandover.hashWordSegments(a), SongHandover.hashWordSegments(b))
        assertTrue(SongHandover.hashWordSegments(a) != SongHandover.hashWordSegments(c))
    }

    @Test
    fun `保存分词与自动分词同形状也不撞`() {
        val saved = listOf(line("今天天气", segments = listOf("今天", "天气")))
        val auto = listOf(line("今天天气", auto = listOf("今天", "天气")))
        assertTrue(SongHandover.hashWordSegments(saved) != SongHandover.hashWordSegments(auto))
    }

    @Test
    fun `无分词的行走不进哈希`() {
        assertEquals(
            SongHandover.hashWordSegments(listOf(line("今天天气"))),
            SongHandover.hashWordSegments(listOf(line("完全不同"))),
        )
    }

    @Test
    fun `歌词签名：空则空串，否则 行数 首行文本 哈希`() {
        assertEquals("", SongHandover.getLyricsSignature(emptyList()))
        val signature = SongHandover.getLyricsSignature(listOf(line("今天天气", segments = listOf("今天", "天气"))))
        assertTrue(signature.startsWith("1|今天天气|"))
    }

    @Test
    fun `同一首歌不同的词就地接住`() {
        val decision = SongHandover.decideSongCommit(
            SongCommitInput(seed = 1, committedSeed = 1, lyricsSignature = "new", committedSignature = "old", isCommittedInstrumental = false)
        )
        assertEquals(SongCommitDecision.Commit(false), decision)
    }

    @Test
    fun `同一首歌相同的词什么都不做`() {
        assertEquals(
            SongCommitDecision.Idle,
            SongHandover.decideSongCommit(
                SongCommitInput(seed = 1, committedSeed = 1, lyricsSignature = "same", committedSignature = "same", isCommittedInstrumental = false)
            ),
        )
    }

    @Test
    fun `同一首歌词消失：已确认纯音乐则 idle，否则 watch`() {
        assertEquals(
            SongCommitDecision.Idle,
            SongHandover.decideSongCommit(
                SongCommitInput(seed = 1, committedSeed = 1, lyricsSignature = "", committedSignature = "old", isCommittedInstrumental = true)
            ),
        )
        assertEquals(
            SongCommitDecision.Watch,
            SongHandover.decideSongCommit(
                SongCommitInput(seed = 1, committedSeed = 1, lyricsSignature = "", committedSignature = "old", isCommittedInstrumental = false)
            ),
        )
    }

    @Test
    fun `换歌：词不同才提交，否则继续等`() {
        assertEquals(
            SongCommitDecision.Commit(false),
            SongHandover.decideSongCommit(
                SongCommitInput(seed = 2, committedSeed = 1, lyricsSignature = "new", committedSignature = "old", isCommittedInstrumental = false)
            ),
        )
        assertEquals(
            SongCommitDecision.Watch,
            SongHandover.decideSongCommit(
                SongCommitInput(seed = 2, committedSeed = 1, lyricsSignature = "old", committedSignature = "old", isCommittedInstrumental = false)
            ),
        )
        assertEquals(
            SongCommitDecision.Watch,
            SongHandover.decideSongCommit(
                SongCommitInput(seed = 2, committedSeed = 1, lyricsSignature = "", committedSignature = "old", isCommittedInstrumental = false)
            ),
        )
    }

    @Test
    fun `纯音乐确认：要么播放过 2 秒，要么墙钟兜底 8 秒`() {
        assertEquals(
            true,
            SongHandover.isInstrumentalConfirmed(
                InstrumentalWatchState(sawPlaybackReset = true, playbackTime = 2.0, elapsedMs = 0L)
            ),
        )
        assertEquals(
            false,
            SongHandover.isInstrumentalConfirmed(
                InstrumentalWatchState(sawPlaybackReset = false, playbackTime = 30.0, elapsedMs = 100L)
            ),
        )
        assertEquals(
            true,
            SongHandover.isInstrumentalConfirmed(
                InstrumentalWatchState(sawPlaybackReset = false, playbackTime = 0.0, elapsedMs = 8000L)
            ),
        )
    }

    @Test
    fun `闸门：新歌带词立刻上场`() {
        val gate = SongHandover.Gate(seed = 1, lines = listOf(line("旧歌", segments = listOf("旧歌"))))
        val changed = gate.update(seed = 2, lines = listOf(line("新歌", segments = listOf("新歌"))), nowMs = 0L)
        assertTrue(changed)
        assertEquals(2, gate.committed.seed)
        assertFalse(gate.isWatching)
    }

    @Test
    fun `闸门：词还没到位就 watch，播放重置且过 2 秒则落定纯音乐`() {
        val gate = SongHandover.Gate(seed = 1, lines = listOf(line("旧歌", segments = listOf("旧歌"))))
        val changed = gate.update(seed = 2, lines = emptyList(), nowMs = 1000L)
        assertFalse(changed)
        assertTrue(gate.isWatching)

        assertFalse(gate.settleIfReady(seed = 2, playbackTime = 0.5, nowMs = 1200L))
        gate.observePlayback(0.2)
        assertTrue(gate.settleIfReady(seed = 2, playbackTime = 2.5, nowMs = 3000L))
        assertTrue(gate.committed.isInstrumental)
        assertEquals(2, gate.committed.seed)
        assertTrue(gate.committed.lines.isEmpty())
    }

    @Test
    fun `闸门：只有墙钟兜底也能落定（从不开始播放）`() {
        val gate = SongHandover.Gate(seed = 1, lines = emptyList())
        gate.update(seed = 2, lines = emptyList(), nowMs = 0L)
        assertTrue(gate.isWatching)
        assertFalse(gate.settleIfReady(seed = 2, playbackTime = 0.0, nowMs = 7999L))
        assertTrue(gate.settleIfReady(seed = 2, playbackTime = 0.0, nowMs = 8000L))
    }
}