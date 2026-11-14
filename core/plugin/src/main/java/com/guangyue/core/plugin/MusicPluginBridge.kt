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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    @com.guangyue.core.plugin.di.PlaybackHttp private val http: OkHttpClient,
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

    /**
     * 把「插件 id 或 platform」统一解析成 [PluginMeta]。
     *
     * 为什么两种都要收：安装后的 id 形如 `36dj-2d5f50a470`，而**歌曲里带的是 platform**
     * （`MusicItem.platform` 是插件声明的 platform，`TrackEntity.platform` 原样存下来，
     * `MediaOrigin.pluginId` 又是从它来的）。0.0.7 装机实测：搜索正常，点播放却报
     * 「插件不存在或未启用：36DJ」—— 就是因为播放链路拿 platform 当 id 去 find()。
     */
    private fun resolveMeta(pluginId: String): PluginMeta? =
        manager.find(pluginId) ?: manager.findByPlatform(pluginId)

    private suspend fun runtimeFor(pluginId: String, lane: String = MAIN_LANE): PluginRuntime = withContext(Dispatchers.IO) {
        manager.load()
        val meta = resolveMeta(pluginId) ?: throw PluginRuntimeException("插件不存在或未启用：$pluginId")
        // M8：歌词走独立的第二个运行时（lane），不用排在取链后面 —— 取链一次 4–13 s，
        // 以前同一把锁串行，切歌后歌词要等取链结束才开始取（用户反馈「有歌词，只是比较慢」）。
        val key = if (lane == MAIN_LANE) meta.id else "${meta.id}#$lane"
        if (!meta.enabled) throw PluginRuntimeException("插件已禁用：${meta.displayName}")

        synchronized(runtimes) {
            runtimes[key]?.takeIf { it.isAlive }?.let { return@withContext it }
            val code = readCode(meta.id) ?: throw PluginRuntimeException("插件文件缺失，请重新安装")

            val rt = PluginRuntime(meta, code, http, preludeProvider)
            val varsId = meta.id
            rt.userVariables = { readUserVariables(varsId) }
            rt.onLog = { level, msg -> onLog?.invoke(key, level, msg) }
            rt.install()
            runtimes[key] = rt
            rt
        }
    }

    private fun readCode(id: String): String? =
        File(context.filesDir, "plugins/$id/plugin.js").takeIf { it.isFile }?.readText()

    private suspend fun <T> invoke(
        pluginId: String,
        lane: String = MAIN_LANE,
        block: PluginRuntime.() -> T,
    ): T = withContext(Dispatchers.IO) {
        val rt = runtimeFor(pluginId, lane)
        synchronized(lockOf(if (lane == MAIN_LANE) pluginId else "$pluginId#$lane")) { rt.block() }
    }

    // ------------------------------------------------------------------ search

    suspend fun search(pluginId: String, keyword: String, page: Int = 1, type: String = SearchType.MUSIC): SearchResult =
        withContext(Dispatchers.IO) {
            val cacheKey = "search|$pluginId|$type|$keyword|$page"
            cache.get(cacheKey)?.let { return@withContext it as SearchResult }
            val meta = resolveMeta(pluginId)
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

    private fun parseSearch(raw: JsonElement, platform: String, type: String): SearchResult =
        SearchParser.parse(raw, platform, type)

    /** Search every enabled plugin in parallel and merge, tagged by platform. */
    suspend fun searchAll(keyword: String, page: Int = 1, type: String = SearchType.MUSIC): List<SearchResult> =
        searchAllDetailed(keyword, page, type).map { it.second }

    /**
     * 并发搜索：每个插件一个协程，但全局最多 [SEARCH_CONCURRENCY] 个同时在飞。
     *
     * 返回 `pluginId → 结果` 的有序列表，调用方（SearchViewModel）用来把错误的插件单独渲染。
     */
    suspend fun searchAllDetailed(
        keyword: String,
        page: Int = 1,
        type: String = SearchType.MUSIC,
    ): List<Pair<String, SearchResult>> = withContext(Dispatchers.IO) {
        manager.load()
        val targets = manager.enabled()
            .filter { plugin ->
                when (type) {
                    SearchType.MUSIC -> plugin.hasSearch
                    SearchType.ALBUM -> plugin.hasAlbumSearch
                    SearchType.ARTIST -> plugin.hasArtistSearch
                    else -> plugin.hasSearch
                }
            }
        val gate = Semaphore(SEARCH_CONCURRENCY)
        coroutineScope {
            targets
                .map { plugin ->
                    async(Dispatchers.IO) {
                        plugin.id to gate.withPermit { search(plugin.id, keyword, page, type) }
                    }
                }
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
            SearchParser.mediaSource(raw)
        } catch (e: PluginRuntimeException) {
            android.util.Log.w("GuangYuePlugin", "getMediaSource failed for $pluginId: ${e.message}")
            null
        }
    }

    // ------------------------------------------------------------------ metadata / lyrics

    suspend fun getLyric(pluginId: String, item: MusicItem): LyricSourceResult? = withContext(Dispatchers.IO) {
        val payload = item.raw ?: item.toJson()
        try {
            val raw = invoke(pluginId, LYRIC_LANE) { call("getLyric", payload) }
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

    /** 一页歌手作品 + 插件给的 isEnd（MusicFree 契约 `{ isEnd, data }`；没给时为 null）。 */
    data class ArtistWorksPage(val music: List<MusicItem>, val albums: List<AlbumItem>, val isEnd: Boolean?)

    suspend fun getArtistWorksPage(
        pluginId: String,
        artist: ArtistItem,
        page: Int = 1,
        type: String = SearchType.MUSIC,
    ): ArtistWorksPage? = withContext(Dispatchers.IO) {
        val payload = artist.raw ?: buildJsonObject {
            put("platform", artist.platform); put("id", artist.id); put("name", artist.name)
        }
        runCatching {
            val raw = invoke(pluginId) {
                call("getArtistWorks", payload, JsonPrimitive(page), JsonPrimitive(type))
            }
            val o = raw.asObj() ?: return@runCatching null
            val isEnd = (o["isEnd"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
            if (type == SearchType.ALBUM) {
                val albums = (o["data"] ?: o["albumList"]).asArr()?.mapNotNull { AlbumItem.from(it.asObj(), artist.platform) } ?: emptyList()
                ArtistWorksPage(emptyList(), albums, isEnd)
            } else {
                val music = MusicItem.list(o["data"] ?: o["musicList"] ?: o, artist.platform)
                ArtistWorksPage(music, emptyList(), isEnd)
            }
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

    /**
     * 推荐歌单的分类标签（`getRecommendSheetTags`）。
     *
     * 返回形如 `[{ title: "推荐", data: [{ id: "hot", title: "热门" }, ...] }, ...]`，
     * **内层 data 里的每一项**才是 [getRecommendSheets] 能吃的「分类叶子」。
     */
    suspend fun getRecommendSheetTags(pluginId: String): List<JsonObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = invoke(pluginId) { call("getRecommendSheetTags") }
                (raw.asArr() ?: raw.asObj()?.get("data").asArr())
                    ?.mapNotNull { it.asObj() } ?: emptyList()
            }.getOrDefault(emptyList())
        }

    /** 某个分类下的推荐歌单列表（`getRecommendSheetTags` 的叶子 + 页码）。 */
    suspend fun getRecommendSheets(pluginId: String, tagItem: JsonObject, page: Int = 1): List<JsonObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                val raw = invoke(pluginId) {
                    call("getRecommendSheetsByTag", tagItem, JsonPrimitive(page))
                }
                val o = raw.asObj()
                (o?.get("data").asArr() ?: raw.asArr())
                    ?.mapNotNull { it.asObj() } ?: emptyList()
            }.getOrDefault(emptyList())
        }

    /**
     * 播放一个推荐歌单：按「分类叶子 + 歌单 + 页码」重新问一次当前音源。
     *
     * 必须重问的原因：推荐歌单在首页只缓存了 id 与标题，而 `getMusicSheetInfo`
     * 要的是插件自己的歌单对象；直接把歌单当歌曲喂给取链会报「取不到播放地址」。
     */
    suspend fun getRecommendSheetInfo(
        pluginId: String,
        tagItem: JsonObject,
        sheetId: String,
        page: Int = 1,
    ): List<MusicItem> = withContext(Dispatchers.IO) {
        runCatching {
            val sheets = getRecommendSheets(pluginId, tagItem, page)
            val sheet = sheets.firstOrNull {
                (it["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content == sheetId
            } ?: sheets.firstOrNull() ?: return@runCatching emptyList()
            getMusicSheetInfo(pluginId, sheet, page)
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
                val list = MusicItem.list(o?.get("musicList") ?: o?.get("data") ?: raw, pluginId)
                if (list.isEmpty()) android.util.Log.w("GYSHEET", "getMusicSheetInfo empty plugin=$pluginId sheet=${sheetItem.toString().take(300)} raw=${raw.toString().take(500)}")
                list
            }.onFailure { android.util.Log.w("GYSHEET", "getMusicSheetInfo failed plugin=$pluginId: ${it.message?.take(400)}") }
                .getOrDefault(emptyList())
        }

    /** Optional: plugin-provided comment section. */
    suspend fun getMusicComments(pluginId: String, item: MusicItem, page: Int = 1): List<JsonObject> =        withContext(Dispatchers.IO) {
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

    // ------------------------------------------------------------------ 用户变量（插件「登录」）

    private fun userVarsFile(id: String) = File(context.filesDir, "plugins/$id/uservars.json")

    /** 已保存的用户变量（MusicFree env.getUserVariables 的值）。 */
    fun readUserVariables(pluginId: String): Map<String, String> {
        val id = resolveMeta(pluginId)?.id ?: pluginId
        val f = userVarsFile(id)
        if (!f.isFile) return emptyMap()
        return runCatching {
            Json2.parse(f.readText()).asObj()?.mapNotNull { (k, v) -> v.asStr()?.let { k to it } }?.toMap()
        }.getOrNull() ?: emptyMap()
    }

    /** 保存用户变量；空值的键删掉。插件每次调用现取，清掉结果缓存即可生效。 */
    suspend fun saveUserVariables(pluginId: String, values: Map<String, String>) = withContext(Dispatchers.IO) {
        val id = resolveMeta(pluginId)?.id ?: pluginId
        val f = userVarsFile(id)
        f.parentFile?.mkdirs()
        val kept = values.filterValues { it.isNotBlank() }
        f.writeText(JsonObject(kept.mapValues { JsonPrimitive(it.value.trim()) }).toString())
        cache.clear()
    }

    /** 插件声明的用户变量（没声明 = 这个平台不需要登录）。 */
    suspend fun declaredUserVariables(pluginId: String): List<com.guangyue.core.plugin.runtime.PluginUserVariable> =
        runCatching { invoke(pluginId) { declaredUserVariables() } }.getOrDefault(emptyList())

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

    /** 可选功能：插件声明的动态音质档位（`getMediaSource` 的 quality 入参取值）。 */
    suspend fun getQualities(pluginId: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = invoke(pluginId) { call("getQualities") }
            (raw.asArr() ?: raw.asObj()?.get("data").asArr())
                ?.mapNotNull { it.asStr()?.takeIf(String::isNotBlank) }
                ?: emptyList()
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAIN_LANE = "main"
        private const val LYRIC_LANE = "lyric"
        /** 并发搜索同时在飞的插件数量上限：过多会触发源站限流或本机 socket 耗尽。 */
        const val SEARCH_CONCURRENCY = 6
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
