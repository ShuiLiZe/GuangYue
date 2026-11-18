package com.guangyue.core.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.guangyue.core.data.model.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** One entry of the queue as the UI understands it. */
data class QueueItem(
    val key: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUri: String? = null,
    val origin: MediaOrigin,
    /** Raw MusicFree musicItem JSON, used to fetch lyrics lazily. */
    val rawJson: String? = null,
) {
    @UnstableApi
    fun toMediaItem(): MediaItem {
        OriginStore.put(key, origin)
        // 队列里的曲目可能还没落库（首页推荐歌单直接开播），byKey 查不到就没法还原
        // MusicItem，洛雪取链源需要它的 raw。这里顺带把行写库，取链时就能查到。
        return MediaItem.Builder()
            .setMediaId(key)
            .setUri(Uri.parse(resolveUri(key)))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(artworkUri?.let { Uri.parse(it) })
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build(),
            )
            .build()
    }

    /**
     * 队列项 → 可落库行。首页 / 歌单直连开播的曲目本地库里还没有这一行，
     * 取链时 TrackMapper.toMusicItem 就还原不出带 raw 的 MusicItem（洛雪源依赖 raw）。
     */
    fun toCachedTrack(): TrackEntity? {
        // 2026-11-10：本地文件（下载 / 手机音乐）开播时记成 local 行，「本地音乐」与收藏才认得出它。
        val local = origin.directUrl?.takeIf { it.startsWith("file:") || it.startsWith("content:") || it.startsWith("/") }
        return TrackEntity(
            key = key,
            title = title.ifBlank { "未知歌曲" },
            artist = artist.ifBlank { "未知艺术家" },
            album = album.orEmpty(),
            artwork = artworkUri,
            platform = origin.pluginId,
            songId = origin.trackKey.substringAfter(":"),
            source = if (local != null) TrackEntity.SOURCE_LOCAL else TrackEntity.SOURCE_PLUGIN,
            quality = origin.quality.wire,
            rawJson = rawJson,
            localUri = local,
        )
    }

    companion object {
        /** Builds a queue entry from a persisted track row. */
        fun of(track: TrackEntity): QueueItem = QueueItem(
            key = track.key,
            title = track.title,
            artist = track.artist,
            album = track.album,
            artworkUri = track.artwork,
            rawJson = track.rawJson,
            origin = MediaOrigin(
                pluginId = track.platform,
                trackKey = track.key,
                quality = Quality.fromWire(track.quality),
                directUrl = track.localUri,
            ),
        )
    }
}

/** Observable playback snapshot consumed by every UI surface. */
data class PlaybackState(
    val item: QueueItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val error: String? = null,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queueSize: Int = 0,
    val index: Int = 0,
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Owns the single ExoPlayer instance and turns it into a plain state machine for Compose.
 *
 * Created once by Hilt; the [GuangYuePlaybackService] attaches a MediaLibrarySession on top so
 * playback keeps running (and shows a notification) with the UI gone.
 */
@Singleton
@UnstableApi
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataSources: MediaDataSourceProvider,
    private val prefetcher: PlaybackPrefetcher,
    private val tracks: com.guangyue.core.data.repository.TrackRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSources.factory()),
        )
        .build()
        .apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            playWhenReady = false
        }

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** Emits the track when a song starts, so lyrics can be fetched lazily. */
    private val _trackChanged = MutableSharedFlow<QueueItem>(replay = 1, extraBufferCapacity = 4)
    val trackChanged: SharedFlow<QueueItem> = _trackChanged.asSharedFlow()

    private val queue = ArrayList<QueueItem>()

    private var resolverRetriedFor: String? = null

    /**
     * 重试保险：插件取不到链时，二次 `prepare()` 在模拟器上会卡在 BUFFERING，
     * ExoPlayer 既不回调 `onPlayerError` 也不回调 `onIsPlayingChanged`，界面上就是一句提示都没有。
     * 这里给「重试后 N 秒仍没播出声」兜一个可读失败态。
     */
    private var retryWatchdog: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                publish()
            }

            override fun onPlayerError(error: PlaybackException) {
                val key = player.currentMediaItem?.mediaId ?: return
                // A plugin url usually expires / is bound to the requesting session:
                // re-resolve exactly once, then surface the error.
                if (resolverRetriedFor != key) {
                    resolverRetriedFor = key
                    dataSources.pluginResolver.invalidate(error.message)
                    android.util.Log.w(TAG, "playback error, retrying once: ${error.message}")
                    // 注意：报错后 player 已回到 IDLE，此时直接 prepare() 在某些失败路径上
                    // 不会再分发第二个 error（onPlayerError 只回调一次的那个现象就是这么来的），
                    // 界面于是「既没声也没提示」。先 stop() 明确切回 IDLE 再 prepare。
                    player.stop()
                    player.prepare()
                    player.play()
                    armRetryWatchdog(key, error.message)
                } else {
                    retryWatchdog?.cancel()
                    retryWatchdog = null
                    val text = PlaybackErrorText.readable(
                        error.message ?: error.cause?.message,
                        error.cause?.message,
                    )
                    android.util.Log.w(TAG, "playback error (final), surfacing: $text")
                    _state.value = _state.value.copy(error = text)
                    // 11-18 Folia autoSkipUnplayable：不是网络问题（连不上 / 超时）就倒计时 3 秒跳下一首。
                    val network = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                    if (!network && PlaybackBehavior.flow(context).value.autoSkipUnplayable && player.hasNextMediaItem()) {
                        val failedKey = key
                        scope.launch {
                            delay(3000)
                            if (player.currentMediaItem?.mediaId == failedKey && !player.isPlaying) {
                                player.seekToNextMediaItem()
                                play()
                            }
                        }
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val key = mediaItem?.mediaId ?: return
                resolverRetriedFor = null
                retryWatchdog?.cancel()
                retryWatchdog = null
                // 换歌就把上一首的失败提示清掉，否则新歌一打开就顶着一行旧错误。
                if (_state.value.error != null) {
                    _state.value = _state.value.copy(error = null)
                }
                currentQueueItem(key)?.let { scope.launch { _trackChanged.emit(it) } }
                // 2026-11-10：记「最近播放」（以前 markPlayed 没人调，最近播放其实是随便几行缓存）。
                currentQueueItem(key)?.toCachedTrack()?.let { row ->
                    scope.launch { try { tracks.played(row) } catch (_: Throwable) { } }
                }
                publish()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // 真播出声了就把重试兜底撤掉。
                if (isPlaying) {
                    retryWatchdog?.cancel()
                    retryWatchdog = null
                }
            }
        })

        // 11-18 Folia 恢复上次的歌曲：冷启动还原队列 / 下标 / 进度（autoPlayOnLaunch 开着就续播）。
        scope.launch {
            val snap = kotlinx.coroutines.withContext(Dispatchers.IO) { PlaybackSession.load(context) } ?: return@launch
            if (queue.isNotEmpty() || player.mediaItemCount > 0) return@launch
            queue.clear()
            queue += snap.items
            player.setMediaItems(snap.items.map { it.toMediaItem() }, snap.index, snap.positionMs)
            if (PlaybackSession.autoPlayOnLaunch(context)) play()
            publish()
            // 播放器没 prepare 时 currentPosition 仍是 setMediaItems 给的起点，进度条照常显示。
        }
        // 存档：换歌 / 暂停时立即存，播放中每 5 秒存一次进度。
        scope.launch {
            var lastSaved = 0L
            var lastKey: String? = null
            var lastPlaying = false
            while (true) {
                delay(1000)
                if (queue.isEmpty()) continue
                val key = player.currentMediaItem?.mediaId
                val playing = player.isPlaying
                val now = android.os.SystemClock.elapsedRealtime()
                if (key != lastKey || playing != lastPlaying || (playing && now - lastSaved >= 5000)) {
                    lastKey = key; lastPlaying = playing; lastSaved = now
                    val snap = PlaybackSession.Snapshot(queue.toList(), player.currentMediaItemIndex.coerceAtLeast(0), player.currentPosition.coerceAtLeast(0L))
                    launch(Dispatchers.IO) { PlaybackSession.save(context, snap) }
                }
            }
        }

        // ~4 Hz progress ticker while something is loaded: enough for the mini bar, the lyric
        // flow auto-scroll and the stage highlight, cheap enough to run always.
        scope.launch {
            while (true) {
                if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) {
                    publish()
                    maybePrefetchNext()
                    delay(250)
                } else {
                    delay(500)
                }
            }
        }
    }

    /**
     * 无缝切歌预热：进度到 80% 或剩余 ≤15s 时，提前异步解析并缓冲下一首。
     *
     * 只对「下一首」做，且 [PlaybackPrefetcher] 内部用 key 去重 —— 同一首不会因为 250ms
     * 的 tick 反复触发。单曲循环 / 队列末尾直接返回。
     */
    private fun maybePrefetchNext() {
        if (player.repeatMode == Player.REPEAT_MODE_ONE) return
        val duration = player.duration
        if (duration <= 0L || duration == androidx.media3.common.C.TIME_UNSET) return
        val position = player.currentPosition
        if (!PlaybackPrefetcher.shouldPrefetch(position, duration)) return

        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == androidx.media3.common.C.INDEX_UNSET || nextIndex == player.currentMediaItemIndex) return
        val nextKey = player.getMediaItemAt(nextIndex).mediaId
        val next = currentQueueItem(nextKey) ?: return
        prefetcher.prefetch(next)
    }

    fun currentQueueItem(key: String): QueueItem? = queue.firstOrNull { it.key == key }

    fun queueSnapshot(): List<QueueItem> = queue.toList()

    /**
     * 2026-11-10：自动补全封面后换掉队列里这首的封面（播放页 / 迷你条马上用上）。
     * 只改界面状态，不重设 MediaItem（避免打断正在播的这首）。
     */
    fun updateArtwork(key: String, uri: String) {
        val i = queue.indexOfFirst { it.key == key }
        if (i < 0 || queue[i].artworkUri == uri) return
        queue[i] = queue[i].copy(artworkUri = uri)
        publish()
    }

    fun setQueue(items: List<QueueItem>, startIndex: Int = 0, play: Boolean = true) {
        if (items.isEmpty()) return
        // 0.0.18：以前只在插件取到链时才拉起播放服务，本地下载 / 洛雪源开播时系统里没有 media session，
        // 通知栏、锁屏、媒体键全都没有。开播就拉起（UI 前台调用，startService 合法）。
        GuangYuePlaybackService.start(context)
        val remembered = items.mapNotNull { it.toCachedTrack() }
        queue.clear()
        queue += items
        // 队列里的曲目可能还没落库（首页推荐歌单直接开播）。先写库，取链时
        // TrackMapper.toMusicItem 才能还原出带 raw 的 MusicItem，洛雪源要用 raw 里的字段。
        if (remembered.isNotEmpty()) {
            scope.launch { try { remembered.forEach { tracks.cache(it) } } catch (_: Throwable) { } }
        }
        player.setMediaItems(
            items.map { it.toMediaItem() },
            startIndex.coerceIn(0, items.size - 1),
            0L,
        )
        player.prepare()
        if (play) player.play()
        publish()
    }

    /** Play tracks straight from the library (keys are `platform:songId`). */
    fun setQueueFromTracks(tracks: List<TrackEntity>, startIndex: Int = 0, play: Boolean = true) =
        setQueue(tracks.map { QueueItem.of(it) }, startIndex, play)

    /**
     * 加入队列（Folia 聚焦卡「加入队列」）：追加到队尾；已在队列里的先挪走再放到队尾。队列为空时等同 setQueue 但不自动播放。
     */
    fun appendTracks(tracks: List<TrackEntity>) {
        if (tracks.isEmpty()) return
        val items = tracks.map { QueueItem.of(it) }
        if (queue.isEmpty()) { setQueue(items, 0, play = false); return }
        val keys = items.map { it.key }.toSet()
        val currentKey = queue.getOrNull(player.currentMediaItemIndex)?.key
        for (i in queue.indices.reversed()) {
            if (queue[i].key in keys && queue[i].key != currentKey) { queue.removeAt(i); player.removeMediaItem(i) }
        }
        val fresh = items.filter { it.key != currentKey }
        val remembered = fresh.mapNotNull { it.toCachedTrack() }
        if (remembered.isNotEmpty()) scope.launch { try { remembered.forEach { this@PlaybackController.tracks.cache(it) } } catch (_: Throwable) { } }
        // 11-18 Folia queueAddBehavior「加入队列的默认位置」：append 追加到末尾 / next 追加到下一首。
        if (PlaybackBehavior.flow(context).value.queueAddNext && queue.isNotEmpty()) {
            val at = (player.currentMediaItemIndex + 1).coerceIn(0, queue.size)
            queue.addAll(at, fresh)
            player.addMediaItems(at, fresh.map { it.toMediaItem() })
        } else {
            queue += fresh
            player.addMediaItems(fresh.map { it.toMediaItem() })
        }
        publish()
    }

    /** Play a single track without disturbing the rest of the queue. */
    fun playNow(item: QueueItem) {
        if (queue.none { it.key == item.key }) queue.add(0, item)
        GuangYuePlaybackService.start(context)
        player.setMediaItem(item.toMediaItem())
        player.prepare()
        player.play()
        publish()
    }

    /**
     * 播放队列里第 [index] 首（Folia QueueTab 点行 = onPlaySong(song, playQueue)：队列不变，只换当前）。
     * 原来走 [playNow] 会 setMediaItem 把播放器列表压成一首，和 [queue] 对不上（下一首失效、删行错位）。
     */
    fun playAt(index: Int) {
        if (index !in queue.indices) return
        GuangYuePlaybackService.start(context)
        if (player.mediaItemCount != queue.size) {
            player.setMediaItems(queue.map { it.toMediaItem() }, index, 0L)
        } else {
            player.seekToDefaultPosition(index)
        }
        player.prepare()
        player.play()
        publish()
    }

    /** 把第 [from] 首挪到 [to]（Folia「下一首播放」= 当前之后、「移到队尾」= 末尾）。 */
    fun moveTo(from: Int, to: Int) {
        if (from !in queue.indices) return
        val target = to.coerceIn(0, queue.size - 1)
        if (from == target) return
        val item = queue.removeAt(from)
        queue.add(target, item)
        if (player.mediaItemCount == queue.size) player.moveMediaItem(from, target)
        publish()
    }

    /** 下一首播放：挪到当前这首后面。 */
    fun moveToNext(index: Int) {
        val cur = player.currentMediaItemIndex
        if (index == cur) return
        moveTo(index, if (index < cur) cur else cur + 1)
    }

    /** 移到队尾。 */
    fun moveToEnd(index: Int) = moveTo(index, queue.size - 1)

    /**
     * Folia QueueTab「打乱队列」：正在播的挪到队首、其余打乱（[QueueShuffle]），不打断当前这首。
     * 播放器列表与 [queue] 对齐时：先把当前项挪到 0，删掉其后全部，再按新顺序追加。
     */
    fun shuffleQueue() {
        if (!QueueShuffle.canShuffle(queue.size)) return
        val aligned = player.mediaItemCount == queue.size
        val curKey = player.currentMediaItem?.mediaId
        val curInQueue = queue.indexOfFirst { it.key == curKey }
        val order = QueueShuffle.order(queue.size, curInQueue)
        val reordered = order.map { queue[it] }
        queue.clear()
        queue += reordered
        val cur = player.currentMediaItemIndex
        if (aligned && curInQueue >= 0 && cur in 0 until player.mediaItemCount) {
            if (cur != 0) player.moveMediaItem(cur, 0)
            if (player.mediaItemCount > 1) player.removeMediaItems(1, player.mediaItemCount)
            player.addMediaItems(reordered.drop(1).map { it.toMediaItem() })
        } else if (aligned && player.mediaItemCount > 0 && curInQueue < 0) {
            player.setMediaItems(reordered.map { it.toMediaItem() }, 0, 0L)
            player.prepare()
        }
        // 未对齐（单曲 playNow 后）只改 queue；下次 playAt 会按 queue 重建播放器列表。
        publish()
    }

    fun toggle() {
        if (player.isPlaying) pause() else play()
    }

    /** 用户设的音量（淡入淡出只临时改 player.volume，结束后回到它）。 */
    private var userVolume = 1f
    private var fadeJob: Job? = null

    /** Folia services/playbackFade：0.2 秒线性渐变音量。 */
    private fun fadeTo(target: Float, then: () -> Unit = {}) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val from = player.volume
            val steps = 10
            for (i in 1..steps) {
                delay((PlaybackFade.SECONDS * 1000 / steps).toLong())
                player.volume = from + (target - from) * i / steps
            }
            then()
        }
    }

    /** 冷启动恢复的队列没 prepare（只显示、不取链）：第一次按播放时再 prepare 并拉起播放服务。 */
    fun play() {
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
            GuangYuePlaybackService.start(context)
            player.prepare()
        }
        if (PlaybackFade.enabled(context) && !player.isPlaying) {
            // 恢复时淡入（Folia playbackFade）。
            fadeJob?.cancel()
            player.volume = 0f
            player.play()
            fadeTo(userVolume)
        } else {
            // 淡出途中又按了播放：撤掉淡出，音量回原值。
            fadeJob?.cancel()
            player.volume = userVolume
            player.play()
        }
    }

    /** 暂停前先淡出 0.2 秒（Folia playbackFade，默认开；设置里可关）。 */
    fun pause() {
        if (PlaybackFade.enabled(context) && player.isPlaying) {
            fadeTo(0f) { player.pause(); player.volume = userVolume }
        } else {
            fadeJob?.cancel()
            player.volume = userVolume
            player.pause()
        }
    }

    fun stop() {
        player.stop()
        publish()
    }

    fun next() = player.seekToNextMediaItem()

    /**
     * 从队列里删掉第一项（Folia 的「移除」按钮走这里）。
     *
     * 删除当前项时 Media3 会自动顺延到下一首；删到空就停播，避免留下
     * 「播放器还挂着一条已经不在队列里的 mediaItem」的鬼状态。
     */
    fun removeAt(index: Int) {
        if (index !in queue.indices) return
        queue.removeAt(index)
        if (queue.isEmpty()) {
            player.stop()
            player.clearMediaItems()
        } else {
            player.removeMediaItem(index)
        }
        publish()
    }

    fun previous() {
        if (player.currentPosition > 3_000L) player.seekTo(0L) else player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    fun seekToProgress(fraction: Float) {
        val dur = player.duration
        if (dur > 0) player.seekTo((dur * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun setShuffle(on: Boolean) {
        player.shuffleModeEnabled = on
        publish()
    }

    /** U4：三态播放模式直接给目标值（顺序 / 单曲 / 随机），由播放页的纯逻辑算出来。 */
    fun setRepeatMode(mode: Int) {
        player.repeatMode = mode
        publish()
    }

    fun cycleRepeat() {
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publish()
    }

    fun setQuality(quality: Quality) {
        val key = player.currentMediaItem?.mediaId ?: return
        val item = currentQueueItem(key) ?: return
        val updated = item.copy(origin = item.origin.copy(quality = quality))
        val at = queue.indexOfFirst { it.key == key }
        if (at >= 0) queue[at] = updated
        dataSources.pluginResolver.invalidate("quality switched to ${quality.wire}")
        prefetcher.reset()
        player.setMediaItem(updated.toMediaItem(), player.currentPosition)
        player.prepare()
        saveDefaultQuality(quality)
        publish()
    }

    // ------------------------------------------------------------- 音质偏好

    private val prefs by lazy {
        context.getSharedPreferences("guangyue_player", Context.MODE_PRIVATE)
    }

    /** 用户选定的默认音质；新歌进来时按它解析直链。 */
    val defaultQuality: Quality
        get() = Quality.fromWire(prefs.getString(KEY_QUALITY, null))

    fun setDefaultQuality(quality: Quality) {
        prefs.edit().putString(KEY_QUALITY, quality.wire).apply()
    }

    private fun saveDefaultQuality(quality: Quality) = setDefaultQuality(quality)

    /** 预取状态，供调试面板 / 设置页展示。 */
    fun prefetchResult(): PrefetchResult = prefetcher.lastResult

    fun setVolume(v: Float) {
        fadeJob?.cancel()
        userVolume = v.coerceIn(0f, 1f)
        player.volume = userVolume
    }

    /** 11-18 播放面板音量行（Folia VolumeRow）读当前值。 */
    val volume: Float get() = userVolume

    /** 11-18 播放面板「音频效果器」：交给系统均衡器面板的会话号。 */
    val audioSessionId: Int get() = player.audioSessionId

    fun release() {
        player.release()
    }

    /**
     * 重试兜底：`PREPARE_TIMEOUT_MS` 内既没播出声、也没再次报错，就按失败处理。
     *
     * 正常起播或再次报错时会 [retryWatchdog] 取消，因此这条路径只在「卡住」时生效。
     */
    private fun armRetryWatchdog(key: String, firstMessage: String?) {
        retryWatchdog?.cancel()
        retryWatchdog = scope.launch {
            delay(PREPARE_TIMEOUT_MS)
            if (player.currentMediaItem?.mediaId != key) return@launch
            if (player.isPlaying || player.playbackState == Player.STATE_READY) return@launch
            _state.value = _state.value.copy(error = PlaybackErrorText.readable(firstMessage))
        }
    }

    /**
     * 把 ExoPlayer 的内部报错换成用户能看懂的一句。
     *
     * `error.message` 常常是 `Source error` / `Unexpected ok` 这种没有信息量、还带异常类名的原文，
     * 直接贴到播放页等于没提示。包含 `ResolutionException` 文案的原话才原样显示。
     */
    private fun readablePlaybackError(message: String?): String = PlaybackErrorText.readable(message)

    private fun publish() {
        val key = player.currentMediaItem?.mediaId
        val item = key?.let { k -> queue.firstOrNull { it.key == k } }
        val rawDuration = player.duration
        val duration =
            if (rawDuration > 0L && rawDuration != androidx.media3.common.C.TIME_UNSET) rawDuration else 0L
        _state.value = PlaybackState(
            item = item,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queueSize = queue.size,
            index = player.currentMediaItemIndex.coerceAtLeast(0),
            // 错误要跨 publish() 存活：进度 ticker / onEvents 每 250ms 都会重建整份 state，
            // 这里不清空就等于 UI 永远看不到失败态。
            error = _state.value.error,
        )
    }

    companion object {
        private const val TAG = "GuangYuePlayer"
        private const val KEY_QUALITY = "default_quality"

        /** 二次 prepare 后等多久还没起播就判失败（毫秒）。 */
        private const val PREPARE_TIMEOUT_MS = 8_000L
    }
}
