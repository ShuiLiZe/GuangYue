package com.guangyue.core.plugin

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Tolerant readers usable anywhere in this module (no `Json2.` prefix needed). */
internal fun JsonElement?.asObj(): JsonObject? = this as? kotlinx.serialization.json.JsonObject

internal fun JsonElement?.asArr(): kotlinx.serialization.json.JsonArray? =
    this as? kotlinx.serialization.json.JsonArray

internal fun JsonElement?.asStr(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content
}

internal fun JsonElement?.asLong(): Long? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.content.trim().toLongOrNull() ?: p.content.trim().toDoubleOrNull()?.toLong()
}

internal fun JsonElement?.asInt(): Int? = asLong()?.toInt()

internal fun JsonElement?.asBool(): Boolean? = (this as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

/** First non-blank string among [keys]. */
internal fun JsonObject.string(vararg keys: String): String? {
    for (k in keys) {
        val v = this[k].asStr()
        if (!v.isNullOrBlank()) return v
    }
    return null
}

/**
 * The MusicFree data shapes the app consumes, plus tolerant JSON helpers.
 *
 * Nothing here throws on malformed plugin output: every accessor falls back to a sane
 * default, because third-party plugins are the least trustworthy part of the system.
 */

object Json2 {
    val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun parse(text: String): JsonElement? = runCatching { json.parseToJsonElement(text) }.getOrNull()

    fun JsonElement?.obj(): JsonObject? = (this as? JsonObject)
    fun JsonElement?.arr(): JsonArray? = (this as? JsonArray)
    fun JsonElement?.str(): String? = asStr()

    fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.let { it.intOrNullCompat() }
    fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

    fun JsonPrimitive.intOrNullCompat(): Int? =
        longOrNull?.toInt() ?: doubleOrNull?.toInt() ?: content.toIntOrNull()

    fun JsonObject.string(vararg keys: String): String? {
        for (k in keys) {
            val v = this[k]?.str()
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    fun JsonObject.arrOrNull(key: String): JsonArray? = this[key].arr()

    fun emptyObj(): JsonObject = buildJsonObject { }

    fun objOf(vararg pairs: Pair<String, JsonElement>): JsonObject = JsonObject(pairs.toMap())

    fun str(value: String?): JsonElement = value?.let { JsonPrimitive(it) } ?: JsonNull
    fun num(value: Long): JsonElement = JsonPrimitive(value)
    fun bool(value: Boolean): JsonElement = JsonPrimitive(value)

    fun putAll(target: MutableMap<String, JsonElement>, from: JsonObject?) {
        from?.forEach { (k, v) -> target[k] = v }
    }

    fun obj(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
        for ((k, v) in pairs) {
            when (v) {
                null -> put(k, JsonNull)
                is String -> put(k, v)
                is Int -> put(k, v)
                is Long -> put(k, v)
                is Boolean -> put(k, v)
                is JsonElement -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    }
}

/**
 * MusicFree `IMusicItem`.
 *
 * `raw` keeps the untouched plugin object so `getMediaSource` receives exactly what the
 * plugin produced (MusicFree contract: the same object comes back to the plugin).
 */
data class MusicItem(
    /** MusicFree platform id, e.g. "demo", "local" — must match the plugin's platform. */
    val platform: String,
    /** plugin-internal id */
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artwork: String? = null,
    val durationMs: Long? = null,
    /** album / artist item id when the row came from a non-search context */
    val albumId: String? = null,
    val artistId: String? = null,
    val quality: String? = null,
    /** "music" | "album" | "artist" | "sheet" | "toplist" */
    val type: String = "music",
    val raw: JsonObject? = null,
) {
    val displayArtist: String get() = artist.ifBlank { "未知艺术家" }

    fun toJson(): JsonObject = Json2.obj(
        "platform" to platform,
        "id" to id,
        "title" to title,
        "artist" to artist,
        "album" to album,
        "artwork" to artwork,
        "duration" to durationMs,
        "albumId" to albumId,
        "artistId" to artistId,
        "quality" to quality,
        "type" to type,
    )

    companion object {
        fun from(json: JsonObject?, fallbackPlatform: String): MusicItem? {
            if (json == null) return null
            val id = json.string("id", "songId", "songmid", "rid", "hash") ?: return null
            val platform = json.string("platform") ?: fallbackPlatform
            val duration = json["duration"].asLong() ?: json["interval"].asLong()
            return MusicItem(
                platform = platform,
                id = id,
                title = json.string("title", "name", "songname", "songName") ?: "未知歌曲",
                artist = json.string("artist", "artists", "singer", "author") ?: "未知艺术家",
                album = json.string("album", "albumName", "alname"),
                artwork = json.string("artwork", "pic", "cover", "img", "albumPic"),
                durationMs = duration?.let { if (it in 1..100_000L) it * 1000L else it },
                albumId = json.string("albumId", "albumMid", "albummid"),
                artistId = json.string("artistId", "singerMid"),
                quality = json.string("quality"),
                type = json.string("type") ?: "music",
                raw = json,
            )
        }

        fun list(element: JsonElement?, platform: String): List<MusicItem> {
            val arr = element.asArr() ?: return emptyList()
            return arr.mapNotNull { o -> from(o.asObj(), platform) }
        }
    }
}

/** MusicFree `IAlbumItem`. */
data class AlbumItem(
    val platform: String,
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String?,
    val description: String? = null,
    val date: String? = null,
    val raw: JsonObject? = null,
) {
    companion object {
        fun from(json: JsonObject?, platform: String): AlbumItem? {
            val id = json?.string("id", "albumId", "albumMid") ?: return null
            return AlbumItem(
                platform = json.string("platform") ?: platform,
                id = id,
                title = json.string("title", "name", "albumName") ?: "未知专辑",
                artist = json.string("artist", "singer", "artists") ?: "未知艺术家",
                artwork = json.string("artwork", "pic", "cover"),
                description = json.string("description", "desc"),
                date = json.string("date", "publishTime", "year"),
                raw = json,
            )
        }
    }
}

/** MusicFree `IArtistItem`. */
data class ArtistItem(
    val platform: String,
    val id: String,
    val name: String,
    val avatar: String? = null,
    val fans: String? = null,
    val description: String? = null,
    val raw: JsonObject? = null,
) {
    companion object {
        fun from(json: JsonObject?, platform: String): ArtistItem? {
            val id = json?.string("id", "artistId", "singerMid") ?: return null
            return ArtistItem(
                platform = json.string("platform") ?: platform,
                id = id,
                name = json.string("name", "title", "artist") ?: "未知艺术家",
                avatar = json.string("avatar", "artwork", "pic"),
                fans = json.string("fans", "fansCount"),
                description = json.string("description", "desc"),
                raw = json,
            )
        }
    }
}

/** Top-level search results for one page. */
data class SearchResult(
    val items: List<MusicItem> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val isEnd: Boolean = false,
    val error: String? = null,
)

/** MediaFree `SearchType`. */
object SearchType {
    const val MUSIC = "music"
    const val ALBUM = "album"
    const val ARTIST = "artist"
    const val SHEET = "sheet"
    val ALL: List<String> = listOf(MUSIC, ALBUM, ARTIST)
}

/** MusicFree `IMediaSource` result. */
data class MediaSourceResult(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val mimeType: String? = null,
)

/** MusicFree `ILyricSource`. */
data class LyricSourceResult(
    val rawLrc: String? = null,
    val translation: String? = null,
)
