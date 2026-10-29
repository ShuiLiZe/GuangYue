package com.guangyue.music.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.guangyue.core.player.PlaybackController
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.library.home.HomeScreen
import com.guangyue.feature.library.home.ShelfCard
import com.guangyue.feature.library.screen.PluginManagerScreen
import com.guangyue.feature.library.viewmodel.HomeTab
import com.guangyue.feature.library.viewmodel.HomeViewModel
import com.guangyue.feature.library.viewmodel.PluginViewModel
import com.guangyue.feature.player.lyrics.LyricFlowScreen
import com.guangyue.feature.player.lyrics.LyricStageScreen
import com.guangyue.feature.player.screen.IPlayerRoute
import com.guangyue.feature.player.viewmodel.LyricsViewModel
import com.guangyue.feature.search.SearchScreen
import com.guangyue.feature.search.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val PLAYER = "player"
    const val LYRIC_FLOW = "lyricFlow"
    const val LYRIC_STAGE = "lyricStage"
    const val PLUGINS = "plugins"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val LICENSES = "licenses"
}

/**
 * 根导航。
 *
 * 所有页面共享同一个 [PlaybackController] 与歌词状态，所以首页迷你播放条和全屏播放页永远一致。
 * 播放页 / 歌词页是全屏路由（[GuangYueRoot] 会在这些路由下隐藏底部导航）。
 */
@Composable
fun GuangYueNavHost(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit = {},
    playback: PlaybackController,
    navController: NavHostController = rememberNavController(),
    rootBar: (@Composable () -> Unit)? = null,
) {
    val accent: Color = GuangYueDesign.Blue
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val playbackState by playback.state.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            val vm: HomeViewModel = hiltViewModel()
            val tab by vm.tab.collectAsStateWithLifecycle()
            val cards by vm.cards.collectAsStateWithLifecycle()
            val tracks by vm.currentTracks.collectAsStateWithLifecycle()

            HomeScreen(
                cards = cards,
                selectedTab = tab.ordinal,
                tabs = HomeTab.entries.map { it.label },
                onTabSelected = { vm.selectTab(HomeTab.entries[it]) },
                onSearch = { navController.navigate(Routes.SEARCH) },
                onOpenCard = { _: ShelfCard ->
                    if (tracks.isNotEmpty()) {
                        playback.setQueueFromTracks(tracks, 0, true)
                        navController.navigate(Routes.PLAYER)
                    }
                },
                onPluginManager = { navController.navigate(Routes.PLUGINS) },
                onDownloads = { navController.navigate(Routes.DOWNLOADS) },
                playbackTitle = playbackState.item?.title ?: "还没有在听的歌",
                playbackArtwork = playbackState.item?.artworkUri,
                progress = playbackState.progress,
                isPlaying = playbackState.isPlaying,
                onTogglePlay = { playback.toggle() },
                onOpenNowPlaying = {
                    if (playbackState.item != null) navController.navigate(Routes.PLAYER)
                },
                bottomBar = rootBar,
            )
        }

        composable(Routes.SEARCH) {
            val vm: SearchViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxSize()) {
            SearchScreen(
                state = state,
                onQueryChange = vm::onQueryChange,
                onSearch = vm::search,
                onPickAll = { vm.selectPlatform("") },
                onPickPlatform = { id -> vm.selectPlatform(id) },
                onPlay = { index ->
                    vm.playFrom(index) { items, at ->
                        playback.setQueue(items, at, true)
                        navController.navigate(Routes.PLAYER)
                    }
                },
                onAddToSheet = { item -> vm.addToCurrentSheet(item) },
                onDownload = { item -> vm.download(item, playback.defaultQuality.wire) },
                onBack = { navController.popBackStack() },
            )
            rootBar?.invoke()
            }
        }

        composable(Routes.SETTINGS) {
            Column(Modifier.fillMaxSize()) {
                com.guangyue.feature.library.screen.SettingsScreen(
                    darkMode = darkMode,
                    onDarkModeChange = onDarkModeChange,
                    onOpenLicenses = { navController.navigate(Routes.LICENSES) },
                    onBack = { navController.popBackStack() },
                )
                rootBar?.invoke()
            }
        }

        composable(Routes.LICENSES) {
            com.guangyue.feature.library.screen.LicensesScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PLAYER) {
            IPlayerRoute(
                playback = playbackState,
                accent = accent,
                darkMode = darkMode,
                onTogglePlay = { playback.toggle() },
                onNext = { playback.next() },
                onPrevious = { playback.previous() },
                onSeek = { playback.seekToProgress(it) },
                onCycleRepeat = { playback.cycleRepeat() },
                onToggleShuffle = { playback.setShuffle(!playbackState.shuffle) },
                onQuality = {
                    // 音质按钮按「插件支持的档位」循环切换；没有插件档位时退回内置全集。
                    val current = playbackState.item?.origin?.quality ?: playback.defaultQuality
                    val ladder = com.guangyue.core.player.QualityLadder.of(
                        playbackState.item?.origin?.availableQualities,
                    ).ifEmpty { com.guangyue.core.player.QualityLadder.default() }
                    playback.setQuality(com.guangyue.core.player.QualityLadder.cycle(ladder, current))
                },
                onOpenLyrics = { navController.navigate(Routes.LYRIC_FLOW) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.LYRIC_FLOW) {
            val vm: LyricsViewModel = hiltViewModel()
            val sync by vm.syncState.collectAsStateWithLifecycle()
            LyricFlowScreen(
                vm = vm,
                state = sync,
                playback = playbackState,
                accent = accent,
                onBack = { navController.popBackStack() },
                onSwitchStage = { navController.navigate(Routes.LYRIC_STAGE) },
            )
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

        composable(Routes.DOWNLOADS) {
            com.guangyue.feature.library.screen.DownloadScreen(
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
            )
        }

        composable(Routes.PLUGINS) {
            val vm: PluginViewModel = hiltViewModel()
            Column(Modifier.fillMaxSize()) {
            val plugins by vm.plugins.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()
            val message by vm.message.collectAsStateWithLifecycle()
            val messageDetails by vm.messageDetails.collectAsStateWithLifecycle()
            val subscriptions by vm.subscriptions.collectAsStateWithLifecycle()
            val batch by vm.batchProgress.collectAsStateWithLifecycle()
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
                onToggle = { plugin, enabled -> vm.toggle(plugin, enabled) },
                onDelete = { vm.delete(it) },
                onCheckUpdate = { vm.checkUpdate(it) },
                onBack = { navController.popBackStack() },
                subscriptions = subscriptions,
                batchStage = batch.stage,
                batchFraction = batch.fraction,
                onInstallOfficial = { vm.installOfficial() },
                onCheckAllUpdates = { vm.checkAllUpdates() },
                onSetAllEnabled = { vm.setAllEnabled(it) },
                onForgetSubscription = { vm.forgetSubscription(it) },
                errorDetails = messageDetails,
            )
            rootBar?.invoke()
            }
        }
    }
}

