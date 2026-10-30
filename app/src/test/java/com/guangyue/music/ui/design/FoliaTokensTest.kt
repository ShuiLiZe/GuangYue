package com.guangyue.music.ui.design

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * U1 token 的纯 JVM 断言：数值必须和 Folia 源码对得上。
 * 出处见各 assert 的注释；这些测试不需要 Robolectric，跑得很快。
 */
class FoliaTokensTest {

    // ---- 弹簧换算 -----------------------------------------------------------------------

    /** Carousel3D.tsx:42 `{stiffness: 300, damping: 30}` → ζ = 30 / (2·√300) ≈ 0.866。 */
    @Test
    fun carouselSpringRatio() {
        val zeta = FoliaMotion.dampingRatio(damping = 30f, stiffness = 300f)
        assertEquals(0.8660f, zeta, 0.001f)
    }

    /** FloatingPlayerControls.tsx:38 `{stiffness: 280, damping: 28}` → ζ = 28 / (2·√280) ≈ 0.837。 */
    @Test
    fun playerSpringRatio() {
        val zeta = FoliaMotion.dampingRatio(damping = 28f, stiffness = 280f)
        assertEquals(0.8366f, zeta, 0.001f)
    }

    /** 按压缩放取自 Button.tsx:11 `active:scale-[0.97]`。 */
    @Test
    fun pressScaleMatchesSource() {
        assertEquals(0.97f, FoliaMotion.PRESS_SCALE, 0f)
    }

    /** 滑条拇指按下放大 1.15（kit.css:26-32）。 */
    @Test
    fun thumbActiveScaleMatchesSource() {
        assertEquals(1.15f, FoliaMotion.THUMB_ACTIVE_SCALE, 0f)
    }

    // ---- 颜色 ---------------------------------------------------------------------------

    /** baseThemes.ts:9-28 的两套页面底色与文字色。 */
    @Test
    fun pageColorsMatchBaseThemes() {
        assertEquals("09090b", hex(FoliaDarkColors.page))
        assertEquals("f4f4f5", hex(FoliaDarkColors.ink))
        assertEquals("f5f5f4", hex(FoliaLightColors.page))
        assertEquals("1c1917", hex(FoliaLightColors.ink))
        assertEquals("ea580c", hex(FoliaLightColors.accent))
        // accent 深色页 = zinc-100（baseThemes.ts:12）
        assertEquals("f4f4f5", hex(FoliaDarkColors.accent))
        // on-accent：深色页 #17171a、浅色页 #ffffff（kitTheme.ts:12-18 ON_ACCENT_*）
        assertEquals("17171a", hex(FoliaDarkColors.onAccent))
        assertEquals("ffffff", hex(FoliaLightColors.onAccent))
    }

    private fun hex(c: androidx.compose.ui.graphics.Color): String =
        String.format("%06x", c.toArgb() and 0xFFFFFF)

    /** kitTheme.ts:39-45 的 alpha 百分比。 */
    @Test
    fun alphaTokensMatchKitTheme() {
        assertEquals(0.62f, FoliaLightColors.text2.alpha, 0.005f)
        assertEquals(0.42f, FoliaLightColors.text3.alpha, 0.005f)
        assertEquals(0.08f, FoliaLightColors.line.alpha, 0.005f)
        assertEquals(0.16f, FoliaLightColors.lineStrong.alpha, 0.005f)
        assertEquals(0.07f, FoliaLightColors.fill.alpha, 0.005f)
        assertEquals(0.12f, FoliaLightColors.fillStrong.alpha, 0.005f)
        assertEquals(0.17f, FoliaLightColors.track.alpha, 0.005f)
    }

    /** index.css:163-168 玻璃面板：底 85%、描边 12%。 */
    @Test
    fun glassPanelAlphaMatchesIndexCss() {
        assertEquals(0.85f, FoliaDarkColors.glassFill.alpha, 0.005f)
        assertEquals(0.12f, FoliaDarkColors.glassBorder.alpha, 0.005f)
    }

    /** SearchResultRow.tsx:78-83 行底 3.5% / 4%，描边 5% / 6%。 */
    @Test
    fun searchRowAlphaMatchesSource() {
        assertEquals(0.035f, FoliaLightColors.rowFill.alpha, 0.005f)
        assertEquals(0.05f, FoliaLightColors.rowBorder.alpha, 0.005f)
        assertEquals(0.04f, FoliaDarkColors.rowFill.alpha, 0.005f)
        assertEquals(0.06f, FoliaDarkColors.rowBorder.alpha, 0.005f)
    }

    // ---- 圆角 / 间距 --------------------------------------------------------------------

    /** inventory.md §1：卡片圆角统一 16dp。 */
    @Test
    fun cardCornerIs16dp() {
        assertEquals(16f, FoliaShapeTokens().xl.topStart.toPx(androidx.compose.ui.geometry.Size(100f, 100f), androidx.compose.ui.unit.Density(1f)), 0.01f)
    }

    /** Tailwind rounded-* 档位：md 6 / lg 8 / xl 12 / 2xl 16。 */
    @Test
    fun shapeScaleMatchesTailwind() {
        val s = FoliaShapeTokens()
        assertEquals(6f, s.sm.topStart.toPx(androidx.compose.ui.geometry.Size(100f, 100f), androidx.compose.ui.unit.Density(1f)), 0.01f)
        assertEquals(8f, s.md.topStart.toPx(androidx.compose.ui.geometry.Size(100f, 100f), androidx.compose.ui.unit.Density(1f)), 0.01f)
        assertEquals(12f, s.lg.topStart.toPx(androidx.compose.ui.geometry.Size(100f, 100f), androidx.compose.ui.unit.Density(1f)), 0.01f)
        assertEquals(16f, s.xl.topStart.toPx(androidx.compose.ui.geometry.Size(100f, 100f), androidx.compose.ui.unit.Density(1f)), 0.01f)
    }

    /** 触控下限 48dp（无障碍目标尺寸）。 */
    @Test
    fun spacingTokens() {
        val s = FoliaSpacingTokens()
        assertTrue(s.rowMin >= 48)
        assertEquals(16, s.screenPad)
        assertEquals(18, s.sectionGap)
        assertEquals(10, s.rowPadY)
    }

    // ---- 主题 ---------------------------------------------------------------------------

    @Test
    fun blurTokensMatchTailwind() {
        val b = FoliaBlurTokens()
        assertEquals(12f, b.md.value, 0.01f)
        assertEquals(24f, b.xl.value, 0.01f)
    }

    @Test
    fun typeScaleIsPresent() {
        val t = FoliaLightType
        assertEquals(24f, t.headline.fontSize.value, 0.01f)
        assertEquals(16f, t.bodyStrong.fontSize.value, 0.01f)
        assertEquals(14f, t.body.fontSize.value, 0.01f)
        assertEquals(12f, t.caption.fontSize.value, 0.01f)
        assertEquals(12f, t.tag.fontSize.value, 0.01f)
        assertFalse(t.body.fontSize.value == 0f)
    }
}

private fun androidx.compose.ui.unit.Dp.toDp(): Float = this.value
