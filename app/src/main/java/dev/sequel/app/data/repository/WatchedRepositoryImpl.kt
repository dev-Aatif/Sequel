package dev.sequel.app.data.repository

import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.domain.repository.WatchedRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchedRepositoryImpl @Inject constructor(
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val syncManager: SyncManager
) : WatchedRepository {

    override suspend fun markWatched(
        episodeId: Int,
        showId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    ) {
        val entity = WatchedEpisodeEntity(
            episodeId = episodeId,
            showId = showId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            syncStatus = SyncStatus.PENDING
        )
        watchedEpisodeDao.insertWatchedEpisode(entity)

        // Trigger immediate sync attempt
        syncManager.syncWatchedEpisodesNow()
    }

    override suspend fun unmarkWatched(episodeId: Int) {
        watchedEpisodeDao.unwatchEpisode(episodeId)
    }

    override suspend fun isWatched(episodeId: Int): Boolean =
        watchedEpisodeDao.isEpisodeWatched(episodeId)

    override fun observeWatchedByShow(showId: Int): Flow<List<WatchedEpisodeEntity>> =
        watchedEpisodeDao.observeWatchedByShow(showId)

    override fun observeWatchedBySeason(
        showId: Int,
        seasonNumber: Int
    ): Flow<List<WatchedEpisodeEntity>> =
        watchedEpisodeDao.observeWatchedBySeason(showId, seasonNumber)

    override fun observeRecentlyWatched(limit: Int): Flow<List<WatchedEpisodeEntity>> =
        watchedEpisodeDao.observeRecentlyWatched(limit)

    override suspend fun getWatchedCount(showId: Int): Int =
        watchedEpisodeDao.getWatchedCountForShow(showId)

    override suspend fun getWatchedCountForSeason(showId: Int, seasonNumber: Int): Int =
        watchedEpisodeDao.getWatchedCountForSeason(showId, seasonNumber)
}
