package com.guangyue.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.guangyue.core.player.PlaybackState
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.MarbleBackground
import com.guangyue.feature.library.home.HomeScreen
import com.guangyue.feature.library.home.ShelfCard
import com.guangyue.feature.library.screen.LicensesScreen
import com.guangyue.feature.library.screen.PluginManagerScreen
import com.guangyue.feature.library.screen.SettingsScreen
import com.guangyue.feature.library.viewmodel.HomeTab
import com.guangyue.feature.player.screen.IPlayerRoute
import com.guangyue.feature.search.SearchScreen
import com.guangyue.feature.search.SearchUiState
import com.guangyue.music.theme.GuangYueTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 0.0.5 每个主要页面的 JVM 截图（Robolectric 真渲染 + Roborazzi 抓图）。
 *
 * 没有真机 adb 也能看界面：`GUANGYUE_RECORD_SCREENSHOTS=true ./gradlew :app:testDebugUnitTest`
 * 会把图写到 `docs/ui-0.0.5/`。图里全部用**空状态**（没有假数据）：歌单/搜索结果都是空的，
 * 播放条停在「还没有在听的歌」，这样截图看到的就是首次安装时的真实样子。
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
        compose.onRoot().captureRoboImage("docs/ui-0.0.5/$name.png")
    }

    private val emptyPlayback = PlaybackState()

    @Test
    fun homeLight() = shoot("home-light") { Home(withBar = true) }

    @Test
    fun homeDark() = shoot("home-dark", dark = true) { Home(withBar = true) }

    /** 首页没有底部导航时的样子（导航由 App 外壳注入，这里验证两者都不依赖对方）。 */
    @Test
    fun homeNoBar() = shoot("home-no-bar") { Home(withBar = false) }

    @Composable
    private fun Home(withBar: Boolean) {
        HomeScreen(
            cards = emptyList<ShelfCard>(),
            selectedTab = HomeTab.SHELF.ordinal,
            tabs = HomeTab.entries.map { it.label },
            onTabSelected = {},
            onSearch = {},
            onOpenCard = {},
            onPluginManager = {},
            onDownloads = {},
            playbackTitle = "还没有在听的歌",
            playbackArtwork = null,
            progress = 0f,
            isPlaying = false,
            onTogglePlay = {},
            onOpenNowPlaying = {},
            bottomBar = if (withBar) ({ FauxBottomBar() }) else null,
        )
    }

    @Test
    fun searchEmpty() = shoot("search-empty") {
        SearchScreen(
            state = SearchUiState(
                query = "",
                loading = false,
                platforms = emptyList(),
                available = emptyList(),
                rows = emptyList(),
                resultCounts = emptyMap(),
                errors = emptyList(),
                history = listOf("晴天", "海阔天空"),
                message = null,
            ),
            onQueryChange = {},
            onSearch = {},
            onPickAll = {},
            onPickPlatform = {},
            onPlay = {},
            onAddToSheet = {},
            onDownload = {},
            onBack = {},
        )
    }

    @Test
    fun playerIdle() = shoot("player-idle", dark = true) {
        IPlayerRoute(
            playback = emptyPlayback,
            accent = GuangYueDesign.Blue,
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
    fun settings() = shoot("settings") {
        SettingsScreen(
            darkMode = false,
            onDarkModeChange = {},
            onOpenLicenses = {},
            onBack = {},
        )
    }

    @Test
    fun licenses() = shoot("licenses") {
        LicensesScreen(onBack = {})
    }
}

/**
 * 截图用的底部导航：和 App 外壳里那条视觉一致，但不依赖 NavController，
 * 这样单页截图也能看到「首页 + 迷你播放条 + 导航栏」的完整构图。
 */
@Composable
private fun FauxBottomBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (com.guangyue.feature.library.design.Gyp.isDark) Color(0xFF16203A) else Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 8.dp)
            .padding(top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val items = listOf(
            Triple("首页", Icons.Filled.Home, true),
            Triple("搜索", Icons.Filled.Search, false),
            Triple("插件", Icons.Filled.Extension, false),
            Triple("设置", Icons.Filled.Settings, false),
        )
        items.forEach { (label, icon, active) ->
            val tint = if (active) Color(0xFF1A73E8) else Color(0xFF5B6879)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(width = 44.dp, height = 26.dp),
                ) {
                    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
                }
                Text(
                    label,
                    color = tint,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
