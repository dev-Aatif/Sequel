package dev.sequel.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.sequel.app.data.local.converter.Converters
import dev.sequel.app.data.local.dao.EpisodeDao
import dev.sequel.app.data.local.dao.ReviewDao
import dev.sequel.app.data.local.dao.SeasonDao
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.entity.EpisodeEntity
import dev.sequel.app.data.local.entity.ReviewEntity
import dev.sequel.app.data.local.entity.SeasonEntity
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity

import dev.sequel.app.data.local.entity.RemoteKeys
import dev.sequel.app.data.local.dao.RemoteKeysDao

/**
 * Sequel Room Database — Single Source of Truth.
 *
 * All TMDB data is cached here. User-generated data (watches, reviews)
 * is written here first, then synced to Supabase via WorkManager.
 */
@Database(
    entities = [
        ShowEntity::class,
        SeasonEntity::class,
        EpisodeEntity::class,
        WatchedEpisodeEntity::class,
        ReviewEntity::class,
        RemoteKeys::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class SequelDatabase : RoomDatabase() {

    abstract fun showDao(): ShowDao
    abstract fun seasonDao(): SeasonDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun watchedEpisodeDao(): WatchedEpisodeDao
    abstract fun reviewDao(): ReviewDao
    abstract fun remoteKeysDao(): RemoteKeysDao

    companion object {
        const val DATABASE_NAME = "sequel_database"
    }
}
