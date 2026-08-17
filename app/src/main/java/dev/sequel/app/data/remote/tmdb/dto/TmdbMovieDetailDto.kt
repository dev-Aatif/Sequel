package dev.sequel.app.data.remote.tmdb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Movie detail response from /movie/{id} endpoint.
 */
@Serializable
data class TmdbMovieDetailDto(
    @SerialName("id")
    val id: Int,

    @SerialName("title")
    val title: String,

    @SerialName("overview")
    val overview: String = "",

    @SerialName("poster_path")
    val posterPath: String? = null,

    @SerialName("backdrop_path")
    val backdropPath: String? = null,

    @SerialName("release_date")
    val releaseDate: String? = null,

    @SerialName("vote_average")
    val voteAverage: Double = 0.0,

    @SerialName("genres")
    val genres: List<TmdbGenreDto> = emptyList(),

    @SerialName("runtime")
    val runtime: Int? = null,

    @SerialName("status")
    val status: String = "",

    @SerialName("tagline")
    val tagline: String = "",

    @SerialName("budget")
    val budget: Long = 0,

    @SerialName("revenue")
    val revenue: Long = 0
)
