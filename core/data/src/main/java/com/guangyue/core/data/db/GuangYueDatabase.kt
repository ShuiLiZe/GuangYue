package com.guangyue.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.guangyue.core.data.model.PlaylistEntity
import com.guangyue.core.data.model.PlaylistTrackEntity
import com.guangyue.core.data.model.SearchHistoryEntity
import com.guangyue.core.data.model.StationEntity
import com.guangyue.core.data.model.TrackEntity

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        StationEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class GuangYueDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun stationDao(): StationDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val NAME = "guangyue.db"
    }
}
