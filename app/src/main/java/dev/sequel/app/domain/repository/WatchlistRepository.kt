package dev.sequel.app.domain.repository

import dev.sequel.app.data.local.entity.WatchlistEntity
import kotlinx.coroutines.flow.Flow

interface WatchlistRepository {
    fun observeWatchlist(): Flow<List<WatchlistEntity>>
    fun observeIsInWatchlist(tmdbId: Int, mediaType: String): Flow<Boolean>
    suspend fun insertToWatchlist(entity: WatchlistEntity)
    suspend fun removeFromWatchlist(tmdbId: Int, mediaType: String)
}
