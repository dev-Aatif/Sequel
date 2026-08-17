package dev.sequel.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * User review for a show — rating (1-10) and optional text.
 * One review per show per user (enforced by unique index on show_id).
 */
@Entity(
    tableName = "reviews",
    foreignKeys = [
        ForeignKey(
            entity = ShowEntity::class,
            parentColumns = ["id"],
            childColumns = ["show_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["show_id"], unique = true),
        Index(value = ["sync_status"])
    ]
)
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "show_id")
    val showId: Int,

    /** Rating from 1 to 10. */
    @ColumnInfo(name = "rating")
    val rating: Int,

    @ColumnInfo(name = "review_text")
    val reviewText: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING,

    @ColumnInfo(name = "supabase_id")
    val supabaseId: String? = null
)
