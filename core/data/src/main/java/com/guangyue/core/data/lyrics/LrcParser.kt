package com.guangyue.core.data.lyrics

/**
 * LRC parser.
 *
 * Supported syntax (superset of the usual `.lrc` dialect):
 *  - `[mm:ss]`, `[mm:ss.xx]`, `[mm:ss.xxx]`, `[mm:ss:xx]` timestamps
 *  - several timestamps on one line: `[00:12.00][01:20.00]同一个句子`
 *  - metadata: `[ti:]`, `[ar:]`, `[al:]`, `[by:]`, `[offset:±ms]`
 *  - word level timing: `<00:12.34>词<00:12.60>语`
 *  - a second LRC track merged in as translation
 *
 * The parser never throws: anything unrecognised degrades to plain text.
 */
object LrcParser {

    private val TIME_TAG = Regex("""^\[(\d{1,4}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_TAG = Regex("""<(\d{1,4}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val META_TAG = Regex("""^\[([a-zA-Z]+):(.*)]$""")

    private data class Raw(var timeMs: Long, var text: String, var tags: List<LyricTag>)

    fun parse(text: String, applyOffset: Boolean = true): Lyric {
        var offsetMs = 0L
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var by: String? = null
        var hasWordTiming = false

        val timed = ArrayList<Raw>()
        val untimed = ArrayList<String>()

        for (rawLine in text.split('\n')) {
            val line = rawLine.trim('\r', ' ', '\uFEFF', '\t')
            if (line.isEmpty()) continue

            // metadata scan (may be stacked: [ti:x][ar:y])
            var cursor = line
            var consumedMeta = false
            while (true) {
                val m = TIME_TAG.find(cursor)
                if (m == null || m.range.first != 0) break
                cursor = cursor.substring(m.range.last + 1)
            }
            if (cursor.isEmpty() && line.startsWith("[")) {
                // metadata-only line(s)
                var rest = line
                while (true) {
                    val meta = META_TAG.find(rest) ?: break
                    if (meta.range.first != 0) break
                    val k = meta.groupValues[1].lowercase()
                    val v = meta.groupValues[2].trim()
                    when (k) {
                        "ti" -> title = v
                        "ar" -> artist = v
                        "al" -> album = v
                        "by" -> by = v
                        "offset" -> offsetMs = v.toLongOrNull() ?: 0L
                    }
                    rest = rest.substring(meta.range.last + 1)
                }
                consumedMeta = true
            }
            if (consumedMeta && cursor.isEmpty()) continue

            // collect every leading timestamp
            val times = ArrayList<Long>()
            var body = line
            while (true) {
                val m = TIME_TAG.find(body) ?: break
                if (m.range.first != 0) break
                times += m.groupValues[1].toLong() * 60_000L +
                    m.groupValues[2].toLong() * 1_000L +
                    fractionToMs(m.groupValues[3])
                body = body.substring(m.range.last + 1)
            }

            if (times.isEmpty()) {
                val t = body.trim()
                if (t.isNotEmpty() && !t.startsWith("[")) untimed += t
                continue
            }

            val (plain, tags) = parseWordTiming(body)
            if (tags.isNotEmpty()) hasWordTiming = true
            val lineStart = times.first()
            for (t in times) {
                val shift = t - lineStart
                timed += Raw(
                    timeMs = t,
                    text = plain,
                    tags = tags.map { it.copy(startMs = it.startMs + shift, endMs = it.endMs + shift) },
                )
            }
        }

        val started = timed.filter { it.timeMs >= 0 }.sortedBy { it.timeMs }
        val effOffset = if (applyOffset) offsetMs else 0L

        var lines = started.map { r ->
            LyricLine(
                timeMs = (r.timeMs - effOffset).coerceAtLeast(0L),
                text = r.text,
                tags = r.tags.map { t -> t.copy(startMs = (t.startMs - effOffset).coerceAtLeast(0L), endMs = (t.endMs - effOffset).coerceAtLeast(0L)) },
            )
        }

        if (lines.isEmpty() && untimed.isNotEmpty()) {
            // Instrumental / no timestamps at all: give a gentle fixed cadence so the UI still works.
            lines = untimed.mapIndexed { i, s -> LyricLine(i * 4_000L, s) }
        }

        return Lyric(
            lines = fillEnds(lines),
            offsetMs = effOffset,
            title = title,
            artist = artist,
            album = album,
            by = by,
            hasWordTiming = hasWordTiming,
            raw = text,
        )
    }

    /** Merge [translationText] into [base] as per-line translations matched by timestamp. */
    fun mergeTranslation(base: Lyric, translationText: String): Lyric {
        if (base.isEmpty) return base
        val tr = parse(translationText, applyOffset = false)
        if (tr.isEmpty) return base

        val exact = HashMap<Long, String>(tr.lines.size * 2)
        for (l in tr.lines) {
            val t = l.text.trim()
            if (t.isNotEmpty()) exact[l.timeMs] = t
        }

        val merged = base.lines.map { line ->
            val own = line.text.trim()
            val hit = exact[line.timeMs]
            when {
                hit != null && hit != own -> line.copy(translation = hit)
                else -> {
                    val near = nearest(tr.lines, line.timeMs)
                    if (near != null && near.text.trim() != own &&
                        kotlin.math.abs(near.timeMs - line.timeMs) <= 400L
                    ) {
                        line.copy(translation = near.text.trim())
                    } else {
                        line
                    }
                }
            }
        }
        return base.copy(lines = merged)
    }

    private fun nearest(lines: List<LyricLine>, timeMs: Long): LyricLine? {
        if (lines.isEmpty()) return null
        val idx = lines.binarySearchBy(timeMs) { it.timeMs }
        val i = if (idx >= 0) idx else -(idx + 1)
        val a = lines.getOrNull(i)
        val b = lines.getOrNull(i - 1)
        return when {
            a == null -> b
            b == null -> a
            a.timeMs - timeMs <= timeMs - b.timeMs -> a
            else -> b
        }
    }

    private fun parseWordTiming(body: String): Pair<String, List<LyricTag>> {
        if (!WORD_TAG.containsMatchIn(body)) return body.trim() to emptyList()
        val sb = StringBuilder()
        val tags = ArrayList<LyricTag>()
        var cursor = 0
        var start: Long? = null
        for (m in WORD_TAG.findAll(body)) {
            if (m.range.first > cursor) {
                val chunk = body.substring(cursor, m.range.first)
                if (chunk.isNotEmpty()) {
                    val s = start
                    if (s != null) tags += LyricTag(s, m.toMs(), chunk)
                    sb.append(chunk)
                }
            }
            start = m.toMs()
            cursor = m.range.last + 1
        }
        if (cursor < body.length) {
            val tail = body.substring(cursor)
            if (tail.isNotEmpty()) {
                val s = start
                if (s != null) tags += LyricTag(s, s + 600L, tail)
                sb.append(tail)
            }
        }
        return sb.toString().trim() to tags
    }

    private fun MatchResult.toMs(): Long =
        groupValues[1].toLong() * 60_000L + groupValues[2].toLong() * 1_000L + fractionToMs(groupValues[3])

    private fun fractionToMs(f: String): Long = when (f.length) {
        0 -> 0L
        1 -> f.toLong() * 100L
        2 -> f.toLong() * 10L
        else -> f.take(3).toLong()
    }

    /** Clamp word ends to the next line start and fix degenerate ranges. */
    private fun fillEnds(lines: List<LyricLine>): List<LyricLine> =
        lines.mapIndexed { i, l ->
            if (l.tags.isEmpty()) return@mapIndexed l
            val next = lines.getOrNull(i + 1)?.timeMs ?: (l.tags.last().startMs + 2_000L)
            val fixed = l.tags.map { t ->
                val end = if (t.endMs <= t.startMs) t.startMs + 400L else t.endMs
                if (end > next) t.copy(endMs = next) else t.copy(endMs = end)
            }
            l.copy(tags = fixed)
        }
}
