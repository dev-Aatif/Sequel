package dev.sequel.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Records that a user has watched a specific movie.
 * Dedicated table for movies — no longer mixed with TV episodes.
 * Written locally first, then synced to Supabase via WorkManager.
 */
@Entity(
    tableName = "watched_movies",
    primaryKeys = ["tmdb_movie_id"],
    indices = [
        Index(value = ["sync_status"])
    ]
)
data class WatchedMovieEntity(
    @ColumnInfo(name = "tmdb_movie_id")
    val tmdbMovieId: Int,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "poster_path")
    val posterPath: String? = null,

    @ColumnInfo(name = "watched_at")
    val watchedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,

    /** The remote ID from Supabase after successful sync, null if not yet synced. */
    @ColumnInfo(name = "supabase_id")
    val supabaseId: String? = null
)
