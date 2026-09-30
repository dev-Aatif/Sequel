package dev.sequel.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User review or rating for a show.
 *
 * Two types of rows:
 * - **Rating row**: `is_rating_only = true`, `rating != null`, `reviewText = null`.
 *   One per show per user (enforced in DAO code, not DB constraint).
 * - **Review row**: `is_rating_only = false`, `reviewText != null`.
 *   Multiple allowed per user per show.
 */
@Entity(
    tableName = "reviews",
    foreignKeys = [
        ForeignKey(
            entity = ShowEntity::class,
            parentColumns = ["id", "media_type"],
            childColumns = ["media_id", "media_type"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["media_id", "media_type", "season_num", "episode_num"], unique = true),
        Index(value = ["sync_status"])
    ]
)
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "media_id")
    val mediaId: Int,

    @ColumnInfo(name = "media_type")
    val mediaType: String,

    @ColumnInfo(name = "season_num")
    val seasonNum: Int? = null,

    @ColumnInfo(name = "episode_num")
    val episodeNum: Int? = null,

    @ColumnInfo(name = "review_text")
    val reviewText: String?,

    @ColumnInfo(name = "rating")
    val rating: Int? = null, // 1-10 rating

    @ColumnInfo(name = "is_spoiler")
    val isSpoiler: Boolean = false,

    /** True if this row is a rating-only entry (no review text). */
    @ColumnInfo(name = "is_rating_only")
    val isRatingOnly: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,

    @ColumnInfo(name = "supabase_id")
    val supabaseId: String? = null
)
