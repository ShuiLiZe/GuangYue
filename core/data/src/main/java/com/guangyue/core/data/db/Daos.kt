package com.guangyue.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.guangyue.core.data.model.PlaylistEntity
import com.guangyue.core.data.model.PlaylistTrackEntity
import com.guangyue.core.data.model.SearchHistoryEntity
import com.guangyue.core.data.model.StationEntity
import com.guangyue.core.data.model.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("SELECT * FROM track WHERE `key` = :key")
    suspend fun byKey(key: String): TrackEntity?

    @Query("SELECT * FROM track WHERE `key` = :key")
    fun observe(key: String): Flow<TrackEntity?>

    // 2026-11-10：只算真播过的（以前搜索结果写库也算「最近播放」）。
    @Query("SELECT * FROM track WHERE last_played_at > 0 ORDER BY last_played_at DESC LIMIT :limit")
    fun recentlyPlayed(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track WHERE favorite = 1 ORDER BY added_at DESC")
    fun favorites(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track WHERE source = 'local' ORDER BY title")
    fun localTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track WHERE source = 'local' AND `key` = :key")
    suspend fun localByKey(key: String): TrackEntity?

    @Query("UPDATE track SET favorite = :favorite WHERE `key` = :key")
    suspend fun setFavorite(key: String, favorite: Boolean)

    @Query("UPDATE track SET play_count = play_count + 1, last_played_at = :at WHERE `key` = :key")
    suspend fun markPlayed(key: String, at: Long = System.currentTimeMillis())

    @Query("UPDATE track SET quality = :quality WHERE `key` = :key")
    suspend fun setQuality(key: String, quality: String)

    @Query("DELETE FROM track WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("SELECT COUNT(*) FROM track")
    suspend fun count(): Int

    @Query("SELECT `key` FROM track WHERE favorite = 1")
    suspend fun favoriteKeys(): List<String>

    /** 写库但保留收藏 / 播放次数 / 本地文件（[com.guangyue.core.data.model.LibraryRules.merge]）。 */
    @Transaction
    suspend fun mergeUpsert(track: TrackEntity) {
        upsert(com.guangyue.core.data.model.LibraryRules.merge(byKey(track.key), track))
    }

    @Transaction
    suspend fun mergeUpsertAll(tracks: List<TrackEntity>) {
        tracks.forEach { mergeUpsert(it) }
    }

    /** 收藏 / 取消收藏：库里没这一行就用 [fallback] 建行（下载页 / 歌单直连开播时常见）。 */
    @Transaction
    suspend fun setFavoriteTrack(fallback: TrackEntity, favorite: Boolean, now: Long) {
        upsert(com.guangyue.core.data.model.LibraryRules.favorite(byKey(fallback.key), fallback, favorite, now))
    }

    /** 记一次播放（最近播放 / 播放次数），没有行先建行。 */
    @Transaction
    suspend fun touchPlayed(track: TrackEntity, now: Long) {
        upsert(com.guangyue.core.data.model.LibraryRules.played(byKey(track.key), track, now))
    }
}

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM playlist ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist WHERE pinned = 1 ORDER BY updated_at DESC")
    fun observePinned(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist WHERE id = :id")
    suspend fun byId(id: Long): PlaylistEntity?

    @Query("UPDATE playlist SET updated_at = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTracks(rows: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_track WHERE playlist_id = :playlistId AND track_key = :trackKey")
    suspend fun removeTrack(playlistId: Long, trackKey: String)

    @Query("DELETE FROM playlist_track WHERE playlist_id = :playlistId")
    suspend fun clear(playlistId: Long)

    @Query("SELECT MAX(position) FROM playlist_track WHERE playlist_id = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int?

    @Query(
        """
        SELECT t.* FROM track t
        INNER JOIN playlist_track p ON p.track_key = t.`key`
        WHERE p.playlist_id = :playlistId
        ORDER BY p.position ASC
        """,
    )
    fun observeTracks(playlistId: Long): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM track t
        INNER JOIN playlist_track p ON p.track_key = t.`key`
        WHERE p.playlist_id = :playlistId
        ORDER BY p.position ASC
        """,
    )
    suspend fun tracks(playlistId: Long): List<TrackEntity>

    /** Appends tracks in order, skipping ones already present. */
    @Transaction
    suspend fun appendTracks(playlistId: Long, trackKeys: List<String>) {
        var position = (maxPosition(playlistId) ?: -1) + 1
        val rows = trackKeys.map { PlaylistTrackEntity(playlistId, it, position++) }
        addTracks(rows)
        touch(playlistId)
    }
}

@Dao
interface StationDao {
    @Insert
    suspend fun insert(station: StationEntity): Long

    @Query("SELECT * FROM station ORDER BY created_at DESC")
    fun observeAll(): Flow<List<StationEntity>>

    @Query("DELETE FROM station WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY used_at DESC LIMIT :limit")
    fun observe(limit: Int = 20): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
