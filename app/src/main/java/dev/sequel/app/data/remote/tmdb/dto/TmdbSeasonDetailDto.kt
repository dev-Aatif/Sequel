package dev.sequel.app.data.remote.tmdb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Full season detail from /tv/{id}/season/{season_number} endpoint.
 * Includes the full episode list.
 */
@Serializable
data class TmdbSeasonDetailDto(
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

    @SerialName("air_date")
    val airDate: String? = null,

    @SerialName("episodes")
    val episodes: List<TmdbEpisodeDto> = emptyList()
)
