package dev.sequel.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a season of a TV show.
 * Foreign-keyed to [ShowEntity] with cascade delete.
 */
@Entity(
    tableName = "seasons",
    foreignKeys = [
        ForeignKey(
            entity = ShowEntity::class,
            parentColumns = ["id"],
            childColumns = ["show_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["show_id"])]
)
data class SeasonEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int, // TMDB season ID

    @ColumnInfo(name = "show_id")
    val showId: Int,

    @ColumnInfo(name = "season_number")
    val seasonNumber: Int,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "overview")
    val overview: String?,

    @ColumnInfo(name = "poster_path")
    val posterPath: String?,

    @ColumnInfo(name = "episode_count")
    val episodeCount: Int,

    @ColumnInfo(name = "air_date")
    val airDate: String?
)
