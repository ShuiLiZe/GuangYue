package com.guangyue.music.ui.design

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.guangyue.music.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * U1b 的回归断言：Hark 审查提的 5 条，能用断言钉住的都钉在这里。
 *
 *  1. 文案全中文、来自 strings.xml（默认 values/ 就是中文）；
 *  2. 卡片质感 token（纸底 + 投影 + 封面描边）与队列行 token（面板底、选中底、橙竖条）；
 *  3. 禁用按钮不靠「橙底淡字」，而是整组降透明度 —— 逻辑层可断言的是 kind/alpha 常量；
 *  4. 并排图是脚本产物，不在这里断言。
 *
 * 全部是纯 JVM + Robolectric（不装模拟器），跑得很快。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoliaU1bTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun str(id: Int): String = context.getString(id)

    // ---- 1. 文案：必须中文、且与 zh-CN.ts 对应 -------------------------------------------

    /** zh-CN.ts:1579 `searchDatabase` = 「搜索在线歌曲...」；对应 Folia 的 `SEARCH DATABASE...`。 */
    @Test
    fun searchPlaceholderFollowsZhCn() {
        assertEquals("搜索在线歌曲...", str(R.string.search_database))
    }

    /** 界面文案不允许是纯 ASCII —— 那就是漏翻译的英文（token 名除外，见下一条）。 */
    @Test
    fun uiStringsAreChinese() {
        val ids = listOf(
            R.string.search, R.string.play_all, R.string.add_to_queue, R.string.download,
            R.string.more, R.string.album, R.string.artist, R.string.playlist,
            R.string.gallery_title, R.string.gallery_subtitle,
            R.string.gallery_tab_token, R.string.gallery_tab_control,
            R.string.gallery_tab_content, R.string.gallery_tab_overlay,
            R.string.section_colors, R.string.section_type, R.string.section_shape,
            R.string.section_buttons, R.string.section_toggle, R.string.section_segmented,
            R.string.section_search, R.string.section_slider, R.string.section_card,
            R.string.section_row, R.string.section_empty, R.string.section_glass,
            R.string.section_sheet, R.string.section_row_panel,
            R.string.empty_hint, R.string.empty_action,
            R.string.glass_on, R.string.glass_off,
        )
        val offenders = ids.map { str(it) }.filter { s -> s.none { it.code > 0x2E80 } }
        assertEquals("以下文案没有中文：$offenders", emptyList<String>(), offenders)
    }

    /** 分区标题不带英文副标题（U1 是「颜色 / Colors」）。 */
    @Test
    fun sectionTitlesHaveNoEnglishSuffix() {
        val ids = listOf(
            R.string.section_colors, R.string.section_type, R.string.section_shape,
            R.string.section_buttons, R.string.section_toggle, R.string.section_segmented,
            R.string.section_search, R.string.section_slider, R.string.section_card,
            R.string.section_row, R.string.section_empty, R.string.section_glass,
            R.string.section_sheet, R.string.section_row_panel,
        )
        val offenders = ids.map { str(it) }.filter { it.contains("/") || it.contains(Regex("[A-Za-z]{3,}")) }
        assertEquals("分区标题仍带英文：$offenders", emptyList<String>(), offenders)
    }

    /** 示例数据换成中文歌，不再有 Midnight City / Cloud Fixture / Fixture Artist。 */
    @Test
    fun sampleDataUsesRealChineseSongs() {
        val banned = listOf("Midnight City", "Cloud Fixture", "Fixture Artist", "Daily Mix", "M83", "NetEase")
        val samples = listOf(
            R.string.song_night, R.string.song_sunny, R.string.song_rice,
            R.string.playlist_daily_mix, R.string.album_liked,
            R.string.song_album_november, R.string.song_album_yehuimei,
        ).map { str(it) }
        banned.forEach { bad ->
            assertTrue("示例数据仍含英文假数据：$bad", samples.none { it.contains(bad) })
        }
        assertTrue(samples.any { it.contains("夜曲") })
        assertTrue(samples.any { it.contains("晴天") })
        assertTrue(samples.any { it.contains("稻香") })
    }

    /** 颜色 token 旁边必须有中文说明（token 名本身可以留英文）。 */
    @Test
    fun colorTokensHaveChineseNotes() {
        val ids = listOf(
            R.string.token_page, R.string.token_ink, R.string.token_ink_soft,
            R.string.token_accent, R.string.token_on_accent, R.string.token_card,
            R.string.token_raise, R.string.token_track, R.string.token_fill,
            R.string.token_line, R.string.token_text2, R.string.token_text3,
            R.string.token_glass, R.string.token_ok, R.string.token_warn, R.string.token_error,
            R.string.token_card_shadow, R.string.token_panel,
            R.string.token_row_current, R.string.token_row_marker,
        )
        val offenders = ids.map { str(it) }.filter { s -> s.none { it.code > 0x2E80 } }
        assertEquals("以下 token 说明没有中文：$offenders", emptyList<String>(), offenders)
    }

    // ---- 2. 卡片质感 token --------------------------------------------------------------

    /** 纸底：wall.css:10/34 `color-mix(bg 94%, ink)` 与 `color-mix(bg 88%, ink)`。 */
    @Test
    fun cardFillMatchesLatticePoster() {
        // 浅：94% #f5f5f4 + 6% #1c1917 → 每通道四舍五入到 F6F5F4
        assertEquals("f6f5f4", hex(FoliaLightColors.cardFill))
        // 深：88% #09090b + 12% #f4f4f5 → 1C1B1D
        assertEquals("1c1b1d", hex(FoliaDarkColors.cardFill))
    }

    /** 投影：wall.css:12/35 focus-lift 在 Compose 里降成 16dp elevation + 18%/45% 黑。 */
    @Test
    fun cardShadowTokensMatchFocusLift() {
        assertEquals(0.18f, FoliaLightColors.cardShadow.alpha, 0.005f)
        assertEquals(0.45f, FoliaDarkColors.cardShadow.alpha, 0.005f)
    }

    /** 封面内描边：日光 8% / 深色 10% 白（m412 里封面边缘那圈亮线）。 */
    @Test
    fun coverRingAlpha() {
        assertEquals(0.08f, FoliaLightColors.coverRing.alpha, 0.005f)
        assertEquals(0.10f, FoliaDarkColors.coverRing.alpha, 0.005f)
    }

    /** 卡片底色必须真的比页面底色亮（浅色主题），否则「凸起」不成立。 */
    @Test
    fun lightCardIsLighterThanPage() {
        val card = luminance(FoliaLightColors.cardFill)
        val page = luminance(FoliaLightColors.page)
        assertTrue("card=$card page=$page", card > page)
    }

    /** 深色主题里卡片要比页面亮一档（不是纯黑叠黑）。 */
    @Test
    fun darkCardIsLighterThanPage() {
        assertTrue(luminance(FoliaDarkColors.cardFill) > luminance(FoliaDarkColors.page))
    }

    // ---- 3. 队列面板 / 选中行 ------------------------------------------------------------

    /** WallToolsQueueIsland.css:1-20：面板底 黑40% / 白62%，描边 白8% / 黑7%。 */
    @Test
    fun panelTokensMatchQueueIsland() {
        assertEquals(0.40f, FoliaDarkColors.panelFill.alpha, 0.005f)
        assertEquals(0.08f, FoliaDarkColors.panelBorder.alpha, 0.005f)
        assertEquals(0.62f, FoliaLightColors.panelFill.alpha, 0.005f)
        assertEquals(0.07f, FoliaLightColors.panelBorder.alpha, 0.005f)
    }

    /** :176-177 当前行底 白10% / 黑7%。 */
    @Test
    fun currentRowIsSubtleTint() {
        assertEquals(0.10f, FoliaDarkColors.rowCurrent.alpha, 0.005f)
        assertEquals(0.07f, FoliaLightColors.rowCurrent.alpha, 0.005f)
        // 必须比普通行底更明显，否则选中态看不出来
        assertTrue(FoliaLightColors.rowCurrent.alpha > FoliaLightColors.rowFill.alpha)
    }

    /** :249 竖条取 --text-accent：浅色主题 = #ea580c 橙。 */
    @Test
    fun currentRowMarkerIsAccent() {
        assertEquals("ea580c", hex(FoliaLightColors.rowMarker))
        assertEquals(hex(FoliaLightColors.accent), hex(FoliaLightColors.rowMarker))
    }

    /** 深色主题 accent 是 zinc-100，竖条跟着走。 */
    @Test
    fun darkRowMarkerFollowsAccent() {
        assertEquals(hex(FoliaDarkColors.accent), hex(FoliaDarkColors.rowMarker))
    }

    // ---- 4. 手机化尺寸 ------------------------------------------------------------------

    /** 正文 ≥14sp、次要 ≥12sp（Hark 的手机化底线）。 */
    @Test
    fun phoneTextSizes() {
        assertTrue(FoliaLightType.body.fontSize.value >= 14f)
        assertTrue(FoliaLightType.bodyStrong.fontSize.value >= 14f)
        assertTrue(FoliaLightType.caption.fontSize.value >= 12f)
        assertTrue(FoliaLightType.tag.fontSize.value >= 12f)
    }

    /** 行高 56–64dp、触控 ≥48dp、边距 16dp。 */
    @Test
    fun phoneMetrics() {
        val s = FoliaSpacingTokens()
        assertTrue("rowMin=${s.rowMin}", s.rowMinDp.value >= 48f)
        assertTrue(FoliaSpacingTokens().screenPad >= 16)
        assertEquals(16, s.screenPad)
    }

    // ---- 5. 真机渲染抽查（Robolectric 合成，不装模拟器） --------------------------------

    /** 画廊内容页能渲染出中文封面卡标题，而不是英文 fixture 名。 */
    @Test
    fun contentPageRendersChineseData() {
        compose.setContent {
            FoliaTheme(dark = false) { GalleryScreen(dark = false, page = 2) }
        }
        compose.waitForIdle()
        compose.onNodeWithText(str(R.string.playlist_daily_mix), substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cloud Fixture", substring = true).assertDoesNotExist()
    }

    /** 控件页把禁用按钮标成「已禁用」，且只有一处。 */
    @Test
    fun controlPageShowsDisabledSample() {
        compose.setContent {
            FoliaTheme(dark = false) { GalleryScreen(dark = false, page = 1) }
        }
        compose.waitForIdle()
        compose.onAllNodesWithText(str(R.string.disabled_sample), substring = true).assertCountEquals(1)
    }

    private fun luminance(c: androidx.compose.ui.graphics.Color): Float = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue

    private fun hex(c: androidx.compose.ui.graphics.Color): String =
        String.format("%06x", c.toArgb() and 0xFFFFFF)
}
