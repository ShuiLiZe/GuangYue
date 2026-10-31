package com.guangyue.music.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import com.guangyue.music.nav.homeStrings
import com.guangyue.music.ui.home.HomeWallScreen
import com.guangyue.music.ui.home.HomeWallState
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import com.guangyue.feature.library.screen.ProfileScreen
import com.guangyue.feature.search.FoliaSearchScreen
import com.guangyue.music.nav.searchStrings
import com.guangyue.feature.search.SearchUiState
import com.guangyue.music.ui.design.Folia
import com.guangyue.music.ui.design.FoliaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * U2 外壳的 JVM 截图（Robolectric 真渲染 + Roborazzi），照 U1 的
 * `FoliaGalleryScreenshotTest` 写法：360×800dp / xhdpi，浅深各一套，输出目录由
 * `app/build.gradle.kts` 的 `roborazzi.output.dir` 决定（U2 段为 `docs/ui-u2`），
 * 只有 `GUANGYUE_RECORD_SCREENSHOTS=true` 时才写盘。
 *
 * 覆盖：浅/深 × 三个 Tab 根页、有 / 无迷你条、三键导航 inset 与手势 inset、
 * 字体 1.3 倍、小屏 320dp 宽。
 *
 * Robolectric 造不出真的系统栏，所以底部留白由 [FoliaBottomNav.navBarInset] 显式给：
 * 三键导航 48dp、手势条 24dp（Android 规范值）——和生产环境走的是同一条版式路径，
 * 只是把「从 WindowInsets 读」换成「由参数给」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class U2ShellScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val outDir: java.io.File = run {
        val prop = System.getProperty("roborazzi.output.dir")
        val dir = if (prop.isNullOrBlank()) java.io.File("build/roborazzi-u2") else java.io.File(prop)
        dir.mkdirs()
        dir
    }

    /** 整屏 800dp 高：外壳 = 内容 + 迷你条（可选）+ 底栏，与 Root.kt 的 ShellColumn 同一版式。 */
    private fun shoot(name: String, dark: Boolean, fontScale: Float = 1f, content: @Composable () -> Unit) {
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

    @Composable
    private fun Shell(
        current: String,
        navBarInset: Dp,
        withMiniPlayer: Boolean,
        content: @Composable () -> Unit,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content() }
            if (withMiniPlayer) {
                Mini()
            }
            FoliaBottomNav(
                items = tabItems(),
                current = current,
                onSelect = {},
                navBarInset = navBarInset,
            )
        }
    }

    private fun tabItems() = listOf(
        FoliaNavItem("home", "首页", GuangYueIcons.House),
        FoliaNavItem("search", "搜索", GuangYueIcons.Search),
        FoliaNavItem("profile", "我的", GuangYueIcons.User),
    )

    @Composable
    private fun Mini() {
        Box(Modifier.padding(start = GySpace.pageH, end = GySpace.pageH, bottom = GySpace.s)) {
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
    }

    // ---------- 三个 Tab 根页的示例数据 ----------

    private val sampleRecommend = listOf(
        HomeTile("playlist:1", "夜间城市流行", "本地歌单", null, HomeTileKind.PLAYLIST),
        HomeTile("playlist:2", "女声华语精选", "本地歌单", null, HomeTileKind.PLAYLIST),
        HomeTile("playlist:3", "华语摇滚", "本地歌单", null, HomeTileKind.PLAYLIST),
    )

    private val sampleTracks = listOf(
        HomeTile("chart:n1", "夜曲", "周杰伦", null, HomeTileKind.TRACK),
        HomeTile("chart:n2", "红豆", "王菲", null, HomeTileKind.TRACK),
    )

    @Composable
    private fun HomeRoot() {
        HomeWallScreen(
            recommended = sampleRecommend,
            recent = sampleTracks,
            charts = sampleTracks,
            hasAnyPlugin = true,
            strings = homeStrings(),
            state = HomeWallState.Content,
            onOpenTile = {},
            onOpenPlugins = {},
            onOpenPlayer = {},
            playbackTitle = "夜曲",
            playbackArtwork = null,
            playbackProgress = 0.35f,
            isPlaying = true,
            onTogglePlay = {},
            onOpenSearch = {},
            onRetry = {},
        )
    }

    @Composable
    private fun SearchRoot() {
        FoliaSearchScreen(
            res = searchStrings(),
            state = SearchUiState(query = "", history = listOf("晴天", "海阔天空"), searched = false),
            onQueryChange = {},
            onSearch = {},
            onPickSource = {},
            onPlay = {},
            onAddToSheet = {},
            onDownload = {},
            onPickHistory = {},
            onOpenAlbum = {},
            onOpenArtist = {},
            onTabChange = {},
        )
    }

    @Composable
    private fun ProfileRoot() {
        ProfileScreen(
            likedCount = 24,
            downloadedCount = 6,
            recentCount = 50,
            playlists = emptyList(),
            isDark = false,
            onOpenLiked = {},
            onOpenDownloads = {},
            onOpenRecent = {},
            onOpenPlaylist = {},
            onOpenPlugins = {},
            onOpenDownloadSettings = {},
            onToggleDark = {},
            onOpenLicenses = {},
            onOpenAbout = {},
        )
    }

    // ---------- 浅 / 深 × 三个 Tab 根页（手势 inset、无迷你条） ----------

    @Test
    fun homeLight() = shoot("u2-home-light", false) { Shell("home", GESTURE, false) { HomeRoot() } }

    @Test
    fun homeDark() = shoot("u2-home-dark", true) { Shell("home", GESTURE, false) { HomeRoot() } }

    @Test
    fun searchLight() = shoot("u2-search-light", false) { Shell("search", GESTURE, false) { SearchRoot() } }

    @Test
    fun searchDark() = shoot("u2-search-dark", true) { Shell("search", GESTURE, false) { SearchRoot() } }

    @Test
    fun profileLight() = shoot("u2-profile-light", false) { Shell("profile", GESTURE, false) { ProfileRoot() } }

    @Test
    fun profileDark() = shoot("u2-profile-dark", true) { Shell("profile", GESTURE, false) { ProfileRoot() } }

    // ---------- 有无迷你条 ----------

    @Test
    fun homeWithoutMiniPlayer() = shoot("u2-no-mini-light", false) {
        Shell("home", GESTURE, false) { HomeRoot() }
    }

    @Test
    fun homeWithMiniPlayerLight() = shoot("u2-mini-light", false) {
        Shell("home", GESTURE, true) { HomeRoot() }
    }

    @Test
    fun homeWithMiniPlayerDark() = shoot("u2-mini-dark", true) {
        Shell("home", GESTURE, true) { HomeRoot() }
    }

    @Test
    fun profileWithMiniPlayer() = shoot("u2-profile-mini-light", false) {
        Shell("profile", GESTURE, true) { ProfileRoot() }
    }

    @Test
    fun searchWithMiniPlayer() = shoot("u2-search-mini-light", false) {
        Shell("search", GESTURE, true) { SearchRoot() }
    }

    // ---------- 三键导航 inset / 手势 inset ----------

    @Test
    fun homeThreeKeyInset() = shoot("u2-inset-3key-light", false) {
        Shell("home", THREE_KEY, true) { HomeRoot() }
    }

    @Test
    fun homeGestureInset() = shoot("u2-inset-gesture-light", false) {
        Shell("home", GESTURE, true) { HomeRoot() }
    }

    // ---------- 字体 1.3 倍 / 小屏 320dp ----------

    @Test
    fun homeFontScale130() = shoot("u2-font130-light", false, fontScale = 1.3f) {
        Shell("home", GESTURE, true) { HomeRoot() }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w320dp-h800dp-xhdpi")
    fun homeSmallScreen320dp() = shoot("u2-320dp-light", false) {
        Shell("home", GESTURE, true) { HomeRoot() }
    }

    private companion object {
        const val RootTag = "u2-shell"

        /** Android 三键导航栏高度：48dp。 */
        val THREE_KEY = 48.dp

        /** Android 手势条高度：24dp。 */
        val GESTURE = 24.dp
    }
}

