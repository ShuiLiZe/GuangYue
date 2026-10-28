package com.guangyue.core.data.lyrics

/**
 * A single synchronized lyric line.
 *
 * @param timeMs start time in milliseconds (already offset-corrected)
 * @param text main lyric text of this line
 * @param translation optional translated text merged from a sibling LRC track
 * @param tags per-character timing, empty when the source had no word-level timing
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String? = null,
    val tags: List<LyricTag> = emptyList(),
) {
    val endMsHint: Long = timeMs

    val isBlank: Boolean get() = text.isBlank() && translation.isNullOrBlank()
}

/**
 * Word / character level timing inside one line.
 *
 * @param startMs absolute start time in ms
 * @param endMs absolute end time in ms
 * @param data the tagged text
 */
data class LyricTag(
    val startMs: Long,
    val endMs: Long,
    val data: String,
)
