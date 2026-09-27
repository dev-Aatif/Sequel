package dev.sequel.app.domain.repository

import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import kotlinx.coroutines.flow.Flow

interface WatchedEpisodeRepository {
    fun observeTotalMoviesWatched(): Flow<Int>
    fun observeWatchedByShow(showId: Int, mediaType: String): Flow<List<WatchedEpisodeEntity>>
    suspend fun unwatchAllForShow(showId: Int, mediaType: String)
    suspend fun upsertWatchedEpisode(
        mediaType: MediaType,
        showId: Int,
        episodeId: Int?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        isSkipped: Boolean = false
    )
}
