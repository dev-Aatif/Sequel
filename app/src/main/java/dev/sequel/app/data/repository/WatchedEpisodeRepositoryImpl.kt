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
    override fun observeTotalMoviesWatched(): Flow<Int> = watchedEpisodeDao.observeTotalMoviesWatched()
    
    override fun observeWatchedByShow(showId: Int, mediaType: String): Flow<List<WatchedEpisodeEntity>> = 
        watchedEpisodeDao.observeWatchedByShow(showId, mediaType)
        
    override suspend fun unwatchAllForShow(showId: Int, mediaType: String) = 
        watchedEpisodeDao.unwatchAllForShow(showId, mediaType)
        
    override suspend fun upsertWatchedEpisode(
        mediaType: MediaType,
        showId: Int,
        episodeId: Int?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        isSkipped: Boolean
    ) = watchedEpisodeDao.upsertWatchedEpisode(
        mediaType = mediaType,
        showId = showId,
        episodeId = episodeId,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        isSkipped = isSkipped
    )
}
