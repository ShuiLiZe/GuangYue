package com.guangyue.core.data.lyrics

/**
 * Parsed lyric document.
 *
 * Lines are sorted by time and always start with concept of "lead-in" handled by the
 * player (progress < first line => index -1).
 */
data class Lyric(
    val lines: List<LyricLine>,
    /** `[offset:...]` value in ms, positive means "lyrics appear later". Already applied to [lines]. */
    val offsetMs: Long = 0L,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val by: String? = null,
    /** true when word-level (`<mm:ss.xx>`) timing was found and parsed */
    val hasWordTiming: Boolean = false,
    /** original raw source, kept for debugging / export */
    val raw: String? = null,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /** Binary search: index of the line playing at [positionMs], or -1 before the first line. */
    fun indexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }

    fun lineAt(positionMs: Long): LyricLine? {
        val i = indexAt(positionMs)
        return if (i < 0) null else lines[i]
    }

    /** Progress (0f..1f) through the character group [tagIndex] of [index] line. */
    fun charProgress(index: Int, tagIndex: Int, positionMs: Long): Float {
        val line = lines.getOrNull(index) ?: return 0f
        val tag = line.tags.getOrNull(tagIndex) ?: return 0f
        val span = (tag.endMs - tag.startMs).coerceAtLeast(1L)
        return ((positionMs - tag.startMs).toFloat() / span).coerceIn(0f, 1f)
    }

    companion object {
        val EMPTY = Lyric(emptyList())
    }
}
