package dev.sequel.app.domain.repository

import dev.sequel.app.data.local.entity.ShowEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for show/movie data.
 * The domain layer depends only on this interface — never on Room or Retrofit directly.
 */
interface ShowRepository {

    // ── Remote fetch + local cache ────────────────────────────────

    /** Fetch trending shows from TMDB and cache to Room (manual sync). */
    suspend fun fetchTrending(mediaType: String = "tv", timeWindow: String = "week", page: Int = 1): Result<List<ShowEntity>>

    /** Get paginated trending shows using Paging 3 + Room single source of truth. */
    fun getPagedTrendingShows(mediaType: String): Flow<androidx.paging.PagingData<ShowEntity>>

    /** Search TMDB and cache results to Room. */
    suspend fun search(query: String, page: Int = 1): Result<List<ShowEntity>>

    /** Fetch full show detail from TMDB and cache to Room. */
    suspend fun fetchShowDetail(showId: Int): Result<ShowEntity>

    /** Fetch full movie detail from TMDB and cache to Room. */
    suspend fun fetchMovieDetail(movieId: Int): Result<ShowEntity>

    // ── Local queries (reactive) ──────────────────────────────────

    /** Observe a single show by ID from Room. */
    fun observeShow(showId: Int, mediaType: String): Flow<ShowEntity?>

    /** Observe all shows by media type from Room. */
    fun observeShowsByType(mediaType: String): Flow<List<ShowEntity>>

    /** Observe favorite shows. */
    fun observeFavorites(): Flow<List<ShowEntity>>

    /** Observe watchlist shows. */
    fun observeWatchlist(): Flow<List<ShowEntity>>

    /** Search local cache. */
    fun searchLocal(query: String): Flow<List<ShowEntity>>

    /** Observe started TV shows. */
    fun observeStartedTvShows(): Flow<List<ShowEntity>>

    // ── Local mutations ───────────────────────────────────────────

    /** Insert a single show. */
    suspend fun insertShow(show: ShowEntity)

    /** Toggle favorite status for a show. */
    suspend fun toggleFavorite(showId: Int, mediaType: String, isFavorite: Boolean)

    /** Toggle watchlist status for a show. */
    suspend fun toggleWatchlist(showId: Int, mediaType: String, isInWatchlist: Boolean)

    /** Update watchlist status (identical to toggleWatchlist, added for explicit matching). */
    suspend fun updateWatchlistStatus(showId: Int, mediaType: String, isInWatchlist: Boolean)
}
