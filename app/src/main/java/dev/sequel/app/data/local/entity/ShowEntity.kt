package dev.sequel.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a TV show or movie cached from TMDB.
 * Acts as the single source of truth for show metadata.
 */
@Entity(tableName = "shows")
data class ShowEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int, // TMDB ID

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "overview")
    val overview: String,

    @ColumnInfo(name = "poster_path")
    val posterPath: String?,

    @ColumnInfo(name = "backdrop_path")
    val backdropPath: String?,

    /** "tv" or "movie" */
    @ColumnInfo(name = "media_type")
    val mediaType: String,

    @ColumnInfo(name = "first_air_date")
    val firstAirDate: String?,

    @ColumnInfo(name = "vote_average")
    val voteAverage: Double,

    /** Stored as JSON string, e.g. "[18,10765]" */
    @ColumnInfo(name = "genre_ids")
    val genreIds: String,

    @ColumnInfo(name = "number_of_seasons")
    val numberOfSeasons: Int?,

    @ColumnInfo(name = "number_of_episodes")
    val numberOfEpisodes: Int?,

    /** e.g. "Returning Series", "Ended", "Released" */
    @ColumnInfo(name = "status")
    val status: String?,

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "is_in_watchlist", defaultValue = "0")
    val isInWatchlist: Boolean = false,

    @ColumnInfo(name = "runtime")
    val runtime: Int? = null,

    /** Human-readable genre names, stored as comma-separated string e.g. "Action, Drama" */
    @ColumnInfo(name = "genres_display")
    val genresDisplay: String? = null,

    /** Average episode runtime in minutes (TV only) */
    @ColumnInfo(name = "episode_runtime")
    val episodeRuntime: Int? = null,

    /** Content rating / certification e.g. "TV-MA", "PG-13" */
    @ColumnInfo(name = "content_rating")
    val contentRating: String? = null,

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
)
