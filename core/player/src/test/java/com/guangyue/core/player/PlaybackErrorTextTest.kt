package com.guangyue.core.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 回归测试：播放失败必须给出人能看懂的一句提示，且不能把插件原文吞掉。
 */
class PlaybackErrorTextTest {

    @Test
    fun `plugin wording in cause wins and is shown verbatim`() {
        val text = PlaybackErrorText.readable(
            message = "Source error",
            causeMessage = "这个音源取不到「七里香」的播放地址",
        )
        assertEquals("这个音源取不到「七里香」的播放地址", text)
    }

    @Test
    fun `plugin wording in message is kept when cause is empty`() {
        val text = PlaybackErrorText.readable("这个音源取不到「稻香」的播放地址")
        assertEquals("这个音源取不到「稻香」的播放地址", text)
    }

    @Test
    fun `bare source error becomes the no-source hint`() {
        assertEquals(PlaybackErrorText.NO_SOURCE, PlaybackErrorText.readable("Source error"))
        assertEquals(PlaybackErrorText.NO_SOURCE, PlaybackErrorText.readable("source error: -1"))
    }

    @Test
    fun `blank message becomes the generic retry hint`() {
        assertEquals(PlaybackErrorText.FAILED_HINT, PlaybackErrorText.readable(null))
        assertEquals(PlaybackErrorText.FAILED_HINT, PlaybackErrorText.readable("   "))
    }

    @Test
    fun `overlong raw message is collapsed instead of dumped`() {
        val long = "Unexpected ".repeat(20)
        assertEquals(PlaybackErrorText.FAILED_HINT, PlaybackErrorText.readable(long))
    }

    @Test
    fun `short readable message is prefixed`() {
        assertEquals("播放失败：Decoder init failed", PlaybackErrorText.readable("Decoder init failed"))
    }
}
