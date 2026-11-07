package com.guangyue.music.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.guangyue.core.data.repository.TrackRepository
import com.guangyue.core.player.PlaybackController
import com.guangyue.core.player.PlaybackState
import com.guangyue.core.player.QualityLadder
import com.guangyue.music.R
import com.guangyue.feature.library.design.GuangYueDesign
import com.guangyue.feature.player.lyrics.LyricStrings
import com.guangyue.feature.player.screen.FoliaPlayerPage
import com.guangyue.feature.player.screen.LikeState
import com.guangyue.feature.player.screen.PlayerQueueRow
import com.guangyue.feature.player.screen.PlayerStringHost
import com.guangyue.feature.player.screen.FoliaMotion
import com.guangyue.feature.player.screen.PlayerStrings
import com.guangyue.feature.player.screen.nextLikeTarget
import com.guangyue.feature.player.screen.nextPlayerModeTarget
import com.guangyue.feature.player.screen.playerMode
import com.guangyue.feature.player.screen.playerModeTarget
import com.guangyue.feature.player.screen.StagePanel
import com.guangyue.feature.player.screen.StagePanelTab
import com.guangyue.feature.player.lyrics.LyricStyle
import kotlinx.coroutines.launch

/**
 * U4：全屏播放页的外壳（`app` 层）。
 *
 * 三件事只在 `app` 层做得了，所以放在这里而不是 `feature:player`：
 *  1. **文案**：feature 模块没有 `R`，用 [playerStrings] + [PlayerStringHost] 把 strings.xml 注进去；
 *  2. **收藏**：`TrackRepository.setFavorite`（Hilt 注入）与「加入歌单」的跳转；
 *  3. **展开/收起转场**：迷你条在全屏页**底下**留着（如 Folia 的 mini-bar 一直在），
 *     全屏页从底部 spring 上来盖住它；返回值决定要不要放同一段 spring。
 *
 * spring 参数取 `FoliaMotion.springPlayer`（`dampingRatio=28/280 stiffness=280`），
 * 与 Folia `FloatingPlayerControls.tsx` 的展开曲线一致，见 docs/ui-u4/report.md §3。
 */
@Composable
fun IPlayerRoute(
    playback: PlaybackController,
    playbackState: PlaybackState,
    darkMode: Boolean,
    accent: Color,
    onOpenLyrics: () -> Unit,
    /** V0：播放页「歌词样式」入口（打开底部弹层）；为 null 时不渲染这颗按钮。 */
    onOpenLyricStyle: (() -> Unit)? = null,
    /** 「歌词样式」按钮的无障碍文案（app 侧注入 strings.xml 的 id）。 */
    lyricStyleLabel: String = "",
    onAddToPlaylist: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    coverColor: Color? = null,
) {
    val res = playerStrings()
    val ctx = LocalContext.current
    val resolver: (Int) -> String = { ctx.getString(it) }
    val tracks = LocalTrackRepository.current
    val scope = rememberCoroutineScope()

    // 收藏态：从库里按 key 取（旧页面的 liked 与「我的·喜欢」是同一张表）
    var liked by remember(playbackState.item?.key) { mutableStateOf(false) }
    var pending by remember(playbackState.item?.key) { mutableStateOf(false) }
    LaunchedEffect(playbackState.item?.key) {
        val key = playbackState.item?.key
        liked = if (key == null) false else runCatching { tracks?.byKey(key)?.favorite == true }.getOrDefault(false)
    }

    val queueRows = remember(playbackState.index, playbackState.queueSize, playbackState.item?.key) {
        playback.queueSnapshot().mapIndexed { i, q ->
            PlayerQueueRow(
                index = i,
                title = q.title.ifBlank { ctx.getString(res.noSong) },
                subtitle = q.artist.orEmpty(),
                current = i == playbackState.index,
            )
        }
    }

    // 展开：先让页面以 0.86 缩放落位，再示意完成进入动画
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settled = true }

    // 收起：返回键 / 下滑 → 先播收起（settled=false），动画完再真正出栈
    var leaving by remember { mutableStateOf(false) }
    val triggerBack: () -> Unit = { leaving = true }
    LaunchedEffect(leaving) {
        if (leaving) {
            settled = false
            kotlinx.coroutines.delay(CONTRACT_MS)
            onBack()
        }
    }
    androidx.activity.compose.BackHandler(enabled = !leaving) { triggerBack() }

    CompositionLocalProvider(PlayerStringHost provides resolver) {
        FoliaPlayerPage(
            playback = playbackState,
            res = res,
            darkMode = darkMode,
            mode = playerMode(shuffle = playbackState.shuffle, repeatMode = playbackState.repeatMode),
            like = when {
                pending -> LikeState.Pending
                liked -> LikeState.Liked
                else -> LikeState.Idle
            },
            queue = queueRows,
            // 取不到封面主色时走主题色兜底（report §3）；测试里从这里注入假色。
            coverColor = coverColor,
            accent = accent,
            onTogglePlay = { playback.toggle() },
            onNext = { playback.next() },
            onPrevious = { playback.previous() },
            onSeek = { playback.seekToProgress(it) },

            onCycleMode = {
                val target = nextPlayerModeTarget(
                    playerMode(shuffle = playbackState.shuffle, repeatMode = playbackState.repeatMode),
                )
                playback.setShuffle(target.shuffle)
                playback.setRepeatMode(target.repeatMode)
            },
            onToggleLike = {
                val key = playbackState.item?.key
                val target = nextLikeTarget(
                    when {
                        pending -> LikeState.Pending
                        liked -> LikeState.Liked
                        else -> LikeState.Idle
                    },
                )
                if (key != null && target != null) {
                    pending = true
                    scope.launch {
                        runCatching { tracks?.setFavorite(key, target) }
                            .onSuccess { liked = target }
                        pending = false
                    }
                }
            },
            onAddToPlaylist = onAddToPlaylist,
            onOpenLyrics = onOpenLyrics,
            onOpenLyricStyle = onOpenLyricStyle,
            lyricStyleLabel = lyricStyleLabel,
            onPickQueueRow = { index ->
                queueRows.getOrNull(index)?.let { row ->
                    playback.queueSnapshot().getOrNull(row.index)?.let { playback.playNow(it) }
                }
            },
            onQuality = {
                val current = playbackState.item?.origin?.quality ?: playback.defaultQuality
                val ladder = QualityLadder.of(playbackState.item?.origin?.availableQualities)
                    .ifEmpty { QualityLadder.default() }
                playback.setQuality(QualityLadder.cycle(ladder, current))
            },
            onBack = triggerBack,
            modifier = modifier,
            settled = settled,
        )
    }
}

/**
 * P1b：舞台上的浮卡面板外壳（收藏 / 队列 / 音质 / 模式都在 app 层接线，界面在 feature 的 [StagePanel]）。
 */
@Composable
fun StagePanelHost(
    open: Boolean,
    tab: StagePanelTab,
    onTab: (StagePanelTab) -> Unit,
    onDismiss: () -> Unit,
    playback: PlaybackController,
    playbackState: PlaybackState,
    darkMode: Boolean,
    accent: Color,
    lyricStyle: LyricStyle,
    onOpenLyricStyle: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val ctx = LocalContext.current
    val tracks = LocalTrackRepository.current
    val scope = rememberCoroutineScope()
    val item = playbackState.item

    var liked by remember(item?.key) { mutableStateOf(false) }
    var pending by remember(item?.key) { mutableStateOf(false) }
    LaunchedEffect(item?.key) {
        val key = item?.key
        liked = if (key == null) false else runCatching { tracks?.byKey(key)?.favorite == true }.getOrDefault(false)
    }
    val like = when {
        pending -> LikeState.Pending
        liked -> LikeState.Liked
        else -> LikeState.Idle
    }
    // 队列在面板打开 / 换歌 / 增删时重取；removeAt 之后用 revision 触发。
    var revision by remember { mutableIntStateOf(0) }
    val queueRows = remember(open, revision, playbackState.index, playbackState.queueSize, item?.key) {
        playback.queueSnapshot().mapIndexed { i, q ->
            PlayerQueueRow(
                index = i,
                title = q.title.ifBlank { ctx.getString(R.string.player_no_song) },
                subtitle = q.artist.orEmpty(),
                current = i == playbackState.index,
            )
        }
    }
    val declared = item?.origin?.availableQualities.orEmpty()
    val qualities = QualityLadder.of(declared).ifEmpty { QualityLadder.default() }.sortedByDescending { it.order }
    val styleName = ctx.getString(
        when (lyricStyle) {
            LyricStyle.CLASSIC -> R.string.lyrics_style_classic
            LyricStyle.FUME -> R.string.lyrics_style_fume
            LyricStyle.PARTITA -> R.string.lyrics_style_partita
            LyricStyle.CADENZA -> R.string.lyrics_style_cadenza
            LyricStyle.STILL -> R.string.lyrics_style_still
            LyricStyle.TILT -> R.string.lyrics_style_tilt
            LyricStyle.CLADDAGH -> R.string.lyrics_style_claddagh
        },
    )

    StagePanel(
        open = open,
        tab = tab,
        onTab = onTab,
        onDismiss = onDismiss,
        dark = darkMode,
        accent = accent,
        title = item?.title?.takeIf { it.isNotBlank() } ?: ctx.getString(R.string.player_no_song),
        artist = item?.artist.orEmpty(),
        album = item?.album,
        coverUrl = item?.artworkUri,
        like = like,
        onToggleLike = {
            val key = item?.key
            val target = nextLikeTarget(like)
            if (key != null && target != null) {
                pending = true
                scope.launch {
                    runCatching { tracks?.setFavorite(key, target) }.onSuccess { liked = target }
                    pending = false
                }
            }
        },
        onAddToPlaylist = onAddToPlaylist,
        mode = playerMode(shuffle = playbackState.shuffle, repeatMode = playbackState.repeatMode),
        onPickMode = { mode ->
            val target = playerModeTarget(mode)
            playback.setShuffle(target.shuffle)
            playback.setRepeatMode(target.repeatMode)
        },
        lyricStyleName = styleName,
        onOpenLyricStyle = onOpenLyricStyle,
        queue = queueRows,
        onPickQueueRow = { index -> playback.queueSnapshot().getOrNull(index)?.let { playback.playNow(it) } },
        onRemoveQueueRow = { index -> playback.removeAt(index); revision++ },
        qualities = qualities,
        currentQuality = item?.origin?.quality ?: playback.defaultQuality,
        qualityDeclared = declared.isNotEmpty(),
        onPickQuality = { playback.setQuality(it) },
    )
}

/** 收起转场时长：`springPlayer`（stiffness 280）约 320ms，留一帧余量。 */
private const val CONTRACT_MS = 340L

/** 收藏仓库注入点：`GuangYueRoot` 在 App 层提供，播放页与截图测试各给各的。 */
val LocalTrackRepository =
    androidx.compose.runtime.staticCompositionLocalOf<TrackRepository?> { null }



/** 播放页文案包：把 strings.xml 的 id 交给 feature 模块。 */
@Composable
internal fun playerStrings(): PlayerStrings = PlayerStrings(
    nowPlaying = R.string.player_now_playing,
    collapse = R.string.player_collapse,
    previous = R.string.player_previous,
    next = R.string.player_next,
    play = R.string.player_play,
    pause = R.string.player_pause,
    shuffle = R.string.player_shuffle,
    modeSequential = R.string.player_mode_sequential,
    modeLoopOne = R.string.player_mode_loop_one,
    modeShuffle = R.string.player_mode_shuffle,
    like = R.string.player_like,
    liked = R.string.player_liked,
    addToPlaylist = R.string.player_add_to_playlist,
    queue = R.string.player_queue,
    lyrics = R.string.player_lyrics,
    // U5：歌词页落地，占位小字撤掉；这里给「歌词」本身，外壳的 strings 包保持定长。
    lyricsSoon = R.string.player_lyrics,
    queueCount = R.string.player_queue_count,
    queueEmpty = R.string.player_queue_empty,
    noSong = R.string.player_no_song,
    unknownArtist = R.string.player_unknown_artist,
    quality = R.string.player_quality,
    retryHint = R.string.player_retry_hint,
    closeQueue = R.string.player_close_queue,
    seekBar = R.string.player_seek_bar,
    coverOf = R.string.player_cover_of,
)

/**
 * U5：把 `strings.xml` 里的「U5 歌词页」段交给 `feature:player` 的歌词视图。
 *
 * 跟 [playerStrings] 同一个套路：feature 模块没有 `R`，只能拿资源 id，文案真值在这儿取齐。
 */
@Composable
internal fun lyricStrings(): LyricStrings = LyricStrings(
    back = R.string.lyrics_back,
    title = R.string.lyrics_title,
    play = R.string.player_play,
    pause = R.string.player_pause,
    empty = R.string.lyrics_empty,
    emptyHint = R.string.lyrics_empty_hint,
    loading = R.string.lyrics_loading,
    loadFailed = R.string.lyrics_load_failed,
    failedHint = R.string.lyrics_failed_hint,
    retry = R.string.lyrics_retry,
    pureMusic = R.string.lyrics_pure_music,
    waiting = R.string.lyrics_waiting,
    seekBar = R.string.lyrics_seek_bar,
    sourceLabel = R.string.lyrics_source,
    sourceNone = R.string.lyrics_source_none,
)

