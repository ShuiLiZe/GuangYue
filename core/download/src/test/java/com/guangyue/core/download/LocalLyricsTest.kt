package com.guangyue.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLyricsTest {
    @Test fun sidecarSameBaseName() {
        assertEquals("/sdcard/Music/a b.lrc", LocalLyrics.sidecarPath("/sdcard/Music/a b.flac"))
        assertEquals("/sdcard/Music.d/song.lrc", LocalLyrics.sidecarPath("/sdcard/Music.d/song"))
    }

    @Test fun decodesUtf8BomAndGbk() {
        val utf = "\uFEFF[00:01.00]你好".toByteArray(Charsets.UTF_8)
        assertEquals("[00:01.00]你好", LocalLyrics.decode(utf))
        val gbk = "[00:01.00]你好".toByteArray(charset("GB18030"))
        assertEquals("[00:01.00]你好", LocalLyrics.decode(gbk))
    }

    @Test fun onlyTimedText() {
        assertTrue(LocalLyrics.isTimed("[01:02.33]x"))
        assertFalse(LocalLyrics.isTimed("just words"))
        assertFalse(LocalLyrics.isTimed(null))
    }
}
