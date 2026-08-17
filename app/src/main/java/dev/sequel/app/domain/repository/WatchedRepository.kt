package dev.sequel.app.domain.repository

import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for episode watch tracking.
 */
interface WatchedRepository {

    /** Mark an episode as watched. Saves to Room and triggers sync. */
    suspend fun markWatched(
        episodeId: Int,
        showId: Int,
        seasonNumber: Int,
        episodeNumber: Int
    )

    /** Unmark an episode as watched. */
    suspend fun unmarkWatched(episodeId: Int)

    /** Check if an episode has been watched. */
    suspend fun isWatched(episodeId: Int): Boolean

    /** Observe all watched episodes for a show. */
    fun observeWatchedByShow(showId: Int): Flow<List<WatchedEpisodeEntity>>

    /** Observe watched episodes for a specific season. */
    fun observeWatchedBySeason(showId: Int, seasonNumber: Int): Flow<List<WatchedEpisodeEntity>>

    /** Observe recently watched episodes across all shows. */
    fun observeRecentlyWatched(limit: Int = 20): Flow<List<WatchedEpisodeEntity>>

    /** Get watched episode count for a show. */
    suspend fun getWatchedCount(showId: Int): Int

    /** Get watched episode count for a specific season. */
    suspend fun getWatchedCountForSeason(showId: Int, seasonNumber: Int): Int
}
