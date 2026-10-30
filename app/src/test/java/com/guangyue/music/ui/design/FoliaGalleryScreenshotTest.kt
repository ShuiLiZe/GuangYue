package com.guangyue.music.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.guangyue.feature.library.design.icons.GuangYueIcons
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * U1 设计地基的 JVM 截图（Robolectric 真渲染 + Roborazzi）。
 *
 * 设备：360×800dp / xhdpi（手机基线），浅深各一套。输出目录由
 * `app/build.gradle.kts` 的 `roborazzi.output.dir` 决定（U1 段为 `docs/ui-u1`），
 * 只有 `GUANGYUE_RECORD_SCREENSHOTS=true` 时才写盘。
 *
 * 产出：
 *  - gallery-light.png / gallery-dark.png（整屏一屏，超出部分靠 gallery-light-2 等分页补）
 *  - closeup-button / closeup-row / closeup-card / closeup-slider（浅深各一）
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoliaGalleryScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * 输出目录：优先取 gradle 注入的 `roborazzi.output.dir`（`docs/ui-u1`），
     * 取不到时退回模块内的 `build/roborazzi-u1`。用绝对 [java.io.File] 直写，
     * 避免 Roborazzi 把相对名落到模块根目录。
     */
    private val outDir: java.io.File = run {
        val prop = System.getProperty("roborazzi.output.dir")
        val dir = if (prop.isNullOrBlank()) java.io.File("build/roborazzi-u1")
        else java.io.File(prop).let { if (it.isAbsolute) it else java.io.File(it.absolutePath) }
        dir.mkdirs()
        dir
    }

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            FoliaTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(Folia.colors.page)) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(java.io.File(outDir, "$name.png"))
    }

    /** 内容自适应高度的特写截图：截「content」节点本身，不留整屏空白。 */
    private fun shootFit(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            FoliaTheme(dark = dark) {
                Box(Modifier.requiredWidth(360.dp).wrapContentHeight().background(Folia.colors.page)) { Box(Modifier.fillMaxWidth()) { content() } }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(java.io.File(outDir, "$name.png"))
    }

    @Test
    fun galleryLight() = shoot("gallery-light", dark = false) { GalleryScreen(dark = false, page = 0) }

    @Test
    fun galleryDark() = shoot("gallery-dark", dark = true) { GalleryScreen(dark = true, page = 0) }

    // ---- 分页长图（每页一屏，浅/深各一套；第 4 页带底部弹层示例） -------------------------

    @Test
    fun galleryLightPage2() = shoot("gallery-light-2", dark = false) { GalleryScreen(dark = false, page = 1) }

    @Test
    fun galleryLightPage3() = shoot("gallery-light-3", dark = false) { GalleryScreen(dark = false, page = 2) }

    @Test
    fun galleryLightPage4() = shoot("gallery-light-4", dark = false) { GalleryScreen(dark = false, page = 3, showSheet = true) }

    @Test
    fun galleryDarkPage2() = shoot("gallery-dark-2", dark = true) { GalleryScreen(dark = true, page = 1) }

    @Test
    fun galleryDarkPage3() = shoot("gallery-dark-3", dark = true) { GalleryScreen(dark = true, page = 2) }

    @Test
    fun galleryDarkPage4() = shoot("gallery-dark-4", dark = true) { GalleryScreen(dark = true, page = 3, showSheet = true) }

    /** 按钮特写：主 / 次 / 图标 / 开关 / 分段 / Tab。 */
    @Test
    fun closeupButtonLight() = shootFit("closeup-button-light", dark = false) { ButtonCloseup() }

    @Test
    fun closeupButtonDark() = shootFit("closeup-button-dark", dark = true) { ButtonCloseup() }

    @Test
    fun closeupRowLight() = shootFit("closeup-row-light", dark = false) { RowCloseup() }

    @Test
    fun closeupRowDark() = shootFit("closeup-row-dark", dark = true) { RowCloseup() }

    @Test
    fun closeupCardLight() = shootFit("closeup-card-light", dark = false) { CardCloseup() }

    @Test
    fun closeupCardDark() = shootFit("closeup-card-dark", dark = true) { CardCloseup() }

    @Test
    fun closeupSliderLight() = shootFit("closeup-slider-light", dark = false) { SliderCloseup() }

    @Test
    fun closeupSliderDark() = shootFit("closeup-slider-dark", dark = true) { SliderCloseup() }

    // ---- 特写内容 -----------------------------------------------------------------------

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Folia.spacing.screenPadDp),
            verticalArrangement = Arrangement.spacedBy(Folia.spacing.x4Dp),
        ) { content() }
    }

    @Composable
    private fun ButtonCloseup() {
        var on by remember { mutableStateOf(true) }
        var seg: Int by remember { mutableStateOf(1) }
        Frame {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FoliaButton("播放全部", onClick = {}, icon = GuangYueIcons.Play)
                FoliaButton("加入队列", onClick = {}, kind = FoliaButtonKind.Secondary, icon = GuangYueIcons.ListPlus)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FoliaButton("已禁用", onClick = {}, enabled = false)
                FoliaButton("更多", onClick = {}, kind = FoliaButtonKind.Quiet)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FoliaIconButton(GuangYueIcons.Play, "播放", onClick = {})
                FoliaIconButton(GuangYueIcons.Heart, "喜欢", onClick = {})
                FoliaIconButton(GuangYueIcons.Download, "下载", onClick = {})
                FoliaIconButton(GuangYueIcons.Shuffle, "随机", onClick = {})
                FoliaToggle(checked = on, onCheckedChange = { on = it })
            }
            FoliaSegmented<Int>(options = listOf(0, 1, 2), selected = seg, onSelect = { seg = it }, label = { listOf("列表", "网格", "墙面")[it] })
            FoliaTabs<String>(tabs = listOf("歌曲", "专辑", "艺人", "歌单"), selected = "歌曲", onSelect = {}, label = { it })
        }
    }

    @Composable
    private fun RowCloseup() {
        Frame {
            Column(Modifier.clip(Folia.shapes.xl).background(Folia.colors.preview)) {
                FoliaSongRow(
                    "夜曲", "周杰伦 · 十一月的萧邦", trailingText = "03:44",
                    leading = { FoliaRowCover(icon = GuangYueIcons.Music) },
                    trailingIcon = GuangYueIcons.Plus, onTrailingClick = {},
                )
                FoliaSongRow(
                    "Midnight City", "M83 · Hurry Up, We're Dreaming", trailingText = "04:03",
                    leading = { FoliaRowCover(color = Color(0xFF2F6F5B)) },
                    trailingIcon = GuangYueIcons.Plus, onTrailingClick = {},
                )
                FoliaSongRow(
                    "晴天", "周杰伦 · 叶惠美", trailingText = "04:29",
                    leading = { FoliaRowCover(color = Color(0xFFC9A227)) },
                    trailingIcon = GuangYueIcons.Plus, onTrailingClick = {},
                    showDivider = false,
                )
            }
            FoliaSearchField(value = "", onValueChange = {}, leadingIcon = GuangYueIcons.Search)
        }
    }

    @Composable
    private fun CardCloseup() {
        Frame {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    FoliaCoverCard(
                        "Daily Mix", "18 首 · 每日推荐", coverColor = Color(0xFFE0453F),
                        cover = { Text("Mix", style = Folia.type.bodyStrong, color = Color.White) },
                    )
                }
                Box(Modifier.weight(1f)) {
                    FoliaCoverCard(
                        "Cloud Fixture", "NetEase", coverColor = Color(0xFF4B6BD8),
                        cover = { Text("Cloud", style = Folia.type.bodyStrong, color = Color.White) },
                    )
                }
            }
            FoliaEmptyState(GuangYueIcons.Inbox, "这里还什么都没有。搜索一首歌，或者先建一个歌单。")
        }
    }

    @Composable
    private fun SliderCloseup() {
        var p by remember { mutableStateOf(0.32f) }
        var v by remember { mutableStateOf(0.72f) }
        Frame {
            FoliaSlider(p, { p = it })
            FoliaSlider(v, { v = it })
            FoliaSlider(0.5f, {}, enabled = false)
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                FoliaBlurLayer(Folia.blur.xl, Modifier.fillMaxWidth().height(120.dp)) {
                    Box(
                        Modifier.fillMaxWidth().height(120.dp).background(Folia.colors.page)
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(
                                        Folia.colors.accent.copy(alpha = 0.55f),
                                        Color(0xFF4B6BD8).copy(alpha = 0.45f),
                                    ),
                                ),
                            ),
                    )
                }
                FoliaGlassPanel(Modifier.align(Alignment.Center).padding(horizontal = 12.dp)) {
                    Text(
                        "毛玻璃 " + if (LocalFoliaBlurEnabled.current) "blur-on" else "blur-off",
                        style = Folia.type.bodyStrong,
                        color = Folia.colors.ink,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
