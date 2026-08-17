package dev.sequel.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an episode of a TV show.
 * Foreign-keyed to [ShowEntity] with cascade delete.
 */
@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = ShowEntity::class,
            parentColumns = ["id"],
            childColumns = ["show_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["show_id"]),
        Index(value = ["show_id", "season_number", "episode_number"], unique = true)
    ]
)
data class EpisodeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int, // TMDB episode ID

    @ColumnInfo(name = "show_id")
    val showId: Int,

    @ColumnInfo(name = "season_number")
    val seasonNumber: Int,

    @ColumnInfo(name = "episode_number")
    val episodeNumber: Int,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "overview")
    val overview: String?,

    @ColumnInfo(name = "still_path")
    val stillPath: String?,

    @ColumnInfo(name = "air_date")
    val airDate: String?,

    @ColumnInfo(name = "runtime")
    val runtime: Int?
)
