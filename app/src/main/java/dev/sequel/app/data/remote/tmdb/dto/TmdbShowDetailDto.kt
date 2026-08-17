package dev.sequel.app.data.remote.tmdb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Detailed TV show response from /tv/{id} endpoint.
 * Contains full metadata including seasons list.
 */
@Serializable
data class TmdbShowDetailDto(
    @SerialName("id")
    val id: Int,

    @SerialName("name")
    val name: String,

    @SerialName("overview")
    val overview: String = "",

    @SerialName("poster_path")
    val posterPath: String? = null,

    @SerialName("backdrop_path")
    val backdropPath: String? = null,

    @SerialName("first_air_date")
    val firstAirDate: String? = null,

    @SerialName("vote_average")
    val voteAverage: Double = 0.0,

    @SerialName("genres")
    val genres: List<TmdbGenreDto> = emptyList(),

    @SerialName("number_of_seasons")
    val numberOfSeasons: Int = 0,

    @SerialName("number_of_episodes")
    val numberOfEpisodes: Int = 0,

    @SerialName("status")
    val status: String = "",

    @SerialName("seasons")
    val seasons: List<TmdbSeasonSummaryDto> = emptyList(),

    @SerialName("networks")
    val networks: List<TmdbNetworkDto> = emptyList(),

    @SerialName("tagline")
    val tagline: String = "",

    @SerialName("type")
    val type: String = "",

    @SerialName("in_production")
    val inProduction: Boolean = false
)

@Serializable
data class TmdbGenreDto(
    @SerialName("id")
    val id: Int,

    @SerialName("name")
    val name: String
)

@Serializable
data class TmdbSeasonSummaryDto(
    @SerialName("id")
    val id: Int,

    @SerialName("season_number")
    val seasonNumber: Int,

    @SerialName("name")
    val name: String,

    @SerialName("overview")
    val overview: String = "",

    @SerialName("poster_path")
    val posterPath: String? = null,

    @SerialName("episode_count")
    val episodeCount: Int = 0,

    @SerialName("air_date")
    val airDate: String? = null
)

@Serializable
data class TmdbNetworkDto(
    @SerialName("id")
    val id: Int,

    @SerialName("name")
    val name: String,

    @SerialName("logo_path")
    val logoPath: String? = null
)
