package com.guangyue.feature.player.lyrics

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.guangyue.core.lyrics.ClassicTuning
import com.guangyue.feature.library.design.Gyp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * V1b-2：在**真 Compose 布局**上验滑杆对齐 —— 直接读滑钮节点的实际绘制矩形，
 * 断言它的水平中心 = 已填充轨道末端（0% / 50% / 100% 三处），而不是只验纯函数。
 *
 * 之前只跑纯函数测试（[FoliaSliderGeometryTest]），无法发现「滑钮盒子落在 44dp 热区里但
 * Canvas 只有 2dp 高」这类**布局层**偏移，所以这里再加一层节点级断言。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricStyleSheetSliderUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun strings() = LyricStyleStrings(
        title = 1,
        classic = 2,
        rotation = 3,
        breathing = 4,
        spacing = 5,
        reset = 6,
        desc = 7,
        close = 8,
    )

    /** 把「面板本体」放到一块已知宽度的底上（弹层之外，避免 ModalBottomSheet 动画影响测量）。 */
    private fun setPanel(tuning: ClassicTuning) {
        compose.setContent {
            CompositionLocalProvider(LyricStringHost provides { id -> "L$id" }) {
                LyricStyleSheetPreview(
                    tuning = tuning,
                    strings = strings(),
                    styleName = "L2",
                    onTuning = {},
                    accent = Gyp.accent,
                    fumeName = "F",
                )
            }
        }
        compose.waitForIdle()
    }

    /** 滑钮中心（相对拖动热区左缘，px）与热区宽度（px）。 */
    private fun thumbCentre(label: String): Pair<Float, Float> {
        val track = compose.onNodeWithTag(FoliaSliderTestTagPrefix + label)
        val thumb = compose.onNodeWithTag(FoliaThumbTestTagPrefix + label)
        track.assertIsDisplayed()
        thumb.assertIsDisplayed()
        val trackBounds = track.fetchSemanticsNode().boundsInRoot
        val thumbBounds = thumb.fetchSemanticsNode().boundsInRoot
        return (thumbBounds.center.x - trackBounds.left) to trackBounds.width
    }

    private fun assertAligned(label: String, value: Float) {
        val density = compose.density.density
        val insetPx = FoliaSliderThumbInsetDp * density
        val (centre, width) = thumbCentre(label)
        val expected = FoliaSliderGeometry.fillEndPx(width, insetPx, (value / 2f).coerceIn(0f, 1f))
        // 取整误差：offset 用了 roundToInt()，允许 1px。
        assertEquals("$label=$value 滑钮中心应等于填充末端", expected, centre, 1.5f)
    }

    @Test
    fun zeroValueCentresThumbOnFillStart() {
        setPanel(ClassicTuning(breathingFloatMultiplier = 0.0, wordSpacing = 2.0))
        compose.waitForIdle()
        // 0.00x → 0%，2.00x → 100%：两条滑杆同时验两端。
        assertAligned("L4", 0f)
        assertAligned("L5", 2f)
    }

    @Test
    fun halfValueCentresThumbOnTrackMiddle() {
        setPanel(ClassicTuning(breathingFloatMultiplier = 1.0))
        assertAligned("L4", 1f)
    }

    @Test
    fun fullValuePutsThumbAtTrackEnd() {
        setPanel(ClassicTuning(breathingFloatMultiplier = 2.0))
        assertAligned("L4", 2f)
    }

    @Test
    fun thumbFollowsDragToMiddle() {
        var tuning by mutableStateOf(ClassicTuning(breathingFloatMultiplier = 0.4))
        compose.setContent {
            CompositionLocalProvider(LyricStringHost provides { id -> "L$id" }) {
                LyricStyleSheetPreview(
                    tuning = tuning,
                    strings = strings(),
                    styleName = "L2",
                    onTuning = { tuning = it },
                    accent = Gyp.accent,
                    fumeName = "F",
                )
            }
        }
        compose.waitForIdle()
        val track = compose.onNodeWithTag(FoliaSliderTestTagPrefix + "L4")
        val width = track.fetchSemanticsNode().boundsInRoot.width
        // 拖到热区正中 → 值应落到 1.00x，滑钮中心 = 正中。
        track.performTouchInput {
            val cy = height / 2f
            down(Offset(width * 0.1f, cy))
            moveTo(Offset(width * 0.5f, cy))
            moveTo(Offset(width / 2f, cy))
            up()
        }
        compose.waitForIdle()
        assertEquals("拖动后值应落到 1.00x", 1.0, tuning.breathingFloatMultiplier, 1e-6)
        val density = compose.density.density
        val (centre, trackWidth) = thumbCentre("L4")
        assertEquals(
            "拖到中点后滑钮中心应落在轨道正中",
            trackWidth / 2f,
            centre,
            1.5f,
        )
        assertTrue("滑钮中心必须离开两端", centre > FoliaSliderThumbInsetDp * density)
    }
}
