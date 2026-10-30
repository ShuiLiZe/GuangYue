package com.guangyue.music.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
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
import com.guangyue.core.plugin.AlbumItem
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.PluginHealth
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.SearchType
import com.guangyue.feature.library.design.FoliaBottomNav
import com.guangyue.feature.library.design.FoliaNavItem
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.home.FoliaMiniPlayer
import com.guangyue.feature.search.FoliaSearchScreen
import com.guangyue.feature.search.SearchRow
import com.guangyue.feature.search.SearchTab
import com.guangyue.feature.search.SearchUiState
import com.guangyue.music.nav.searchStrings
import com.guangyue.music.ui.design.Folia
import com.guangyue.music.ui.design.FoliaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * U3：Folia 风格搜索页的截图（Roborazzi，360×800dp / xhdpi → 720×1600px）。
 *
 * 覆盖：浅/深 × 初始/加载/结果/空/失败，外加有迷你条、键盘 inset、320dp 宽、字体 1.3 倍。
 * 这里只跑 Compose，不碰 ViewModel / 插件 / 网络，状态直接喂给 [SearchUiState]。
 *
 * 出图：`GUANGYUE_RECORD_SCREENSHOTS=true .\gradlew.bat :app:testDebugUnitTest --tests "*U3SearchScreenshotTest*"`
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class U3SearchScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val outDir: File = run {
        val prop = System.getProperty("roborazzi.output.dir")
        val dir = if (prop.isNullOrBlank()) File("build/roborazzi-u3") else File(prop)
        dir.mkdirs()
        dir
    }

    // ---------------- 截图脚手架 ----------------

    private fun shoot(
        name: String,
        dark: Boolean = false,
        fontScale: Float = 1f,
        content: @Composable () -> Unit,
    ) {
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
        compose.onRoot().captureRoboImage(File(outDir, "$name.png"))
    }

    /** Shell 的竖向骨架：内容区 weight(1f) + 迷你条 + 底栏（与 U2 的 ShellColumn 一致）。 */
    @Composable
    private fun Shell(
        current: String = "search",
        navBarInset: Dp = GESTURE,
        withMiniPlayer: Boolean = false,
        state: SearchUiState,
        imeInset: Dp = 0.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                // 手机上没有真实 IME 时 WindowInsets.ime 恒为 0；这里用参数模拟键盘高度，
                // 复刻生产里 `.imePadding()` 的抬升效果（360×800 键盘约 300dp）。
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = imeInset),
                ) {
                    Search(state)
                }
            }
            if (withMiniPlayer) Mini()
            if (imeInset == 0.dp) {
                FoliaBottomNav(
                    items = tabItems(),
                    current = current,
                    onSelect = {},
                    navBarInset = navBarInset,
                )
            }
        }
    }

    @Composable
    private fun Search(state: SearchUiState) {
        FoliaSearchScreen(
            res = searchStrings(),
            state = state,
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
            onRetry = {},
            onOpenSourceManager = {},
        )
    }

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

    private fun tabItems() = listOf(
        FoliaNavItem("home", "首页", GuangYueIcons.House),
        FoliaNavItem("search", "搜索", GuangYueIcons.Search),
        FoliaNavItem("profile", "我的", GuangYueIcons.User),
    )

    // ---------------- 用例 ----------------

    @Test
    fun u3InitialLight() = shoot("u3-initial-light") { Shell(state = idle()) }

    @Test
    fun u3InitialDark() = shoot("u3-initial-dark", dark = true) { Shell(state = idle()) }

    @Test
    fun u3LoadingLight() = shoot("u3-loading-light") {
        Shell(state = idle().copy(query = "周杰伦", loading = true, searched = true))
    }

    @Test
    fun u3LoadingDark() = shoot("u3-loading-dark", dark = true) {
        Shell(state = idle().copy(query = "周杰伦", loading = true, searched = true))
    }

    @Test
    fun u3ResultsLight() = shoot("u3-results-light") { Shell(state = results()) }

    @Test
    fun u3ResultsDark() = shoot("u3-results-dark", dark = true) { Shell(state = results()) }

    @Test
    fun u3EmptyLight() = shoot("u3-empty-light") { Shell(state = empty()) }

    @Test
    fun u3EmptyDark() = shoot("u3-empty-dark", dark = true) { Shell(state = empty()) }

    @Test
    fun u3FailureLight() = shoot("u3-failure-light") { Shell(state = failure()) }

    @Test
    fun u3FailureDark() = shoot("u3-failure-dark", dark = true) { Shell(state = failure()) }

    /** 有迷你条：结果列表底部给迷你条 + 底栏让位，不互相压。 */
    @Test
    fun u3MiniPlayerLight() = shoot("u3-mini-light") {
        Shell(state = results(), withMiniPlayer = true)
    }

    /** 键盘弹起：底栏让位（ShellColumn 的 collapseOnIme），结果不遮、输入框仍在顶部。 */
    @Test
    fun u3ImeInsetLight() = shoot("u3-ime-light") {
        Shell(state = results(), imeInset = IME)
    }

    /** 320dp 窄屏：长歌名两行省略、右侧两个 48dp 按钮不挤压。 */
    @Test
    @Config(qualifiers = "w320dp-h800dp-xhdpi")
    fun u3NarrowLight() = shoot("u3-320dp-light") { Shell(state = results()) }

    /** 字体 1.3 倍：行高与标题放大后不溢出。 */
    @Test
    fun u3Font130Light() = shoot("u3-font130-light", fontScale = 1.3f) { Shell(state = results()) }

    // ---------------- 数据 ----------------

    /** 初始态：装了两个音源、还没搜、有历史和推荐词。 */
    private fun idle(): SearchUiState = SearchUiState(
        query = "",
        available = plugins(),
        sourceId = "netease-1",
        availableTypes = listOf(SearchTab.TRACKS, SearchTab.ALBUMS, SearchTab.ARTISTS),
        history = listOf("晴天", "海阔天空", "周杰伦", "夜曲"),
        searched = false,
    )

    /** 结果：5 行周杰伦，第 1 行刻意给超长中文标题验证两行省略。 */
    private fun results(): SearchUiState = idle().copy(
        query = "周杰伦",
        searched = true,
        rows = listOf(
            "夜曲" to "叶惠美",
            "以父之名（Live 2004 无与伦比演唱会现场版·重制母带）" to "叶惠美",
            "七里香" to "七里香",
            "告白气球" to "周杰伦的床边故事",
            "晴天" to "叶惠美",
        ).mapIndexed { index, (title, album) ->
            SearchRow(
                pluginId = "netease-1",
                pluginName = "网易云音乐",
                platformLabel = "网易",
                item = MusicItem(
                    platform = "netease",
                    id = "ne-$index",
                    title = title,
                    artist = "周杰伦",
                    album = album,
                    durationMs = 260_000L,
                ),
            )
        },
    )

    private fun empty(): SearchUiState = idle().copy(query = "zzzzzz", searched = true)

    private fun failure(): SearchUiState = idle().copy(
        query = "周杰伦",
        searched = true,
        failure = "网易 音源搜索失败：插件 30 秒没返回结果，换个音源试试",
    )

    private fun plugins(): List<PluginMeta> = listOf(
        PluginMeta(
            id = "netease-1",
            platform = "netease",
            name = "网易云音乐",
            version = "1.9.24",
            supportedSearchType = listOf(SearchType.MUSIC, SearchType.ALBUM, SearchType.ARTIST),
            enabled = true,
            health = PluginHealth.OK,
            sortOrder = 0,
        ),
        PluginMeta(
            id = "qq-1",
            platform = "qq",
            name = "QQ音乐",
            version = "1.9.24",
            supportedSearchType = listOf(SearchType.MUSIC),
            enabled = false,
            health = PluginHealth.OK,
            sortOrder = 1,
        ),
        PluginMeta(
            id = "kuwo-1",
            platform = "kuwo",
            name = "酷我音乐",
            version = "1.9.24",
            supportedSearchType = listOf(SearchType.MUSIC),
            enabled = false,
            health = PluginHealth.UNKNOWN,
            sortOrder = 2,
        ),
    )

    companion object {
        const val RootTag = "u3-search"
        val THREE_KEY = 48.dp
        val GESTURE = 24.dp

        /** 360×800dp 屏上键盘约 300dp 高（与常见中文输入法一致）。 */
        val IME = 300.dp
    }
}

