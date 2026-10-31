package com.guangyue.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.guangyue.core.plugin.AlbumItem
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.PluginHealth
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.SearchType
import com.guangyue.core.plugin.lx.LxSource
import com.guangyue.core.player.MediaOrigin
import com.guangyue.core.player.PlaybackState
import com.guangyue.core.player.QueueItem
import com.guangyue.feature.library.design.FoliaBottomNav
import com.guangyue.feature.library.design.FoliaNavItem
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.MarbleBackground
import com.guangyue.feature.library.design.icons.GuangYueIcons
import com.guangyue.feature.library.home.FoliaMiniPlayer
import com.guangyue.music.nav.homeStrings
import com.guangyue.music.ui.home.HomeWallScreen
import com.guangyue.music.ui.home.HomeWallState
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import com.guangyue.feature.library.screen.AboutScreen
import com.guangyue.feature.library.screen.LicensesScreen
import com.guangyue.feature.library.screen.LxSourceScreen
import com.guangyue.feature.library.screen.PluginManagerScreen
import com.guangyue.feature.library.screen.ProfileScreen
import com.guangyue.feature.library.screen.SettingsScreen
import com.guangyue.feature.player.screen.IPlayerRoute
import com.guangyue.feature.search.SearchRow
import com.guangyue.feature.search.FoliaSearchScreen
import com.guangyue.feature.search.SearchTab
import com.guangyue.feature.search.SearchUiState
import com.guangyue.music.nav.searchStrings
import com.guangyue.music.theme.GuangYueTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 0.0.7 主要页面的 JVM 截图（Robolectric 真渲染 + Roborazzi 抓图）。
 *
 * `GUANGYUE_RECORD_SCREENSHOTS=true ./gradlew :app:testDebugUnitTest` 会把图写到
 * `docs/ui-0.0.8/`，尺寸 w411dp-h891dp-xxhdpi。0.0.7 新增四类必交图：
 * 选音源（source-picker-*）、搜索结果（search-result-*）、播放中（player-playing-*）、
 * 失败提示（search-failure / player-failure）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    /**
     * JVM 截图里没有网络：Coil 用内存里预先画好的本地图标作答，好让「插件列表图标」
     * 这张验收图能看到真图标而不是首字占位。真机（gy34）上的图标仍走真网络，见 report.md。
     */
    private fun fakeIcons(): coil3.SingletonImageLoader.Factory = object : coil3.SingletonImageLoader.Factory {
        override fun newImageLoader(context: android.content.Context): coil3.ImageLoader =
            coil3.ImageLoader.Builder(context)
                .memoryCache { coil3.memory.MemoryCache.Builder().maxSizePercent(context, 0.05).build() }
                .components {
                    add(FakePluginIconFetcherFactory())
                }
                .build()
    }

    @Before
    fun installFakeIcons() {
        // 与 V0LyricStyleScreenshotTest 共用同一个安装点：Coil 单例第一次 get 之后 setSafe 会抛。
        installFakePluginIconsOnce()
    }

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

    /** Roborazzi 的路径相对 module 目录，统一指回仓库根上的 docs/ui-0.0.8/。 */
    private fun filePath(name: String): String {
        val module = java.io.File(System.getProperty("user.dir"))
        val root = module.parentFile ?: module
        return java.io.File(root, "docs/ui-0.0.8").resolve(name).absolutePath
    }

    private val emptyPlayback = PlaybackState()

    /** 播放页截图用的队列项（0.0.7 起播放页文案全部取自 [QueueItem]）。 */
    private val nightQu = QueueItem(
        key = "qq:qq-0",
        title = "夜曲",
        artist = "周杰伦",
        album = "十一月的萧邦",
        artworkUri = null,
        origin = MediaOrigin(pluginId = "qq-1", trackKey = "qq:qq-0"),
        rawJson = null,
    )

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
        HomeWallScreen(
            recommended = emptyList(),
            recent = emptyList(),
            charts = emptyList(),
            hasAnyPlugin = false,
            strings = homeStrings(),
            state = HomeWallState.Content,
            onOpenTile = {},
            onOpenPlugins = {},
            onOpenPlayer = {},
            playbackTitle = "还没有在听的歌",
            playbackArtwork = null,
            playbackProgress = 0f,
            isPlaying = false,
            onTogglePlay = {},
            onOpenSearch = {},
            onRetry = {},
            bottomBar = { Bar("home") },
        )
    }

    @Composable
    private fun HomeFilled() {
        HomeWallScreen(
            recommended = sampleRecommend,
            recent = sampleCharts.take(2),
            charts = sampleCharts,
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
    private fun source(
        id: String,
        name: String,
        platform: String,
        types: List<String>,
        health: String,
        sortOrder: Int,
    ) = PluginMeta(
        id = id,
        platform = platform,
        name = name,
        version = "1.9.26",
        supportedSearchType = types,
        enabled = true,
        health = health,
        sortOrder = sortOrder,
        functions = setOf("search", "getMediaSource", "getLyric"),
    )

    /** 「选音源」与「搜索结果」共用的一批音源：全部启用，体检结果各不相同。 */
    private val demoSources = listOf(
        source("qq-1", "QQ音乐", "qq", listOf("music", "album", "artist"), PluginHealth.OK, 1),
        source("netease-1", "网易云音乐", "netease", listOf("music", "album", "artist"), PluginHealth.OK, 2),
        source("kugou-1", "酷狗音乐", "kugou", listOf("music", "album"), PluginHealth.NO_RESULT, 3),
        source("kuwo-1", "酷我音乐", "kuwo", listOf("music"), PluginHealth.NO_MEDIA, 4),
    )

    private fun plugin(
        id: String,
        order: Int,
        health: String,
        enabled: Boolean,
        srcUrl: String? = null,
        iconUrl: String? = null,
    ) = PluginMeta(
        id = id,
        platform = id.substringBefore('-'),
        name = id.substringBefore('-'),
        version = "1.9.24",
        supportedSearchType = listOf(SearchType.MUSIC, SearchType.ALBUM),
        enabled = enabled,
        health = health,
        sortOrder = order,
        functions = setOf("search", "getMediaSource"),
        srcUrl = srcUrl,
        iconUrl = iconUrl,
        installedAt = 0L,
        updatedAt = 0L,
    )

    private fun emptySearchState() = SearchUiState(
        query = "",
        available = emptyList(),
        sourceId = "",
        history = listOf("晴天", "海阔天空"),
    )

    @Composable
    private fun Search(
        state: SearchUiState = emptySearchState(),
        current: String = "search",
    ) {
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
            bottomBar = if (current.isEmpty()) null else ({ Bar(current) }),
        )
    }

    @Test
    fun searchEmptyLight() = shoot("search-empty-light") { Search() }

    @Test
    fun searchEmptyDark() = shoot("search-empty-dark", dark = true) { Search() }

    /** 必交图 ①：顶部音源选择器（单选，当前是「QQ」）。 */
    @Test
    fun sourcePickerLight() = shoot("source-picker-light") {
        Search(
            SearchUiState(
                query = "",
                available = demoSources,
                sourceId = "qq-1",
                availableTypes = listOf(SearchTab.TRACKS, SearchTab.ALBUMS, SearchTab.ARTISTS),
                history = listOf("晴天", "海阔天空", "周杰伦"),
            ),
        )
    }

    /** 必交图 ②：搜「周杰伦」出结果（单曲 Tab，行内带音源角标）。 */
    @Test
    fun searchResultLight() = shoot("search-result-light") {
        Search(
            SearchUiState(
                query = "周杰伦",
                available = demoSources,
                sourceId = "qq-1",
                availableTypes = listOf(SearchTab.TRACKS, SearchTab.ALBUMS, SearchTab.ARTISTS),
                rows = listOf(
                    "夜曲" to "周杰伦",
                    "稻香" to "周杰伦",
                    "七里香" to "周杰伦",
                    "告白气球" to "周杰伦",
                    "晴天" to "周杰伦",
                ).mapIndexed { index, (title, artist) ->
                    SearchRow(
                        pluginId = "qq-1",
                        pluginName = "QQ音乐",
                        platformLabel = "QQ",
                        item = MusicItem(
                            platform = "qq",
                            id = "qq-$index",
                            title = title,
                            artist = artist,
                            album = "叶惠美",
                            durationMs = 260_000L,
                        ),
                    )
                },
                searched = true,
            ),
        )
    }

    /** 必交图 ③：失败提示（「xx 音源搜索失败：原因，换个音源试试」，不是空白页）。 */
    @Test
    fun searchFailureLight() = shoot("search-failure-light") {
        Search(
            SearchUiState(
                query = "周杰伦",
                available = demoSources,
                sourceId = "kugou-1",
                availableTypes = listOf(SearchTab.TRACKS, SearchTab.ALBUMS),
                failure = "酷狗 音源搜索失败：插件超时没返回（30 秒），换个音源试试",
                searched = true,
            ),
        )
    }

    /** 专辑 Tab：结果落在 albums 桶里。 */
    @Test
    fun searchAlbumsLight() = shoot("search-albums-light") {
        Search(
            SearchUiState(
                query = "周杰伦",
                available = demoSources,
                sourceId = "netease-1",
                availableTypes = listOf(SearchTab.TRACKS, SearchTab.ALBUMS, SearchTab.ARTISTS),
                tab = SearchTab.ALBUMS,
                albums = listOf(
                    AlbumItem("netease", "al-1", "叶惠美", "周杰伦", null),
                    AlbumItem("netease", "al-2", "七里香", "周杰伦", null),
                    AlbumItem("netease", "al-3", "魔杰座", "周杰伦", null),
                ),
                searched = true,
            ),
        )
    }

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

    // ---------- 播放页 ----------
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

    /** 必交图 ④：播放中（有曲目、有进度、在播）。 */
    @Test
    fun playerPlayingDark() = shoot("player-playing-dark", dark = true) {
        IPlayerRoute(
            playback = PlaybackState(
                item = nightQu,
                isPlaying = true,
                positionMs = 62_000L,
                durationMs = 226_000L,
                bufferedMs = 180_000L,
                queueSize = 5,
                index = 0,
            ),
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
            lyricPreview = "[00:12.00]一群嗜血的蚂蚁被腐肉所吸引",
            onBack = {},
        )
    }

    /** 必交图 ⑤：播放失败提示（取不到播放地址 → 「用其他音源搜同名歌曲」）。 */
    @Test
    fun playerFailureDark() = shoot("player-failure-dark", dark = true) {
        IPlayerRoute(
            playback = PlaybackState(
                item = nightQu,
                isPlaying = false,
                durationMs = 0L,
                error = "这个音源取不到「夜曲」的播放地址",
            ),
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

    // ---------- 插件管理页 ----------
    @Test
    fun pluginsEmpty() = shoot("plugins-empty") {
        PluginManagerScreen(
            plugins = emptyList(),
            busy = false,
            message = null,
            onBack = {},
            onInstallUri = {},
            onInstallUrl = {},
            onSelectSource = {},
            onDelete = {},
            onCheckUpdate = {},
        )
    }

    /** 必交图 ⑥：插件管理页每行有图标 + 搜索类型 + 健康状态，可开关 / 排序 / 卸载。 */
    @Test
    fun pluginsList() = shoot("plugins-list") {
        PluginManagerScreen(
            plugins = listOf(
                plugin("QQ音乐", 1, PluginHealth.OK, true),
                plugin("网易云音乐", 2, PluginHealth.OK, true),
                plugin("酷狗音乐", 3, PluginHealth.NO_RESULT, false),
                plugin("酷我音乐", 4, PluginHealth.NO_MEDIA, false),
            ),
            busy = false,
            message = "正在用「QQ音乐」搜索和取播放地址。装多个音源不会同时运行，换一个才切过去。",
            onBack = {},
            onInstallUri = {},
            onInstallUrl = {},
            onSelectSource = {},
            onDelete = {},
            onCheckUpdate = {},
        )
    }

    /**
     * 0.0.9 a 第 3 项：图标走「元数据 iconUrl → 平台站点图标 → 首字占位」三级。
     * 这里用真 srcUrl（GitHub 托管，确保不会被当成图标源）+ 真实站点图标地址，
     * 覆盖 QQ/网易云/酷狗/酷我/汽水 5 个源的图标，最后一行的「某某音源」走首字占位。
     */
    private fun iconDemoPlugins(): List<PluginMeta> = listOf(
        plugin(
            "qq-1", 1, PluginHealth.NO_MEDIA, true,
            srcUrl = GH_RAW + "qq-source.plugin.v1.9.26.js",
            iconUrl = "https://y.qq.com/favicon.ico",
        ).copy(name = "QQ音乐"),
        plugin(
            "netease-1", 2, PluginHealth.OK, true,
            srcUrl = GH_RAW + "netease-source.plugin.v1.9.27.js",
            iconUrl = "https://music.163.com/favicon.ico",
        ).copy(name = "网易云音乐"),
        plugin(
            "kugou-1", 3, PluginHealth.NO_RESULT, false,
            srcUrl = GH_RAW + "kugou-source.plugin.v1.9.24.js",
            iconUrl = "https://www.kugou.com/favicon.ico",
        ).copy(name = "酷狗音乐"),
        plugin(
            "kuwo-1", 4, PluginHealth.NO_MEDIA, false,
            srcUrl = GH_RAW + "kuwo-source.plugin.v1.9.24.js",
            iconUrl = "https://www.kuwo.cn/static/image/logo.png",
        ).copy(name = "酷我音乐"),
        plugin(
            "qishui-1", 5, PluginHealth.NO_MEDIA, false,
            srcUrl = GH_RAW + "qishui-source.plugin.v1.9.23.js",
            iconUrl = "https://www.douyin.com/favicon.ico",
        ).copy(name = "汽水音乐"),
        plugin(
            "fanqie-1", 6, PluginHealth.SEARCH_FAILED, false,
            srcUrl = GH_RAW + "fanqie-source.plugin.v1.2.2.js",
        ).copy(name = "番茄畅听", healthNote = "源站返回错误码 6000"),
    )

    private companion object {
        const val GH_RAW =
            "https://raw.githubusercontent.com/hebijunge/musicfree-plugins/master/plugins/"
    }

    @Test
    fun pluginsListIconsLight() = shoot("plugins-list-icons-light") {
        PluginManagerScreen(
            plugins = iconDemoPlugins(),
            busy = false,
            message = "正在用「QQ音乐」搜索和取播放地址。装多个音源不会同时运行，换一个才切过去。",
            onBack = {},
            onInstallUri = {},
            onInstallUrl = {},
            onSelectSource = {},
            onDelete = {},
            onCheckUpdate = {},
            currentSourceId = "qq-1",
        )
    }

    @Test
    fun pluginsListIconsDark() = shoot("plugins-list-icons-dark", dark = true) {
        PluginManagerScreen(
            plugins = iconDemoPlugins(),
            busy = false,
            message = "正在用「QQ音乐」搜索和取播放地址。装多个音源不会同时运行，换一个才切过去。",
            onBack = {},
            onInstallUri = {},
            onInstallUrl = {},
            onSelectSource = {},
            onDelete = {},
            onCheckUpdate = {},
            currentSourceId = "qq-1",
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

    // ---------- 取链源（洛雪自定义源） ----------
    private val lxSamples = listOf(
        LxSource(
            id = "lx-1",
            name = "五源合一",
            version = "1.6.0",
            author = "研发1号",
            enabled = true,
            platforms = listOf("tx", "wy", "kg", "kw", "mg"),
            note = "QQ音乐 / 网易云 / 酷狗 / 酷我 / 咪咕",
        ),
        LxSource(
            id = "lx-2",
            name = "某单源",
            version = "0.3.2",
            enabled = false,
            platforms = emptyList(),
            error = "脚本语法错误，可能不是洛雪自定义源",
        ),
    )

    @Test
    fun lxSourcesLight() = shoot("lx-sources-light") {
        LxSourceScreen(
            sources = lxSamples,
            busy = false,
            message = null,
            errorDetails = null,
            onBack = {},
            onInstallCode = { _, _ -> },
            onInstallUrl = {},
            onInstallUri = {},
            onToggle = { _, _ -> },
            onDelete = {},
            onRecheck = {},
            onRecheckAll = {},
            onMove = { _, _ -> },
        )
    }

    @Test
    fun lxSourcesDark() = shoot("lx-sources-dark", dark = true) {
        LxSourceScreen(
            sources = lxSamples,
            busy = false,
            message = "已导入「五源合一」，5 个平台在用",
            errorDetails = null,
            onBack = {},
            onInstallCode = { _, _ -> },
            onInstallUrl = {},
            onInstallUri = {},
            onToggle = { _, _ -> },
            onDelete = {},
            onRecheck = {},
            onRecheckAll = {},
            onMove = { _, _ -> },
            defaultQuality = "flac",
        )
    }
}


