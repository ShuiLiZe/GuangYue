package com.guangyue.feature.player.screen

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** P1b：舞台浮卡面板的纯逻辑（卡宽、模式直选映射、配色）。 */
class StagePanelTest {
    @Test
    fun widthIs360CappedByScreenMinus32() {
        assertEquals(360.dp, stagePanelWidth(412.dp))
        assertEquals(328.dp, stagePanelWidth(360.dp))
        assertEquals(288.dp, stagePanelWidth(320.dp))
    }

    @Test
    fun modeTargetRoundTripsThroughPlayerMode() {
        PlayerMode.entries.forEach { mode ->
            val t = playerModeTarget(mode)
            assertEquals(mode, playerMode(shuffle = t.shuffle, repeatMode = t.repeatMode))
        }
    }

    @Test
    fun tabsFollowFoliaOrderAndNames() {
        assertEquals(listOf("封面", "歌词", "控制", "播放列表", "音质"), StagePanelTab.entries.map { it.label })
    }

    @Test
    fun darkAndLightGlassDiffer() {
        assertNotEquals(stagePanelColors(true).glass, stagePanelColors(false).glass)
        // 次要字 = 主字 62%（kit --st-t2）
        assertEquals(0.62f, stagePanelColors(false).t2.alpha, 0.01f)
    }
}
