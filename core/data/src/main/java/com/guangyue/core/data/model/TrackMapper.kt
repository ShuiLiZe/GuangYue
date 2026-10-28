package com.guangyue.core.data.model

import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.Json2

/** Bridging helpers between the plugin layer's [MusicItem] and the persisted [TrackEntity]. */
object TrackMapper {

    fun keyOf(platform: String, songId: String): String = "$platform:$songId"

    fun fromPlugin(item: MusicItem, quality: String = "standard"): TrackEntity = TrackEntity(
        key = keyOf(item.platform, item.id),
        title = item.title,
        artist = item.artist,
        album = item.album.orEmpty(),
        artwork = item.artwork,
        durationMs = item.durationMs ?: 0L,
        platform = item.platform,
        songId = item.id,
        source = TrackEntity.SOURCE_PLUGIN,
        quality = quality,
        rawJson = item.raw?.toString() ?: item.toJson().toString(),
    )

    fun toMusicItem(entity: TrackEntity): MusicItem? {
        val raw = entity.rawJson?.let { Json2.parse(it).let { j -> (j as? kotlinx.serialization.json.JsonObject) } }
            ?: return MusicItem(
                platform = entity.platform,
                id = entity.songId,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                artwork = entity.artwork,
                durationMs = entity.durationMs,
            )
        return MusicItem.from(raw, entity.platform)
    }
}
