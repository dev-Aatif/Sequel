package dev.sequel.app.data.repository

import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import dev.sequel.app.domain.repository.WatchedEpisodeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchedEpisodeRepositoryImpl @Inject constructor(
    private val watchedEpisodeDao: WatchedEpisodeDao
) : WatchedEpisodeRepository {
    override fun observeWatchedByShow(showId: Int): Flow<List<WatchedEpisodeEntity>> = 
        watchedEpisodeDao.observeWatchedByShow(showId)
        
    override suspend fun unwatchAllForShow(showId: Int) = 
        watchedEpisodeDao.unwatchAllForShow(showId)
        
    override suspend fun upsertWatchedEpisode(
        showId: Int,
        tmdbEpisodeId: Int,
        seasonNumber: Int,
        episodeNumber: Int,
        isSkipped: Boolean
    ) = watchedEpisodeDao.upsertWatchedEpisode(
        showId = showId,
        tmdbEpisodeId = tmdbEpisodeId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        isSkipped = isSkipped
    )
}
