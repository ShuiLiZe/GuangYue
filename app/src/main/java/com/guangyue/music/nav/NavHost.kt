package com.guangyue.music.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.guangyue.core.player.PlaybackController
import com.guangyue.music.R
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.design.GySpace
import com.guangyue.feature.library.home.FoliaMiniPlayer
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.music.ui.home.HomeStrings
import com.guangyue.music.ui.home.HomeWallScreen
import com.guangyue.music.ui.home.HomeWallState
import com.guangyue.music.ui.library.LibraryPageScreen
import com.guangyue.music.ui.library.LibraryState
import com.guangyue.music.ui.library.LibraryStrings
import com.guangyue.feature.library.screen.AboutScreen
import com.guangyue.feature.library.screen.DownloadScreen
import com.guangyue.feature.library.screen.LicensesScreen
import com.guangyue.feature.library.screen.LxSourceScreen
import com.guangyue.feature.library.screen.PluginManagerScreen
import com.guangyue.feature.library.screen.ProfileScreen
import com.guangyue.feature.library.screen.SettingsScreen
import com.guangyue.feature.library.viewmodel.DownloadViewModel
import com.guangyue.feature.library.viewmodel.HomeViewModel
import com.guangyue.feature.library.viewmodel.LxViewModel
import com.guangyue.feature.library.viewmodel.PluginViewModel
import com.guangyue.feature.player.lyrics.LyricFlowScreen
import com.guangyue.feature.player.lyrics.LyricStageScreen
import com.guangyue.feature.player.lyrics.LyricStringHost
import com.guangyue.feature.player.screen.IPlayerRoute
import com.guangyue.feature.player.viewmodel.LyricsViewModel
import com.guangyue.feature.search.FoliaSearchScreen
import com.guangyue.feature.search.SearchStrings
import com.guangyue.feature.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val PROFILE = "profile"
    const val PLAYER = "player"
    const val LYRIC_FLOW = "lyricFlow"
    const val LYRIC_STAGE = "lyricStage"
    const val PLUGINS = "plugins"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val LICENSES = "licenses"
    const val ABOUT = "about"

    /** U7 音乐库页（Folia 资料库 / 书库）。 */
    const val LIBRARY = "library"

    /** 取链源（洛雪自定义源）管理页。 */
    const val LX_SOURCES = "lxSources"
}

/**
 * 根导航。
 *
 * 信息架构按手机重排：三个顶层页（首页 / 搜索 / 我的）挂底栏，其余都是二级页或全屏页。
 * 所有页面共享同一个 [PlaybackController]，所以迷你播放条和全屏播放页永远一致。
 * [bottomBar] 由 [GuangYueRoot] 只注入给顶层页，二级页 / 播放页传入 null 即自然隐藏底栏。
 */
@Composable
fun GuangYueNavHost(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit = {},
    playback: PlaybackController,
    navController: NavHostController = rememberNavController(),
    bottomBar: (@Composable () -> Unit)? = null,
) {
    val accent: Color = GuangYueDesign.AccentDaylight
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val playbackState by playback.state.collectAsStateWithLifecycle()

    // 迷你播放条：三个顶层页共用同一份，避免各页各建一条。
    // 0.0.9（U2）：加了「下一首」按钮（走现有播放器 playback.next()），文案全部走 strings.xml。
    val miniPlayer: (@Composable () -> Unit)? = playbackState.item?.let { item ->
        {
            FoliaMiniPlayer(
                title = item.title,
                subtitle = item.artist ?: stringResource(R.string.app_name),
                artwork = item.artworkUri,
                progress = playbackState.progress,
                isPlaying = playbackState.isPlaying,
                onTogglePlay = { playback.toggle() },
                onOpen = { navController.navigate(Routes.PLAYER) },
                modifier = Modifier.padding(
                    start = GySpace.pageH,
                    end = GySpace.pageH,
                    bottom = GySpace.s,
                ),
                onNext = { playback.next() },
                playLabel = stringResource(R.string.mini_play),
                pauseLabel = stringResource(R.string.mini_pause),
                nextLabel = stringResource(R.string.mini_next),
            )
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {

        // ---------- 顶层：首页 ----------
        composable(Routes.HOME) {
            val vm: HomeViewModel = hiltViewModel()
            val playlists by vm.playlistsFlow.collectAsStateWithLifecycle()
            val recent by vm.recentTiles.collectAsStateWithLifecycle()
            val charts by vm.charts.collectAsStateWithLifecycle()
            val sheets by vm.sheets.collectAsStateWithLifecycle()
            val plugins by vm.enabledPlugins.collectAsStateWithLifecycle()
            val source by vm.currentSource.collectAsStateWithLifecycle()
            // U6 首页墙：加载 / 失败从 ViewModel 出，空态由墙自己按数据判。
            val wallLoading by vm.wallLoading.collectAsStateWithLifecycle()
            val wallFailed by vm.wallFailed.collectAsStateWithLifecycle()
            val wallState = if (wallLoading) HomeWallState.Loading
            else if (wallFailed) HomeWallState.Failed
            else HomeWallState.Content

            LaunchedEffect(source?.id) {
                if (source != null) {
                    vm.loadChartsIfNeeded()
                    vm.loadSheetsIfNeeded()
                }
            }

            ShellColumn(
                content = {
                    HomeWallScreen(
                        recommended = vm.recommended(sheets, recent),
                        recent = recent,
                        charts = charts,
                        hasAnyPlugin = plugins.isNotEmpty(),
                        strings = homeStrings(),
                        state = wallState,
                        onOpenTile = { tile ->
                            vm.playTracksOf(tile) { tracks, at ->
                                playback.setQueueFromTracks(tracks, at, true)
                                navController.navigate(Routes.PLAYER)
                            }
                        },
                        onOpenPlugins = { navController.navigate(Routes.PLUGINS) },
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                        playbackTitle = playbackState.item?.title ?: "",
                        playbackArtwork = playbackState.item?.artworkUri,
                        playbackProgress = playbackState.progress,
                        isPlaying = playbackState.isPlaying,
                        onTogglePlay = { playback.toggle() },
                        // 0.0.9（U2）：首页占位空态的「去搜索」入口（内容墙 U6 再做）。
                        onOpenSearch = { navController.navigate(Routes.SEARCH) },
                        // U7：推荐歌单段右上角的「看全部」→ 音乐库页。
                        onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                        onRetry = { vm.retryWall() },
                    )
                },
                miniPlayer = miniPlayer,
                bottomBar = bottomBar,
            )
        }

        // ---------- 顶层：搜索 ----------
        composable(Routes.SEARCH) {
            val vm: SearchViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            ShellColumn(
                content = {
                    FoliaSearchScreen(
                        res = searchStrings(),
                        state = state,
                        onQueryChange = vm::onQueryChange,
                        onSearch = vm::searchNow,
                        // 0.0.7：音源单选，只用用户选中的那一个插件搜
                        onPickSource = { id -> vm.selectSource(id) },
                        onPlay = { index ->
                            vm.playFrom(index) { items, at ->
                                playback.setQueue(items, at, true)
                                navController.navigate(Routes.PLAYER)
                            }
                        },
                        onAddToSheet = { item -> vm.addToCurrentSheet(item) },
                        onDownload = { item -> vm.download(item, playback.defaultQuality.wire) },
                        onPickHistory = { q ->
                            vm.onQueryChange(q)
                            vm.searchNow()
                        },
                        onOpenAlbum = { item -> vm.openAlbum(item) },
                        onOpenArtist = { item -> vm.openArtist(item) },
                        onTabChange = { vm.onTabChange(it) },
                        // 0.0.9 a-1：失败提示条里的「重试」——清该插件缓存后真重发一次请求。
                        onRetry = { vm.retry() },
                        onOpenSourceManager = { navController.navigate(Routes.PLUGINS) },
                    )
                },
                miniPlayer = miniPlayer,
                bottomBar = bottomBar,
            )
        }

        // ---------- 顶层：我的 ----------
        composable(Routes.PROFILE) {
            val home: HomeViewModel = hiltViewModel()
            val playlists by home.playlistsFlow.collectAsStateWithLifecycle()
            val recent by home.recentTiles.collectAsStateWithLifecycle()
            val liked by home.likedCount.collectAsStateWithLifecycle()
            val downloaded by home.downloadedCount.collectAsStateWithLifecycle()

            ShellColumn(
                content = {
                    ProfileScreen(
                        likedCount = liked,
                        downloadedCount = downloaded,
                        recentCount = recent.size,
                        playlists = playlists,
                        isDark = darkMode,
                        onOpenLiked = { navController.navigate(Routes.SEARCH) },
                        onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                        onOpenRecent = { navController.navigate(Routes.HOME) },
                        onOpenPlaylist = { tile ->
                            home.playTracksOf(tile) { tracks, at ->
                                playback.setQueueFromTracks(tracks, at, true)
                                navController.navigate(Routes.PLAYER)
                            }
                        },
                        onOpenPlugins = { navController.navigate(Routes.PLUGINS) },
                        onOpenDownloadSettings = { navController.navigate(Routes.DOWNLOADS) },
                        onToggleDark = { onDarkModeChange(!darkMode) },
                        onOpenLicenses = { navController.navigate(Routes.LICENSES) },
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    )
                },
                miniPlayer = miniPlayer,
                bottomBar = bottomBar,
            )
        }

        // ---------- 二级页：音乐库 ----------
        // U7：Folia 的资料库 / 书库（我的歌单 / 收藏 / 本地 / 最近四个分区）。
        composable(Routes.LIBRARY) {
            val vm: HomeViewModel = hiltViewModel()
            val playlists by vm.playlistsFlow.collectAsStateWithLifecycle()
            val favorites by vm.favoriteTiles.collectAsStateWithLifecycle()
            val local by vm.localTiles.collectAsStateWithLifecycle()
            val recent by vm.recentTiles.collectAsStateWithLifecycle()
            val loading by vm.libraryLoading.collectAsStateWithLifecycle()
            val failed by vm.libraryFailed.collectAsStateWithLifecycle()
            val state = if (loading) LibraryState.Loading
            else if (failed) LibraryState.Failed
            else LibraryState.Content

            LibraryPageScreen(
                playlists = playlists,
                favorites = favorites,
                local = local,
                recent = recent,
                strings = libraryStrings(),
                state = state,
                onOpenTile = { tile ->
                    vm.playTracksOf(tile) { tracks, at ->
                        playback.setQueueFromTracks(tracks, at, true)
                        navController.navigate(Routes.PLAYER)
                    }
                },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenPlayer = { navController.navigate(Routes.PLAYER) },
                onRetry = { vm.retryLibrary() },
                onBack = { navController.popBackStack() },
            )
        }

        // ---------- 二级页 ----------
        composable(Routes.PLUGINS) {
            val vm: PluginViewModel = hiltViewModel()
            val plugins by vm.plugins.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()
            val message by vm.message.collectAsStateWithLifecycle()
            val messageDetails by vm.messageDetails.collectAsStateWithLifecycle()
            val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
            val batch by vm.batchProgress.collectAsStateWithLifecycle()
            val currentSource by vm.currentSource.collectAsStateWithLifecycle()
            val hideUnavailable by vm.hideUnavailable.collectAsStateWithLifecycle()
            val ctx = LocalContext.current
            PluginManagerScreen(
                plugins = plugins,
                busy = busy,
                message = message,
                onInstallUri = { uri ->
                    scope.launch {
                        val text = withContext(Dispatchers.IO) {
                            runCatching {
                                ctx.contentResolver.openInputStream(uri)?.use {
                                    it.readBytes().toString(Charsets.UTF_8)
                                }
                            }.getOrNull()
                        }
                        if (text.isNullOrBlank()) vm.report("读取文件失败") else vm.installFromText(text)
                    }
                },
                onInstallUrl = { url -> vm.installFromUrl(url) },
                onSelectSource = { plugin -> vm.selectSource(plugin.id) },
                onDelete = { vm.delete(it) },
                onCheckUpdate = { vm.checkUpdate(it) },
                onBack = { navController.popBackStack() },
                currentSourceId = currentSource,
                subscriptions = subscriptions,
                batchStage = batch.stage,
                batchFraction = batch.fraction,
                onInstallOfficial = { vm.installOfficial() },
                onCheckAllUpdates = { vm.checkAllUpdates() },
                onForgetSubscription = { vm.forgetSubscription(it) },
                errorDetails = messageDetails,
                onRecheckAll = { vm.recheckAll() },
                onMove = { plugin, to -> vm.moveTo(plugin.id, to) },
                hideUnavailable = hideUnavailable,
                onToggleHideUnavailable = { vm.toggleHideUnavailable() },
                onOpenLxSources = { navController.navigate(Routes.LX_SOURCES) },
            )
        }

        // 取链源（洛雪自定义源）：插件取不到链时的第二出口，与搜索音源是两层不同的东西。
        composable(Routes.LX_SOURCES) {
            val vm: LxViewModel = hiltViewModel()
            val sources by vm.sources.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()
            val message by vm.message.collectAsStateWithLifecycle()
            val details by vm.errorDetails.collectAsStateWithLifecycle()
            val quality by vm.quality.collectAsStateWithLifecycle()
            LxSourceScreen(
                sources = sources,
                busy = busy,
                message = message,
                errorDetails = details,
                onBack = { navController.popBackStack() },
                onInstallCode = { code, src -> vm.installFromCode(code, src) },
                onInstallUrl = { url -> vm.installFromUrl(url) },
                onInstallUri = { uri -> vm.installFromUri(uri) },
                onToggle = { source, on -> vm.toggle(source, on) },
                onDelete = { vm.delete(it) },
                onRecheck = { vm.recheck(it) },
                onRecheckAll = { vm.recheckAll() },
                onMove = { source, to -> vm.move(source, to) },
                defaultQuality = quality,
                onPickQuality = { vm.pickQuality(it) },
            )
        }

        composable(Routes.DOWNLOADS) {
            val vm: DownloadViewModel = hiltViewModel()
            DownloadScreen(
                onBack = { navController.popBackStack() },
                onPlayLocal = { uri ->
                    playback.setQueueFromTracks(
                        listOf(
                            com.guangyue.core.data.model.TrackEntity(
                                key = uri,
                                title = uri.substringAfterLast('/'),
                                artist = "本地下载",
                                localUri = uri,
                                source = "local",
                            ),
                        ),
                        0,
                        true,
                    )
                    navController.navigate(Routes.PLAYER)
                },
                vm = vm,
            )
        }

        composable(Routes.SETTINGS) {
            val lxVm: LxViewModel = hiltViewModel()
            val lxQuality by lxVm.quality.collectAsStateWithLifecycle()
            SettingsScreen(
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                onOpenLicenses = { navController.navigate(Routes.LICENSES) },
                onBack = { navController.popBackStack() },
                lxQuality = lxQuality,
                onPickLxQuality = { lxVm.pickQuality(it) },
            )
        }

        composable(Routes.LICENSES) {
            LicensesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        // ---------- 全屏播放 / 歌词 ----------
        composable(Routes.PLAYER) {
            IPlayerRoute(
                playback = playback,
                playbackState = playbackState,
                darkMode = darkMode,
                accent = accent,
                onOpenLyrics = { navController.navigate(Routes.LYRIC_FLOW) },
                onAddToPlaylist = { navController.navigate(Routes.SEARCH) },
                onBack = { navController.popBackStack() },
            )
        }

        // U5：歌词页。数据仍是既有 LyricsViewModel（插件查词 + LRC 解析 + 逐行同步），
        // 文案/空态/点行跳转由新的 LyricFlowScreen 负责；stage（全屏舞台）路由保持原样。
        composable(Routes.LYRIC_FLOW) {
            val vm: LyricsViewModel = hiltViewModel()
            val lyric by vm.lyric.collectAsStateWithLifecycle()
            val sync by vm.syncState.collectAsStateWithLifecycle()
            val loading by vm.loading.collectAsStateWithLifecycle()
            val message by vm.message.collectAsStateWithLifecycle()
            val lyricContext = LocalContext.current
            CompositionLocalProvider(LyricStringHost provides { id -> lyricContext.getString(id) }) {
                LyricFlowScreen(
                    lyrics = lyric.lines,
                    currentIndex = sync.index,
                    strings = lyricStrings(),
                    playback = playbackState,
                    accent = accent,
                    onBack = { navController.popBackStack() },
                    onSeekToLine = { index ->
                        vm.seekToLine(index)
                        vm.resumeAutoScroll()
                    },
                    onManualScroll = { vm.onManualScroll() },
                    onTogglePlay = { playback.toggle() },
                    onSeek = { fraction ->
                        playback.seekTo((fraction * playbackState.durationMs).toLong())
                    },
                    artist = playbackState.item?.artist ?: lyric.artist,
                    source = playbackState.item?.origin?.pluginId,
                    loading = loading,
                    failure = message,
                    darkMode = darkMode,
                )
            }
        }

        composable(Routes.LYRIC_STAGE) {
            val vm: LyricsViewModel = hiltViewModel()
            val sync by vm.syncState.collectAsStateWithLifecycle()
            LyricStageScreen(
                vm = vm,
                state = sync,
                playback = playbackState,
                onTogglePlay = { playback.toggle() },
                onBack = { navController.navigate(Routes.LYRIC_FLOW) },
            )
        }
    }
}

/**
 * U3：把 `strings.xml` 里的 14 条搜索页文案交给 feature 模块。
 *
 * 文案本体在 `app/src/main/res/values/strings.xml`（feature 模块没有 R，只能拿 id），
 * 这里一次取齐，避免 Composable 里散着 `stringResource(R.string.…)` 十几处。
 */
@Composable
internal fun searchStrings(): SearchStrings {
    return SearchStrings(
        placeholder = R.string.search_placeholder_folia,
        clear = R.string.search_clear,
        searching = R.string.search_searching,
        noResults = R.string.search_no_results,
        emptySubtitle = R.string.search_empty_subtitle,
        emptyCategory = R.string.search_empty_category,
        retry = R.string.search_retry,
        history = R.string.search_history,
        suggestions = R.string.search_suggestions,
        addToPlaylist = R.string.search_add_to_playlist,
        download = R.string.search_download,
        noSource = R.string.search_no_source,
            noSourceHint = R.string.search_no_source_hint,
        openPlugins = R.string.search_open_plugins,
    )
}

/**
 * U6：首页墙的 19 条中文文案，一次取齐交给 app 侧 [HomeStrings]。
 *
 * 与 [searchStrings] 同一套理由：文案本体在 `app/src/main/res/values/strings.xml`，
 * 每条都在那里标了 Folia 的 `zh-CN.ts` 键名或「自拟」。
 */
@Composable
internal fun homeStrings(): HomeStrings {
    return HomeStrings(
        greeting = stringResource(R.string.home_wall_greeting),
        greetingGuest = stringResource(R.string.home_wall_greeting_guest),
        search = stringResource(R.string.home_wall_search),
        recommendTitle = stringResource(R.string.home_wall_recommend_title),
        recentTitle = stringResource(R.string.home_wall_recent_title),
        chartsTitle = stringResource(R.string.home_wall_charts_title),
        loading = stringResource(R.string.home_wall_loading),
        failed = stringResource(R.string.home_wall_failed),
        failedHint = stringResource(R.string.home_wall_failed_hint),
        retry = stringResource(R.string.home_wall_retry),
        noSource = stringResource(R.string.home_wall_no_source),
        noSourceHint = stringResource(R.string.home_wall_no_source_hint),
        noSourceAction = stringResource(R.string.home_wall_no_source_action),
        empty = stringResource(R.string.home_wall_empty),
        emptyHint = stringResource(R.string.home_wall_empty_hint),
        emptyAction = stringResource(R.string.home_wall_empty_action),
        openPlayer = stringResource(R.string.home_wall_open_player),
        scrollLeft = stringResource(R.string.home_wall_scroll_left),
        scrollRight = stringResource(R.string.home_wall_scroll_right),
        showAll = stringResource(R.string.library_show_all),
    )
}

/**
 * U7：音乐库页的 19 条中文文案，一次取齐交给 app 侧 [LibraryStrings]。
 *
 * 与 [searchStrings] / [homeStrings] 同一套理由：文案本体在
 * `app/src/main/res/values/strings.xml`，每条都在那里标了 Folia 的 `zh-CN.ts` 键名或「自拟」。
 */
@Composable
internal fun libraryStrings(): LibraryStrings {
    return LibraryStrings(
        title = stringResource(R.string.library_title),
        playlistsTitle = stringResource(R.string.library_playlists_title),
        favoritesTitle = stringResource(R.string.library_favorites_title),
        favoritesSubtitle = stringResource(R.string.library_favorites_subtitle),
        localTitle = stringResource(R.string.library_local_title),
        recentTitle = stringResource(R.string.library_recent_title),
        countPattern = stringResource(R.string.library_count),
        loading = stringResource(R.string.library_loading),
        failed = stringResource(R.string.library_failed),
        failedHint = stringResource(R.string.library_failed_hint),
        retry = stringResource(R.string.library_retry),
        empty = stringResource(R.string.library_empty),
        emptyHint = stringResource(R.string.library_empty_hint),
        emptyAction = stringResource(R.string.library_empty_action),
        emptyPlaylists = stringResource(R.string.library_empty_playlists),
        emptyFavorites = stringResource(R.string.library_empty_favorites),
        emptyLocal = stringResource(R.string.library_empty_local),
        emptyRecent = stringResource(R.string.library_empty_recent),
        openPlayer = stringResource(R.string.library_open_player),
    )
}


