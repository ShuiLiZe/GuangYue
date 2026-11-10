package com.guangyue.core.data.meta

import kotlin.math.abs

/**
 * 自动补全歌词 / 封面时的「这条搜索结果是不是这首歌」打分（纯函数，JVM 单测覆盖）。
 *
 * 2026-11-10 用户：「有些歌没歌词、专辑图片，能不能自动匹配下载」。做法是拿歌名 + 歌手去当前音源搜，
 * 对每条结果按 歌名相似度、歌手相似度、时长差（±3 秒内满分）打分，取最高且过线的一条。
 * 本地文件没写标签时 MediaStore 的标题就是文件名，这里按「歌手 - 歌名」兜底拆开，正反两种都试。
 */
object MetaMatch {

    /** 要找的那首歌。[durationMs] ≤ 0 表示不知道。[swapped] 是文件名拆出来时「歌名 - 歌手」的反向猜测。 */
    data class Query(
        val title: String,
        val artist: String,
        val durationMs: Long = 0L,
        /** 别的猜法（文件名反向拆、不拆整串），打分时取高的。 */
        val alternatives: List<Query> = emptyList(),
    ) {
        /** 搜索关键词：歌名 + 歌手（歌手不知道就只搜歌名）。 */
        val keyword: String get() = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
    }

    /** 搜索结果里的一条。 */
    data class Candidate(val title: String, val artist: String, val durationMs: Long? = null)

    /** 过线分：低于它当作没找到（静默放弃）。 */
    const val THRESHOLD = 0.72

    /** 歌名相似度低于这个直接 0 分（歌手对上也不算，避免同歌手别的歌）。 */
    const val MIN_TITLE = 0.6

    /** 时长差在这个范围内满分。 */
    const val DURATION_TOLERANCE_MS = 3_000L

    /** 时长差超过这个当成别的版本（Live / 剪辑版），总分打对折。 */
    const val DURATION_HARD_MS = 10_000L

    private val UNKNOWN_ARTISTS = setOf("", "未知艺术家", "未知歌手", "<unknown>", "unknown", "various artists", "群星")

    fun isUnknownArtist(a: String?): Boolean = a == null || a.trim().lowercase() in UNKNOWN_ARTISTS

    private val TRACK_NO = Regex("""^\s*\d{1,3}\s*[.\-_、)]\s*""")
    private val FILE_EXT = Regex("""\.(mp3|flac|m4a|aac|ogg|opus|wav|ape|wma|dsf|dff)$""", RegexOption.IGNORE_CASE)
    private val DASH = Regex("""\s*[-–—－_]\s*""")

    /**
     * 由库里的 歌名 / 歌手 / 时长 得出查询。歌手不知道且歌名像文件名「A - B」时拆开：
     * 默认 A 是歌手、B 是歌名（国内下载站最常见），反过来的猜测和不拆的整串放在 [Query.alternatives]。
     */
    fun queryOf(title: String, artist: String?, durationMs: Long = 0L): Query {
        var t = title.trim().replace(FILE_EXT, "")
        t = t.replace(TRACK_NO, "")
        val a = artist?.trim().orEmpty()
        if (isUnknownArtist(a)) {
            val parts = t.split(DASH, limit = 2).map { it.trim() }
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                val swapped = Query(title = parts[0], artist = parts[1], durationMs = durationMs)
                val whole = Query(title = t, artist = "", durationMs = durationMs)
                return Query(title = parts[1], artist = parts[0], durationMs = durationMs, alternatives = listOf(swapped, whole))
            }
            return Query(title = t, artist = "", durationMs = durationMs)
        }
        return Query(title = t, artist = a, durationMs = durationMs)
    }

    private val BRACKETS = Regex("""[(（\[【<《][^)）\]】>》]*[)）\]】>》]""")
    private val FEAT = Regex("""\s+(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE)

    /** 归一化：全角转半角、小写、去括号内容（Live / 伴奏 / 版本说明）、去 feat、只留字母数字和汉字。 */
    fun normalize(s: String, dropBrackets: Boolean = true): String {
        val half = buildString(s.length) {
            for (ch in s) {
                append(
                    when (ch) {
                        '\u3000' -> ' '
                        in '\uFF01'..'\uFF5E' -> (ch.code - 0xFEE0).toChar()
                        else -> ch
                    },
                )
            }
        }
        var t = half.lowercase()
        if (dropBrackets) {
            val stripped = t.replace(BRACKETS, " ")
            // 整个名字都在括号里（《七里香》）时不去掉
            if (stripped.isNotBlank()) t = stripped
        }
        t = t.replace(FEAT, "")
        return t.filter { it.isLetterOrDigit() }
    }

    /** 两段文字的相似度 0..1：归一化后相等 1；一方包含另一方 0.85；否则字二元组 Dice 系数。 */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        if (x.contains(y) || y.contains(x)) {
            val ratio = minOf(x.length, y.length).toDouble() / maxOf(x.length, y.length)
            return 0.7 + 0.15 * ratio
        }
        return dice(x, y)
    }

    private fun dice(x: String, y: String): Double {
        if (x.length < 2 || y.length < 2) return if (x == y) 1.0 else 0.0
        val bx = x.windowed(2).groupingBy { it }.eachCount()
        val by = y.windowed(2).groupingBy { it }.eachCount()
        var inter = 0
        for ((k, v) in bx) inter += minOf(v, by[k] ?: 0)
        return 2.0 * inter / ((x.length - 1) + (y.length - 1))
    }

    private val ARTIST_SPLIT = Regex("""\s*(/|&|、|,|，|;|；|\+|×|\s+and\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""", RegexOption.IGNORE_CASE)

    /** 歌手相似度：拆成多人后有任何一人对上就 1；否则整串相似度。 */
    fun artistSimilarity(query: String, candidate: String): Double {
        if (isUnknownArtist(query) || candidate.isBlank()) return 0.0
        val qs = query.split(ARTIST_SPLIT).map { normalize(it, dropBrackets = false) }.filter { it.isNotEmpty() }
        val cs = candidate.split(ARTIST_SPLIT).map { normalize(it, dropBrackets = false) }.filter { it.isNotEmpty() }
        if (qs.any { q -> cs.any { it == q } }) return 1.0
        return similarity(query, candidate)
    }

    /** 时长分：两边都知道时，差 ≤3 秒 1 分，3–10 秒线性降到 0；不知道返回 null（不计入）。 */
    fun durationScore(queryMs: Long, candidateMs: Long?): Double? {
        if (queryMs <= 0L || candidateMs == null || candidateMs <= 0L) return null
        val d = abs(queryMs - candidateMs)
        if (d <= DURATION_TOLERANCE_MS) return 1.0
        if (d >= DURATION_HARD_MS) return 0.0
        return 1.0 - (d - DURATION_TOLERANCE_MS).toDouble() / (DURATION_HARD_MS - DURATION_TOLERANCE_MS)
    }

    /** 单条打分 0..1。歌名 0.6 / 歌手 0.3 / 时长 0.1，缺哪项就按剩下的权重归一。 */
    fun score(q: Query, c: Candidate): Double {
        val t = similarity(q.title, c.title)
        if (t < MIN_TITLE) return 0.0
        var sum = 0.6 * t
        var w = 0.6
        if (!isUnknownArtist(q.artist)) {
            sum += 0.3 * artistSimilarity(q.artist, c.artist)
            w += 0.3
        }
        val d = durationScore(q.durationMs, c.durationMs)
        if (d != null) {
            sum += 0.1 * d
            w += 0.1
        }
        var s = sum / w
        if (q.durationMs > 0 && (c.durationMs ?: 0) > 0 && abs(q.durationMs - c.durationMs!!) >= DURATION_HARD_MS) s *= 0.5
        return s
    }

    /** 几种猜法取高的（别的猜法略打折，同分时信默认拆法）。 */
    fun bestScore(q: Query, c: Candidate): Double {
        val main = score(q, c)
        val alt = q.alternatives.maxOfOrNull { score(it, c) * 0.97 } ?: 0.0
        return maxOf(main, alt)
    }

    /** 选最匹配的一条：返回下标，没有过线的返回 null。同分取靠前的（音源自己的相关度排序）。 */
    fun pick(q: Query, candidates: List<Candidate>, threshold: Double = THRESHOLD): Int? {
        var best = -1
        var top = -1.0
        candidates.forEachIndexed { i, c ->
            val sc = bestScore(q, c)
            if (sc >= threshold && sc > top + 1e-9) {
                best = i
                top = sc
            }
        }
        return best.takeIf { it >= 0 }
    }
}
