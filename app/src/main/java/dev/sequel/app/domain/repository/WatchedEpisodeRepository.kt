package dev.sequel.app.domain.repository

import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import kotlinx.coroutines.flow.Flow

interface WatchedEpisodeRepository {
    fun observeWatchedByShow(showId: Int): Flow<List<WatchedEpisodeEntity>>
    suspend fun unwatchAllForShow(showId: Int)
    suspend fun upsertWatchedEpisode(
        showId: Int,
        tmdbEpisodeId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        isSkipped: Boolean = false
    )
}
