package dev.sequel.app.domain.repository

import dev.sequel.app.data.local.entity.EpisodeEntity
import dev.sequel.app.data.local.entity.SeasonEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for season and episode data.
 */
interface SeasonRepository {

    /** Fetch season detail (with episodes) from TMDB and cache to Room. */
    suspend fun fetchSeasonDetail(showId: Int, seasonNumber: Int): Result<List<EpisodeEntity>>

    /** Observe seasons for a show from Room. */
    fun observeSeasons(showId: Int): Flow<List<SeasonEntity>>

    /** Observe episodes for a specific season from Room. */
    fun observeEpisodes(showId: Int, seasonNumber: Int): Flow<List<EpisodeEntity>>

    /** Get episode count for a show. */
    suspend fun getEpisodeCount(showId: Int): Int
}
