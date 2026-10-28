package com.guangyue.core.data.lyrics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of "where the lyric UI should be right now".
 *
 * @param index index into [Lyric.lines], -1 before the first line / no lyrics
 * @param line the active line, null when [index] < 0
 * @param charIndex index of the currently sung character group, -1 when the lyric has no
 *   word-level timing
 * @param charProgress 0f..1f progress through that character group
 * @param manual Whether the user is currently dragging the lyric list (auto scroll paused)
 */
data class LyricUiState(
    val lyric: Lyric = Lyric.EMPTY,
    val index: Int = -1,
    val line: LyricLine? = null,
    val charIndex: Int = -1,
    val charProgress: Float = 0f,
    val manual: Boolean = false,
    val translationVisible: Boolean = true,
) {
    val isIdle: Boolean get() = lyric.isEmpty
}

/**
 * Core progress ↔ lyric synchronizer shared by every lyric surface (flow, stage, mini bar).
 *
 * It is deliberately a pure in-memory object: callers push playhead position, everyone reads
 * the same [state]. Binary search keeps per-frame cost O(log n).
 */
class LyricSynchronizer {

    private val _state = MutableStateFlow(LyricUiState())
    val state: StateFlow<LyricUiState> = _state.asStateFlow()

    private var manualUntilMs = 0L

    fun setLyric(lyric: Lyric) {
        _state.value = LyricUiState(lyric = lyric)
    }

    fun clear() {
        manualUntilMs = 0L
        _state.value = LyricUiState()
    }

    /** A user drag; auto-scroll stays paused until [resumeAutoScroll] or 3 s elapse. */
    fun onManualScroll(nowMs: Long = System.currentTimeMillis()) {
        manualUntilMs = nowMs + MANUAL_PAUSE_MS
        _state.value = _state.value.copy(manual = true)
    }

    fun resumeAutoScroll() {
        manualUntilMs = 0L
        _state.value = _state.value.copy(manual = false)
    }

    val manualPauseMillis: Long get() = MANUAL_PAUSE_MS

    /** Feed the player position; called from a ~60fps ticker while the lyric screen is visible. */
    fun onPosition(positionMs: Long, nowMs: Long = System.currentTimeMillis()) {
        val cur = _state.value
        val manual = cur.manual && nowMs < manualUntilMs
        if (!manual && cur.manual) {
            _state.value = cur.copy(manual = false)
        }
        val lyric = cur.lyric
        if (lyric.isEmpty) return

        val index = lyric.indexAt(positionMs)
        val line = if (index >= 0) lyric.lines[index] else null

        var charIndex = -1
        var charProgress = 0f
        if (line != null && line.tags.isNotEmpty()) {
            var ci = -1
            for (i in line.tags.indices) {
                if (line.tags[i].startMs <= positionMs) ci = i else break
            }
            charIndex = ci
            if (ci >= 0) {
                val tag = line.tags[ci]
                val span = (tag.endMs - tag.startMs).coerceAtLeast(1L)
                charProgress = ((positionMs - tag.startMs).toFloat() / span).coerceIn(0f, 1f)
            }
        }

        val next = cur.copy(
            index = index,
            line = line,
            charIndex = charIndex,
            charProgress = charProgress,
            manual = manual,
        )
        if (next != cur) _state.value = next
    }

    companion object {
        /** Manual scroll pauses auto-scroll for 3 s (design requirement). */
        const val MANUAL_PAUSE_MS = 3_000L
    }
}
