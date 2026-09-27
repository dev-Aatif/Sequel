package dev.sequel.app.data.repository

import dev.sequel.app.data.local.dao.WatchlistDao
import dev.sequel.app.data.local.entity.WatchlistEntity
import dev.sequel.app.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepositoryImpl @Inject constructor(
    private val watchlistDao: WatchlistDao
) : WatchlistRepository {
    override fun observeWatchlist(): Flow<List<WatchlistEntity>> = watchlistDao.observeWatchlist()
    
    override fun observeIsInWatchlist(tmdbId: Int, mediaType: String): Flow<Boolean> = watchlistDao.observeIsInWatchlist(tmdbId, mediaType)
    
    override suspend fun insertToWatchlist(entity: WatchlistEntity) = watchlistDao.insertToWatchlist(entity)
    
    override suspend fun removeFromWatchlist(tmdbId: Int, mediaType: String) = watchlistDao.removeFromWatchlist(tmdbId, mediaType)
}
