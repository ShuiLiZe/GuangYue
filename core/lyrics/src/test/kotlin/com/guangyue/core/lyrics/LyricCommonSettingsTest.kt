package com.guangyue.core.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricCommonSettingsTest {
    @Test fun `字号与透明度夹范围、按 0点05 取整`() {
        val s = LyricCommonSettings.sanitize(LyricCommonSettings(fontScale = 2.0, opacity = 0.0))
        assertEquals(1.4, s.fontScale, 1e-9)
        assertEquals(0.2, s.opacity, 1e-9)
        val t = LyricCommonSettings.sanitize(LyricCommonSettings(fontScale = 1.12, opacity = 0.73))
        assertEquals(1.1, t.fontScale, 1e-9)
        assertEquals(0.75, t.opacity, 1e-9)
        val n = LyricCommonSettings.sanitize(LyricCommonSettings(fontScale = Double.NaN, opacity = Double.NaN))
        assertEquals(LyricCommonSettings.DEFAULT, n)
    }
}
