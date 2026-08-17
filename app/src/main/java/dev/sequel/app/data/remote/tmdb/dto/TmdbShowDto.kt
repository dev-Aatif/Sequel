package dev.sequel.app.data.remote.tmdb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TMDB show/movie result from trending, search, and discover endpoints.
 * Covers both TV and movie media types — some fields are nullable
 * because TMDB returns different shapes per media type.
 */
@Serializable
data class TmdbShowDto(
    @SerialName("id")
    val id: Int,

    /** TV shows use "name", movies use "title". We map both. */
    @SerialName("name")
    val name: String? = null,

    @SerialName("title")
    val title: String? = null,

    @SerialName("overview")
    val overview: String = "",

    @SerialName("poster_path")
    val posterPath: String? = null,

    @SerialName("backdrop_path")
    val backdropPath: String? = null,

    /** "tv" or "movie" — present in trending/multi-search responses. */
    @SerialName("media_type")
    val mediaType: String? = null,

    @SerialName("first_air_date")
    val firstAirDate: String? = null,

    @SerialName("release_date")
    val releaseDate: String? = null,

    @SerialName("vote_average")
    val voteAverage: Double = 0.0,

    @SerialName("genre_ids")
    val genreIds: List<Int> = emptyList(),

    @SerialName("popularity")
    val popularity: Double = 0.0,

    @SerialName("vote_count")
    val voteCount: Int = 0,

    @SerialName("origin_country")
    val originCountry: List<String> = emptyList(),

    @SerialName("original_language")
    val originalLanguage: String = ""
) {
    /** Unified display title — prefers "name" (TV) then "title" (movie). */
    val displayTitle: String
        get() = name ?: title ?: "Unknown"

    /** Unified date — prefers firstAirDate (TV) then releaseDate (movie). */
    val displayDate: String?
        get() = firstAirDate ?: releaseDate
}
