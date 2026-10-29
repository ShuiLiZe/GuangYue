package com.guangyue.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.guangyue.core.player.PlaybackState
import com.guangyue.feature.library.design.FoliaBottomNav
import com.guangyue.feature.library.design.FoliaNavItem
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.MarbleBackground
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.home.FoliaMiniPlayer
import com.guangyue.feature.library.home.HomeScreen
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import com.guangyue.feature.library.screen.AboutScreen
import com.guangyue.feature.library.screen.LicensesScreen
import com.guangyue.feature.library.screen.PluginManagerScreen
import com.guangyue.feature.library.screen.ProfileScreen
import com.guangyue.feature.library.screen.SettingsScreen
import com.guangyue.feature.player.screen.IPlayerRoute
import com.guangyue.feature.search.SearchScreen
import com.guangyue.feature.search.SearchTab
import com.guangyue.feature.search.SearchUiState
import com.guangyue.music.theme.GuangYueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 0.0.6 每个主要页面的 JVM 截图（Robolectric 真渲染 + Roborazzi 抓图）。
 *
 * 没有真机 adb 也能看界面：`GUANGYUE_RECORD_SCREENSHOTS=true ./gradlew :app:testDebugUnitTest`
 * 会把图写到仓库里的 `docs/ui-0.0.6/`，尺寸 w411dp-h891dp-xxhdpi（与 Folia 桌面窗口不同比例，
 * 但同一构件、同一 token，逐项对照见 docs/ui-0.0.6/REPORT.md）。
 * 图里先用**空状态**（不造假数据）：歌单 / 搜索结果都是空的，播放条停在「还没有在听的歌」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            GuangYueTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize()) {
                    MarbleBackground(dark = dark, modifier = Modifier.fillMaxSize()) {
                        content()
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(filePath("$name.png"))
    }

    /** Roborazzi 的路径相对 module 目录，统一指回仓库根上的 docs/ui-0.0.6/。 */
    private fun filePath(name: String): String {
        val module = java.io.File(System.getProperty("user.dir"))
        val root = module.parentFile ?: module
        return java.io.File(root, "docs/ui-0.0.6").resolve(name).absolutePath
    }

    private val emptyPlayback = PlaybackState()

    private val navItems = listOf(
        FoliaNavItem("home", "首页", GuangYueIcons.House),
        FoliaNavItem("search", "搜索", GuangYueIcons.Search),
        FoliaNavItem("profile", "我的", GuangYueIcons.User),
    )

    @Composable
    private fun Bar(current: String) {
        FoliaBottomNav(items = navItems, current = current, onSelect = {})
    }

    @Composable
    private fun Mini() {
        FoliaMiniPlayer(
            title = "还没有在听的歌",
            subtitle = "广乐",
            artwork = null,
            progress = 0f,
            isPlaying = false,
            onTogglePlay = {},
            onOpen = {},
        )
    }

    // ---------- 首页 ----------
    private val sampleRecommend = listOf(
        HomeTile("playlist:1", "夜间城市流行", "本地歌单", null, HomeTileKind.PLAYLIST),
        HomeTile("playlist:2", "女声华语精选", "本地歌单", null, HomeTileKind.PLAYLIST),
        HomeTile("playlist:3", "华语摇滚", "本地歌单", null, HomeTileKind.PLAYLIST),
    )

    private val sampleCharts = listOf(
        HomeTile("chart:n1", "夜曲", "周杰伦", null, HomeTileKind.TRACK),
        HomeTile("chart:n2", "红豆", "王菲", null, HomeTileKind.TRACK),
        HomeTile("chart:n3", "平凡之路", "朴树", null, HomeTileKind.TRACK),
    )

    @Composable
    private fun HomeEmpty() {
        HomeScreen(
            recommended = emptyList(),
            recent = emptyList(),
            charts = emptyList(),
            hasAnyPlugin = false,
            onOpenTile = {},
            onOpenPlugins = {},
            onOpenPlayer = {},
            playbackTitle = "还没有在听的歌",
            playbackArtwork = null,
            playbackProgress = 0f,
            isPlaying = false,
            onTogglePlay = {},
            bottomBar = { Bar("home") },
        )
    }

    @Composable
    private fun HomeFilled() {
        HomeScreen(
            recommended = sampleRecommend,
            recent = sampleCharts.take(2),
            charts = sampleCharts,
            hasAnyPlugin = true,
            onOpenTile = {},
            onOpenPlugins = {},
            onOpenPlayer = {},
            playbackTitle = "夜曲",
            playbackArtwork = null,
            playbackProgress = 0.35f,
            isPlaying = true,
            onTogglePlay = {},
            bottomBar = { Bar("home") },
            miniPlayer = { Mini() },
        )
    }

    @Test
    fun homeEmptyLight() = shoot("home-empty-light") { HomeEmpty() }

    @Test
    fun homeEmptyDark() = shoot("home-empty-dark", dark = true) { HomeEmpty() }

    @Test
    fun homeFilledLight() = shoot("home-filled-light") { HomeFilled() }

    @Test
    fun homeFilledDark() = shoot("home-filled-dark", dark = true) { HomeFilled() }

    // ---------- 搜索 ----------
    private fun emptySearchState() = SearchUiState(
        query = "",
        loading = false,
        platforms = emptyList(),
        available = emptyList(),
        rows = emptyList(),
        resultCounts = emptyMap(),
        errors = emptyList(),
        history = listOf("晴天", "海阔天空"),
        message = null,
    )

    @Composable
    private fun Search(current: String = "search") {
        SearchScreen(
            state = emptySearchState(),
            onQueryChange = {},
            onSearch = {},
            onPickAll = {},
            onPickPlatform = {},
            onPlay = {},
            onAddToSheet = {},
            onDownload = {},
            onPickHistory = {},
            onOpenAlbum = {},
            onOpenArtist = {},
            tab = SearchTab.TRACKS,
            onTabChange = {},
            bottomBar = if (current.isEmpty()) null else ({ Bar(current) }),
        )
    }

    @Test
    fun searchEmptyLight() = shoot("search-empty-light") { Search() }

    @Test
    fun searchEmptyDark() = shoot("search-empty-dark", dark = true) { Search() }

    // ---------- 我的 ----------
    @Composable
    private fun Profile(dark: Boolean) {
        ProfileScreen(
            likedCount = 0,
            downloadedCount = 0,
            recentCount = 0,
            playlists = emptyList(),
            isDark = dark,
            onOpenLiked = {},
            onOpenDownloads = {},
            onOpenRecent = {},
            onOpenPlaylist = {},
            onOpenPlugins = {},
            onOpenDownloadSettings = {},
            onToggleDark = {},
            onOpenLicenses = {},
            onOpenAbout = {},
            bottomBar = { Bar("profile") },
        )
    }

    @Test
    fun profileLight() = shoot("profile-light") { Profile(false) }

    @Test
    fun profileDark() = shoot("profile-dark", dark = true) { Profile(true) }

    @Test
    fun profileFilled() = shoot("profile-filled") {
        ProfileScreen(
            likedCount = 24,
            downloadedCount = 6,
            recentCount = 50,
            playlists = sampleRecommend,
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
            bottomBar = { Bar("profile") },
        )
    }

    // ---------- 播放页 / 二级页 ----------
    @Test
    fun playerIdleDark() = shoot("player-idle-dark", dark = true) {
        IPlayerRoute(
            playback = emptyPlayback,
            accent = GuangYueDesign.AccentDaylight,
            darkMode = true,
            onTogglePlay = {},
            onNext = {},
            onPrevious = {},
            onSeek = {},
            onCycleRepeat = {},
            onToggleShuffle = {},
            onQuality = {},
            onOpenLyrics = {},
            lyricPreview = "",
            onBack = {},
        )
    }

    @Test
    fun pluginsEmpty() = shoot("plugins-empty") {
        PluginManagerScreen(
            plugins = emptyList(),
            busy = false,
            message = null,
            onBack = {},
            onInstallUri = {},
            onInstallUrl = {},
            onToggle = { _, _ -> },
            onDelete = {},
            onCheckUpdate = {},
        )
    }

    @Test
    fun settingsLight() = shoot("settings-light") {
        SettingsScreen(
            darkMode = false,
            onDarkModeChange = {},
            onOpenLicenses = {},
            onBack = {},
        )
    }

    @Test
    fun settingsDark() = shoot("settings-dark", dark = true) {
        SettingsScreen(
            darkMode = true,
            onDarkModeChange = {},
            onOpenLicenses = {},
            onBack = {},
        )
    }

    @Test
    fun licenses() = shoot("licenses") {
        LicensesScreen(onBack = {})
    }

    @Test
    fun about() = shoot("about") {
        AboutScreen(onBack = {})
    }
}
