package dev.sequel.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Records that a user has watched a specific episode.
 * This is the core tracking entity — saved locally first, then synced to Supabase.
 */
@Entity(
    tableName = "watched_episodes",
    foreignKeys = [
        ForeignKey(
            entity = ShowEntity::class,
            parentColumns = ["id"],
            childColumns = ["show_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["show_id", "episode_id"], unique = true), // unique watch per show/episode
        Index(value = ["show_id"]),
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

    @ColumnInfo(name = "episode_id")
    val episodeId: Int? = null,

    @ColumnInfo(name = "season_number")
    val seasonNumber: Int? = null,

    @ColumnInfo(name = "episode_number")
    val episodeNumber: Int? = null,

    @ColumnInfo(name = "watched_at")
    val watchedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,

    /** The remote ID from Supabase after successful sync, null if not yet synced. */
    @ColumnInfo(name = "supabase_id")
    val supabaseId: String? = null
)
