package dev.sequel.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Top 4 "Mount Rushmore" pinned favorite movie or TV show.
 * Constrained to slots 0, 1, 2, and 3.
 * Acts as local Single Source of Truth for the user's pinned favorites.
 */
@Entity(tableName = "mount_rushmore")
data class MountRushmoreEntity(
    @PrimaryKey
    @ColumnInfo(name = "slot_index")
    val slotIndex: Int,

    @ColumnInfo(name = "media_id")
    val mediaId: Int,

    @ColumnInfo(name = "media_type")
    val mediaType: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "poster_path")
    val posterPath: String? = null,

    @ColumnInfo(name = "year")
    val year: String? = null,

    @ColumnInfo(name = "rating")
    val rating: Float? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
