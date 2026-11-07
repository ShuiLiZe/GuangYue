package com.guangyue.feature.player.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V1b-2：锁住样式弹层滑杆的几何 —— 「滑块中心 = 填充末端」（0% / 50% / 100% 三处必须对齐）。
 *
 * 缺陷现场（`docs/ui-v0/v1a-style-sheet.png`）：呼吸浮动 1.00x 填充到一半、滑块却贴最左；
 * 词距 0.70x 填充约 35%、滑块也贴最左。根因是滑钮位置用了 `padding = 8dp × fraction × 2`
 * （= 8dp × value），与填充公式 `(宽度 − 2×8dp) × fraction + 8dp` 不同源，也不减滑钮半径。
 *
 * 这些用例用 360dp 手机的实际像素宽度（320dpi：360dp = 900px，左右各 20dp 内边距后热区 320dp = 800px，
 * inset 8dp = 20px）做断言。
 */
class FoliaSliderGeometryTest {

    // 360dp 屏、左右各 20dp 内边距 → 拖动热区 320dp = 800px；inset 8dp = 20px。
    private val trackWidth = 800f
    private val inset = 20f
    private val thumbSize = 40f // 16dp @320dpi

    private fun fractionOf(value: Float) = value.coerceIn(0f, 2f) / 2f

    @Test
    fun `fill end matches thumb centre at every step`() {
        var value = 0f
        while (value <= 2.0001f) {
            val fraction = fractionOf(value)
            val fillEnd = FoliaSliderGeometry.fillEndPx(trackWidth, inset, fraction)
            val left = FoliaSliderGeometry.thumbLeftPx(trackWidth, inset, fraction, thumbSize)
            val centre = left + thumbSize * 0.5f
            assertEquals("value=$value 滑块中心必须落在填充末端", fillEnd, centre, 1e-3f)
            value += 0.1f
        }
    }

    @Test
    fun `zero half and full value align on the same expression`() {
        // V1b-2 验收口径：0% / 50% / 100% 三处直接用像素值断言。
        assertEquals("0% 填充末端 = inset", inset, FoliaSliderGeometry.fillEndPx(trackWidth, inset, 0f), 1e-3f)
        assertEquals(
            "50% 填充末端 = 热区正中",
            trackWidth / 2f,
            FoliaSliderGeometry.fillEndPx(trackWidth, inset, 0.5f),
            1e-3f,
        )
        assertEquals(
            "100% 填充末端 = 宽度 − inset",
            trackWidth - inset,
            FoliaSliderGeometry.fillEndPx(trackWidth, inset, 1f),
            1e-3f,
        )
        for (fraction in listOf(0f, 0.5f, 1f)) {
            val centre = FoliaSliderGeometry.thumbLeftPx(trackWidth, inset, fraction, thumbSize) + thumbSize / 2f
            assertEquals("fraction=$fraction 中心 = 末端", FoliaSliderGeometry.fillEndPx(trackWidth, inset, fraction), centre, 1e-3f)
        }
    }

    @Test
    fun `half value centres the thumb on the track instead of hugging the left edge`() {
        // 呼吸浮动 1.00x：填充到一半，滑块中心必须在轨道正中间（旧代码只偏左 8dp）。
        val fraction = fractionOf(1.00f)
        val fillEnd = FoliaSliderGeometry.fillEndPx(trackWidth, inset, fraction)
        assertEquals(inset + (trackWidth - inset * 2f) * 0.5f, fillEnd, 1e-3f)
        val left = FoliaSliderGeometry.thumbLeftPx(trackWidth, inset, fraction, thumbSize)
        assertEquals("滑块左缘应为中心 − 半径", fillEnd - thumbSize * 0.5f, left, 1e-3f)
        assertTrue("滑块不能贴在最左", left > trackWidth * 0.4f)
    }

    @Test
    fun `thirteen_fifths percent value puts the thumb well past the left edge`() {
        // 词距 0.70x → fraction 0.35 → 填充 35%，滑块中心也应到 35%。
        val fraction = fractionOf(0.70f)
        val fillEnd = FoliaSliderGeometry.fillEndPx(trackWidth, inset, fraction)
        val expected = inset + (trackWidth - inset * 2f) * 0.35f
        assertEquals(expected, fillEnd, 1e-3f)
        val left = FoliaSliderGeometry.thumbLeftPx(trackWidth, inset, fraction, thumbSize)
        assertTrue("0.70x 时滑块不能还在最左", left > trackWidth * 0.25f)
    }

    @Test
    fun `thumb stays inside the track at both ends`() {
        val zero = FoliaSliderGeometry.thumbLeftPx(trackWidth, inset, 0f, thumbSize)
        assertEquals("0 值时滑块中心 = inset", inset - thumbSize * 0.5f, zero, 1e-3f)
        val full = FoliaSliderGeometry.thumbLeftPx(trackWidth, inset, 1f, thumbSize)
        assertEquals("满值滑块中心 = 宽度 − inset", trackWidth - inset - thumbSize * 0.5f, full, 1e-3f)
    }

    @Test
    fun `growing thumb on press keeps the centre fixed`() {
        val fraction = fractionOf(1.20f)
        val small = FoliaSliderGeometry.thumbLeftPx(trackWidth, inset, fraction, 40f)
        val big = FoliaSliderGeometry.thumbLeftPx(trackWidth, inset, fraction, 45f)
        assertEquals("按下放大只改半径，中心不动", small + 20f, big + 22.5f, 1e-3f)
    }

    @Test
    fun `tap and drag positions round trip through the same mapping`() {
        // 点按/拖动落点 → 值 → 再算回填充末端，必须回到同一个像素（±半个 step）。
        for (value in listOf(0f, 0.5f, 0.7f, 1f, 1.6f, 2f)) {
            val x = FoliaSliderGeometry.fillEndPx(trackWidth, inset, fractionOf(value))
            val mapped = FoliaSliderGeometry.valueForPositionPx(x, trackWidth, inset)
            assertEquals("落点 $x 应映射回 $value", value, mapped, 1e-3f)
        }
        // 两端越界钳位。
        assertEquals(0f, FoliaSliderGeometry.valueForPositionPx(-100f, trackWidth, inset), 1e-3f)
        assertEquals(2f, FoliaSliderGeometry.valueForPositionPx(trackWidth + 100f, trackWidth, inset), 1e-3f)
    }

    @Test
    fun `zero width does not divide by zero`() {
        val left = FoliaSliderGeometry.thumbLeftPx(0f, inset, 0.5f, thumbSize)
        assertTrue(left.isFinite())
        assertTrue(FoliaSliderGeometry.valueForPositionPx(10f, 0f, inset).isFinite())
    }

    // V1b-4：通用值域（浮名参数的 0.55..1.85 / 0.05、0.82..1.32 / 0.02、0..1 / 0.05）。
    @Test
    fun genericRangeSnapsFromMinAndHitsBothEnds() {
        val w = 400f
        val inset = 8f
        assertEquals(0.55f, FoliaSliderGeometry.valueForPositionPx(0f, w, inset, 0.55f, 1.85f, 0.05f), 1e-4f)
        assertEquals(1.85f, FoliaSliderGeometry.valueForPositionPx(w, w, inset, 0.55f, 1.85f, 0.05f), 1e-4f)
        // 正中 = 1.20（0.55 + 0.65），不是浮点尾巴 1.2000001
        assertEquals(1.2f, FoliaSliderGeometry.valueForPositionPx(w / 2f, w, inset, 0.55f, 1.85f, 0.05f), 1e-4f)
        assertEquals(1.08f, FoliaSliderGeometry.valueForPositionPx(inset + (w - 2 * inset) * 0.52f, w, inset, 0.82f, 1.32f, 0.02f), 1e-4f)
        assertEquals(0.5f, FoliaSliderGeometry.valueForPositionPx(w / 2f, w, inset, 0f, 1f, 0.05f), 1e-4f)
    }

    @Test
    fun fractionOfMapsRangeAndClamps() {
        assertEquals(0f, FoliaSliderGeometry.fractionOf(0.55f, 0.55f, 1.85f), 1e-6f)
        assertEquals(1f, FoliaSliderGeometry.fractionOf(9f, 0.55f, 1.85f), 1e-6f)
        assertEquals(0.5f, FoliaSliderGeometry.fractionOf(1.2f, 0.55f, 1.85f), 1e-5f)
        assertEquals(0f, FoliaSliderGeometry.fractionOf(1f, 1f, 1f), 1e-6f)
    }

    @Test
    fun formatMatchesFoliaPanelRows() {
        assertEquals("50%", formatPercent(0.5f))
        assertEquals("1.20x", formatTimes(1.2f))
    }
}
