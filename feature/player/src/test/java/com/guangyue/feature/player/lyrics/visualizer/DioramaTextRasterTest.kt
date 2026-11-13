package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** dioramaTextRaster.ts / dioramaLineRasterCache.ts 的尺寸、摆位、方向与缓存逻辑（数值按 Folia 原式手算）。 */
class DioramaTextRasterTest {
    @Test
    fun unitGeometryMatchesFolia() {
        // em 128：留边 ceil(89.6)=90，高 ceil(179.2)=180 + 180。
        val g = dioramaUnitGeometry(100.2)
        assertEquals(101, g.advancePx); assertEquals(281, g.canvasWidthPx); assertEquals(360, g.canvasHeightPx)
        assertEquals(90.0, g.drawX, 0.0); assertEquals(180.0, g.drawY, 0.0)
        assertEquals(1, dioramaUnitGeometry(0.0).advancePx)
    }

    @Test
    fun lineGeometryShrinksPastMaxCanvas() {
        val short = dioramaLineGeometry { px -> 300.0 * px / 128 }
        assertEquals(128, short.fontPx); assertEquals(300, short.advancePx); assertEquals(352, short.canvasWidthPx); assertEquals(232, short.canvasHeightPx)
        // 5000 + 52 > 4096 → shrink (4096−52)/5000 → floor(128·0.8088)=103；留边仍按 128 算（26）。
        val long = dioramaLineGeometry { px -> 5000.0 * px / 128 }
        assertEquals(103, long.fontPx); assertEquals(4024, long.advancePx); assertEquals(4076, long.canvasWidthPx); assertEquals(197, long.canvasHeightPx)
        assertEquals(24, dioramaLineGeometry { px -> 90000.0 * px / 128 }.fontPx)
    }

    @Test
    fun unitSlotLtrAndRtl() {
        val k = DIORAMA_LINE_FONT_SIZE / DIORAMA_RASTER_FONT_PX
        val l = dioramaUnitSlot(400.0, 100.0, 50, 230, 360, false)
        assertEquals((-200.0 + 125.0) * k, l.centerX, 1e-12); assertEquals(230 * k, l.width, 1e-12); assertEquals(360 * k, l.height, 1e-12)
        assertEquals((200.0 - 125.0) * k, dioramaUnitSlot(400.0, 100.0, 50, 230, 360, true).centerX, 1e-12)
    }

    @Test
    fun textDirectionFirstStrongLetter() {
        assertFalse(isDioramaRtlText("你好"))
        assertFalse(isDioramaRtlText("12 hello"))
        assertTrue(isDioramaRtlText("12 שלום"))
        assertTrue(isDioramaRtlText("— مرحبا abc"))
        assertFalse(isDioramaRtlText("abc مرحبا"))
        assertFalse(isDioramaRtlText("123 !"))
    }

    @Test
    fun shadowBlurConversion() {
        assertEquals(((20.48 / 2 - 0.5) / 0.57735).toFloat(), dioramaShadowBlurToMaskRadius(20.48), 1e-4f)
        assertEquals(0.1f, dioramaShadowBlurToMaskRadius(0.5), 1e-6f)
    }

    private fun vl(i: Int, text: String) = DioramaVisibleLine(
        i, Line(emptyList(), 0.0, 0.0, text), DioramaTextPlacement(0.0, 0.0, 1.0, 0.0, 0.0, 0.0),
        DioramaVec(0.0, 0.0, 0.0), DioramaQuat(0.0, 0.0, 0.0, 1.0), false,
    )

    @Test
    fun lineCacheBudgetPruneAndFlush() {
        val released = ArrayList<String>()
        var built = 0
        val cache = DioramaLineRasterCache({ t: String -> built += 1; "R:$t" }, { released += it }, budget = 2)
        val vis = listOf(vl(0, "a"), vl(1, "b"), vl(2, "c"), vl(3, ""), vl(4, "e"))
        assertFalse(cache.sync(vis, 1, "f", 0))
        assertEquals(3, cache.pending) // 0、2、4（当前行 1 与空行 3 不要）
        assertTrue(cache.pump()); assertEquals(2, built); assertEquals(1, cache.pending)
        assertTrue(cache.pump()); assertEquals(3, built); assertFalse(cache.pump())
        assertEquals("R:e", cache.get(4))
        // 窗口前移：0 不要了 → 释放；1 变邻行 → 排队
        assertTrue(cache.sync(vis, 2, "f", 0)); assertEquals(listOf("R:c"), released)
        released.clear()
        assertEquals("R:a", cache.get(0))
        // 歌词版本变 → 全部作废
        assertTrue(cache.sync(vis, 2, "f", 1)); assertEquals(setOf("R:a", "R:e"), released.toSet())
        assertEquals(3, cache.pending)
        cache.ensureOutgoing(2, vis); assertEquals("R:c", cache.get(2))
        cache.disposeAll(); assertEquals(null, cache.get(2))
    }
}
