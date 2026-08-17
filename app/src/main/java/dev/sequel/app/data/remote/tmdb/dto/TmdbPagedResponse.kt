package dev.sequel.app.data.remote.tmdb.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * TMDB API response wrapper for paginated lists
 * (trending, search, discover endpoints).
 */
@Serializable
data class TmdbPagedResponse<T>(
    @SerialName("page")
    val page: Int,

    @SerialName("results")
    val results: List<T>,

    @SerialName("total_pages")
    val totalPages: Int,

    @SerialName("total_results")
    val totalResults: Int
)
