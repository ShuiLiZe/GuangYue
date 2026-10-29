package com.guangyue.core.web

import android.content.Context
import com.guangyue.core.data.db.GuangYueDatabase
import com.guangyue.core.data.model.TrackEntity
import com.guangyue.core.data.repository.PlaylistRepository
import com.guangyue.core.data.repository.SearchHistoryRepository
import com.guangyue.core.data.repository.TrackRepository
import com.guangyue.core.download.DownloadManager
import com.guangyue.core.download.DownloadedLibrary
import com.guangyue.core.player.Quality
import com.guangyue.core.plugin.AlbumItem
import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.MusicPluginBridge
import com.guangyue.core.plugin.PluginManager
import com.guangyue.core.plugin.PluginMeta
import com.guangyue.core.plugin.SubscriptionEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 骞夸箰鑷繁鐨勬暟鎹簮涓庝笟鍔¤兘鍔涳紝渚?Folia 鍓嶇璋冪敤锛坄GuangYueProvider` 鐨勮惤鍦扮锛夈€? *
 * 鍙毚闇插箍涔愬凡缁忔湁鐨勪笢瑗匡細鎻掍欢杩愯鏃讹紙MusicFree 鏍煎紡锛夈€佽闃呮簮銆佹洸搴擄紙Room锛夈€佷笅杞斤紙WorkManager锛夈€? * 涓嶅疄鐜颁换浣曠涓夋柟骞冲彴鐨勭櫥褰曘€佺洿杩?API 鎴?VIP 缁曡繃鈥斺€旈偅浜涜兘鍔涘湪 Folia 閲屾湰鏉ュ氨瑕佽鎽樻帀銆? */
@Singleton
class LibraryGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plugins: PluginManager,
    private val bridge: MusicPluginBridge,
    private val playlistRepo: PlaylistRepository,
    private val trackRepo: TrackRepository,
    private val historyRepo: SearchHistoryRepository,
    private val downloads: DownloadManager,
    private val downloaded: DownloadedLibrary,
    private val db: GuangYueDatabase,
) {


    // ------------------------------------------------------------- 鎻掍欢

    suspend fun pluginList(): JsonElement {
        plugins.load()
        val enabled = plugins.enabled()
        val all = plugins.plugins.value
        return buildJsonObject {
            put("plugins", buildJsonArray { all.forEach { add(pluginJson(it)) } })
            put("enabled", JsonArray(enabled.map { JsonPrimitive(it.id) }))
            put("busy", JsonPrimitive(plugins.busy.value))
            put("progress", buildJsonObject {
                put("stage", JsonPrimitive(plugins.progress.value.stage))
                put("done", JsonPrimitive(plugins.progress.value.done))
                put("total", JsonPrimitive(plugins.progress.value.total))
            })
        }
    }

    private fun pluginJson(meta: PluginMeta): JsonObject = buildJsonObject {
        put("id", JsonPrimitive(meta.id))
        put("platform", JsonPrimitive(meta.platform))
        put("name", JsonPrimitive(meta.displayName))
        put("version", JsonPrimitive(meta.version))
        put("author", JsonPrimitive(meta.author ?: ""))
        put("description", JsonPrimitive(meta.description ?: ""))
        put("iconUrl", JsonPrimitive(meta.iconUrl ?: ""))
        put("enabled", JsonPrimitive(meta.enabled))
        put("error", JsonPrimitive(meta.error ?: ""))
        put("source", JsonPrimitive(meta.source))
        put("sizeBytes", JsonPrimitive(meta.sizeBytes))
        put("supportedSearchType", buildJsonArray { meta.supportedSearchType.forEach { add(JsonPrimitive(it)) } })
        put("supportedQualities", buildJsonArray { meta.supportedQualities.forEach { add(JsonPrimitive(it)) } })
        put("functions", buildJsonArray { meta.functions.sorted().forEach { add(JsonPrimitive(it)) } })
        put("hasSearch", JsonPrimitive(meta.hasSearch))
        put("hasMediaSource", JsonPrimitive(meta.hasMediaSource))
        put("hasLyric", JsonPrimitive(meta.hasLyric))
        put("hasTopLists", JsonPrimitive(meta.hasTopLists))
        put("hasSheetInfo", JsonPrimitive(meta.hasSheetInfo))
    }

    suspend fun pluginSetEnabled(id: String, enabled: Boolean): JsonElement {
        plugins.setEnabled(id, enabled)
        return OK
    }

    suspend fun pluginSetAllEnabled(enabled: Boolean): JsonElement {
        plugins.setAllEnabled(enabled)
        return OK
    }

    suspend fun pluginDelete(id: String): JsonElement {
        plugins.delete(id)
        return OK
    }

    suspend fun pluginInstallFromUrl(url: String): JsonElement = installResult(plugins.installFromUrl(url))

    suspend fun pluginInstallCode(code: String, name: String): JsonElement =
        installResult(plugins.installCode(code, source = "web:$name", fallbackName = name))

    suspend fun pluginCheckUpdates(): JsonElement {
        val result = plugins.checkUpdates(installDiscovered = false)
        return buildJsonObject {
            put("checked", JsonPrimitive(result.checked))
            put("summary", JsonPrimitive(result.summary()))
            put("updated", buildJsonArray { result.updated.forEach { add(pluginJson(it)) } })
            put("failed", buildJsonArray {
                result.failed.forEach { (n, m) -> add(buildJsonObject {
                    put("name", JsonPrimitive(n)); put("message", JsonPrimitive(m))
                }) }
            })
            put("discovered", buildJsonArray {
                result.discovered.forEach { add(buildJsonObject {
                    put("name", JsonPrimitive(it.displayName)); put("url", JsonPrimitive(it.url))
                    put("version", JsonPrimitive(it.version))
                }) }
            })
        }
    }

    suspend fun pluginUpdate(id: String): JsonElement = installResult(plugins.update(id))

    suspend fun pluginSelfTest(id: String): JsonElement =
        buildJsonObject { put("text", JsonPrimitive(bridge.selfTest(id))) }

    private fun installResult(result: com.guangyue.core.plugin.InstallResult): JsonElement = buildJsonObject {
        when (result) {
            is com.guangyue.core.plugin.InstallResult.Success -> {
                put("ok", JsonPrimitive(true)); put("plugin", pluginJson(result.meta))
            }
            is com.guangyue.core.plugin.InstallResult.Failure -> {
                // 详情（含沙箱原始异常与探针 JSON）对排障是必需的，界面只显示 message。
                put("ok", JsonPrimitive(false))
                put("message", JsonPrimitive(result.message))
                put("details", JsonPrimitive(plugins.lastErrorDetails ?: ""))
            }
            is com.guangyue.core.plugin.InstallResult.BatchInstalled -> {
                val batch = result.result
                put("ok", JsonPrimitive(batch.failed.isEmpty())); put("message", JsonPrimitive(batch.summary()))
                put("details", JsonPrimitive(batch.details() ?: ""))
            }
        }
    }

    // ------------------------------------------------------------- 璁㈤槄婧?
    suspend fun subscriptionList(): JsonElement {
        plugins.load()
        return buildJsonObject {
            put("subscriptions", buildJsonArray {
                plugins.subscriptions.value.forEach { sub ->
                    add(buildJsonObject {
                        put("url", JsonPrimitive(sub.url))
                        put("name", JsonPrimitive(sub.name.ifBlank { sub.url.substringAfterLast('/') }))
                        put("count", JsonPrimitive(sub.count))
                        put("checkedAt", JsonPrimitive(sub.checkedAt))
                        put("addedAt", JsonPrimitive(sub.addedAt))
                    })
                }
            })
            put("catalog", buildJsonArray {
                plugins.catalog.value.forEach { entry ->
                    add(buildJsonObject {
                        put("url", JsonPrimitive(entry.entry.url))
                        put("name", JsonPrimitive(entry.entry.displayName))
                        put("version", JsonPrimitive(entry.entry.version))
                        put("sourceUrl", JsonPrimitive(entry.sourceUrl))
                    })
                }
            })
        }
    }

    suspend fun subscriptionAdd(url: String): JsonElement {
        val result = plugins.installFromUrl(url)
        return installResult(result)
    }

    suspend fun subscriptionRefresh(sourceUrl: String): JsonElement {
        plugins.refreshCatalog(sourceUrl)
        return subscriptionList()
    }

    suspend fun subscriptionRemove(url: String): JsonElement {
        plugins.forgetSubscription(url)
        return OK
    }

    suspend fun subscriptionInstallAll(sourceUrl: String): JsonElement =
        installResult(plugins.installOrUpdateAll(sourceUrl, force = false))

    suspend fun subscriptionInstallSelected(sourceUrl: String, selected: JsonArray): JsonElement {
        val entries = selected.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            SubscriptionEntry(name = o.strOr("name", ""), url = o.strOr("url", ""), version = o.strOr("version", ""))
        }.filter { it.url.isNotBlank() }
        return installResult(plugins.installFromSubscription(sourceUrl, entries))
    }

    // ------------------------------------------------------------- 鎼滅储 / 鎻掍欢鏂规硶

    /**
     * 鍦ㄥ凡鍚敤鎻掍欢閲屽苟琛屾悳绱€傝繑鍥?`{ results: [{ pluginId, platform, items[], albums[], artists[], isEnd }] }`銆?     *
     * 涓?Folia 鐨?`searchSongs(query, limit, offset)` 瀵归綈锛歚page` 浠?1 寮€濮嬶紝`limit` 鐢ㄤ簬鎴柇
     * 鍗曚釜鎻掍欢杩斿洖鐨勮鏁帮紙鎻掍欢鏈韩鎸夎嚜宸辩殑鍒嗛〉杩斿洖锛夈€?     */
    suspend fun search(keyword: String, type: String, page: Int, limit: Int): JsonElement {
        plugins.load()
        val wanted = if (type.isBlank() || type == "music") {
            plugins.enabled()
        } else {
            plugins.enabled().filter { it.supportedSearchType.contains(type) }
        }
        val results = wanted.mapNotNull { meta ->
            runCatching { bridge.search(meta.id, keyword, page, type) }
                .map { meta to it }
                .getOrNull()
        }
        return buildJsonObject {
            put("results", buildJsonArray {
                results.forEach { (meta, result) ->
                    add(buildJsonObject {
                        put("pluginId", JsonPrimitive(meta.id))
                        put("platform", JsonPrimitive(meta.platform))
                        put("pluginName", JsonPrimitive(meta.displayName))
                        put("isEnd", JsonPrimitive(result.isEnd))
                        put("error", JsonPrimitive(result.error ?: ""))
                        val items = if (limit > 0) result.items.take(limit) else result.items
                        put("items", buildJsonArray { items.forEach { add(musicItemJson(it)) } })
                        put("albums", buildJsonArray { result.albums.forEach { add(albumJson(it)) } })
                        put("artists", buildJsonArray {
                            result.artists.forEach { a -> add(buildJsonObject {
                                put("platform", JsonPrimitive(a.platform)); put("id", JsonPrimitive(a.id))
                                put("name", JsonPrimitive(a.name)); put("avatar", JsonPrimitive(a.avatar ?: ""))
                            }) }
                        })
                    })
                }
            })
            put("pluginsQueried", JsonPrimitive(wanted.size))
        }
    }

    private fun musicItemJson(item: MusicItem): JsonObject = buildJsonObject {
        put("pluginId", JsonPrimitive(item.platform))
        put("platform", JsonPrimitive(item.platform))
        put("id", JsonPrimitive(item.id))
        put("title", JsonPrimitive(item.title))
        put("artist", JsonPrimitive(item.artist))
        put("album", JsonPrimitive(item.album ?: ""))
        put("artwork", JsonPrimitive(item.artwork ?: ""))
        put("duration", JsonPrimitive(item.durationMs ?: 0L))
        put("albumId", JsonPrimitive(item.albumId ?: ""))
        put("artistId", JsonPrimitive(item.artistId ?: ""))
        put("raw", item.raw ?: JsonNull)
    }

    private fun albumJson(item: AlbumItem): JsonObject = buildJsonObject {
        put("pluginId", JsonPrimitive(item.platform))
        put("id", JsonPrimitive(item.id))
        put("title", JsonPrimitive(item.title))
        put("artist", JsonPrimitive(item.artist))
        put("artwork", JsonPrimitive(item.artwork ?: ""))
        put("description", JsonPrimitive(item.description ?: ""))
        put("date", JsonPrimitive(item.date ?: ""))
    }

    /** 杩樺師鍓嶇浼犲洖鏉ョ殑 MusicFree 鍘熷瀵硅薄锛坓etMediaSource/getLyric 蹇呴』鏀跺埌鎻掍欢鍘熸牱鍚愬嚭鐨勫璞★級銆?*/
    private fun itemFrom(params: JsonObject): MusicItem? {
        val raw = params.objOrNull("raw") ?: params
        val platform = raw.str("pluginId") ?: raw.str("platform") ?: return null
        return MusicItem.from(raw, platform)
    }

    suspend fun mediaSource(params: JsonObject): JsonElement {
        val platform = params.str("pluginId") ?: params.str("platform") ?: return error("missing_plugin")
        val item = itemFrom(params) ?: return error("bad_item")
        val quality = Quality.parse(params.str("quality"))
        val result = bridge.getMediaSource(platform, musicItemRaw = item.raw ?: item.toJson(), item = item, quality = quality.wire) ?: return error("no_source")
        return buildJsonObject {
            put("url", JsonPrimitive(result.url))
            put("mimeType", JsonPrimitive(result.mimeType ?: ""))
            put("quality", JsonPrimitive(quality.wire))
            put("headers", buildJsonObject { result.headers.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
        }
    }

    suspend fun lyric(params: JsonObject): JsonElement {
        val platform = params.str("pluginId") ?: params.str("platform") ?: return error("missing_plugin")
        val item = itemFrom(params) ?: return error("bad_item")
        val result = bridge.getLyric(platform, item) ?: return buildJsonObject { put("lrc", JsonNull) }
        return buildJsonObject {
            put("lrc", JsonPrimitive(result.rawLrc ?: ""))
            put("translation", JsonPrimitive(result.translation ?: ""))
        }
    }

    suspend fun musicInfo(params: JsonObject): JsonElement {
        val platform = params.str("pluginId") ?: params.str("platform") ?: return error("missing_plugin")
        val item = itemFrom(params) ?: return error("bad_item")
        val result = bridge.getMusicInfo(platform, item) ?: return JsonNull
        return musicItemJson(result)
    }

    suspend fun albumInfo(params: JsonObject): JsonElement {
        val platform = params.str("pluginId") ?: params.str("platform") ?: return error("missing_plugin")
        val raw = params.objOrNull("raw") ?: params
        val album = AlbumItem.from(raw, platform) ?: return error("bad_album")
        val pair = bridge.getAlbumInfo(platform, album) ?: return JsonNull
        return buildJsonObject {
            put("album", albumJson(pair.first))
            put("songs", buildJsonArray { pair.second.forEach { add(musicItemJson(it)) } })
        }
    }

    suspend fun topLists(pluginId: String): JsonElement {
        val lists = bridge.getTopLists(pluginId)
        return buildJsonObject { put("lists", JsonArray(lists)) }
    }

    suspend fun topListDetail(pluginId: String, item: JsonObject, page: Int): JsonElement {
        val songs = bridge.getTopListDetail(pluginId, item, page)
        return buildJsonObject { put("songs", buildJsonArray { songs.forEach { add(musicItemJson(it)) } }) }
    }

    suspend fun qualities(pluginId: String): JsonElement {
        val list = runCatching { bridge.getQualities(pluginId) }.getOrDefault(emptyList())
        return buildJsonObject { put("qualities", JsonArray(list.map { JsonPrimitive(it) })) }
    }

    // ------------------------------------------------------------- 鏇插簱 / 鏀惰棌 / 鍘嗗彶

    suspend fun libraryTracks(bucket: String, limit: Int): JsonElement {
        val tracks: List<TrackEntity> = when (bucket) {
            "favorites" -> trackRepo.favorites().first()
            "local" -> trackRepo.localTracks().first()
            "recent" -> trackRepo.recentlyPlayed(limit).first()
            else -> trackRepo.recentlyPlayed(limit).first()
        }
        return buildJsonObject { put("tracks", buildJsonArray { tracks.forEach { add(trackJson(it)) } }) }
    }

    suspend fun libraryPlaylists(): JsonElement {
        val list = playlistRepo.playlists().first()
        return buildJsonObject { put("playlists", buildJsonArray { list.forEach { p ->
            add(buildJsonObject {
                put("id", JsonPrimitive(p.id)); put("name", JsonPrimitive(p.name))
                put("description", JsonPrimitive(p.description ?: ""))
                put("artwork", JsonPrimitive(p.artwork ?: "")); put("pinned", JsonPrimitive(p.pinned))
                put("remoteId", JsonPrimitive(p.remoteId ?: ""))
                put("platform", JsonPrimitive(p.platform ?: ""))
                put("updatedAt", JsonPrimitive(p.updatedAt))
            })
        } }) }
    }

    suspend fun playlistTracks(playlistId: Long): JsonElement {
        val tracks = playlistRepo.snapshot(playlistId)
        return buildJsonObject { put("tracks", buildJsonArray { tracks.forEach { add(trackJson(it)) } }) }
    }

    suspend fun playlistCreate(name: String): JsonElement =
        buildJsonObject { put("id", JsonPrimitive(playlistRepo.create(name))) }

    suspend fun playlistRename(id: Long, name: String): JsonElement {
        playlistRepo.rename(id, name); return OK
    }

    suspend fun playlistDelete(id: Long): JsonElement {
        playlistRepo.delete(id); return OK
    }

    suspend fun playlistAddItems(playlistId: Long, items: JsonArray, quality: String): JsonElement {
        val list = items.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val platform = o.str("pluginId") ?: o.str("platform") ?: return@mapNotNull null
            MusicItem.from(o.objOrNull("raw") ?: o, platform)
        }
        if (list.isNotEmpty()) playlistRepo.addTracks(playlistId, list, quality)
        return buildJsonObject { put("added", JsonPrimitive(list.size)) }
    }

    suspend fun playlistRemoveTrack(playlistId: Long, key: String): JsonElement {
        playlistRepo.removeTrack(playlistId, key); return OK
    }

    suspend fun trackSetFavorite(key: String, favorite: Boolean): JsonElement {
        trackRepo.setFavorite(key, favorite); return OK
    }

    suspend fun searchHistory(): JsonElement {
        val list = historyRepo.history().first()
        return buildJsonObject { put("history", buildJsonArray { list.forEach {
            add(buildJsonObject { put("query", JsonPrimitive(it.query)); put("usedAt", JsonPrimitive(it.usedAt)) })
        } }) }
    }

    suspend fun searchHistoryRecord(query: String): JsonElement {
        historyRepo.record(query); return OK
    }

    suspend fun searchHistoryClear(): JsonElement {
        historyRepo.clear(); return OK
    }

    private fun trackJson(track: TrackEntity): JsonObject = buildJsonObject {
        put("key", JsonPrimitive(track.key))
        put("pluginId", JsonPrimitive(track.platform))
        put("platform", JsonPrimitive(track.platform))
        put("id", JsonPrimitive(track.songId))
        put("title", JsonPrimitive(track.title))
        put("artist", JsonPrimitive(track.artist))
        put("album", JsonPrimitive(track.album))
        put("artwork", JsonPrimitive(track.artwork ?: ""))
        put("duration", JsonPrimitive(track.durationMs))
        put("quality", JsonPrimitive(track.quality))
        put("source", JsonPrimitive(track.source))
        put("localUri", JsonPrimitive(track.localUri ?: ""))
        put("favorite", JsonPrimitive(track.favorite))
        put("lastPlayedAt", JsonPrimitive(track.lastPlayedAt))
        put("addedAt", JsonPrimitive(track.addedAt))
        put("playCount", JsonPrimitive(track.playCount))
        put("raw", track.rawJson?.let { com.guangyue.core.plugin.Json2.parse(it) } ?: JsonNull)
    }

    // ------------------------------------------------------------- 涓嬭浇

    suspend fun downloadList(): JsonElement {
        val done = downloaded.tracks.value
        val active = downloads.snapshot()
        return buildJsonObject {
            put("completed", buildJsonArray { done.forEach { t ->
                add(buildJsonObject {
                    put("key", JsonPrimitive(t.key)); put("title", JsonPrimitive(t.title))
                    put("artist", JsonPrimitive(t.artist)); put("album", JsonPrimitive(t.album ?: ""))
                    put("artwork", JsonPrimitive(t.artwork ?: "")); put("uri", JsonPrimitive(t.uri))
                    put("fileName", JsonPrimitive(t.fileName)); put("sizeBytes", JsonPrimitive(t.sizeBytes))
                    put("quality", JsonPrimitive(t.quality)); put("downloadedAt", JsonPrimitive(t.downloadedAt))
                })
            } })
            put("active", buildJsonArray { active.forEach { p ->
                add(buildJsonObject {
                    put("key", JsonPrimitive(p.key)); put("stage", JsonPrimitive(p.stage.name))
                    put("done", JsonPrimitive(p.bytesDone)); put("total", JsonPrimitive(p.bytesTotal))
                    put("failed", JsonPrimitive(p.error != null))
                    put("message", JsonPrimitive(p.error ?: ""))
                })
            } })
            put("summary", buildJsonObject {
                put("totalBytes", JsonPrimitive(downloaded.totalBytes()))
                put("completed", JsonPrimitive(done.size))
                put("active", JsonPrimitive(active.size))
            })
        }
    }

    suspend fun downloadEnqueue(items: JsonArray, quality: String): JsonElement {
        val q = Quality.parse(quality.ifBlank { null })
        var queued = 0
        items.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val platform = o.str("pluginId") ?: o.str("platform") ?: return@forEach
            val item = MusicItem.from(o.objOrNull("raw") ?: o, platform) ?: return@forEach
            if (downloads.enqueue(item, q)) queued++
        }
        return buildJsonObject { put("queued", JsonPrimitive(queued)) }
    }

    suspend fun downloadCancel(key: String): JsonElement {
        downloads.cancel(key); return OK
    }

    suspend fun downloadRemove(key: String, deleteFile: Boolean): JsonElement {
        downloaded.remove(key, deleteFile); return OK
    }

    // ------------------------------------------------------------- 闊宠川

    suspend fun qualityLadder(): JsonElement = buildJsonObject {
        put("qualities", buildJsonArray {
            Quality.ALL.forEach { q ->
                add(buildJsonObject {
                    put("wire", JsonPrimitive(q.wire)); put("label", JsonPrimitive(q.label))
                    put("subtitle", JsonPrimitive(q.subtitle)); put("order", JsonPrimitive(q.order))
                })
            }
        })
    }

    // ------------------------------------------------------------- 鏇茬嚎鏁戝浗鐨勯潤榛樺垎鏀?
    private val OK: JsonElement = buildJsonObject { put("ok", JsonPrimitive(true)) }
    private fun error(code: String): JsonElement = buildJsonObject {
        put("error", JsonPrimitive(code))
    }

    /** 璁╄皟鐢ㄦ柟鍙互鎷垮埌鏁版嵁搴撳疄渚嬪仛鍚庣画鎵╁睍锛堟殏鏈敤锛屼絾淇濇寔渚濊禆娉ㄥ叆瀹屾暣锛夈€?*/
    fun database(): GuangYueDatabase = db
}

internal suspend fun <T> io(block: suspend CoroutineScope.() -> T): T =
    withContext(Dispatchers.IO) { block() }

