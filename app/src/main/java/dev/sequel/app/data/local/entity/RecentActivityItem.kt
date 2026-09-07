package dev.sequel.app.data.local.entity

data class RecentActivityItem(
    val historyId: Long,
    val watchedAt: Long,
    val mediaType: MediaType,
    val showId: Int,
    val showTitle: String,
    val posterPath: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?
)
