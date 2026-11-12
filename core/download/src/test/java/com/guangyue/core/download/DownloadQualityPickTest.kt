package com.guangyue.core.download

import com.guangyue.core.player.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadQualityPickTest {

    @Test
    fun `没声明档位时列 MusicFree 四档，从高到低`() {
        assertEquals(
            listOf(Quality.SUPER, Quality.HIGH, Quality.STANDARD, Quality.LOW),
            DownloadQualityPick.options(emptyList(), null),
        )
        assertEquals(listOf("无损", "很高", "高品", "标准"), DownloadQualityPick.options().map(DownloadQualityPick::label))
    }

    @Test
    fun `插件回报的档位优先于 meta 声明，认不出的丢掉并去重`() {
        val fromPlayback = listOf("128k", "320k", "flac", "FLAC", "奇怪的档")
        val fromMeta = listOf("low", "standard", "high", "super", "hires")
        assertEquals(
            listOf(Quality.LOSSLESS, Quality.HIGH, Quality.LOW),
            DownloadQualityPick.options(fromPlayback, fromMeta),
        )
        // 回报为空 / 全认不出就退到 meta 声明
        assertEquals(
            listOf(Quality.HIRES, Quality.SUPER, Quality.HIGH, Quality.STANDARD, Quality.LOW),
            DownloadQualityPick.options(listOf("???"), fromMeta),
        )
    }

    @Test
    fun `默认档 = 点一下时实际先试的档位`() {
        val opts = listOf(Quality.LOSSLESS, Quality.HIGH, Quality.LOW)
        assertEquals(Quality.HIGH, DownloadQualityPick.defaultOf(opts, Quality.HIGH))
        // 设置是「无损」但源只到 320k：标在 320k
        assertEquals(Quality.HIGH, DownloadQualityPick.defaultOf(listOf(Quality.HIGH, Quality.LOW), Quality.SUPER))
        // 设置是「高品 192k」，源只有 128k / FLAC：往下取 128k
        assertEquals(Quality.LOW, DownloadQualityPick.defaultOf(opts, Quality.STANDARD))
        // 源最低档都高于设置：取最低档（同 DownloadPolicy.qualityLadder）
        assertEquals(Quality.HIGH, DownloadQualityPick.defaultOf(listOf(Quality.LOSSLESS, Quality.HIGH), Quality.LOW))
        assertNull(DownloadQualityPick.defaultOf(emptyList(), Quality.HIGH))
    }

    @Test
    fun `默认档与取链阶梯第一档一致`() {
        val declared = listOf("low", "high", "lossless")
        val opts = DownloadQualityPick.options(declared)
        for (q in Quality.entries) {
            assertEquals(q.name, DownloadPolicy.qualityLadder(q, declared).first(), DownloadQualityPick.defaultOf(opts, q))
        }
    }

    @Test
    fun `换音质丢掉播放直链，本地文件保留`() {
        val web = "https://cdn.example.com/a.mp3?sig=1"
        assertEquals(web, DownloadQualityPick.directUrlFor(Quality.HIGH, Quality.HIGH, web))
        assertNull(DownloadQualityPick.directUrlFor(Quality.LOSSLESS, Quality.HIGH, web))
        assertEquals("file:///sdcard/a.flac", DownloadQualityPick.directUrlFor(Quality.LOW, Quality.HIGH, "file:///sdcard/a.flac"))
        assertEquals("content://media/1", DownloadQualityPick.directUrlFor(Quality.LOW, Quality.HIGH, "content://media/1"))
        assertNull(DownloadQualityPick.directUrlFor(Quality.LOW, Quality.HIGH, null))
    }

    @Test
    fun `Toast 文案带档位名`() {
        assertEquals("已加入下载（无损 FLAC），进度在「我的 → 下载」", DownloadQualityPick.addedToast(Quality.LOSSLESS))
    }
}
