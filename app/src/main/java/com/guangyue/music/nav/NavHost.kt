package com.guangyue.music.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import javax.inject.Inject

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val PLAYER = "player"
    const val LYRIC_FLOW = "lyricFlow"
    const val LYRIC_STAGE = "lyricStage"
    const val PLUGINS = "plugins"
}

/**
 * Root navigation.
 *
 * Every screen shares [PlaybackController] and [LyricsViewModel]-backed state, so the mini bar
 * on the home screen and the full controls always agree.
 */
@Composable
fun GuangYueNavHost(
    darkMode: Boolean,
    playback: PlaybackController,
    navController: NavHostController = rememberNavController(),
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
                onOpenCard = { card: ShelfCard ->
                    if (tracks.isNotEmpty()) {
                        playback.setQueueFromTracks(tracks, 0, true)
                        navController.navigate(Routes.PLAYER)
                    }
                },
                onPluginManager = { navController.navigate(Routes.PLUGINS) },
                playbackTitle = playbackState.item?.title ?: "还没有在听的歌",
                playbackArtwork = playbackState.item?.artworkUri,
                progress = playbackState.progress,
                isPlaying = playbackState.isPlaying,
                onTogglePlay = { playback.toggle() },
                onOpenNowPlaying = {
                    if (playbackState.item != null) navController.navigate(Routes.PLAYER)
                },
            )
        }

        composable(Routes.SEARCH) {
            val vm: SearchViewModel = hiltViewModel()
            val state by vm.state.collectAsStateWithLifecycle()
            SearchScreen(
                state = state,
                onQueryChange = vm::onQueryChange,
                onSearch = vm::search,
                onPickPlatforms = vm::togglePlatform,
                onPlay = { index ->
                    vm.playFrom(index) { items, at ->
                        playback.setQueue(items, at, true)
                        navController.navigate(Routes.PLAYER)
                    }
                },
                onAddToSheet = { item -> vm.addToCurrentSheet(item) },
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
                    playback.setQuality(playbackState.item?.origin?.quality?.next()
                        ?: com.guangyue.core.player.Quality.STANDARD)
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

        composable(Routes.PLUGINS) {
            val vm: PluginViewModel = hiltViewModel()
            val plugins by vm.plugins.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()
            val message by vm.message.collectAsStateWithLifecycle()
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
            )
        }
    }
}
