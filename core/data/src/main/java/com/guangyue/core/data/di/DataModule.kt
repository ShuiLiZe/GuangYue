package com.guangyue.core.data.di

import android.content.Context
import androidx.room.Room
import com.guangyue.core.data.db.GuangYueDatabase
import com.guangyue.core.data.db.SearchHistoryDao
import com.guangyue.core.data.db.StationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GuangYueDatabase =
        Room.databaseBuilder(context, GuangYueDatabase::class.java, GuangYueDatabase.NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun stationDao(db: GuangYueDatabase): StationDao = db.stationDao()

    @Provides
    fun searchHistoryDao(db: GuangYueDatabase): SearchHistoryDao = db.searchHistoryDao()
}
