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
                    player.prepare()
                    player.play()
                } else {
                    _state.value = _state.value.copy(error = error.message ?: "播放失败")
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val key = mediaItem?.mediaId ?: return
                resolverRetriedFor = null
                currentQueueItem(key)?.let { scope.launch { _trackChanged.emit(it) } }
                publish()
            }
        })

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

    fun setQueue(items: List<QueueItem>, startIndex: Int = 0, play: Boolean = true) {
        if (items.isEmpty()) return
        queue.clear()
        queue += items
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

    /** Play a single track without disturbing the rest of the queue. */
    fun playNow(item: QueueItem) {
        if (queue.none { it.key == item.key }) queue.add(0, item)
        player.setMediaItem(item.toMediaItem())
        player.prepare()
        player.play()
        publish()
    }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun play() = player.play()

    fun pause() = player.pause()

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
        player.volume = v.coerceIn(0f, 1f)
    }

    fun release() {
        player.release()
    }

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
        )
    }

    companion object {
        private const val TAG = "GuangYuePlayer"
        private const val KEY_QUALITY = "default_quality"
    }
}
