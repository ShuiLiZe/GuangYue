package com.guangyue.core.download

import com.guangyue.core.player.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSettingsTest {

    private class MapKv : DownloadKeyValue {
        val m = HashMap<String, Any>()
        override fun getString(key: String): String? = m[key] as? String
        override fun getBoolean(key: String, default: Boolean): Boolean = m[key] as? Boolean ?: default
        override fun getInt(key: String, default: Int): Int = m[key] as? Int ?: default
        override fun putString(key: String, value: String) { m[key] = value }
        override fun putBoolean(key: String, value: Boolean) { m[key] = value }
        override fun putInt(key: String, value: Int) { m[key] = value }
    }

    @Test
    fun defaultsWhenEmpty() {
        val c = DownloadSettings(MapKv()).current()
        assertEquals(Quality.HIGH, c.quality)
        assertTrue(c.saveLyric)
        assertTrue(c.lyricTranslation)
        assertEquals(3, c.concurrency)
    }

    @Test
    fun writesPersistAcrossInstances() {
        val kv = MapKv()
        DownloadSettings(kv).apply {
            setQuality(Quality.LOSSLESS)
            setSaveLyric(false)
            setLyricTranslation(false)
            setConcurrency(5)
        }
        // 模拟重启 App：新实例从同一份存储读
        val c = DownloadSettings(kv).current()
        assertEquals(Quality.LOSSLESS, c.quality)
        assertFalse(c.saveLyric)
        assertFalse(c.lyricTranslation)
        assertEquals(5, c.concurrency)
    }

    @Test
    fun concurrencyClampedAndBadValuesFallBack() {
        val kv = MapKv()
        val s = DownloadSettings(kv)
        s.setConcurrency(99); assertEquals(5, s.current().concurrency)
        s.setConcurrency(0); assertEquals(1, s.current().concurrency)
        kv.m[DownloadSettings.KEY_QUALITY] = "garbage"
        kv.m[DownloadSettings.KEY_CONCURRENCY] = -4
        val r = DownloadSettings(kv).current()
        assertEquals(Quality.HIGH, r.quality)
        assertEquals(1, r.concurrency)
    }

    @Test
    fun configFlowUpdatesImmediately() {
        val s = DownloadSettings(MapKv())
        s.setQuality(Quality.LOW)
        assertEquals(Quality.LOW, s.config.value.quality)
    }

    @Test
    fun qualityLadderOnlyGoesDown() {
        val l = DownloadPolicy.qualityLadder(Quality.LOSSLESS)
        assertEquals(Quality.LOSSLESS, l.first())
        assertEquals(Quality.LOW, l.last())
        assertTrue(l.zipWithNext().all { (a, b) -> a.order > b.order })
        assertTrue(l.none { it.order > Quality.LOSSLESS.order })
        assertEquals(listOf(Quality.LOW), DownloadPolicy.qualityLadder(Quality.LOW))
    }

    @Test
    fun qualityLadderFollowsDeclaredLevels() {
        // 插件只声明 128k/320k：要无损 → 先 320k 再 128k
        assertEquals(listOf(Quality.HIGH, Quality.LOW), DownloadPolicy.qualityLadder(Quality.LOSSLESS, listOf("128k", "320k")))
        // 声明的全都高于偏好：退到它的最低档（Folia audioQualityFor 的 levels[0]）
        assertEquals(listOf(Quality.LOSSLESS), DownloadPolicy.qualityLadder(Quality.LOW, listOf("flac", "flac24bit")))
    }

    @Test
    fun lrcTranslationInterleavedBySameTimestamp() {
        val lrc = "[ti:x]\n[00:01.00]你好\n[00:02.50]世界"
        val trans = "[00:01.0]hello\n[00:02.50]world\n[00:09.00]extra\nno-stamp"
        val withT = DownloadPolicy.lrcContent(lrc, trans, true).trimEnd().lines()
        assertEquals(listOf("[ti:x]", "[00:01.00]你好", "[00:01.0]hello", "[00:02.50]世界", "[00:02.50]world", "[00:09.00]extra"), withT)
        val without = DownloadPolicy.lrcContent(lrc, trans, false).trimEnd().lines()
        assertEquals(listOf("[ti:x]", "[00:01.00]你好", "[00:02.50]世界"), without)
        assertEquals("[00:01.00]a\n", DownloadPolicy.lrcContent("[00:01.00]a", null, true))
    }

    @Test
    fun pluginWiresUseDeclaredSpellingOrMusicFreeFour() {
        // 没声明：要无损 FLAC → 问 MusicFree 的 super，再往下
        assertEquals(listOf("super", "high", "standard", "low"), DownloadPolicy.pluginWires(Quality.LOSSLESS, emptyList()))
        assertEquals(listOf("low"), DownloadPolicy.pluginWires(Quality.LOW, emptyList()))
        // 声明了 128k/320k/flac：按它的写法问
        assertEquals(listOf("flac", "320k", "128k"), DownloadPolicy.pluginWires(Quality.HIRES, listOf("128k", "320k", "flac")))
    }
}
