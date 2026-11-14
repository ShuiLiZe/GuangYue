package com.guangyue.music.ui.home

import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 0.0.27 首页 3D 滑卡：数值逐条对 Folia Grid3DSlider / grid3DSliderModel / androidPhoneFit ⑤。 */
class GridSliderModelTest {

    @Test fun `居中卡放大到峰值、上移 6、层级 10`() {
        val l = GridSliderModel.look(0f, 1.2f)
        assertEquals(1.2f, l.scale, 1e-4f)
        assertEquals(1f, l.alpha, 1e-4f)
        assertEquals(-6f, l.liftDp, 1e-4f)
        assertEquals(10f, l.z, 1e-4f)
    }

    @Test fun `600 以外缩到 0_5、淡到 0_15、层级 1`() {
        listOf(600f, 900f, -1200f).forEach { d ->
            val l = GridSliderModel.look(d, 1.25f)
            assertEquals(0.5f, l.scale, 1e-4f)
            assertEquals(0.15f, l.alpha, 1e-4f)
            assertEquals(0f, l.liftDp, 1e-4f)
            assertEquals(1f, l.z, 1e-4f)
        }
    }

    @Test fun `半程线性插值`() {
        val l = GridSliderModel.look(300f, 1.2f)
        assertEquals(0.85f, l.scale, 1e-4f)
        assertEquals(0.575f, l.alpha, 1e-4f)
        assertEquals(-3f, l.liftDp, 1e-4f)
    }

    @Test fun `放大峰值：紧凑 1_12、拍立得 1_2、图片 1_25`() {
        assertEquals(1.12f, GridSliderModel.peakScale(compact = true, polaroid = true), 0f)
        assertEquals(1.2f, GridSliderModel.peakScale(compact = false, polaroid = true), 0f)
        assertEquals(1.25f, GridSliderModel.peakScale(compact = false, polaroid = false), 0f)
    }

    @Test fun `横屏紧凑档按高度反算，放大后整卡装得下`() {
        // 360dp 宽机横屏：滑条约 230dp 高。
        val c = GridSliderModel.coverSize(compact = true, widthDp = 800f, sliderHeightDp = 230f, polaroid = true)
        assertEquals(169f, c, 0f) // floor((230 − 40) / 1.12)
        assertTrue(c * 1.12f + 2 * GridSliderModel.COMPACT_PAD_DP <= 230f + 0.5f)
        // 再矮也不小于 64；高度量不到退回上限。
        assertEquals(64f, GridSliderModel.coverSize(true, 800f, 80f, true), 0f)
        assertEquals(208f, GridSliderModel.coverSize(true, 800f, 0f, true), 0f) // 横屏 800dp 宽 ≥ 768：上限 208
    }

    @Test fun `竖屏手机：上限 192，屏宽 62 以内`() {
        assertEquals(192f, GridSliderModel.coverSize(compact = false, widthDp = 360f, sliderHeightDp = 520f, polaroid = true), 0f)
        assertEquals(192f, GridSliderModel.coverSize(compact = false, widthDp = 412f, sliderHeightDp = 600f, polaroid = false), 0f)
        assertEquals(186f, GridSliderModel.coverSize(compact = false, widthDp = 300f, sliderHeightDp = 600f, polaroid = false), 0f)
        // 字体放大时拍立得文字区变高，卡宽让出来。
        val small = GridSliderModel.coverSize(false, 360f, 380f, true, fontScale = 1f)
        val big = GridSliderModel.coverSize(false, 360f, 380f, true, fontScale = 1.3f)
        assertTrue("$small > $big", small > big)
    }

    @Test fun `文案全中文：来源 · 类型`() {
        assertEquals("周杰伦 · 歌曲", GridSliderModel.secondary(HomeTile("t", "晴天", "周杰伦", kind = HomeTileKind.TRACK)))
        assertEquals("歌单", GridSliderModel.secondary(HomeTile("p", "我喜欢", " ", kind = HomeTileKind.PLAYLIST)))
        assertEquals("每日更新 · 电台", GridSliderModel.secondary(HomeTile("s", "电台", "每日更新", kind = HomeTileKind.STATION)))
    }

    @Test fun `点居中卡：集合进入、单曲播放`() {
        assertTrue(GridSliderModel.opensCollection(HomeTile("sheet:1", "a", "", kind = HomeTileKind.SHEET)))
        assertTrue(GridSliderModel.opensCollection(HomeTile("album:p:qq:1", "a", "", kind = HomeTileKind.TRACK)))
        assertFalse(GridSliderModel.opensCollection(HomeTile("track:1", "a", "", kind = HomeTileKind.TRACK)))
    }

    @Test fun `焦点夹回范围`() {
        assertEquals(0, GridSliderModel.clampFocus(5, 0))
        assertEquals(3, GridSliderModel.clampFocus(9, 4))
        assertEquals(0, GridSliderModel.clampFocus(-2, 4))
    }
}
