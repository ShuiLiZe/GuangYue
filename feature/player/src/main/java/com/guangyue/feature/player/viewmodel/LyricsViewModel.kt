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
) : ViewModel() {

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
                    loadLyrics(current)
                }
                sync.onPosition(state.positionMs, System.currentTimeMillis())
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
                val row = tracks.byKey(item.key)
                val musicItem = row?.let { TrackMapper.toMusicItem(it) }
                    ?: MusicItem(
                        platform = item.origin.pluginId,
                        id = item.key,
                        title = item.title,
                        artist = item.artist,
                    )

                val source = bridge.getLyric(item.origin.pluginId, musicItem)
                val raw = source?.rawLrc ?: item.rawJson?.let { embeddedLrc(it) }
                if (raw.isNullOrBlank()) {
                    _message.value = "暂无歌词"
                    return@launch
                }
                var parsed = LrcParser.parse(raw)
                source?.translation?.let { parsed = LrcParser.mergeTranslation(parsed, it) }
                _lyric.value = parsed
                sync.setLyric(parsed)
                _syncState.value = sync.state.value
            } catch (t: Throwable) {
                _message.value = "歌词获取失败：${t.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    /** Some plugins inline the LRC in the music item (MusicFree `rawLrc`). */
    private fun embeddedLrc(rawJson: String): String? {
        val o = Json2.parse(rawJson) as? JsonObject ?: return null
        val v = o["rawLrc"] ?: o["lrc"] ?: o["lyric"]
        return (v as? JsonPrimitive)?.content
    }
}
