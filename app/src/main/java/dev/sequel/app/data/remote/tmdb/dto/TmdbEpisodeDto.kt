package dev.sequel.app.data.remote.tmdb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Episode data from TMDB — returned inside [TmdbSeasonDetailDto].
 */
@Serializable
data class TmdbEpisodeDto(
    @SerialName("id")
    val id: Int,

    @SerialName("season_number")
    val seasonNumber: Int,

    @SerialName("episode_number")
    val episodeNumber: Int,

    @SerialName("name")
    val name: String,

    @SerialName("overview")
    val overview: String = "",

    @SerialName("still_path")
    val stillPath: String? = null,

    @SerialName("air_date")
    val airDate: String? = null,

    @SerialName("runtime")
    val runtime: Int? = null,

    @SerialName("vote_average")
    val voteAverage: Double = 0.0,

    @SerialName("vote_count")
    val voteCount: Int = 0
)
