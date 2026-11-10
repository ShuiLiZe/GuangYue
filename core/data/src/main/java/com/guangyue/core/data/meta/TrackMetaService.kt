package com.guangyue.core.data.meta

import android.content.Context
import android.net.Uri
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.MusicPluginBridge
import com.guangyue.core.plugin.PluginManager
import com.guangyue.core.plugin.SearchType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动补全歌词 / 封面（2026-11-10 用户：「有些歌没歌词、专辑图片，能不能自动匹配下载」）。
 *
 * 不新造网络源：用用户选中的「当前音源」插件 `search` 找这首歌（[MetaMatch] 打分挑一条），
 * 再用同一插件的 `getLyric` / `getMusicInfo` 拿歌词和封面地址。结果缓存在应用数据
 * `files/track_meta/<key 的 sha1>.json`（歌词）+ `.img`（封面），按曲目 key 关联，下次直接用。
 * 没找到也记一笔（[MISS_RETRY_MS] 内不再搜），全程静默：任何失败都只返回 null。
 */
@Singleton
class TrackMetaService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: MusicPluginBridge,
    private val plugins: PluginManager,
) {
    @Serializable
    data class Entry(
        val lrc: String? = null,
        val translation: String? = null,
        /** 匹配到的那条（platform:id），排查用。 */
        val matched: String? = null,
        /** 上次去搜的时间；没找到时据此决定多久后再试。 */
        val triedAt: Long = 0L,
        val lyricMiss: Boolean = false,
        val coverMiss: Boolean = false,
    )

    /** 一首歌的已缓存结果。[artworkUri] 是 `file://` 封面。 */
    data class Cached(val lrc: String?, val translation: String?, val artworkUri: String?)

    /** 要补的那首歌。 */
    data class Request(
        val key: String,
        val title: String,
        val artist: String,
        val durationMs: Long = 0L,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val dir: File get() = File(context.filesDir, "track_meta").apply { mkdirs() }
    private val mutex = Mutex()

    private fun id(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun entryFile(key: String) = File(dir, id(key) + ".json")
    private fun coverFile(key: String) = File(dir, id(key) + ".img")

    private fun readEntry(key: String): Entry? =
        runCatching { entryFile(key).takeIf { it.isFile }?.readText()?.let { json.decodeFromString(Entry.serializer(), it) } }.getOrNull()

    private fun writeEntry(key: String, e: Entry) {
        runCatching {
            val f = entryFile(key)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(json.encodeToString(Entry.serializer(), e))
            tmp.renameTo(f)
        }
    }

    /** 缓存里的封面（`file://`），没有返回 null。只看文件是否存在，便宜，可以逐行调。 */
    fun cachedArtwork(key: String): String? =
        coverFile(key).takeIf { it.isFile && it.length() > 0 }?.let { Uri.fromFile(it).toString() }

    /** 缓存里的歌词 + 封面。 */
    fun cached(key: String): Cached? {
        val e = readEntry(key)
        val art = cachedArtwork(key)
        if (e?.lrc == null && art == null) return null
        return Cached(e?.lrc, e?.translation, art)
    }

    /** 这个封面地址读得出来吗：http(s) 当作能；file 看文件；content 试着打开（本地歌没内嵌封面时打不开）。 */
    fun artworkReadable(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        return when {
            uri.startsWith("http://") || uri.startsWith("https://") -> true
            uri.startsWith("file:") -> runCatching { File(Uri.parse(uri).path.orEmpty()).let { it.isFile && it.length() > 0 } }.getOrDefault(false)
            uri.startsWith("/") -> File(uri).let { it.isFile && it.length() > 0 }
            uri.startsWith("content:") -> runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.read() >= 0 } ?: false
            }.getOrDefault(false)
            else -> true
        }
    }

    /**
     * 去补 [needLyric] / [needCover]。已缓存的部分直接返回；上次没找到且没过 [MISS_RETRY_MS] 不再搜（[force] 除外）。
     * 返回补完后的缓存（可能仍缺），任何失败返回 null，不抛。
     */
    suspend fun ensure(req: Request, needLyric: Boolean, needCover: Boolean, force: Boolean = false): Cached? =
        withContext(Dispatchers.IO) {
            runCatching { mutex.withLock { ensureLocked(req, needLyric, needCover, force) } }
                .onFailure { android.util.Log.w(TAG, "ensure ${req.key} failed: ${it.message}") }
                .getOrNull()
        }

    private suspend fun ensureLocked(req: Request, needLyric0: Boolean, needCover0: Boolean, force: Boolean): Cached? {
        val old = readEntry(req.key) ?: Entry()
        val needLyric = needLyric0 && old.lrc == null
        val needCover = needCover0 && cachedArtwork(req.key) == null
        if (!needLyric && !needCover) return cached(req.key)
        val now = System.currentTimeMillis()
        val recentMiss = now - old.triedAt < MISS_RETRY_MS
        if (!force && recentMiss && (!needLyric || old.lyricMiss) && (!needCover || old.coverMiss)) return cached(req.key)

        plugins.load()
        val source = plugins.currentSource() ?: return cached(req.key)
        val q = MetaMatch.queryOf(req.title, req.artist, req.durationMs)
        if (q.keyword.isBlank()) return cached(req.key)

        val items = search(source.id, q.keyword).ifEmpty {
            // 歌手写法对不上时只用歌名再搜一次
            if (q.artist.isNotBlank()) search(source.id, q.title) else emptyList()
        }
        val pickIndex = MetaMatch.pick(q, items.map { MetaMatch.Candidate(it.title, it.artist, it.durationMs) })
        val hit = pickIndex?.let { items[it] }
        if (hit == null) {
            writeEntry(req.key, old.copy(triedAt = now, lyricMiss = old.lyricMiss || needLyric, coverMiss = old.coverMiss || needCover))
            return cached(req.key)
        }

        var lrc = old.lrc
        var trans = old.translation
        if (needLyric) {
            val res = runCatching { bridge.getLyric(source.id, hit) }.getOrNull()
            lrc = (res?.rawLrc ?: embedded(hit, "rawLrc"))?.takeIf { LRC_TIME.containsMatchIn(it) }
            trans = res?.translation?.takeIf { LRC_TIME.containsMatchIn(it) }
        }
        var coverOk = cachedArtwork(req.key) != null
        if (needCover) {
            val url = hit.artwork?.takeIf { it.startsWith("http") }
                ?: runCatching { bridge.getMusicInfo(source.id, hit)?.artwork }.getOrNull()?.takeIf { it.startsWith("http") }
            if (url != null) coverOk = download(url, coverFile(req.key))
        }
        writeEntry(
            req.key,
            Entry(
                lrc = lrc,
                translation = trans,
                matched = "${hit.platform}:${hit.id}",
                triedAt = now,
                lyricMiss = needLyric && lrc == null,
                coverMiss = needCover && !coverOk,
            ),
        )
        android.util.Log.i(TAG, "matched ${req.key} → ${hit.platform}:${hit.id} lyric=${lrc != null} cover=$coverOk")
        return cached(req.key)
    }

    private suspend fun search(pluginId: String, keyword: String): List<MusicItem> =
        runCatching { bridge.search(pluginId, keyword, 1, SearchType.MUSIC).items }.getOrDefault(emptyList()).take(20)

    private fun embedded(item: MusicItem, field: String): String? =
        (item.raw?.get(field) as? kotlinx.serialization.json.JsonPrimitive)?.content

    /** 下载封面：≤ 8 MB、内容是图片（JPEG / PNG / WebP / GIF 文件头）才落盘。 */
    private fun download(url: String, out: File): Boolean = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) GuangYue")
        }
        try {
            if (conn.responseCode !in 200..299) return@runCatching false
            val bytes = conn.inputStream.use { input ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                    if (buf.size() > MAX_COVER_BYTES) return@runCatching false
                }
                buf.toByteArray()
            }
            if (!MetaFiles.looksLikeImage(bytes)) return@runCatching false
            val tmp = File(out.parentFile, out.name + ".tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(out)
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(false)

    companion object {
        private const val TAG = "TrackMeta"
        /** 没找到后多久再试（3 天）。 */
        const val MISS_RETRY_MS = 3L * 24 * 3600 * 1000
        const val MAX_COVER_BYTES = 8 * 1024 * 1024
        private val LRC_TIME = Regex("""\[\d{1,3}:\d{1,2}([.:]\d{1,3})?]""")
    }
}

/** 纯函数小工具（单测覆盖）。 */
object MetaFiles {
    /** JPEG / PNG / GIF / WebP 文件头。 */
    fun looksLikeImage(b: ByteArray): Boolean {
        if (b.size < 12) return false
        fun u(i: Int) = b[i].toInt() and 0xff
        return (u(0) == 0xFF && u(1) == 0xD8) ||
            (u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47) ||
            (u(0) == 'G'.code && u(1) == 'I'.code && u(2) == 'F'.code) ||
            (u(0) == 'R'.code && u(1) == 'I'.code && u(2) == 'F'.code && u(3) == 'F'.code && u(8) == 'W'.code && u(9) == 'E'.code && u(10) == 'B'.code && u(11) == 'P'.code)
    }
}
