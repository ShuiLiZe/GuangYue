package com.guangyue.music.nav

import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.guangyue.core.data.lyrics.LyricTimeline
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
import com.guangyue.feature.player.lyrics.LyricStyleSheet
import com.guangyue.feature.player.lyrics.LyricStyleStrings
import com.guangyue.feature.player.viewmodel.LyricStyleViewModel
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
    const val LYRIC_FLOW = "lyricFlow"
    const val LYRIC_STAGE = "lyricStage"
    /** B2：Bravais 集合层（歌单 / 榜单 / 电台的曲目墙）。 */
    const val COLLECTION = "collection"
    const val PLUGINS = "plugins"
    const val DOWNLOADS = "downloads"
    const val DOWNLOAD_SETTINGS = "downloadSettings"
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
    // 0.0.27：首页样式（封面墙 / 3D 滑卡）。取外壳的 ShellViewModel（这里还在 NavHost 外，作用域是 Activity，与 MainActivity 同一个）。
    val shellVm: com.guangyue.music.ShellViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val homeStyle by shellVm.homeStyle.collectAsStateWithLifecycle()
    val gridCardStyle by shellVm.gridCardStyle.collectAsStateWithLifecycle()
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
                onOpen = { navController.navigate(Routes.LYRIC_FLOW) },
                modifier = Modifier.padding(
                    start = GySpace.pageH,
                    end = GySpace.pageH,
                    bottom = GySpace.s,
                ),
                onNext = { playback.next() },
                playLabel = stringResource(R.string.mini_play),
                pauseLabel = stringResource(R.string.mini_pause),
                nextLabel = stringResource(R.string.mini_next),
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs,
                modeIcon = when (com.guangyue.feature.player.screen.playerMode(playbackState.shuffle, playbackState.repeatMode)) {
                    com.guangyue.feature.player.screen.PlayerMode.Shuffle -> com.guangyue.feature.library.design.icons.GuangYueIcons.Shuffle
                    com.guangyue.feature.player.screen.PlayerMode.LoopOne -> com.guangyue.feature.library.design.icons.GuangYueIcons.Repeat
                    com.guangyue.feature.player.screen.PlayerMode.Sequential -> com.guangyue.feature.library.design.icons.GuangYueIcons.ListMusic
                },
                modeLabel = when (com.guangyue.feature.player.screen.playerMode(playbackState.shuffle, playbackState.repeatMode)) {
                    com.guangyue.feature.player.screen.PlayerMode.Shuffle -> "随机播放"
                    com.guangyue.feature.player.screen.PlayerMode.LoopOne -> "单曲循环"
                    com.guangyue.feature.player.screen.PlayerMode.Sequential -> "顺序播放"
                },
                onCycleMode = {
                    val t = com.guangyue.feature.player.screen.nextPlayerModeTarget(
                        com.guangyue.feature.player.screen.playerMode(playbackState.shuffle, playbackState.repeatMode),
                    )
                    playback.setShuffle(t.shuffle)
                    playback.setRepeatMode(t.repeatMode)
                },
            )
        }
    }

    // B1h：聚焦卡的「正在播放 / 已在队列」按真实播放器判断。砖的 key：首页单曲 track:<key>，集合页 ctrack:<i>:<key>。
    val queueKeys = remember(playbackState.queueSize, playbackState.item?.key) { playback.queueSnapshot().map { it.key }.toSet() }
    fun tileTrackKey(t: com.guangyue.feature.library.home.HomeTile): String? = when {
        t.key.startsWith("track:") -> t.key.removePrefix("track:")
        t.key.startsWith("ctrack:") -> t.key.removePrefix("ctrack:").substringAfter(":")
        else -> null
    }
    val wallIsCurrent: (com.guangyue.feature.library.home.HomeTile) -> Boolean = { t -> tileTrackKey(t)?.let { it == playbackState.item?.key } == true }
    val wallIsQueued: (com.guangyue.feature.library.home.HomeTile) -> Boolean = { t -> tileTrackKey(t)?.let { it in queueKeys } == true }

    // B2：进入的集合（HomeTile 不可序列化；进程被杀后集合页回首页）。
    var openedCollection by remember { mutableStateOf<com.guangyue.feature.library.home.HomeTile?>(null) }
    // 2026-11-14：集合层可以叠（歌手页 → 专辑）：每个返回栈条目记住自己打开的是哪个集合，返回时回到原来的。
    val collectionOfEntry = remember { mutableMapOf<String, com.guangyue.feature.library.home.HomeTile>() }

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

            // B1b：有内容时首页换成 Folia Bravais 墙（推荐歌单 → 榜单 → 最近播放，按 key 去重）；
            // 一块砖都没有（加载中 / 失败 / 无插件）时仍走原来的墙状态页；榜单部分失败不挡墙。
            val wallSections = remember(sheets, recent, charts) {
                listOf("推荐" to vm.recommended(sheets, recent), "榜单" to charts, "最近" to recent).filter { it.second.isNotEmpty() }
            }
            // 网格首页照 Folia 页签：歌单 / 推荐 / 探索 / 专辑 / 本地（空的页签照样在，里面给空态说明）。
            val homeMine by vm.playlistsFlow.collectAsStateWithLifecycle()
            val homeLocal by vm.localTiles.collectAsStateWithLifecycle()
            val gridSections = remember(homeMine, sheets, recent, charts, homeLocal) {
                listOf(
                    "歌单" to homeMine,
                    "推荐" to vm.recommended(sheets, recent),
                    "探索" to charts,
                    "专辑" to emptyList(),
                    "本地" to homeLocal,
                )
            }
            if (wallSections.isNotEmpty() || homeStyle == com.guangyue.core.prefs.HomeStyles.GRID) {
                val homeCtx = androidx.compose.ui.platform.LocalContext.current
                var homePick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<com.guangyue.core.data.model.TrackEntity>?>(null) }
                ShellColumn(
                    content = {
                        if (homeStyle == com.guangyue.core.prefs.HomeStyles.GRID) {
                        // 0.0.27：第二种首页样式「网格」（Folia 网格首页 Grid3DSlider：3D 滑卡），数据与回调同封面墙。
                        com.guangyue.music.ui.home.GridSliderHome(
                            sections = gridSections,
                            initialTab = 1,
                            emptyText = { label ->
                                when (label) {
                                    "歌单" -> "先登录或搜几首歌。登录音源后，这里会显示你的歌单、收藏专辑和电台。"
                                    "推荐" -> if (wallLoading) "正在加载推荐…" else if (wallFailed) "推荐加载失败，稍后再试。" else "当前音源没有推荐内容。"
                                    "探索" -> if (wallLoading) "正在加载…" else "当前音源暂不支持探索。"
                                    "专辑" -> "当前音源暂不支持专辑库。"
                                    "本地" -> "暂无本地音乐。下载的歌会出现在这里。"
                                    else -> "这里还没有内容"
                                }
                            },
                            polaroid = gridCardStyle != com.guangyue.core.prefs.GridCardStyles.IMAGE,
                            // folia-sonnet 手机版首页：没有底栏，标题旁齿轮进设置，页签胶囊末尾带「去别处」的页签。
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            // Folia 手机版：首页背后跑的是播放页同一层可视化背景（不暂停），右下角是平台按钮。
                            background = {
                                val homeStyleVm: LyricStyleViewModel = hiltViewModel()
                                val homeBg by homeStyleVm.background.collectAsStateWithLifecycle()
                                com.guangyue.feature.player.lyrics.visualizer.HomeVisualizerBackground(
                                    coverUrl = playbackState.item?.artworkUri,
                                    dark = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f,
                                    latent = homeBg == "latent",
                                    playing = playbackState.isPlaying,
                                )
                            },
                            emptyAction = { label ->
                                if (label == "歌单") {
                                    val src by vm.currentSource.collectAsStateWithLifecycle()
                                    com.guangyue.music.ui.home.ProviderLoginPill(
                                        label = src?.name,
                                        ink = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                                        onClick = { navController.navigate(Routes.PLUGINS) },
                                    )
                                }
                            },
                            providerButton = {
                                val src by vm.currentSource.collectAsStateWithLifecycle()
                                com.guangyue.music.ui.home.ProviderPill(
                                    label = src?.name,
                                    dark = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f,
                                    onClick = { navController.navigate(Routes.PLUGINS) },
                                )
                            },

                            title = "广乐",
                            inkColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                            groundColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                            onOpenSearch = { navController.navigate(Routes.SEARCH) },
                            onEnqueueTile = { tile -> vm.playTracksOf(tile) { tracks, _ -> playback.appendTracks(tracks) } },
                            onDownloadTile = { tile ->
                                vm.tracksForDownload(tile) { ts ->
                                    if (ts.isNotEmpty()) {
                                        val (added, skipped) = vm.downloadTracks(ts, vm.defaultDownloadQuality)
                                        com.guangyue.feature.library.design.StatusToasts.show(downloadToastText(ts, added, skipped))
                                    }
                                }
                            },
                            onDownloadTileLong = { tile -> vm.tracksForDownload(tile) { ts -> if (ts.isNotEmpty()) homePick = ts } },
                            isCurrent = wallIsCurrent,
                            isQueued = wallIsQueued,
                            playing = playbackState.isPlaying,
                            onTogglePlayback = { playback.toggle() },
                            onEnterPlayback = { navController.navigate(Routes.LYRIC_FLOW) },
                            onEnterTile = { tile ->
                                openedCollection = tile
                                navController.navigate(Routes.COLLECTION)
                            },
                            onOpenTile = { tile ->
                                vm.playTracksOf(tile) { tracks, at ->
                                    playback.setQueueFromTracks(tracks, at, true)
                                    navController.navigate(Routes.LYRIC_FLOW)
                                }
                            },
                        )
                        } else
                        com.guangyue.music.ui.home.BravaisWall(
                            sections = wallSections,
                            title = "广乐",
                            inkColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                            groundColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                            onOpenSearch = { navController.navigate(Routes.SEARCH) },
                            onEnqueueTile = { tile -> vm.playTracksOf(tile) { tracks, _ -> playback.appendTracks(tracks) } },
                            // 2026-11-12：首页聚焦卡单曲下载（只对歌曲块）：点一下按默认音质，长按选音质。
                            onDownloadTile = { tile ->
                                vm.tracksForDownload(tile) { ts ->
                                    if (ts.isNotEmpty()) {
                                        val (added, skipped) = vm.downloadTracks(ts, vm.defaultDownloadQuality)
                                        com.guangyue.feature.library.design.StatusToasts.show(downloadToastText(ts, added, skipped))
                                    }
                                }
                            },
                            onDownloadTileLong = { tile -> vm.tracksForDownload(tile) { ts -> if (ts.isNotEmpty()) homePick = ts } },
                            isCurrent = wallIsCurrent,
                            isQueued = wallIsQueued,
                            playing = playbackState.isPlaying,
                            onTogglePlayback = { playback.toggle() },
                            onEnterPlayback = { navController.navigate(Routes.LYRIC_FLOW) },
                            onEnterTile = { tile ->
                                openedCollection = tile
                                navController.navigate(Routes.COLLECTION)
                            },
                            onOpenTile = { tile ->
                                vm.playTracksOf(tile) { tracks, at ->
                                    playback.setQueueFromTracks(tracks, at, true)
                                    navController.navigate(Routes.LYRIC_FLOW)
                                }
                            },
                        )
                        TrackDownloadPick(homePick, vm) { homePick = null }
                    },
                    miniPlayer = miniPlayer,
                    // 网格首页照 folia-sonnet 手机版不挂底栏，只让出系统导航条的高度。
                    // 网格首页背景铺到系统导航条下面（Folia 整屏背景）；迷你条在时它自己让开导航条。
                    bottomBar = if (homeStyle == com.guangyue.core.prefs.HomeStyles.GRID) null else bottomBar,
                )
                return@composable
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
                                navController.navigate(Routes.LYRIC_FLOW)
                            }
                        },
                        onOpenPlugins = { navController.navigate(Routes.PLUGINS) },
                        onOpenPlayer = { navController.navigate(Routes.LYRIC_FLOW) },
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
        // ---------- B2：集合层（Bravais 墙换成该集合的曲目） ----------
        composable(Routes.COLLECTION) {
            val tile = collectionOfEntry[it.id] ?: openedCollection?.also { t -> collectionOfEntry[it.id] = t }
            if (tile == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }
            // 与首页共用同一个 HomeViewModel：推荐歌单取曲目要用首页已拉到的分类叶子。
            val homeEntry = remember(it) { runCatching { navController.getBackStackEntry(Routes.HOME) }.getOrNull() }
            val vm: HomeViewModel = if (homeEntry != null) hiltViewModel(homeEntry) else hiltViewModel()
            var tracks by remember(tile.key) { mutableStateOf<List<com.guangyue.core.data.model.TrackEntity>?>(null) }
            var failed by remember(tile.key) { mutableStateOf(false) }
            var attempt by remember(tile.key) { mutableStateOf(0) }
            var fillingMeta by remember(tile.key) { mutableStateOf(false) }
            // 2026-11-14：「⋯ 更多」（Folia 集合低频动作）：改名 / 删除只给本地歌单；加入歌单给所有有曲目的集合。
            val localPlaylistId = if (tile.kind == com.guangyue.feature.library.home.HomeTileKind.PLAYLIST) tile.key.substringAfter("playlist:").toLongOrNull() else null
            var shownName by remember(tile.key) { mutableStateOf(tile.title) }
            var seamRename by remember(tile.key) { mutableStateOf(false) }
            var seamDelete by remember(tile.key) { mutableStateOf(false) }
            var seamPick by remember(tile.key) { mutableStateOf(false) }
            // 2026-11-10：我喜欢 / 全部歌曲 / 最近播放（lib:*）跟着收藏数、下载数变就重取，
            // 在播放页取消收藏回来列表就是新的。
            val isLib = tile.key.startsWith(com.guangyue.feature.library.viewmodel.LibraryTiles.PREFIX)
            val likedNow by vm.likedCount.collectAsStateWithLifecycle()
            val downloadedNow by vm.downloadedCount.collectAsStateWithLifecycle()
            val libVersion = if (isLib) "$likedNow/$downloadedNow" else ""
            // 全部歌曲要读手机里的音乐：没授权先问一次，拒了也照样列出下载的歌。
            val collCtx = androidx.compose.ui.platform.LocalContext.current
            val audioPermission = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            ) { granted -> if (granted) { vm.refreshDeviceAudio(); attempt++ } }
            LaunchedEffect(tile.key) {
                if (tile.key == com.guangyue.feature.library.viewmodel.LibraryTiles.ALL &&
                    !com.guangyue.feature.library.viewmodel.DeviceAudio.granted(collCtx)
                ) {
                    runCatching { audioPermission.launch(com.guangyue.feature.library.viewmodel.DeviceAudio.permission) }
                }
            }
            LaunchedEffect(tile.key, attempt, libVersion) {
                failed = false
                // 曲库页重取时保留旧列表直到新列表到（先清空再填回，曲目墙会停在空白）。
                if (!isLib) tracks = null
                vm.loadTracksOf(tile) { list -> if (list == null) failed = true else tracks = list }
            }
            // 歌手页：热门歌曲之后接专辑（Folia projectArtistItems），专辑点进去是专辑集合层。
            val isArtistPage = com.guangyue.feature.library.viewmodel.ArtistTiles.isArtist(tile.key)
            var albumTiles by remember(tile.key) { mutableStateOf<List<com.guangyue.feature.library.home.HomeTile>>(emptyList()) }
            LaunchedEffect(tile.key, attempt) {
                if (isArtistPage) vm.loadArtistAlbums(tile) { got -> albumTiles = got }
            }
            val openAlbum: (com.guangyue.feature.library.home.HomeTile) -> Unit = { t ->
                openedCollection = t
                navController.navigate(Routes.COLLECTION)
            }
            val list = tracks.orEmpty()
            val trackTiles = remember(list) {
                list.mapIndexed { i, t ->
                    com.guangyue.feature.library.home.HomeTile(
                        key = "ctrack:$i:${t.key}",
                        title = t.title,
                        subtitle = t.artist,
                        artwork = t.artwork,
                        kind = com.guangyue.feature.library.home.HomeTileKind.TRACK,
                    )
                }
            }
            val kindLabel = when {
                isLib -> "曲库"
                com.guangyue.feature.library.viewmodel.ArtistTiles.isArtist(tile.key) -> com.guangyue.feature.library.viewmodel.ArtistTiles.kindLabel(tile)
                com.guangyue.feature.library.viewmodel.AlbumTiles.isAlbum(tile.key) -> "专辑"
                else -> when (tile.kind) {
                com.guangyue.feature.library.home.HomeTileKind.STATION -> "榜单"
                else -> "歌单"
                }
            }
            val collectionCtx = androidx.compose.ui.platform.LocalContext.current
            // 2026-11-12：长按下载键 → 选音质弹层。
            var collectionPick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<com.guangyue.core.data.model.TrackEntity>?>(null) }
            // 0.0.19：歌单 / 榜单「全部下载」与单曲下载，结果用 Toast 告诉用户（和搜索页一致）。
            val downloadSome: (List<com.guangyue.core.data.model.TrackEntity>) -> Unit = { picked ->
                val (added, skipped) = vm.downloadTracks(picked, playback.defaultQuality)
                val msg = when {
                    added > 0 && skipped > 0 -> "已加入下载 $added 首（$skipped 首已下载或在下载中）"
                    added > 0 -> if (picked.size == 1) "已加入下载：${picked[0].title}" else "已加入下载 $added 首"
                    else -> if (picked.size == 1) "这首已经下载过或正在下载" else "这些歌都已下载或正在下载"
                }
                com.guangyue.feature.library.design.StatusToasts.show(msg)
            }
            ShellColumn(
                content = {
                    com.guangyue.music.ui.home.BravaisWall(
                        sections = if (albumTiles.isEmpty()) listOf("曲目" to trackTiles) else listOf("曲目" to trackTiles, "专辑" to albumTiles),
                        title = "广乐",
                        inkColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                        groundColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                        onOpenSearch = { navController.navigate(Routes.SEARCH) },
                        onEnqueueTile = { t ->
                            if (com.guangyue.feature.library.viewmodel.AlbumTiles.isAlbum(t.key)) vm.playTracksOf(t) { ts, _ -> playback.appendTracks(ts) }
                            else trackTiles.indexOf(t).takeIf { i -> i >= 0 }?.let { i -> playback.appendTracks(listOf(list[i])) }
                        },
                        onDownloadTile = { t ->
                            if (com.guangyue.feature.library.viewmodel.AlbumTiles.isAlbum(t.key)) vm.tracksForDownload(t) { ts -> if (ts.isNotEmpty()) downloadSome(ts) }
                            else trackTiles.indexOf(t).takeIf { i -> i >= 0 }?.let { i -> downloadSome(listOf(list[i])) }
                        },
                        onDownloadTileLong = { t ->
                            if (com.guangyue.feature.library.viewmodel.AlbumTiles.isAlbum(t.key)) vm.tracksForDownload(t) { ts -> if (ts.isNotEmpty()) collectionPick = ts }
                            else trackTiles.indexOf(t).takeIf { i -> i >= 0 }?.let { i -> collectionPick = listOf(list[i]) }
                        },
                        onEnterTile = openAlbum,
                        // 2026-11-14：列表曲目行长按「查看歌手 / 查看专辑」（Folia 聚焦卡上的链接），本页自己不出。
                        trackLinksOf = { t ->
                            trackTiles.indexOf(t).takeIf { i -> i >= 0 }?.let { i ->
                                vm.trackLinks(list[i], ownKey = tile.key).map { link -> link.label to { openAlbum(link.tile) } }
                            }.orEmpty()
                        },
                        onOpenTile = { t ->
                            if (com.guangyue.feature.library.viewmodel.AlbumTiles.isAlbum(t.key)) {
                                vm.playTracksOf(t) { ts, at ->
                                    playback.setQueueFromTracks(ts, at, true)
                                    navController.navigate(Routes.LYRIC_FLOW)
                                }
                            } else {
                                val i = trackTiles.indexOf(t)
                                if (i >= 0) {
                                    playback.setQueueFromTracks(list, i, true)
                                    navController.navigate(Routes.LYRIC_FLOW)
                                }
                            }
                        },
                            isCurrent = wallIsCurrent,
                            isQueued = wallIsQueued,
                            playing = playbackState.isPlaying,
                            onTogglePlayback = { playback.toggle() },
                            onEnterPlayback = { navController.navigate(Routes.LYRIC_FLOW) },
                        collection = com.guangyue.music.ui.home.SeamCollection(
                            name = shownName,
                            kindLabel = kindLabel,
                            count = tracks?.size,
                            loading = tracks == null && !failed,
                            failed = tracks == null && failed,
                            onBack = { navController.popBackStack() },
                            onPlayAll = {
                                if (list.isNotEmpty()) {
                                    playback.setShuffle(false)
                                    playback.setQueueFromTracks(list, 0, true)
                                    navController.navigate(Routes.LYRIC_FLOW)
                                }
                            },
                            onShuffle = {
                                if (list.isNotEmpty()) {
                                    playback.setQueueFromTracks(list, list.indices.random(), true)
                                    playback.setShuffle(true)
                                    navController.navigate(Routes.LYRIC_FLOW)
                                }
                            },
                            onRetry = { attempt++ },
                            onDownloadAll = { if (list.isNotEmpty()) downloadSome(list) },
                            // 2026-11-14：过滤中「播放全部 / 随机」只放命中的曲目（Folia playScope）。
                            onPlayTiles = { shown, shuffle ->
                                val picked = shown.mapNotNull { t -> trackTiles.indexOf(t).takeIf { i -> i >= 0 }?.let { i -> list[i] } }
                                if (picked.isNotEmpty()) {
                                    if (shuffle) {
                                        playback.setQueueFromTracks(picked, picked.indices.random(), true)
                                        playback.setShuffle(true)
                                    } else {
                                        playback.setShuffle(false)
                                        playback.setQueueFromTracks(picked, 0, true)
                                    }
                                    navController.navigate(Routes.LYRIC_FLOW)
                                }
                            },
                            // 2026-11-10：曲库（我喜欢 / 全部歌曲 / 最近播放）补全歌词与封面。
                            onFillMeta = if (isLib) ({
                                if (list.isNotEmpty() && !fillingMeta) {
                                    fillingMeta = true
                                    com.guangyue.feature.library.design.StatusToasts.show("正在为 ${list.size} 首歌匹配歌词和封面…")
                                    vm.fillMeta(list) { filled, missed ->
                                        fillingMeta = false
                                        attempt++
                                        val msg = when {
                                            filled == 0 && missed == 0 -> "这些歌的歌词和封面都已齐全"
                                            missed == 0 -> "已补全 $filled 首"
                                            else -> "已补全 $filled 首，$missed 首没找到匹配"
                                        }
                                        com.guangyue.feature.library.design.StatusToasts.show(msg)
                                    }
                                }
                            }) else null,
                            fillingMeta = fillingMeta,
                            avatar = if (com.guangyue.feature.library.viewmodel.ArtistTiles.isArtist(tile.key)) tile.artwork else null,
                            // 2026-11-14：歌手简介 / 专辑介绍（Folia BravaisSeamAbout 正文），来自插件搜索结果的 description。
                            description = when {
                                com.guangyue.feature.library.viewmodel.ArtistTiles.isArtist(tile.key) -> com.guangyue.feature.library.viewmodel.ArtistTiles.artistOf(tile.key)?.second?.description
                                com.guangyue.feature.library.viewmodel.AlbumTiles.isAlbum(tile.key) -> com.guangyue.feature.library.viewmodel.AlbumTiles.albumOf(tile.key)?.second?.description
                                else -> null
                            },
                            menu = buildList {
                                if (!isLib && localPlaylistId == null) {
                                    add(com.guangyue.music.ui.home.SeamMenuItem("reload", "重新加载", disabled = tracks == null && !failed) { attempt++ })
                                }
                                if (localPlaylistId != null) {
                                    add(com.guangyue.music.ui.home.SeamMenuItem("rename", "改名") { seamRename = true })
                                }
                                add(com.guangyue.music.ui.home.SeamMenuItem("add-to-playlist", "添加到歌单", disabled = list.isEmpty()) { seamPick = true })
                                if (localPlaylistId != null) {
                                    add(com.guangyue.music.ui.home.SeamMenuItem("delete-collection", "删除歌单", danger = true) { seamDelete = true })
                                }
                            },
                        ),
                    )
                    TrackDownloadPick(collectionPick, vm) { collectionPick = null }
                    val seamRepo = remember { playlistRepositoryOf(collectionCtx) }
                    if (seamPick) {
                        val sheets by seamRepo.playlists().collectAsStateWithLifecycle(initialValue = emptyList())
                        var counts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
                        LaunchedEffect(sheets) {
                            counts = runCatching { seamRepo.trackCounts() }.getOrDefault(emptyMap())
                        }
                        com.guangyue.feature.player.screen.PlaylistSelectionSheet(
                            // 不能把歌单加进它自己。
                            playlists = sheets.filter { p -> p.id != localPlaylistId }
                                .map { p -> com.guangyue.feature.player.screen.PlaylistChoice(p.id, p.name, counts[p.id] ?: 0) },
                            onSelect = { id ->
                                seamRepo.addTracks(id, list)
                                val name = sheets.firstOrNull { p -> p.id == id }?.name.orEmpty()
                                com.guangyue.feature.library.design.StatusToasts.show("已把 ${list.size} 首加入「$name」")
                            },
                            onCreate = { name ->
                                val id = seamRepo.create(name)
                                seamRepo.addTracks(id, list)
                                com.guangyue.feature.library.design.StatusToasts.show("已新建「$name」，共 ${list.size} 首")
                            },
                            onDismiss = { seamPick = false },
                        )
                    }
                    if (seamRename && localPlaylistId != null) {
                        com.guangyue.feature.player.screen.PlaylistNameSheet(
                            title = "给这个歌单改名",
                            hint = "输入歌单名称",
                            initial = shownName,
                            onSave = { name ->
                                seamRepo.rename(localPlaylistId, name)
                                shownName = name
                                com.guangyue.feature.library.design.StatusToasts.show("已改名为「$name」")
                            },
                            onDismiss = { seamRename = false },
                        )
                    }
                    if (seamDelete && localPlaylistId != null) {
                        val scope = androidx.compose.runtime.rememberCoroutineScope()
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { seamDelete = false },
                            title = { androidx.compose.material3.Text("删除「$shownName」？") },
                            text = { androidx.compose.material3.Text("歌单里的歌不会从曲库删除。") },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    seamDelete = false
                                    scope.launch {
                                        seamRepo.delete(localPlaylistId)
                                        com.guangyue.feature.library.design.StatusToasts.show("已删除「$shownName」")
                                        navController.popBackStack()
                                    }
                                }) { androidx.compose.material3.Text("删除", color = androidx.compose.ui.graphics.Color(0xFFEF4444)) }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { seamDelete = false }) { androidx.compose.material3.Text("取消") }
                            },
                        )
                    }
                },
                miniPlayer = miniPlayer,
                bottomBar = bottomBar,
            )
        }

        composable(Routes.SEARCH) {
            val vm: SearchViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            // 0.0.14 真机：搜索页点下载/加入歌单没有任何反馈，看起来像「不能下载」。
            val searchCtx = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.runtime.LaunchedEffect(state.message) {
                state.message?.let {
                    com.guangyue.feature.library.design.StatusToasts.show(it)
                    vm.clearMessage()
                }
            }
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
                                navController.navigate(Routes.LYRIC_FLOW)
                            }
                        },
                        onAddToSheet = { item -> vm.addToCurrentSheet(item) },
                        onDownload = { item -> vm.download(item, playback.defaultQuality.wire) },
                        onPickHistory = { q ->
                            vm.onQueryChange(q)
                            vm.searchNow()
                        },
                        // 2026-11-14：音源有 getAlbumInfo 时专辑进集合层（专辑曲目墙）；否则仍按专辑名再搜一次。
                        onOpenAlbum = { item ->
                            val plugin = state.selected
                            val album = state.albums.firstOrNull { a -> a.platform == item.platform && a.id == item.id }
                            if (plugin != null && plugin.hasAlbumInfo && album != null) {
                                openedCollection = com.guangyue.feature.library.viewmodel.AlbumTiles.tile(plugin.id, album)
                                navController.navigate(Routes.COLLECTION)
                            } else {
                                vm.openAlbum(item)
                            }
                        },
                        // 2026-11-14 歌手页：音源有 getArtistWorks 时进集合层（歌手的歌曲墙 / 列表）；否则仍按歌手名再搜一次。
                        onOpenArtist = { item ->
                            val plugin = state.selected
                            val artist = state.artists.firstOrNull { a -> a.platform == item.platform && a.id == item.id && item.type == "artist" }
                                ?: when {
                                    item.type == "artist" -> com.guangyue.core.plugin.ArtistItem(item.platform, item.id, item.title, item.artwork)
                                    item.artistId != null -> com.guangyue.core.plugin.ArtistItem(item.platform, item.artistId!!, item.displayArtist)
                                    else -> null
                                }
                            if (plugin != null && plugin.hasArtistWorks && artist != null) {
                                openedCollection = com.guangyue.feature.library.viewmodel.ArtistTiles.tile(plugin.id, artist)
                                navController.navigate(Routes.COLLECTION)
                            } else {
                                vm.openArtist(item)
                            }
                        },
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
            val allSongs by home.allSongsCount.collectAsStateWithLifecycle()
            // 2026-11-10：曲库入口都进集合层（以前「我喜欢」跳搜索页、「最近播放」跳首页）。
            fun openLibrary(t: com.guangyue.feature.library.home.HomeTile) {
                openedCollection = t
                navController.navigate(Routes.COLLECTION)
            }

            ShellColumn(
                content = {
                    ProfileScreen(
                        likedCount = liked,
                        downloadedCount = downloaded,
                        recentCount = recent.size,
                        playlists = playlists,
                        isDark = darkMode,
                        onOpenLiked = { openLibrary(com.guangyue.feature.library.viewmodel.LibraryTiles.liked) },
                        onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                        onOpenRecent = { openLibrary(com.guangyue.feature.library.viewmodel.LibraryTiles.recent) },
                        // 歌单与 Folia 一样先进集合层看曲目，再播放全部 / 随机 / 单首。
                        onOpenPlaylist = { tile -> openLibrary(tile) },
                        allSongsCount = allSongs,
                        onOpenAllSongs = { openLibrary(com.guangyue.feature.library.viewmodel.LibraryTiles.all) },
                        onOpenPlugins = { navController.navigate(Routes.PLUGINS) },
                        onOpenDownloadSettings = { navController.navigate(Routes.DOWNLOAD_SETTINGS) },
                        onToggleDark = { onDarkModeChange(!darkMode) },
                        onOpenLicenses = { navController.navigate(Routes.LICENSES) },
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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
                        navController.navigate(Routes.LYRIC_FLOW)
                    }
                },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenPlayer = { navController.navigate(Routes.LYRIC_FLOW) },
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
                loadAccount = { p -> vm.accountOf(p) },
                onSaveAccount = { p, values -> vm.saveAccount(p, values) },
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
                    navController.navigate(Routes.LYRIC_FLOW)
                },
                onPlayDownloaded = { entity ->
                    playback.setQueueFromTracks(listOf(entity), 0, true)
                    navController.navigate(Routes.LYRIC_FLOW)
                },
                onOpenSettings = { navController.navigate(Routes.DOWNLOAD_SETTINGS) { launchSingleTop = true } },
                vm = vm,
            )
        }

        composable(Routes.DOWNLOAD_SETTINGS) {
            com.guangyue.feature.library.screen.DownloadSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) { launchSingleTop = true } },
            )
        }

        composable(Routes.SETTINGS) {
            val lxVm: LxViewModel = hiltViewModel()
            val lxQuality by lxVm.quality.collectAsStateWithLifecycle()
            // S1：设置页（Folia SettingsModal 手机版）。歌词样式与舞台共用 LyricStyleViewModel 的落盘偏好。
            val styleVm: LyricStyleViewModel = hiltViewModel()
            val style by styleVm.style.collectAsStateWithLifecycle()
            val settingsCtx = LocalContext.current
            var defaultQuality by remember { mutableStateOf(playback.defaultQuality) }
            SettingsScreen(
                darkMode = darkMode,
                onDarkModeChange = onDarkModeChange,
                onOpenLicenses = { navController.navigate(Routes.LICENSES) },
                onBack = { navController.popBackStack() },
                lxQuality = lxQuality,
                onPickLxQuality = { lxVm.pickQuality(it) },
                lyricStyles = com.guangyue.feature.player.lyrics.LyricStyle.entries.map { st ->
                    st.id to settingsCtx.getString(
                        when (st) {
                            com.guangyue.feature.player.lyrics.LyricStyle.CLASSIC -> R.string.lyrics_style_classic
                            com.guangyue.feature.player.lyrics.LyricStyle.FUME -> R.string.lyrics_style_fume
                            com.guangyue.feature.player.lyrics.LyricStyle.PARTITA -> R.string.lyrics_style_partita
                            com.guangyue.feature.player.lyrics.LyricStyle.CADENZA -> R.string.lyrics_style_cadenza
                            com.guangyue.feature.player.lyrics.LyricStyle.STILL -> R.string.lyrics_style_still
                            com.guangyue.feature.player.lyrics.LyricStyle.MONET -> R.string.lyrics_style_monet
                            com.guangyue.feature.player.lyrics.LyricStyle.PENDOLO -> R.string.lyrics_style_pendolo
                            com.guangyue.feature.player.lyrics.LyricStyle.NEBBIA -> R.string.lyrics_style_nebbia
                            com.guangyue.feature.player.lyrics.LyricStyle.SONNET -> R.string.lyrics_style_sonnet
                            com.guangyue.feature.player.lyrics.LyricStyle.HANABI -> R.string.lyrics_style_hanabi
                            com.guangyue.feature.player.lyrics.LyricStyle.TEMPERA -> R.string.lyrics_style_tempera
                            com.guangyue.feature.player.lyrics.LyricStyle.LUMIERE -> R.string.lyrics_style_lumiere
                            com.guangyue.feature.player.lyrics.LyricStyle.MACHI -> R.string.lyrics_style_machi
                            com.guangyue.feature.player.lyrics.LyricStyle.DIORAMA -> R.string.lyrics_style_diorama
                            com.guangyue.feature.player.lyrics.LyricStyle.TILT -> R.string.lyrics_style_tilt
                            com.guangyue.feature.player.lyrics.LyricStyle.CLADDAGH -> R.string.lyrics_style_claddagh
                        },
                    )
                },
                backgrounds = listOf("latent" to "隐现", "common" to "通用"),
                background = styleVm.background.collectAsStateWithLifecycle().value,
                onPickBackground = { styleVm.pickBackground(it) },
                lyricStyle = style.id,
                onPickLyricStyle = { id -> styleVm.pickStyle(com.guangyue.feature.player.lyrics.LyricStyle.fromId(id)) },
                playQualities = com.guangyue.core.player.Quality.entries.sortedBy { it.order }.map { it.wire to it.label },
                playQuality = defaultQuality.wire,
                onPickPlayQuality = { w ->
                    val q = com.guangyue.core.player.Quality.fromWire(w)
                    playback.setDefaultQuality(q)
                    defaultQuality = q
                },
                onOpenPlugins = { navController.navigate(Routes.PLUGINS) },
                onOpenDownloads = { navController.navigate(Routes.DOWNLOAD_SETTINGS) },
                homeStyles = HOME_STYLE_OPTIONS,
                homeStyle = homeStyle,
                onPickHomeStyle = { id ->
                    shellVm.setHomeStyle(id)
                    // Folia notifications.homeLayoutGrid「首页布局已切换为…」。
                    com.guangyue.feature.library.design.StatusToasts.show("已切换为" + (HOME_STYLE_OPTIONS.firstOrNull { it.id == id }?.title ?: "") + "首页")
                },
                gridCardStyle = gridCardStyle.takeIf { homeStyle == com.guangyue.core.prefs.HomeStyles.GRID },
                onPickGridCardStyle = { id ->
                    shellVm.setGridCardStyle(id)
                    // Folia notifications.cardStyleImage / cardStyleCard。
                    com.guangyue.feature.library.design.StatusToasts.show(
                        if (id == com.guangyue.core.prefs.GridCardStyles.IMAGE) "卡片样式已切换为纯图片封面" else "卡片样式已切换为拍立得卡片",
                    )
                },
                versionName = com.guangyue.music.BuildConfig.VERSION_NAME,
            )
        }

        composable(Routes.LICENSES) {
            LicensesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }

        // U5：歌词页。数据仍是既有 LyricsViewModel（插件查词 + LRC 解析 + 逐行同步），
        // 文案/空态/点行跳转由新的 LyricFlowScreen 负责；stage（全屏舞台）路由保持原样。
        //
        // V0：在 U5 之上接歌词渲染内核——
        //   1. 逐行 [LyricLine] 经 [LyricTimeline.toLines] 变成内核 [Line]（无逐字时间时词内按字均分）；
        //   2. 当前行交给「流光」(classic) 逐词画布，其余行仍是 U5 档位视图；
        //   3. 封面 url 走封面取色 + 流体背景 + 换歌柔焦（VisualizerBackdrop）；
        //   4. 顶栏右侧「歌词样式」入口打开 LyricStyleSheet，调参落 AppPreferences。
        composable(Routes.LYRIC_FLOW) {
            val vm: LyricsViewModel = hiltViewModel()
            val styleVm: LyricStyleViewModel = hiltViewModel()
            val lyric by vm.lyric.collectAsStateWithLifecycle()
            val sync by vm.syncState.collectAsStateWithLifecycle()
            val loading by vm.loading.collectAsStateWithLifecycle()
            val message by vm.message.collectAsStateWithLifecycle()
            val tuning by styleVm.tuning.collectAsStateWithLifecycle()
            val fumeTuning by styleVm.fumeTuning.collectAsStateWithLifecycle()
            val partitaTuning by styleVm.partitaTuning.collectAsStateWithLifecycle()
            val tiltTuning by styleVm.tiltTuning.collectAsStateWithLifecycle()
            val claddaghTuning by styleVm.claddaghTuning.collectAsStateWithLifecycle()
            val dioramaTuning by styleVm.dioramaTuning.collectAsStateWithLifecycle()
            val subtitleSettings by styleVm.subtitle.collectAsStateWithLifecycle()
            val lyricOffset by vm.offsetMs.collectAsStateWithLifecycle()
            // V1a：当前样式（流光 / 浮名），同一份 styleVm —— 播放页切好、这里直接生效。
            val style by styleVm.style.collectAsStateWithLifecycle()
            val visBackground by styleVm.background.collectAsStateWithLifecycle()
            val lyricContext = LocalContext.current
            var styleSheetOpen by rememberSaveable { mutableStateOf(false) }
            // P1b：面板是舞台上的浮卡（Folia UnifiedPanel），不再整页跳 PLAYER。
            var panelOpen by rememberSaveable { mutableStateOf(false) }
            var panelTab by rememberSaveable { mutableStateOf(com.guangyue.feature.player.screen.StagePanelTab.COVER) }
            var addToPlaylistOpen by rememberSaveable { mutableStateOf(false) }
            androidx.activity.compose.BackHandler(enabled = panelOpen && !styleSheetOpen) { panelOpen = false }
            val kernelLines = remember(lyric) {
                LyricTimeline.toLines(
                    lines = lyric.lines,
                    durationMs = playbackState.durationMs.takeIf { it > 0L },
                )
            }
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
                    kernelLines = kernelLines,
                    classicTuning = tuning,
                    coverUrl = playbackState.item?.artworkUri,
                    // 2026-11-14：Folia NowPlayingToast（正在播放 / 接下来播放）。下一首按 nextUpIndex 推，推不出不预告。
                    showNowPlayingCard = true,
                    nowPlayingNext = remember(playbackState.index, playbackState.queueSize, playbackState.shuffle, playbackState.repeatMode, playbackState.item?.key) {
                        com.guangyue.feature.player.lyrics.nextUpIndex(playbackState.queueSize, playbackState.index, playbackState.shuffle, playbackState.repeatMode)
                            ?.let { playback.queueSnapshot().getOrNull(it) }
                            ?.let { com.guangyue.feature.player.lyrics.NowPlayingCardSong(it.title, it.artist, it.artworkUri) }
                    },
                    onOpenStyleSheet = { styleSheetOpen = true },
                    onOpenPanel = { panelOpen = !panelOpen },
                    panelOpen = panelOpen,
                    lyricOffsetMs = lyricOffset,
                    onNext = { playback.next() },
                    onPrevious = { playback.previous() },
                    modeIcon = stageModeIcon(playbackState.shuffle, playbackState.repeatMode),
                    modeLabel = stageModeLabel(playbackState.shuffle, playbackState.repeatMode),
                    onCycleMode = {
                        val t = com.guangyue.feature.player.screen.nextPlayerModeTarget(
                            com.guangyue.feature.player.screen.playerMode(playbackState.shuffle, playbackState.repeatMode),
                        )
                        playback.setShuffle(t.shuffle)
                        playback.setRepeatMode(t.repeatMode)
                    },
                    styleLabel = lyricContext.getString(R.string.lyrics_style),
                    // V1a：把当前样式透传给歌词画布（流光 / 浮名切换后立刻换渲染器）。
                    // 参数由 fume-draw 加在 LyricFlowScreen 末尾（默认 CLASSIC）；若该层尚未落地，
                    // 本行会在统一收口时由 lead 处理。
                    lyricStyle = style,
                    fumeTuning = fumeTuning,
                    partitaTuning = partitaTuning,
                    tiltTuning = tiltTuning,
                    claddaghTuning = claddaghTuning,
                    dioramaTuning = dioramaTuning,
                    subtitleSettings = subtitleSettings,
                    latentBackground = visBackground == "latent",
                )
                StagePanelHost(
                    open = panelOpen,
                    tab = panelTab,
                    onTab = { panelTab = it },
                    onDismiss = { panelOpen = false },
                    playback = playback,
                    playbackState = playbackState,
                    darkMode = darkMode,
                    accent = accent,
                    lyricStyle = style,
                    onOpenLyricStyle = {
                        panelOpen = false
                        styleSheetOpen = true
                    },
                    // 2026-11-14：以前「加入歌单」直接跳搜索页（错的）。改成 Folia AddToPlaylistHost：
                    // 弹出歌单选择弹层，把当前这首加进选中的歌单，或新建歌单再加。
                    onAddToPlaylist = {
                        panelOpen = false
                        addToPlaylistOpen = true
                    },
                    lyricOffsetMs = lyricOffset,
                    onLyricOffset = vm::setOffset,
                    lyricSourceNote = when {
                        loading -> "加载中"
                        lyric.lines.isEmpty() -> "暂无歌词"
                        else -> (playbackState.item?.origin?.pluginId ?: "").substringBefore("-").ifBlank { "音源" } + " · ${lyric.lines.size} 行"
                    },
                )
                if (addToPlaylistOpen) {
                    val playlistRepo = remember { playlistRepositoryOf(lyricContext) }
                    val sheets by playlistRepo.playlists().collectAsStateWithLifecycle(initialValue = emptyList())
                    var counts by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
                    LaunchedEffect(sheets) {
                        counts = runCatching { playlistRepo.trackCounts() }.getOrDefault(emptyMap())
                    }
                    val song = playbackState.item
                    com.guangyue.feature.player.screen.PlaylistSelectionSheet(
                        playlists = sheets.map { com.guangyue.feature.player.screen.PlaylistChoice(it.id, it.name, counts[it.id] ?: 0) },
                        onSelect = { id ->
                            val row = song?.toCachedTrack() ?: return@PlaylistSelectionSheet
                            playlistRepo.addTracks(id, listOf(row))
                            val name = sheets.firstOrNull { it.id == id }?.name.orEmpty()
                            com.guangyue.feature.library.design.StatusToasts.show("已加入「$name」")
                        },
                        onCreate = { name ->
                            val row = song?.toCachedTrack() ?: return@PlaylistSelectionSheet
                            val id = playlistRepo.create(name)
                            playlistRepo.addTracks(id, listOf(row))
                            com.guangyue.feature.library.design.StatusToasts.show("已新建「$name」并加入")
                        },
                        onDismiss = { addToPlaylistOpen = false },
                    )
                }
                if (styleSheetOpen) {
                    LyricStyleSheet(
                        tuning = tuning,
                        strings = lyricStyleStrings(),
                        styleName = lyricContext.getString(R.string.lyrics_style_classic),
                        onTuning = styleVm::update,
                        onDismiss = { styleSheetOpen = false },
                        accent = accent,
                        style = style,
                        fumeName = lyricContext.getString(R.string.lyrics_style_fume),
                        onStyle = styleVm::pickStyle,
                        fumeTuning = fumeTuning,
                        onFumeTuning = styleVm::updateFume,
                        partitaName = lyricContext.getString(R.string.lyrics_style_partita),
                        partitaTuning = partitaTuning,
                        onPartitaTuning = styleVm::updatePartita,
                        tiltTuning = tiltTuning,
                        onTiltTuning = styleVm::updateTilt,
                        claddaghTuning = claddaghTuning,
                        onCladdaghTuning = styleVm::updateCladdagh,
                        dioramaTuning = dioramaTuning,
                        onDioramaTuning = styleVm::updateDiorama,
                        subtitle = subtitleSettings,
                        onSubtitle = styleVm::updateSubtitle,
                    )
                }
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

@Composable
internal fun lyricStyleStrings(): LyricStyleStrings {
    return LyricStyleStrings(
        title = R.string.lyrics_style_settings,
        classic = R.string.lyrics_style_classic,
        rotation = R.string.lyrics_style_rotation,
        breathing = R.string.lyrics_style_breathing,
        spacing = R.string.lyrics_style_spacing,
        reset = R.string.lyrics_style_reset,
        desc = R.string.lyrics_style_desc,
        close = R.string.lyrics_style_close,
        // V1a：「浮名」（Folia key `ui.visualizerFume`）。
        fume = R.string.lyrics_style_fume,
        partita = R.string.lyrics_style_partita,
        cadenza = R.string.lyrics_style_cadenza,
        still = R.string.lyrics_style_still,
        monet = R.string.lyrics_style_monet,
        pendolo = R.string.lyrics_style_pendolo,
        nebbia = R.string.lyrics_style_nebbia,
        sonnet = R.string.lyrics_style_sonnet,
        hanabi = R.string.lyrics_style_hanabi,
        tempera = R.string.lyrics_style_tempera,
        lumiere = R.string.lyrics_style_lumiere,
        machi = R.string.lyrics_style_machi,
        diorama = R.string.lyrics_style_diorama,
        tilt = R.string.lyrics_style_tilt,
        claddagh = R.string.lyrics_style_claddagh,
        dioramaCameraSpeed = R.string.lyrics_style_dio_camera_speed,
        dioramaMotionAmount = R.string.lyrics_style_dio_motion_amount,
        dioramaAudioReactivity = R.string.lyrics_style_dio_audio_reactivity,
        dioramaGlowEffect = R.string.lyrics_style_dio_glow_effect,
        dioramaSoulEffect = R.string.lyrics_style_dio_soul_effect,
        dioramaSoulActiveEffect = R.string.lyrics_style_dio_soul_active_effect,
        dioramaGradientEffect = R.string.lyrics_style_dio_gradient_effect,
        dioramaEffectStrength = R.string.lyrics_style_dio_effect_strength,
        dioramaKeywordColoring = R.string.lyrics_style_dio_keyword_coloring,
        dioramaGeometry = R.string.lyrics_style_dio_geometry,
        dioramaGeometryMode = R.string.lyrics_style_dio_geometry_mode,
        dioramaGeometryModeClouds = R.string.lyrics_style_dio_geometry_mode_clouds,
        dioramaGeometryModeCorridor = R.string.lyrics_style_dio_geometry_mode_corridor,
        dioramaGeometryStrands = R.string.lyrics_style_dio_geometry_strands,
        dioramaGeometryBlobs = R.string.lyrics_style_dio_geometry_blobs,
        dioramaGeometryRibbons = R.string.lyrics_style_dio_geometry_ribbons,
        dioramaGeometryRings = R.string.lyrics_style_dio_geometry_rings,
        dioramaParticleDensity = R.string.lyrics_style_dio_particle_density,
        dioramaParticleScale = R.string.lyrics_style_dio_particle_scale,
        dioramaParticleGlow = R.string.lyrics_style_dio_particle_glow,
        dioramaParticleGlowIntensity = R.string.lyrics_style_dio_particle_glow_intensity,
        dioramaShowParticles = R.string.lyrics_style_dio_show_particles,
        dioramaMoteCircumference = R.string.lyrics_style_dio_mote_circumference,
        dioramaMoteRadial = R.string.lyrics_style_dio_mote_radial,
        claddaghFocusScaleRatio = R.string.lyrics_style_cl_focus,
        claddaghRadiusScale = R.string.lyrics_style_cl_radius,
        claddaghEllipseTiltDeg = R.string.lyrics_style_cl_tilt,
        claddaghShowAxisLine = R.string.lyrics_style_cl_axis,
        claddaghLetterSpacingOffset = R.string.lyrics_style_cl_spacing,
        tiltSplitProbability = R.string.lyrics_style_tilt_split,
        tiltStyleProbability = R.string.lyrics_style_tilt_style,
        tiltColorScheme = R.string.lyrics_style_tilt_scheme,
        tiltSchemeDefault = R.string.lyrics_style_tilt_scheme_default,
        tiltSchemeSwap = R.string.lyrics_style_tilt_scheme_swap,
        tiltSchemeAccentAll = R.string.lyrics_style_tilt_scheme_accent,
        tiltSchemePrimaryAll = R.string.lyrics_style_tilt_scheme_primary,
        noOptions = R.string.lyrics_style_no_options,
        partitaGuideLines = R.string.lyrics_style_partita_guide,
        partitaSemanticLayout = R.string.lyrics_style_partita_semantic,
        partitaStaggerMin = R.string.lyrics_style_partita_stagger_min,
        partitaStaggerMax = R.string.lyrics_style_partita_stagger_max,
        fumeHidePrintSymbols = R.string.lyrics_style_fume_hide_print,
        fumeGeometricBackground = R.string.lyrics_style_fume_geometric,
        fumeBackgroundObjectOpacity = R.string.lyrics_style_fume_bg_opacity,
        fumeTextHoldRatio = R.string.lyrics_style_fume_text_hold,
        fumeCameraSpeed = R.string.lyrics_style_fume_camera_speed,
        fumeCameraTrackingMode = R.string.lyrics_style_fume_tracking,
        fumeCameraTrackingStepped = R.string.lyrics_style_fume_tracking_stepped,
        fumeCameraTrackingSmooth = R.string.lyrics_style_fume_tracking_smooth,
        fumeGlowIntensity = R.string.lyrics_style_fume_glow,
        fumeHeroScale = R.string.lyrics_style_fume_hero_scale,
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
        playlistCountPattern = stringResource(R.string.library_count_playlist),
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

/** P2b：播放模式 → 胶囊左槽图标 / 文案（顺序 = ListMusic、单曲 = Repeat、随机 = Shuffle）。 */
internal fun stageModeIcon(shuffle: Boolean, repeatMode: Int) =
    when (com.guangyue.feature.player.screen.playerMode(shuffle, repeatMode)) {
        com.guangyue.feature.player.screen.PlayerMode.Shuffle -> com.guangyue.feature.library.design.icons.GuangYueIcons.Shuffle
        com.guangyue.feature.player.screen.PlayerMode.LoopOne -> com.guangyue.feature.library.design.icons.GuangYueIcons.Repeat
        com.guangyue.feature.player.screen.PlayerMode.Sequential -> com.guangyue.feature.library.design.icons.GuangYueIcons.ListMusic
    }

internal fun stageModeLabel(shuffle: Boolean, repeatMode: Int) =
    when (com.guangyue.feature.player.screen.playerMode(shuffle, repeatMode)) {
        com.guangyue.feature.player.screen.PlayerMode.Shuffle -> "随机播放"
        com.guangyue.feature.player.screen.PlayerMode.LoopOne -> "单曲循环"
        com.guangyue.feature.player.screen.PlayerMode.Sequential -> "顺序播放"
    }

/** 0.0.27：「资料库界面」的两套首页（Folia librarySuiteOnboarding：bravais 无限 / grid 经典），顺序照 Folia 引导页；默认是网格（HomeStyles.DEFAULT）。 */
internal val HOME_STYLE_OPTIONS = listOf(
    com.guangyue.feature.library.screen.HomeStyleOption(
        id = com.guangyue.core.prefs.HomeStyles.WALL,
        title = "墙面",
        tag = "无限",
        description = "一整面没有尽头的磁贴墙，拖动着浏览；点一块展开，再点进入或播放。",
    ),
    com.guangyue.feature.library.screen.HomeStyleOption(
        id = com.guangyue.core.prefs.HomeStyles.GRID,
        title = "网格",
        tag = "经典",
        description = "卡片一字排开，左右滑动挑选；点居中的卡片进入歌单或播放。",
    ),
)
