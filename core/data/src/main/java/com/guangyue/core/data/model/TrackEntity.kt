package com.guangyue.core.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A track as the library stores it.
 *
 * Rows come from two places: plugin search results (`source = "plugin"`, `platform`/`id` point
 * back at the plugin's own item) and scanned local files (`source = "local"`, [localUri] set).
 * Everything is cached so the shelf renders instantly offline and `getMediaSource` can be called
 * with the exact object the plugin handed us ([rawJson]).
 */
@Entity(tableName = "track")
data class TrackEntity(
    @PrimaryKey val key: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val artwork: String? = null,
    val durationMs: Long = 0L,

    @ColumnInfo(name = "platform") val platform: String = "",
    @ColumnInfo(name = "song_id") val songId: String = "",
    /** "plugin" | "local" */
    val source: String = SOURCE_PLUGIN,
    /** Last played quality for this track. */
    val quality: String = "standard",
    /** MusicFree musicItem as returned by the plugin, verbatim (JSON). */
    val rawJson: String? = null,

    @ColumnInfo(name = "local_uri") val localUri: String? = null,
    @ColumnInfo(name = "file_size") val fileSize: Long = 0L,

    val favorite: Boolean = false,
    @ColumnInfo(name = "play_count") val playCount: Int = 0,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Long = 0L,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val SOURCE_PLUGIN = "plugin"
        const val SOURCE_LOCAL = "local"
    }
}

/** A user playlist (MusicFree calls it 歌单). */
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val artwork: String? = null,
    /** For imported remote sheets: which plugin imported it and its remote id. */
    val platform: String? = null,
    @ColumnInfo(name = "remote_id") val remoteId: String? = null,
    /** Playlists pinned to the home coverflow. */
    val pinned: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

/** Ordered membership of a track in a playlist. */
@Entity(tableName = "playlist_track", primaryKeys = ["playlist_id", "track_key"])
data class PlaylistTrackEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "track_key") val trackKey: String,
    val position: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
)

/** A "station" (电台): a lazily-grown queue seeded from a track / artist / tag. */
@Entity(tableName = "station")
data class StationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "track" | "artist" | "album" | "tag" */
    val seedType: String,
    val seedValue: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/** Search history row. */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    @ColumnInfo(name = "used_at") val usedAt: Long = System.currentTimeMillis(),
)
