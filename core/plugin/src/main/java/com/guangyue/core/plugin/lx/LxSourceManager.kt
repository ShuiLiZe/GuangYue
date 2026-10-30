package com.guangyue.core.plugin.lx

import android.content.Context
import android.util.Log
import com.guangyue.core.plugin.Json2
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.asObj
import com.guangyue.core.plugin.asStr
import com.guangyue.core.plugin.di.PlaybackHttp
import com.guangyue.core.plugin.runtime.PreludeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 「取链源」管理器：装 / 删 / 排序 / 开关 / 体检，以及播放时的取链回退。
 *
 * 与 [com.guangyue.core.plugin.PluginManager] 平行的一层 —— 洛雪源不参与搜索，只被
 * 当作「插件自己取不到链时的第二出口」。加载完成后 [runtimeFor] 给出一个可调用的
 * [LxRuntime]；同一份脚本只会跑一个实例（源之间不并发）。
 */
@Singleton
class LxSourceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: LxStore,
    @PlaybackHttp private val http: OkHttpClient,
    private val prelude: PreludeProvider,
) {
    private val mutex = Mutex()
    private val runtimes = HashMap<String, LxRuntime>()
    private val sourceLocks = HashMap<String, Mutex>()

    private val _sources = MutableStateFlow<List<LxSource>>(emptyList())
    val sources: StateFlow<List<LxSource>> = _sources.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** 最近一次操作的提示（成功 / 失败原因），UI 上做一行提示条。 */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 失败源码细节，供「详情」展开。 */
    @Volatile
    var lastErrorDetails: String? = null
        private set

    private var loaded = false

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (loaded) return@withContext
            _sources.value = store.readIndex().sortedWith(compareBy({ it.sortOrder }, { it.name }))
            loaded = true
        }
    }

    fun report(text: String?) {
        _message.value = text
    }

    fun clearMessage() {
        _message.value = null
    }

    /** 启用中的源，按用户排序（列表顺序即优先级）。 */
    fun enabled(): List<LxSource> = _sources.value.filter { it.enabled && it.error == null }

    fun find(id: String): LxSource? = _sources.value.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ 安装 / 删除

    /** 从链接安装（也接受 `file://` 与本地绝对路径）。 */
    suspend fun installFromUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        _busy.value = true
        try {
            val text = if (url.startsWith("file://") || File(url).isAbsolute) {
                val path = url.removePrefix("file://")
                File(path).readText()
            } else {
                download(url)
            }
            installScript(text, srcUrl = url)
        } catch (t: Throwable) {
            fail(t)
            false
        } finally {
            _busy.value = false
        }
    }

    /** 从 `content://` 或 `file://` 的 Uri 读脚本并安装（系统文件选择器给的就是这种）。 */
    suspend fun installFromUriCompat(uri: android.net.Uri): Boolean = withContext(Dispatchers.IO) {
        val code = when (uri.scheme?.lowercase()) {
            "content" -> context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            else -> uri.path?.let { File(it).readText() }
        } ?: throw LxRuntimeException("读不到这个文件")
        installScript(code, srcUrl = uri.toString())
    }

    /** 从已读进内存的脚本文本安装（文件选择器走这条路）。 */
    suspend fun installScript(code: String, srcUrl: String? = null): Boolean =
        withContext(Dispatchers.IO) {
            _busy.value = true
            try {
                if (code.isBlank()) {
                    report("脚本是空的")
                    return@withContext false
                }
                val meta = LxMetaParser.parse(code)
                val id = store.idFor(meta.name, code)
                val now = System.currentTimeMillis()

                // 先落盘再试跑：加载失败也要把「装过、跑不起来」如实留在列表里，
                // 用户能删掉它，而不是像 0.0.7 那样悄悄消失。
                store.writeScript(id, code)
                val record = meta.copy(
                    id = id,
                    srcUrl = srcUrl ?: find(id)?.srcUrl,
                    enabled = find(id)?.enabled ?: false,
                    sortOrder = find(id)?.sortOrder
                        ?: (_sources.value.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                    installedAt = find(id)?.installedAt ?: now,
                    updatedAt = now,
                    sizeBytes = code.toByteArray().size.toLong(),
                )
                val probe = probe(record, code)
                persist(record.copy(error = probe.error, note = probe.note, platforms = probe.platforms))
                runtimes.remove(id)?.close()
                if (probe.error == null) {
                    report("已装「${record.displayName}」：${record.platformLabel}")
                    lastErrorDetails = null
                } else {
                    report("「${record.displayName}」装上了但跑不起来：${probe.error}")
                }
                probe.error == null
            } catch (t: Throwable) {
                fail(t)
                false
            } finally {
                _busy.value = false
            }
        }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        runCatching { runtimes.remove(id)?.close() }
        store.delete(id)
        mutex.withLock {
            val next = _sources.value.filterNot { it.id == id }
            _sources.value = next
            store.writeIndex(next)
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        update(id) { it.copy(enabled = enabled, updatedAt = System.currentTimeMillis()) }
    }

    suspend fun move(id: String, to: Int) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val list = _sources.value.toMutableList()
            val from = list.indexOfFirst { it.id == id }
            if (from < 0) return@withLock
            val target = to.coerceIn(0, list.size - 1)
            if (target == from) return@withLock
            val item = list.removeAt(from)
            list.add(target, item)
            val renumbered = list.mapIndexed { index, s -> s.copy(sortOrder = index) }
            _sources.value = renumbered
            store.writeIndex(renumbered)
        }
    }

    /** 重新试跑一遍：解析平台、清掉/更新错误原因。 */
    suspend fun recheck(id: String): Boolean = withContext(Dispatchers.IO) {
        val record = find(id) ?: return@withContext false
        val code = store.readScript(id) ?: return@withContext false
        _busy.value = true
        try {
            runCatching { runtimes.remove(id)?.close() }
            val probe = probe(record, code)
            update(id) { it.copy(error = probe.error, note = probe.note, platforms = probe.platforms) }
            if (probe.error == null) {
                report("「${record.displayName}」可用：${record.platformLabel}")
                lastErrorDetails = null
            } else {
                report("「${record.displayName}」不可用：${probe.error}")
            }
            probe.error == null
        } finally {
            _busy.value = false
        }
    }

    suspend fun recheckAll() = withContext(Dispatchers.IO) {
        _busy.value = true
        try {
            _sources.value.forEach { runCatching { recheck(it.id) } }
            val ok = _sources.value.count { it.error == null }
            report("体检完成：${ok}/${_sources.value.size} 个取链源可用")
        } finally {
            _busy.value = false
        }
    }

    // ------------------------------------------------------------------ 取链

    /**
     * 按启用顺序问每个取链源，谁先给出链接就用谁。
     *
     * @param qualityWire 播放器当前档位（`standard` / `lossless` / `hires` …）
     * @param defaultWire 设置里的「取链默认音质」（128k / 320k / flac / flac24bit）
     * @return 直链，全部失败返回 null（调用方沿用原来的「取不到链」文案）
     */
    suspend fun resolveMediaUrl(
        item: MusicItem,
        qualityWire: String,
        defaultWire: String = "320k",
    ): String? {
        load()
        val candidates = enabled()
        if (candidates.isEmpty()) return null
        val key = LxPlatforms.of(item.platform) ?: return null
        val quality = LxQuality.pick(qualityWire, defaultWire)
        for (source in candidates) {
            if (source.platforms.isNotEmpty() && !source.platforms.contains(key)) continue
            val musicInfo = LxMusicInfo.of(key, item) ?: continue
            val url = runCatching { resolveWith(source, key, quality, musicInfo) }
                .onFailure { runCatching { Log.d(TAG, "lx ${source.displayName} 取链失败：${it.message}") } }
                .getOrNull()
            if (!url.isNullOrBlank()) {
                Log.d(TAG, "lx ${source.displayName} 取到链：$key ${item.title}")
                return url
            }
        }
        return null
    }

    private suspend fun resolveWith(
        source: LxSource,
        platform: String,
        quality: String,
        musicInfo: JsonObject,
    ): String? {
        val runtime = runtimeFor(source)
        val lock = sourceLocks.getOrPut(source.id) { Mutex() }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                val url = runtime.musicUrl(platform, quality, musicInfo)
                if (url.isBlank()) null else url
            }
        }
    }

    private fun runtimeFor(source: LxSource): LxRuntime {
        runtimes[source.id]?.takeIf { it.isAlive }?.let { return it }
        val code = store.readScript(source.id)
            ?: throw LxRuntimeException("脚本文件丢了，删掉重装一个")
        val runtime = LxRuntime(source, code, http, prelude)
        runtime.onLog = { level, msg -> Log.d(TAG, "[${source.displayName}] $level: $msg") }
        runtime.install()
        runtimes[source.id] = runtime
        return runtime
    }

    /** 装一个临时运行时试跑，成功与否都关掉（不占内存）。 */
    private fun probe(source: LxSource, code: String): Probe {
        val runtime = LxRuntime(source, code, http, prelude)
        return try {
            runtime.install()
            Probe(
                platforms = runtime.platforms,
                error = null,
                note = "上报音源：" + runtime.platforms.mapNotNull { LxPlatforms.label(it) }
                    .joinToString(" / "),
            )
        } catch (t: Throwable) {
            val err = t as? LxRuntimeException
            lastErrorDetails = err?.original?.let { od ->
                buildString {
                    appendLine(od::class.java.name)
                    appendLine(od.message ?: "")
                    od.stackTrace.take(15).forEach { appendLine("  at $it") }
                }
            }
            Probe(emptyList(), humanError(t), null)
        } finally {
            runCatching { runtime.close() }
        }
    }

    private data class Probe(val platforms: List<String>, val error: String?, val note: String?)

    /** 原始异常栈不外露：列表上只给一行中文。 */
    private fun humanError(t: Throwable): String {
        val msg = t.message ?: t::class.simpleName ?: "加载失败"
        return when {
            msg.contains("SyntaxError", true) || msg.contains("unexpected token", true) ->
                "脚本语法错误，可能不是洛雪自定义源"
            msg.contains("out of memory", true) -> "脚本内存超限"
            msg.contains("interrupt", true) || msg.contains("超时", true) -> "加载超时"
            msg.contains("没有上报任何音源") -> "脚本没有上报音源"
            msg.contains("没有注册", true) -> "脚本没有注册取链处理器"
            msg.startsWith("[") -> msg.take(120)
            else -> msg.take(120)
        }
    }

    private suspend fun download(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", LxRuntime.UA).build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw LxRuntimeException("下载失败：HTTP ${resp.code}")
                }
                resp.body?.string() ?: throw LxRuntimeException("下载到的内容是空的")
            }
        }
    }

    private fun update(id: String, transform: (LxSource) -> LxSource) {
        val list = _sources.value.map { if (it.id == id) transform(it) else it }
        _sources.value = list
        store.writeIndex(list)
    }

    private fun persist(record: LxSource) {
        val current = _sources.value.toMutableList()
        val index = current.indexOfFirst { it.id == record.id }
        if (index >= 0) current[index] = record else current.add(record)
        val sorted = current.sortedWith(compareBy({ it.sortOrder }, { it.name }))
        _sources.value = sorted
        store.writeIndex(sorted)
    }

    private fun fail(t: Throwable) {
        lastErrorDetails = t.stackTraceToString()
        report(t.message ?: "操作失败")
        Log.w(TAG, "lx 操作失败", t)
    }

    private companion object {
        const val TAG = "GuangYueLx"
    }
}

/**
 * 广乐曲目 → 洛雪 `musicInfo`。
 *
 * 洛雪协议里每个源的字段名都不一样（tx 认 `songmid`、wy 认 `musicId`/`hash`、
 * kg 认 `hash`、kw/mg 认 `copyrightId`），插件给的原始 JSON 里通常已经带着这些键，
 * 所以优先从 `raw` 里原样透传，缺了再从 [MusicItem] 的通用字段补。
 */
object LxMusicInfo {

    fun of(platformKey: String, item: MusicItem): JsonObject? {
        val raw = item.raw
        val fields = LinkedHashMap<String, JsonPrimitive>()

        fun put(key: String, value: String?) {
            val v = value?.trim()
            if (!v.isNullOrBlank() && v != "0") fields[key] = JsonPrimitive(v)
        }

        fun fromRaw(vararg keys: String): String? {
            val obj = raw ?: return null
            for (key in keys) {
                obj[key].asStr()?.let { if (it.isNotBlank()) return it }
            }
            return null
        }

        // 通用字段：洛雪源几乎都要 name / singer / interval。
        put("name", item.title)
        put("singer", item.artist)
        put("interval", item.durationMs?.takeIf { it > 0 }?.let(::formatInterval))

        when (platformKey) {
            "tx" -> {
                put("songmid", fromRaw("songmid", "mid", "songMid") ?: item.id)
                put("mediaMid", fromRaw("mediaMid", "strMediaMid", "media_mid"))
                put("albumMid", fromRaw("albumMid", "albummid"))
                put("songId", fromRaw("songId", "songid"))
            }
            "wy" -> {
                put("musicId", fromRaw("musicId", "songId") ?: item.id)
                put("id", fromRaw("id", "musicId") ?: item.id)
                put("hash", fromRaw("hash"))
                put("albumId", fromRaw("albumId", "albumid") ?: item.albumId)
            }
            "kg" -> {
                put("hash", fromRaw("hash", "fileHash", "FileHash") ?: item.id)
                put("albumId", fromRaw("albumId", "album_id") ?: item.albumId)
                put("albumAudioId", fromRaw("albumAudioId"))
            }
            "kw" -> {
                put("copyrightId", fromRaw("copyrightId", "copyright_id"))
                put("musicId", fromRaw("musicId", "rid") ?: item.id)
                put("songmid", fromRaw("songmid", "rid") ?: item.id)
            }
            "mg" -> {
                put("copyrightId", fromRaw("copyrightId", "copyright_id"))
                put("musicId", fromRaw("musicId") ?: item.id)
                put("albumId", fromRaw("albumId") ?: item.albumId)
                put("songId", fromRaw("songId") ?: item.id)
            }
            else -> return null
        }
        if (fields.isEmpty()) return null
        return JsonObject(fields)
    }

    /** 洛雪 `interval` 是 `"04:29"` 这种 mm:ss。 */
    private fun formatInterval(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        val minutes = total / 60
        val seconds = total % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}