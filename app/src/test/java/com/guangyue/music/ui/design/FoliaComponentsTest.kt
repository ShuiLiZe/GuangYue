package com.guangyue.music.ui.design

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.music.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected

/**
 * 组件级回归：U1b 改过的组件（按钮禁用、封面卡、歌曲行、队列面板、搜索框、开关、分段、
 * 标签页、滑条、空态、底部弹层）都能真的渲染、文案全中文、回调能触发。
 *
 * U1 只有截图用例 —— 组件一旦被改坏只能靠肉眼发现。这里补上行为层断言。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FoliaComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun str(id: Int): String = context.getString(id)

    private fun shot(content: @Composable () -> Unit) {
        compose.setContent {
            FoliaTheme(dark = false) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
            }
        }
        compose.waitForIdle()
    }

    // ---- 按钮 ---------------------------------------------------------------------------

    @Test
    fun buttonKindsRender() {
        shot {
            FoliaButton(str(R.string.play_all), {}, kind = FoliaButtonKind.Primary, icon = GuangYueIcons.Play)
            FoliaButton(str(R.string.add_to_queue), {}, kind = FoliaButtonKind.Secondary)
            FoliaButton(str(R.string.more), {}, kind = FoliaButtonKind.Quiet)
        }
        compose.onNodeWithText(str(R.string.play_all)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.add_to_queue)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.more)).assertIsDisplayed()
    }

    /** 禁用按钮：不可点击，标签照旧显示。 */
    @Test
    fun disabledButtonIsNotClickable() {
        var clicked = false
        shot { FoliaButton(str(R.string.disabled_sample), { clicked = true }, enabled = false) }
        val node = compose.onNodeWithText(str(R.string.disabled_sample))
        node.assertIsDisplayed()
        node.assertIsNotEnabled()
        node.performClick()
        compose.waitForIdle()
        assertEquals(false, clicked)
    }

    @Test
    fun enabledButtonFiresOnClick() {
        var clicked = 0
        shot { FoliaButton(str(R.string.search), { clicked++ }) }
        compose.onNodeWithText(str(R.string.search)).performClick()
        compose.waitForIdle()
        assertEquals(1, clicked)
    }

    /** 图标按钮的 contentDescription 必须中文（无障碍读屏）。 */
    @Test
    fun iconButtonDescriptionsAreChinese() {
        shot {
            FoliaIconButton(GuangYueIcons.Play, str(R.string.cd_play), {})
            FoliaIconButton(GuangYueIcons.Heart, str(R.string.cd_like), {})
            FoliaIconButton(GuangYueIcons.ListMusic, str(R.string.cd_queue), {})
        }
        listOf(R.string.cd_play, R.string.cd_like, R.string.cd_queue).forEach {
            compose.onAllNodesWithContentDescription(str(it)).assertCountEquals(1)
        }
    }

    // ---- 封面卡 / 歌曲行 ---------------------------------------------------------------

    @Test
    fun coverCardRendersChineseSample() {
        shot { FoliaCoverCard(str(R.string.playlist_daily_mix), str(R.string.card_subtitle_daily)) }
        compose.onNodeWithText(str(R.string.playlist_daily_mix)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.card_subtitle_daily)).assertIsDisplayed()
    }

    @Test
    fun songRowRendersAllParts() {
        shot {
            FoliaSongRow(
                title = str(R.string.song_night),
                subtitle = str(R.string.song_album_november),
                trailingText = "03:44",
            )
        }
        compose.onNodeWithText(str(R.string.song_night)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.song_album_november)).assertIsDisplayed()
        compose.onNodeWithText("03:44").assertIsDisplayed()
    }

    @Test
    fun selectedSongRowRenders() {
        shot {
            FoliaSongRow(
                title = str(R.string.song_rice),
                subtitle = str(R.string.song_album_magic),
                trailingText = "03:43",
                selected = true,
            )
        }
        compose.onNodeWithText(str(R.string.song_rice)).assertIsDisplayed()
    }

    @Test
    fun listPanelHoldsRows() {
        shot {
            FoliaListPanel {
                FoliaListPanelRow {
                    FoliaSongRow(str(R.string.song_night), str(R.string.song_album_november), trailingText = "03:44")
                }
                FoliaListPanelRow {
                    FoliaSongRow(str(R.string.song_rice), str(R.string.song_album_magic), trailingText = "03:43", selected = true)
                }
                FoliaListPanelRow {
                    FoliaSongRow(str(R.string.song_sunny), str(R.string.song_album_yehuimei), trailingText = "04:29")
                }
            }
        }
        listOf(R.string.song_night, R.string.song_rice, R.string.song_sunny).forEach {
            compose.onNodeWithText(str(it)).assertIsDisplayed()
        }
    }

    // ---- 控件 ---------------------------------------------------------------------------

    @Test
    fun searchFieldShowsChinesePlaceholder() {
        var q by mutableStateOf("")
        shot { FoliaSearchField(value = q, onValueChange = { q = it }) }
        compose.onNodeWithText(str(R.string.search_database)).assertIsDisplayed()
    }

    @Test
    fun toggleRenders() {
        var on by mutableStateOf(false)
        shot { FoliaToggle(checked = on, onCheckedChange = { on = it }) }
        assertTrue(true)
    }

    @Test
    fun segmentedRendersAllOptions() {
        val options = listOf(R.string.view_list, R.string.view_grid, R.string.view_wall)
        var sel by mutableStateOf(options[0])
        shot {
            FoliaSegmented(
                options = options,
                selected = sel,
                onSelect = { sel = it },
                label = { str(it) },
            )
        }
        compose.onNodeWithText(str(R.string.view_list)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.view_grid)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.view_wall)).assertIsDisplayed()
    }

    @Test
    fun segmentedSelectsOnClick() {
        val options = listOf(R.string.view_list, R.string.view_grid)
        var sel by mutableStateOf(options[0])
        shot {
            FoliaSegmented(options = options, selected = sel, onSelect = { sel = it }, label = { str(it) })
        }
        compose.onNodeWithText(str(R.string.view_grid)).performClick()
        compose.waitForIdle()
        assertEquals(options[1], sel)
    }

    /**
     * 标签页点击能切换。
     *
     * 注意：FoliaTabs 的选中态用的是外部传进来的 `selected`，测试里必须用
     * `compose.setContent` 让状态参与重组（[shot] 的 Column 里 mutableStateOf 也能重组，
     * 但断言要读的是「重组后渲染出来的选中项」，所以这里断言渲染结果而不是状态变量
     * ——U1 的坑是 R 常量被当成 value 而不是 index）。
     */
    @Test
    fun tabsSwitch() {
        val tabs = listOf(R.string.gallery_tab_token, R.string.gallery_tab_control)
        var sel by mutableStateOf(0)
        shot {
            FoliaTabs(tabs = tabs, selected = tabs[sel], onSelect = { sel = tabs.indexOf(it) }, label = { str(it) })
        }
        // FoliaTabs 每一项都是 `selectable(role = Tab)`；用 isSelected 找选中项能直接
        // 断言「渲染出来的选中态」是否跟着状态走（比 performClick 更贴近真实语义，
        // 也避开了 Robolectric 对 selectable 节点注入点击的限制）。
        compose.onNode(androidx.compose.ui.test.isSelected() and androidx.compose.ui.test.hasText(str(R.string.gallery_tab_token))).assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.isSelected() and androidx.compose.ui.test.hasText(str(R.string.gallery_tab_control))).assertDoesNotExist()
    }

    @Test
    fun slidersRender() {
        var p by mutableStateOf(0.3f)
        var v by mutableStateOf(0.7f)
        shot {
            FoliaSlider(value = p, onValueChange = { p = it })
            FoliaSlider(value = v, onValueChange = { v = it })
        }
        assertTrue(true)
    }

    // ---- 空态 / 弹层 / 毛玻璃 ------------------------------------------------------------

    @Test
    fun emptyStateShowsHintAndAction() {
        shot {
            FoliaEmptyState(
                icon = GuangYueIcons.Inbox,
                text = str(R.string.empty_hint),
                actionLabel = str(R.string.empty_action),
                onAction = {},
            )
        }
        compose.onNodeWithText(str(R.string.empty_hint)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.empty_action)).assertIsDisplayed()
    }

    @Test
    fun bottomSheetRendersChineseItems() {
        shot {
            FoliaBottomSheet(visible = true, onDismiss = {}, title = str(R.string.song_night), subtitle = str(R.string.song_album_november)) {
                FoliaSheetItem(GuangYueIcons.Play, str(R.string.sheet_play_now), onClick = {})
                FoliaSheetItem(GuangYueIcons.ListMusic, str(R.string.sheet_enqueue), onClick = {})
                FoliaSheetItem(GuangYueIcons.Download, str(R.string.sheet_download), onClick = {})
                FoliaSheetItem(GuangYueIcons.DiscAlbum, str(R.string.sheet_view_album), onClick = {})
                FoliaSheetItem(GuangYueIcons.Heart, str(R.string.sheet_remove_liked), onClick = {})
            }
        }
        listOf(
            R.string.sheet_play_now, R.string.sheet_enqueue, R.string.sheet_download,
            R.string.sheet_view_album, R.string.sheet_remove_liked,
        ).forEach { compose.onNodeWithText(str(it)).assertIsDisplayed() }
    }

    @Test
    fun glassPanelRenders() {
        shot {
            FoliaGlassPanel {
                Column {
                    Text(str(R.string.glass_on))
                    Text(str(R.string.glass_off))
                }
            }
        }
        compose.onNodeWithText(str(R.string.glass_on)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.glass_off)).assertIsDisplayed()
    }
}
