package com.guangyue.feature.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guangyue.core.data.lyrics.LrcParser
import com.guangyue.core.data.lyrics.Lyric
import com.guangyue.core.data.lyrics.LyricSynchronizer
import com.guangyue.core.data.lyrics.LyricUiState
import com.guangyue.core.data.model.TrackMapper
import com.guangyue.core.data.repository.TrackRepository
import com.guangyue.core.player.PlaybackController
import com.guangyue.core.player.PlaybackState
import com.guangyue.core.player.QueueItem
import com.guangyue.core.plugin.Json2
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.MusicPluginBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * The shared lyric controller: one instance for the whole playback session.
 *
 * It owns the [Lyric] document, drives [LyricSynchronizer] from the player position and exposes
 * the current line plus per-character progress, so the lyric-flow view and the stage view stay
 * in lockstep with the music.
 */
@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val playback: PlaybackController,
    private val bridge: MusicPluginBridge,
    private val tracks: TrackRepository,
    private val meta: com.guangyue.core.data.meta.TrackMetaService,
    @ApplicationContext context: Context,
) : ViewModel() {

    /**
     * P1c：歌词时间轴偏移（Folia LyricTimelineOffsetControl + lyricOffsetMemory）：每首歌记一份，步长 250ms，
     * 正数让歌词更晚出现（歌词时间 = 播放时间 - 偏移）。
     */
    private val offsets = context.getSharedPreferences("lyric_offsets", Context.MODE_PRIVATE)
    private val _offsetMs = MutableStateFlow(0L)
    val offsetMs: StateFlow<Long> = _offsetMs.asStateFlow()

    fun setOffset(ms: Long) {
        val v = ms.coerceIn(-30_000L, 30_000L)
        _offsetMs.value = v
        lastKey?.let { k -> offsets.edit().apply { if (v == 0L) remove(k) else putLong(k, v) }.apply() }
        val pos = playback.state.value.positionMs
        sync.onPosition(pos - v, System.currentTimeMillis())
        _syncState.value = sync.state.value
    }

    val playbackState: StateFlow<PlaybackState> =
        playback.state.stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackState())

    private val sync = LyricSynchronizer()

    private val _lyric = MutableStateFlow(Lyric.EMPTY)
    val lyric: StateFlow<Lyric> = _lyric.asStateFlow()

    private val _syncState = MutableStateFlow(sync.state.value)
    val syncState: StateFlow<LyricUiState> = _syncState.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var lyricJob: Job? = null
    private var lastKey: String? = null

    init {
        viewModelScope.launch {
            playback.state.collect { state ->
                val current = state.item
                val key = current?.key
                if (current != null && key != null && key != lastKey) {
                    lastKey = key
                    _offsetMs.value = offsets.getLong(key, 0L)
                    loadLyrics(current)
                }
                sync.onPosition(state.positionMs - _offsetMs.value, System.currentTimeMillis())
                _syncState.value = sync.state.value
            }
        }
    }

    /** Manual drag on the lyric view: freeze auto-scroll for 3 s (spec). */
    fun onManualScroll() {
        sync.onManualScroll()
        _syncState.value = sync.state.value
    }

    fun resumeAutoScroll() {
        sync.resumeAutoScroll()
        _syncState.value = sync.state.value
    }

    fun clearMessage() {
        _message.value = null
    }

    fun seekToLine(index: Int) {
        val line = _lyric.value.lines.getOrNull(index) ?: return
        playback.seekTo(line.timeMs)
    }

    private fun loadLyrics(item: QueueItem) {
        lyricJob?.cancel()
        _lyric.value = Lyric.EMPTY
        sync.clear()
        _syncState.value = sync.state.value

        lyricJob = viewModelScope.launch {
            _loading.value = true
            try {
                // 2026-11-10：自动补全过的歌词 / 封面先用缓存。
                val (cached, artOk) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    runCatching { meta.cached(item.key) }.getOrNull() to meta.artworkReadable(item.artworkUri)
                }
                if (!artOk) cached?.artworkUri?.let { playback.updateArtwork(item.key, it) }
                // 0.0.18：本地下载的歌把歌词塞在 rawJson（rawLrc / translation）里，先用它，离线也有歌词。
                val inline = item.rawJson?.let { embeddedLrc(it) }
                val inlineTrans = item.rawJson?.let { embeddedTranslation(it) }
                var source: com.guangyue.core.plugin.LyricSourceResult? = when {
                    inline != null -> com.guangyue.core.plugin.LyricSourceResult(rawLrc = inline, translation = inlineTrans)
                    cached?.lrc != null -> com.guangyue.core.plugin.LyricSourceResult(rawLrc = cached.lrc, translation = cached.translation)
                    // 手机本地歌没有插件可问，直接走自动匹配。
                    isDeviceTrack(item) -> null
                    else -> runCatching {
                        val row = runCatching { tracks.byKey(item.key) }.getOrNull()
                        val songId = item.key.substringAfter(':', item.key)
                        val musicItem = row?.let { TrackMapper.toMusicItem(it) }
                            ?: item.rawJson?.let { r -> (Json2.parse(r) as? JsonObject)?.let { MusicItem.from(it, item.origin.pluginId) } }
                            ?: MusicItem(
                                platform = item.origin.pluginId,
                                // 以前误传整条 key（platform:id），插件按 id 取不到歌词。
                                id = songId,
                                title = item.title,
                                artist = item.artist,
                                album = item.album,
                            )
                        bridge.getLyric(item.origin.pluginId, musicItem)
                    }.getOrNull()
                }
                val hasLyric = !source?.rawLrc.isNullOrBlank()
                val needCover = !artOk && cached?.artworkUri == null
                if (!hasLyric || needCover) {
                    // 2026-11-10：没歌词 / 没封面 → 用歌名 + 歌手去当前音源找最匹配的一条，缓存下来。失败静默。
                    val got = meta.ensure(
                        com.guangyue.core.data.meta.TrackMetaService.Request(
                            key = item.key,
                            title = item.title,
                            artist = item.artist,
                            durationMs = durationOf(item.key),
                        ),
                        needLyric = !hasLyric,
                        needCover = needCover,
                    )
                    if (lastKey != item.key) return@launch
                    if (needCover) got?.artworkUri?.let { playback.updateArtwork(item.key, it) }
                    if (!hasLyric && got?.lrc != null) {
                        source = com.guangyue.core.plugin.LyricSourceResult(rawLrc = got.lrc, translation = got.translation)
                    }
                }
                val raw = source?.rawLrc
                if (raw.isNullOrBlank()) {
                    _message.value = "暂无歌词"
                    return@launch
                }
                var parsed = LrcParser.parse(raw)
                source?.translation?.let { parsed = LrcParser.mergeTranslation(parsed, it) }
                _lyric.value = parsed
                sync.setLyric(parsed)
                _syncState.value = sync.state.value
            } catch (t: kotlinx.coroutines.CancellationException) {
                throw t
            } catch (t: Throwable) {
                _message.value = "暂无歌词"
            } finally {
                _loading.value = false
            }
        }
    }

    /** 手机本地音乐（MediaStore）行：没有插件能问歌词。 */
    private fun isDeviceTrack(item: QueueItem): Boolean =
        item.key.startsWith(com.guangyue.core.data.model.LibraryRules.DEVICE_PREFIX) ||
            item.origin.pluginId == "mediastore" || item.origin.pluginId == "local"

    /** 时长（打分用）：库里有就用库里的，否则等播放器报出时长（最多 3 秒），拿不到就 0（不按时长打分）。 */
    private suspend fun durationOf(key: String): Long {
        runCatching { tracks.byKey(key)?.durationMs }.getOrNull()?.takeIf { it > 0 }?.let { return it }
        repeat(12) {
            val st = playback.state.value
            if (st.item?.key == key && st.durationMs > 0) return st.durationMs
            kotlinx.coroutines.delay(250)
        }
        return 0L
    }

    /** Some plugins inline the LRC in the music item (MusicFree `rawLrc`). */
    private fun embeddedLrc(rawJson: String): String? {
        val o = runCatching { Json2.parse(rawJson) as? JsonObject }.getOrNull() ?: return null
        val v = o["rawLrc"] ?: o["lrc"] ?: o["lyric"]
        // 有的插件 lrc 字段是歌词 URL，不是正文；只认带时间戳的文本。
        return (v as? JsonPrimitive)?.content?.takeIf { LRC_TIME.containsMatchIn(it) }
    }

    private fun embeddedTranslation(rawJson: String): String? {
        val o = runCatching { Json2.parse(rawJson) as? JsonObject }.getOrNull() ?: return null
        return ((o["translation"] ?: o["tlyric"]) as? JsonPrimitive)?.content?.takeIf { LRC_TIME.containsMatchIn(it) }
    }

    private companion object {
        val LRC_TIME = Regex("""\[\d{1,3}:\d{1,2}([.:]\d{1,3})?]""")
    }
}
