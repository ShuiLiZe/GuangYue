package com.guangyue.core.data.repository

import com.guangyue.core.data.db.GuangYueDatabase
import com.guangyue.core.data.db.SearchHistoryDao
import com.guangyue.core.data.model.PlaylistEntity
import com.guangyue.core.data.model.PlaylistTrackEntity
import com.guangyue.core.data.model.SearchHistoryEntity
import com.guangyue.core.data.model.TrackEntity
import com.guangyue.core.data.model.TrackMapper
import com.guangyue.core.plugin.MusicItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(private val db: GuangYueDatabase) {

    private val dao = db.playlistDao()

    fun playlists(): Flow<List<PlaylistEntity>> = dao.observeAll()

    fun pinned(): Flow<List<PlaylistEntity>> = dao.observePinned()

    fun tracks(playlistId: Long): Flow<List<TrackEntity>> = dao.observeTracks(playlistId)

    suspend fun create(name: String, description: String? = null): Long =
        dao.insert(PlaylistEntity(name = name, description = description))

    suspend fun rename(id: Long, name: String) {
        val p = dao.byId(id) ?: return
        dao.update(p.copy(name = name))
    }

    suspend fun setPinned(id: Long, pinned: Boolean) {
        val p = dao.byId(id) ?: return
        dao.update(p.copy(pinned = pinned))
    }

    suspend fun delete(id: Long) {
        dao.clear(id)
        dao.delete(id)
    }

    /** Adds plugin items to a playlist, caching the track rows on the way in. */
    suspend fun addTracks(playlistId: Long, items: List<MusicItem>, quality: String = "standard") {
        val tracks = items.map { TrackMapper.fromPlugin(it, quality) }
        db.trackDao().upsertAll(tracks)
        dao.appendTracks(playlistId, tracks.map { it.key })
    }

    suspend fun addTrackKeys(playlistId: Long, keys: List<String>) = dao.appendTracks(playlistId, keys)

    suspend fun addTracks(playlistId: Long, tracks: List<TrackEntity>) {
        db.trackDao().upsertAll(tracks)
        dao.appendTracks(playlistId, tracks.map { it.key })
    }

    suspend fun removeTrack(playlistId: Long, trackKey: String) = dao.removeTrack(playlistId, trackKey)

    suspend fun removeAt(playlistId: Long, index: Int) {
        val list = dao.tracks(playlistId)
        list.getOrNull(index)?.let { dao.removeTrack(playlistId, it.key) }
    }

    suspend fun snapshot(playlistId: Long): List<TrackEntity> = dao.tracks(playlistId)
}

@Singleton
class TrackRepository @Inject constructor(private val db: GuangYueDatabase) {

    private val dao = db.trackDao()

    fun recentlyPlayed(limit: Int = 50): Flow<List<TrackEntity>> = dao.recentlyPlayed(limit)

    fun favorites(): Flow<List<TrackEntity>> = dao.favorites()

    fun localTracks(): Flow<List<TrackEntity>> = dao.localTracks()

    suspend fun byKey(key: String): TrackEntity? = dao.byKey(key)

    suspend fun cache(items: List<MusicItem>, quality: String = "standard") {
        dao.upsertAll(items.map { TrackMapper.fromPlugin(it, quality) })
    }

    suspend fun cache(track: TrackEntity) = dao.upsert(track)

    suspend fun setFavorite(key: String, favorite: Boolean) = dao.setFavorite(key, favorite)

    suspend fun markPlayed(key: String) = dao.markPlayed(key)

    suspend fun setQuality(key: String, quality: String) = dao.setQuality(key, quality)

    suspend fun delete(key: String) = dao.delete(key)

    suspend fun count(): Int = dao.count()
}

@Singleton
class SearchHistoryRepository @Inject constructor(db: GuangYueDatabase) {

    private val dao: SearchHistoryDao = db.searchHistoryDao()

    fun history(): Flow<List<SearchHistoryEntity>> = dao.observe()

    suspend fun record(query: String) {
        if (query.isBlank()) return
        dao.put(SearchHistoryEntity(query.trim()))
    }

    suspend fun clear() = dao.clear()
}
