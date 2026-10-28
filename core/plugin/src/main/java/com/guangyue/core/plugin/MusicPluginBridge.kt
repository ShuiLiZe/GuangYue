package com.guangyue.core.plugin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.guangyue.core.plugin.runtime.PluginRuntime
import com.guangyue.core.plugin.runtime.PluginRuntimeException
import com.guangyue.core.plugin.runtime.PreludeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The bridge between the app and MusicFree-style JS plugins.
 *
 * Public surface (all suspend, all off the main thread):
 *  - [search] / [searchAlbum] / [searchArtist]      – browser
 *  - [getMediaSource]                               – played lazily by :core:player
 *  - [getLyric] / [getMusicInfo] / [getAlbumInfo] / [getArtistWorks]
 *  - [getTopLists] / [getTopListDetail] / [importMusicSheet] / [getMusicSheetInfo]
 *
 * Every function degrades gracefully: if a plugin does not implement it, the caller gets an
 * empty result (or `null` + a warning) instead of an exception, exactly like MusicFree.
 *
 * Isolation: one [PluginRuntime] (= one QuickJS heap) per plugin, created on first use and kept
 * alive until the plugin is disabled/updated/deleted. Calls are serialised per plugin.
 */
@Singleton
class MusicPluginBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: PluginManager,
    private val preludeProvider: PreludeProvider,
    private val http: OkHttpClient,
) {
    private val runtimes = HashMap<String, PluginRuntime>()
    private val locks = HashMap<String, Any>()
    private val cache = PluginCache()

    /** Called whenever a plugin writes to `console.*`; wired to the plugin log screen. */
    var onLog: ((pluginId: String, level: String, message: String) -> Unit)? = null

    init {
        manager.reloadHook = { platform ->
            val victims = runtimes.filterValues { platform == null || it.meta.platform == platform }
            victims.forEach { (id, rt) -> rt.close(); runtimes.remove(id) }
            cache.clear()
        }
    }

    // ------------------------------------------------------------------ plugin access

    private fun lockOf(id: String): Any = synchronized(locks) { locks.getOrPut(id) { Any() } }

    private suspend fun runtimeFor(pluginId: String): PluginRuntime = withContext(Dispatchers.IO) {
        manager.load()
        val meta = manager.find(pluginId) ?: throw PluginRuntimeException("插件不存在或未启用：$pluginId")
        if (!meta.enabled) throw PluginRuntimeException("插件已禁用：${meta.displayName}")

        synchronized(runtimes) {
            runtimes[pluginId]?.takeIf { it.isAlive }?.let { return@withContext it }
            val code = readCode(meta.id) ?: throw PluginRuntimeException("插件文件缺失，请重新安装")

            val rt = PluginRuntime(meta, code, http, preludeProvider)
            rt.onLog = { level, msg -> onLog?.invoke(pluginId, level, msg) }
            rt.install()
            runtimes[pluginId] = rt
            rt
        }
    }

    private fun readCode(id: String): String? =
        File(context.filesDir, "plugins/$id/plugin.js").takeIf { it.isFile }?.readText()

    private suspend fun <T> invoke(pluginId: String, block: PluginRuntime.() -> T): T =
        withContext(Dispatchers.IO) {
        val rt = runtimeFor(pluginId)
        synchronized(lockOf(pluginId)) { rt.block() }
    }

    // ------------------------------------------------------------------ search

    suspend fun search(pluginId: String, keyword: String, page: Int = 1, type: String = SearchType.MUSIC): SearchResult =
        withContext(Dispatchers.IO) {
            val cacheKey = "search|$pluginId|$type|$keyword|$page"
            cache.get(cacheKey)?.let { return@withContext it as SearchResult }
            val meta = manager.find(pluginId)
            val fn = when (type) {
                SearchType.ALBUM -> "searchAlbum"
                SearchType.ARTIST -> "searchArtist"
                SearchType.SHEET -> "searchSheet"
                else -> "search"
            }
            if (meta != null && fn !in meta.functions && meta.functions.isNotEmpty()) {
                return@withContext SearchResult(isEnd = true)
            }
            val result = try {
                val raw = invoke(pluginId) {
                    call(fn, JsonPrimitive(keyword), JsonPrimitive(page), JsonPrimitive(type))
                }
                parseSearch(raw, meta?.platform ?: pluginId, type)
            } catch (e: PluginRuntimeException.Missing) {
                SearchResult(isEnd = true)
            } catch (e: PluginRuntimeException) {
                SearchResult(error = e.message)
            }
            cache.put(cacheKey, result, ttlFor(meta?.cacheControl))
            result
        }

    private fun parseSearch(raw: JsonElement, platform: String, type: String): SearchResult {
        val o = raw.asObj()
        // MusicFree returns { isEnd, data: [...] } but some plugins return the array directly.
        val arr = raw.asArr() ?: o?.get("data").asArr()
            ?: return SearchResult(isEnd = true)
        val isEnd = o?.get("isEnd").asBool() ?: arr.isEmpty()
        return when (type) {
            SearchType.ALBUM -> SearchResult(
                albums = arr.mapNotNull { AlbumItem.from(it.asObj(), platform) },
                isEnd = isEnd,
            )
            SearchType.ARTIST -> SearchResult(
                artists = arr.mapNotNull { ArtistItem.from(it.asObj(), platform) },
                isEnd = isEnd,
            )
            else -> SearchResult(
                items = arr.mapNotNull { MusicItem.from(it.asObj(), platform) },
                isEnd = isEnd,
            )
        }
    }

    /** Search every enabled plugin in parallel and merge, tagged by platform. */
    suspend fun searchAll(keyword: String, page: Int = 1, type: String = SearchType.MUSIC): List<SearchResult> =
        withContext(Dispatchers.IO) {
            manager.load()
            coroutineScope {
                manager.enabled()
                    .filter { plugin ->
                        when (type) {
                            SearchType.MUSIC -> plugin.hasSearch
                            SearchType.ALBUM -> plugin.hasAlbumSearch
                            SearchType.ARTIST -> plugin.hasArtistSearch
                            else -> plugin.hasSearch
                        }
                    }
                    .map { plugin -> async(Dispatchers.IO) { search(plugin.id, keyword, page, type) } }
                    .awaitAll()
            }
        }

    // ------------------------------------------------------------------ playback

    /**
     * MusicFree `getMediaSource(musicItem, quality)`.
     *
     * MusicFree signature is contractually `(musicItem, quality)`; older plugins only take
     * `(musicItem)`, so a missing/extra argument is tolerated (JS ignores extras).
     */
    suspend fun getMediaSource(
        pluginId: String,
        musicItemRaw: JsonObject?,
        item: MusicItem?,
        quality: String,
    ): MediaSourceResult? = withContext(Dispatchers.IO) {
        val payload = musicItemRaw ?: item?.raw ?: item?.toJson()
        payload ?: return@withContext null
        try {
            val raw = invoke(pluginId) { call("getMediaSource", payload, JsonPrimitive(quality)) }
            val o = raw.asObj() ?: return@withContext null
            val url = o["url"].asStr()?.takeIf { it.isNotBlank() } ?: return@withContext null
            val headers = LinkedHashMap<String, String>()
            o["headers"].asObj()?.forEach { (k, v) -> v.asStr()?.let { headers[k] = it } }
            MediaSourceResult(url, headers, o["mimeType"].asStr())
        } catch (e: PluginRuntimeException) {
            android.util.Log.w("GuangYuePlugin", "getMediaSource failed for $pluginId: ${e.message}")
            null
        }
    }

    // ------------------------------------------------------------------ metadata / lyrics

    suspend fun getLyric(pluginId: String, item: MusicItem): LyricSourceResult? = withContext(Dispatchers.IO) {
        val payload = item.raw ?: item.toJson()
        try {
            val raw = invoke(pluginId) { call("getLyric", payload) }
            val o = raw.asObj() ?: return@withContext null
            LyricSourceResult(
                rawLrc = o["rawLrc"].asStr() ?: o["lrc"].asStr() ?: o["lyric"].asStr(),
                translation = o["translation"].asStr(),
            )
        } catch (e: PluginRuntimeException) {
            null
        }
    }

    suspend fun getMusicInfo(pluginId: String, item: MusicItem): MusicItem? = withContext(Dispatchers.IO) {
        val payload = item.raw ?: item.toJson()
        runCatching {
            val raw = invoke(pluginId) { call("getMusicInfo", payload) }
            MusicItem.from(raw.asObj(), item.platform)?.let { info ->
                // keep the original payload: getMediaSource needs it
                info.copy(raw = info.raw ?: payload)
            }
        }.getOrNull()
    }

    suspend fun getAlbumInfo(pluginId: String, album: AlbumItem): Pair<AlbumItem, List<MusicItem>>? =
        withContext(Dispatchers.IO) {
            val payload = album.raw ?: buildJsonObject {
                put("platform", album.platform); put("id", album.id); put("title", album.title)
            }
            runCatching {
                val raw = invoke(pluginId) { call("getAlbumInfo", payload) }
                val o = raw.asObj() ?: return@runCatching null
                val info = AlbumItem.from(o["albumItem"].asObj() ?: o, album.platform) ?: album
                val songs = MusicItem.list(o["musicList"] ?: o["musics"], album.platform)
                info to songs
            }.getOrNull()
        }

    suspend fun getArtistWorks(
        pluginId: String,
        artist: ArtistItem,
        page: Int = 1,
        type: String = SearchType.MUSIC,
    ): Pair<List<MusicItem>, List<AlbumItem>>? = withContext(Dispatchers.IO) {
        val payload = artist.raw ?: buildJsonObject {
            put("platform", artist.platform); put("id", artist.id); put("name", artist.name)
        }
        runCatching {
            val raw = invoke(pluginId) {
                call("getArtistWorks", payload, JsonPrimitive(page), JsonPrimitive(type))
            }
            val o = raw.asObj() ?: return@runCatching null
            val music = MusicItem.list(o["data"] ?: o["musicList"] ?: o, artist.platform)
            val albums = o["albumList"].asArr()?.mapNotNull { AlbumItem.from(it.asObj(), artist.platform) } ?: emptyList()
            music to albums
        }.getOrNull()
    }

    // ------------------------------------------------------------------ lists & sheets

    suspend fun getTopLists(pluginId: String): List<JsonObject> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = invoke(pluginId) { call("getTopLists") }
            (raw.asArr() ?: raw.asObj()?.get("data").asArr())
                ?.mapNotNull { it.asObj() } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    suspend fun getTopListDetail(pluginId: String, topListItem: JsonObject, page: Int = 1): List<MusicItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = invoke(pluginId) {
                    call("getTopListDetail", topListItem, JsonPrimitive(page), JsonPrimitive(SearchType.MUSIC))
                }
                val o = raw.asObj()
                MusicItem.list(o?.get("musicList") ?: o?.get("data") ?: raw, pluginId)
            }.getOrDefault(emptyList())
        }

    suspend fun importMusicSheet(pluginId: String, url: String): List<MusicItem> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = invoke(pluginId) { call("importMusicSheet", JsonPrimitive(url)) }
            MusicItem.list(raw.asObj()?.get("data") ?: raw, pluginId)
        }.getOrDefault(emptyList())
    }

    suspend fun getMusicSheetInfo(pluginId: String, sheetItem: JsonObject, page: Int = 1): List<MusicItem> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = invoke(pluginId) {
                    call("getMusicSheetInfo", sheetItem, JsonPrimitive(page))
                }
                val o = raw.asObj()
                MusicItem.list(o?.get("musicList") ?: o?.get("data") ?: raw, pluginId)
            }.getOrDefault(emptyList())
        }

    /** Optional: plugin-provided comment section. */
    suspend fun getMusicComments(pluginId: String, item: MusicItem, page: Int = 1): List<JsonObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = invoke(pluginId) {
                    call("getMusicComments", item.raw ?: item.toJson(), JsonPrimitive(page))
                }
                (raw.asArr() ?: raw.asObj()?.get("data").asArr())?.mapNotNull { it.asObj() } ?: emptyList()
            }.getOrDefault(emptyList())
        }

    // ------------------------------------------------------------------ diagnostics

    /** Boot a plugin and return a human-readable self-test row for the management screen. */
    suspend fun selfTest(pluginId: String): String = withContext(Dispatchers.IO) {
        val meta = manager.find(pluginId) ?: return@withContext "插件不存在"
        val rt = runCatching { runtimeFor(pluginId) }.getOrElse { return@withContext "启动失败：${it.message}" }
        val builder = StringBuilder()
        builder.append("platform=").append(meta.platform)
        builder.append("  version=").append(meta.version)
        builder.append("\n函数：").append(meta.functions.joinToString(", ").ifBlank { "（未探测到）" })
        builder.append("\nsearch：")
        builder.append(
            runCatching { rt.call("search", JsonPrimitive("test"), JsonPrimitive(1), JsonPrimitive("music")) }
                .fold({ "OK" }, { e -> "FAIL ${e.message}" }),
        )
        builder.toString()
    }

    fun releaseAll() {
        synchronized(runtimes) {
            runtimes.values.forEach { it.close() }
            runtimes.clear()
        }
        cache.clear()
    }

    private fun ttlFor(cacheControl: String?): Long = when {
        cacheControl.isNullOrBlank() || cacheControl == "no-cache" -> 0L
        cacheControl == "no-store" -> 0L
        else -> 5 * 60_000L
    }
}

/** Tiny TTL map for search pages (MusicFree's `cacheControl` honouring). */
private class PluginCache {
    private data class Entry(val value: Any, val expiresAt: Long, val key: String)

    private val map = HashMap<String, Entry>()

    @Synchronized
    fun get(key: String): Any? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() > e.expiresAt) {
            map.remove(key)
            return null
        }
        return e.value
    }

    @Synchronized
    fun put(key: String, value: Any, ttlMs: Long) {
        if (ttlMs <= 0L) return
        if (map.size > 128) map.clear()
        map[key] = Entry(value, System.currentTimeMillis() + ttlMs, key)
    }

    @Synchronized
    fun clear() = map.clear()
}
