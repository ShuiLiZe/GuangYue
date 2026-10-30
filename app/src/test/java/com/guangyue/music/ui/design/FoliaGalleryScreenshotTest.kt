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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.music.R
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

    /**
     * 内容自适应高度的特写截图：只截组件本身，不留整屏空白。
     *
     * U1b 修正：U1 版用 `onRoot()` + `wrapContentHeight()`，但 root 节点仍被撑到整屏 800dp，
     * 特写图下方三分之二全是空背景（并排图因此被拉长、且两边尺度对不上）。现在改成
     * `onNodeWithTag` 截挂了 [FitTag] 的节点 —— 但 Roborazzi 的节点截图仍按 root 画布尺寸
     * 输出，所以再用 [cropToContent] 按「内容非底色底边」把下方空白裁掉。
     */
    private fun shootFit(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            FoliaTheme(dark = dark) {
            Box(
                Modifier
                    .requiredWidth(360.dp)
                    .wrapContentHeight()
                    .background(Folia.colors.page)
                    .testTag(FitTag),
            ) { content() }
            }
        }
        compose.waitForIdle()
        val file = java.io.File(outDir, "$name.png")
        compose.onNodeWithTag(FitTag).captureRoboImage(file)
        cropToContent(file)
    }

    /**
     * 把截图里「最后一处内容像素」以下的底色裁掉（下方留 8dp 呼吸）。
     * 取左上角像素为页面底色，逐行从底往上找第一个与底色明显不同的行（欧氏距离 > 12，
     * 这样投影这类极淡像素不会被当成内容）。
     */
    private fun cropToContent(file: java.io.File) {
        val img = javax.imageio.ImageIO.read(file) ?: return
        val bg = img.getRGB(2, 2) and 0xFFFFFF
        var last = 0
        var y = img.height - 1
        while (y > 0) {
            var x = 0
            var hit = false
            while (x < img.width) {
                val p = img.getRGB(x, y) and 0xFFFFFF
                val dr = ((p shr 16 and 0xFF) - (bg shr 16 and 0xFF))
                val dg = ((p shr 8 and 0xFF) - (bg shr 8 and 0xFF))
                val db = ((p and 0xFF) - (bg and 0xFF))
                if (dr * dr + dg * dg + db * db > 144) { hit = true; break }
                x += 2
            }
            if (hit) { last = y; break }
            y -= 1
        }
        if (last <= 0 || last >= img.height - 1) return
        val h = (last + 16).coerceAtMost(img.height)
        javax.imageio.ImageIO.write(img.getSubimage(0, 0, img.width, h), "png", file)
    }

    private companion object {
        const val FitTag = "u1-fit"
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
                FoliaButton(stringResource(R.string.play_all), onClick = {}, icon = GuangYueIcons.Play)
                FoliaButton(stringResource(R.string.add_to_queue), onClick = {}, kind = FoliaButtonKind.Secondary, icon = GuangYueIcons.ListPlus)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FoliaButton(stringResource(R.string.disabled_sample), onClick = {}, enabled = false)
                FoliaButton(stringResource(R.string.more), onClick = {}, kind = FoliaButtonKind.Quiet)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                FoliaIconButton(GuangYueIcons.Play, stringResource(R.string.cd_play), onClick = {})
                FoliaIconButton(GuangYueIcons.Heart, stringResource(R.string.cd_like), onClick = {})
                FoliaIconButton(GuangYueIcons.Download, stringResource(R.string.cd_download), onClick = {})
                FoliaIconButton(GuangYueIcons.Shuffle, stringResource(R.string.cd_shuffle), onClick = {})
                FoliaToggle(checked = on, onCheckedChange = { on = it })
            }
            val viewLabels = listOf(
                stringResource(R.string.view_list), stringResource(R.string.view_grid),
                stringResource(R.string.view_wall),
            )
            FoliaSegmented<Int>(options = listOf(0, 1, 2), selected = seg, onSelect = { seg = it }, label = { viewLabels[it] })
            val kinds = listOf(
                stringResource(R.string.kind_track), stringResource(R.string.album),
                stringResource(R.string.artist), stringResource(R.string.playlist),
            )
            FoliaTabs<String>(tabs = kinds, selected = kinds[0], onSelect = {}, label = { it })
        }
    }

    /**
     * 行特写。
     *
     * 手机化：桌面的队列行贴在玻璃面板上（`WallToolsQueueIsland.css`），所以这里也放进
     * [FoliaListPanel]，并让中间一行 `selected = true`，把「浅底 + 左侧橙竖条」一起截进去
     * （对应基准 `m412-light-panel-queue.png`）。
     */
    @Composable
    private fun RowCloseup() {
        Frame {
            FoliaListPanel {
                FoliaSongRow(
                    stringResource(R.string.song_night), stringResource(R.string.song_album_november), trailingText = "03:44",
                    leading = { FoliaRowCover(icon = GuangYueIcons.Music) },
                    trailingIcon = GuangYueIcons.Plus, onTrailingClick = {},
                    showDivider = false,
                )
                FoliaSongRow(
                    stringResource(R.string.song_rice), stringResource(R.string.song_album_magic), trailingText = "03:43",
                    leading = { FoliaRowCover(color = Color(0xFF8C5A2B)) },
                    trailingIcon = GuangYueIcons.Plus, onTrailingClick = {},
                    showDivider = false,
                    selected = true,
                )
                FoliaSongRow(
                    stringResource(R.string.song_sunny), stringResource(R.string.song_album_yehuimei), trailingText = "04:29",
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
                        stringResource(R.string.playlist_daily_mix), stringResource(R.string.card_subtitle_daily), coverColor = Color(0xFFE0453F),
                        cover = { Text(stringResource(R.string.song_cover_letter_daily), style = Folia.type.bodyStrong, color = Color.White) },
                    )
                }
                Box(Modifier.weight(1f)) {
                    FoliaCoverCard(
                        stringResource(R.string.album_liked), stringResource(R.string.card_subtitle_liked), coverColor = Color(0xFF4B6BD8),
                        cover = { Text(stringResource(R.string.song_cover_letter_night), style = Folia.type.bodyStrong, color = Color.White) },
                    )
                }
            }
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
                        stringResource(if (LocalFoliaBlurEnabled.current) R.string.glass_on else R.string.glass_off),
                        style = Folia.type.bodyStrong,
                        color = Folia.colors.ink,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
