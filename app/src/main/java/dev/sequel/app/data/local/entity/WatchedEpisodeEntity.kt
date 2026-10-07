package dev.sequel.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Records that a user has watched a specific TV episode.
 * This table is now strictly for TV episodes — movies use [WatchedMovieEntity].
 * Saved locally first, then synced to Supabase via WorkManager.
 */
@Entity(
    tableName = "watched_episodes",
    foreignKeys = [
        ForeignKey(
            entity = ShowEntity::class,
            parentColumns = ["id", "media_type"],
            childColumns = ["show_id", "media_type"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tmdb_episode_id"], unique = true),
        Index(value = ["show_id", "media_type"]),
        Index(value = ["sync_status"])
    ]
)
data class WatchedEpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "media_type")
    val mediaType: MediaType = MediaType.TV,

    @ColumnInfo(name = "show_id")
    val showId: Int,

    @ColumnInfo(name = "tmdb_episode_id")
    val tmdbEpisodeId: Int,

    @ColumnInfo(name = "season_number")
    val seasonNumber: Int,

    @ColumnInfo(name = "episode_number")
    val episodeNumber: Int,

    @ColumnInfo(name = "watched_at")
    val watchedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,

    /** The remote ID from Supabase after successful sync, null if not yet synced. */
    @ColumnInfo(name = "supabase_id")
    val supabaseId: String? = null,

    @ColumnInfo(name = "is_skipped")
    val isSkipped: Boolean = false
)
