package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleSettingsTest {
    @Test fun `解析照 Folia`() {
        assertEquals(listOf("translation"), SubtitleLayout.parse("translation"))
        assertEquals(listOf("current"), SubtitleLayout.parse("current"))
        assertNull(SubtitleLayout.parse("next"))
        assertEquals(listOf("current", "next", "translation"), SubtitleLayout.parse("current+next+translation"))
        assertNull(SubtitleLayout.parse("next+current"))
        assertNull(SubtitleLayout.parse("current+next+next+next"))
        assertNull(SubtitleLayout.parse("current+romanization"))
        assertEquals(SubtitleLayout.DEFAULT, SubtitleLayout.decode(null))
        assertEquals(SubtitleLayout.DEFAULT, SubtitleLayout.decode("乱写"))
    }

    @Test fun `行数切换：单行翻译 - 双行 - 单行回到翻译`() {
        val two = SubtitleLayout.withRowCount(listOf("translation"), 2)
        assertEquals(listOf("current", "translation"), two)
        assertEquals(listOf("translation"), SubtitleLayout.withRowCount(two, 1))
        assertEquals(listOf("current", "translation", "next"), SubtitleLayout.withRowCount(two, 3))
        assertEquals(listOf("current", "next"), SubtitleLayout.withRowCount(listOf("current"), 2))
        assertEquals(listOf("current"), SubtitleLayout.withRowCount(listOf("current", "next"), 1))
        assertEquals(listOf("current", "next"), SubtitleLayout.withRowCount(listOf("current", "next", "translation"), 2))
    }

    @Test fun `改某一行`() {
        assertEquals(listOf("current"), SubtitleLayout.withRow(listOf("translation"), 0, "current"))
        assertEquals(listOf("current", "next"), SubtitleLayout.withRow(listOf("current", "translation"), 1, "next"))
        // 多行的第 0 行改不了
        assertEquals(listOf("current", "next"), SubtitleLayout.withRow(listOf("current", "next"), 0, "translation"))
        assertEquals(listOf("current", "next"), SubtitleLayout.withRow(listOf("current", "next"), 5, "translation"))
    }

    @Test fun `透明度夹在 0点2 到 1`() {
        assertEquals(0.2, SubtitleSettings.sanitize(SubtitleSettings(opacity = 0.0)).opacity, 1e-9)
        assertEquals(1.0, SubtitleSettings.sanitize(SubtitleSettings(opacity = 3.0)).opacity, 1e-9)
        assertEquals(0.6, SubtitleSettings.DEFAULT.opacity, 1e-9)
    }
}
