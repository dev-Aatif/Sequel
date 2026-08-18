package dev.sequel.app.data.remote.supabase.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseWatchlistDto(
    @SerialName("user_id")
    val userId: String,
    
    @SerialName("tmdb_id")
    val tmdbId: Int,

    @SerialName("media_type")
    val mediaType: String,

    @SerialName("title")
    val title: String,

    @SerialName("poster_path")
    val posterPath: String?,

    @SerialName("added_at")
    val addedAt: Long
)
