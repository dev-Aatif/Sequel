package dev.sequel.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import dev.sequel.app.data.local.entity.WatchlistEntity

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
        RemoteKeys::class,
        WatchlistEntity::class
    ],
    version = 9,
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
    abstract fun watchlistDao(): dev.sequel.app.data.local.dao.WatchlistDao

    companion object {
        const val DATABASE_NAME = "sequel_database"

        /**
         * Migration 7→8: Rename vibe_emoji column to rating (Integer) in reviews table.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite doesn't support RENAME COLUMN before 3.25, so we add the new column
                // and leave the old one (Room won't query it since entity no longer references it).
                db.execSQL("ALTER TABLE reviews ADD COLUMN rating INTEGER DEFAULT NULL")
            }
        }

        /**
         * Migration 8→9: Add metadata columns to shows table.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shows ADD COLUMN genres_display TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE shows ADD COLUMN episode_runtime INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE shows ADD COLUMN content_rating TEXT DEFAULT NULL")
            }
        }
    }
}
