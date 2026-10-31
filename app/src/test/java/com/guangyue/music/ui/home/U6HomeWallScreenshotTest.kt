package com.guangyue.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.guangyue.feature.library.design.FoliaBottomNav
import com.guangyue.feature.library.design.FoliaNavItem
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.home.FoliaMiniPlayer
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import com.guangyue.music.nav.homeStrings
import com.guangyue.music.ui.design.Folia
import com.guangyue.music.ui.design.FoliaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * U6 首页墙的 JVM 截图（Robolectric 真渲染 + Roborazzi），照 U1/U2/U5 的写法：
 * 360×800dp / xhdpi 打底，输出目录由 `app/build.gradle.kts` 的 `roborazzi.output.dir`
 * 决定（U6 段为 `docs/ui-u6`），只有 `GUANGYUE_RECORD_SCREENSHOTS=true` 时才写盘。
 *
 * 覆盖（验收清单原样对齐）：浅 / 深两套、加载中、空态（有音源 / 没音源）、失败态、
 * 320dp 窄屏、字体 1.3 倍，再加一张「装有迷你条 + 底栏」的整屏，看手势条 inset 有没有遮内容。
 *
 * 文案走真资源：直接调 `nav/homeStrings()`（Robolectric 读 `strings.xml`），
 * 与生产路径同一份中文串，测试里不另写一遍字面量。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class U6HomeWallScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val outDir: java.io.File = run {
        val prop = System.getProperty("roborazzi.output.dir")
        val dir = if (prop.isNullOrBlank()) java.io.File("build/roborazzi-u6") else java.io.File(prop)
        dir.mkdirs()
        dir
    }

    private fun shoot(name: String, dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, base.fontScale * fontScale),
            ) {
                FoliaTheme(dark = dark) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Folia.colors.page)
                            .testTag(RootTag),
                    ) { content() }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(java.io.File(outDir, "$name.png"))
    }

    // ---------- 数据（与 ScreenshotTest.kt 同一批，全是中文） ----------

    private val recommend = listOf(
        HomeTile("sheet:1:1", "华语流行精选", "每日推荐", null, HomeTileKind.SHEET),
        HomeTile("sheet:1:2", "深夜电台", "每日推荐", null, HomeTileKind.SHEET),
        HomeTile("sheet:2:3", "粤语经典", "每日推荐", null, HomeTileKind.SHEET),
        HomeTile("sheet:2:4", "民谣慢歌", "每日推荐", null, HomeTileKind.SHEET),
    )

    private val recent = listOf(
        HomeTile("track:1", "夜曲", "周杰伦", null, HomeTileKind.TRACK),
        HomeTile("track:2", "红豆", "王菲", null, HomeTileKind.TRACK),
        HomeTile("track:3", "平凡之路", "朴树", null, HomeTileKind.TRACK),
    )

    private val charts = listOf(
        HomeTile("board:1:1:1", "飙升榜", "夜曲 / 红豆 / 平凡之路", null, HomeTileKind.STATION),
        HomeTile("board:1:2:1", "新歌榜", "晴天 / 海阔天空 / 稻香", null, HomeTileKind.STATION),
    )

    // ---------- 整屏骨架：与 Root.kt 的 ShellColumn 同一版式 ----------

    @Composable
    private fun Shell(withMini: Boolean, navBarInset: Dp, content: @Composable () -> Unit) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }
            if (withMini) {
                Box(Modifier.padding(start = GySpace.pageH, end = GySpace.pageH, bottom = GySpace.s)) { Mini() }
                // 迷你条略压住内容底边（与生产 ShellColumn 一致：内容是 weight(1f) 的 Box，
                // 迷你条/底栏在 Column 里同属一列，内容列表滚到底时会被盖住一截）。
                Box(Modifier.height(14.dp))
            }
            FoliaBottomNav(items = tabItems(), current = "home", onSelect = {}, navBarInset = navBarInset)
        }
    }

    @Composable
    private fun Mini() {
        FoliaMiniPlayer(
            title = "夜曲",
            subtitle = "周杰伦",
            artwork = null,
            progress = 0.35f,
            isPlaying = true,
            onTogglePlay = {},
            onOpen = {},
            onNext = {},
            playLabel = "播放",
            pauseLabel = "暂停",
            nextLabel = "下一首",
        )
    }

    private fun tabItems() = listOf(
        FoliaNavItem("home", "首页", GuangYueIcons.House),
        FoliaNavItem("search", "搜索", GuangYueIcons.Search),
        FoliaNavItem("profile", "我的", GuangYueIcons.User),
    )

    @Composable
    private fun Wall(
        state: HomeWallState,
        hasAnyPlugin: Boolean = true,
        recommended: List<HomeTile> = this@U6HomeWallScreenshotTest.recommend,
        recent: List<HomeTile> = this@U6HomeWallScreenshotTest.recent,
        charts: List<HomeTile> = this@U6HomeWallScreenshotTest.charts,
    ) {
        HomeWallScreen(
            recommended = recommended,
            recent = recent,
            charts = charts,
            hasAnyPlugin = hasAnyPlugin,
            strings = homeStrings(),
            state = state,
            onOpenTile = {},
            onOpenPlugins = {},
            onOpenPlayer = {},
            onOpenSearch = {},
            playbackTitle = "夜曲",
            playbackArtwork = null,
            playbackProgress = 0.35f,
            isPlaying = true,
            onTogglePlay = {},
            onRetry = {},
        )
    }

    // ---------- 浅 / 深 ----------

    @Test
    fun wallLight() = shoot("u6-wall-light") { Wall(HomeWallState.Content) }

    @Test
    fun wallDark() = shoot("u6-wall-dark", dark = true) { Wall(HomeWallState.Content) }

    // ---------- 加载中 ----------

    @Test
    fun wallLoadingLight() = shoot("u6-loading-light") {
        Wall(HomeWallState.Loading, recommended = emptyList(), recent = emptyList(), charts = emptyList())
    }

    @Test
    fun wallLoadingDark() = shoot("u6-loading-dark", dark = true) {
        Wall(HomeWallState.Loading, recommended = emptyList(), recent = emptyList(), charts = emptyList())
    }

    // ---------- 空态：没音源 / 有音源但没内容 ----------

    @Test
    fun wallNoSourceLight() = shoot("u6-no-source-light") {
        Wall(HomeWallState.Content, hasAnyPlugin = false, recommended = emptyList(), recent = emptyList(), charts = emptyList())
    }

    @Test
    fun wallEmptyLight() = shoot("u6-empty-light") {
        Wall(HomeWallState.Content, hasAnyPlugin = true, recommended = emptyList(), recent = emptyList(), charts = emptyList())
    }

    @Test
    fun wallEmptyDark() = shoot("u6-empty-dark", dark = true) {
        Wall(HomeWallState.Content, hasAnyPlugin = true, recommended = emptyList(), recent = emptyList(), charts = emptyList())
    }

    // ---------- 失败 ----------

    @Test
    fun wallFailedLight() = shoot("u6-failed-light") {
        Wall(HomeWallState.Failed, recommended = emptyList(), recent = emptyList(), charts = emptyList())
    }

    @Test
    fun wallFailedDark() = shoot("u6-failed-dark", dark = true) {
        Wall(HomeWallState.Failed, recommended = emptyList(), recent = emptyList(), charts = emptyList())
    }

    // ---------- 整屏：迷你条 + 底栏 + 手势 inset ----------

    @Test
    fun wallWithMiniPlayerLight() = shoot("u6-mini-light") {
        Shell(withMini = true, navBarInset = GESTURE) { Wall(HomeWallState.Content) }
    }

    @Test
    fun wallThreeKeyInsetLight() = shoot("u6-inset-3key-light") {
        Shell(withMini = true, navBarInset = THREE_KEY) { Wall(HomeWallState.Content) }
    }

    // ---------- 320dp 窄屏 / 字体 1.3 倍 ----------

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h800dp-xhdpi")
    fun wallSmallScreen320dp() = shoot("u6-320dp-light") {
        Shell(withMini = true, navBarInset = GESTURE) { Wall(HomeWallState.Content) }
    }

    @Test
    fun wallFontScale130() = shoot("u6-font130-light", fontScale = 1.3f) {
        Shell(withMini = true, navBarInset = GESTURE) { Wall(HomeWallState.Content) }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h800dp-xhdpi")
    fun wallSmallScreenFontScale130Dark() = shoot("u6-320dp-font130-dark", dark = true, fontScale = 1.3f) {
        Shell(withMini = true, navBarInset = GESTURE) { Wall(HomeWallState.Content) }
    }

    private companion object {
        const val RootTag = "u6-wall"

        /** Android 三键导航栏高度：48dp。 */
        val THREE_KEY = 48.dp

        /** Android 手势条高度：24dp。 */
        val GESTURE = 24.dp
    }
}