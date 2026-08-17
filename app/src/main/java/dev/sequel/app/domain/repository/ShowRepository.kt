package dev.sequel.app.domain.repository

import dev.sequel.app.data.local.entity.ShowEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for show/movie data.
 * The domain layer depends only on this interface — never on Room or Retrofit directly.
 */
interface ShowRepository {

    // ── Remote fetch + local cache ────────────────────────────────

    /** Fetch trending shows from TMDB and cache to Room. */
    suspend fun fetchTrending(mediaType: String = "tv", timeWindow: String = "week", page: Int = 1): Result<List<ShowEntity>>

    /** Search TMDB and cache results to Room. */
    suspend fun search(query: String, page: Int = 1): Result<List<ShowEntity>>

    /** Fetch full show detail from TMDB and cache to Room. */
    suspend fun fetchShowDetail(showId: Int): Result<ShowEntity>

    /** Fetch full movie detail from TMDB and cache to Room. */
    suspend fun fetchMovieDetail(movieId: Int): Result<ShowEntity>

    // ── Local queries (reactive) ──────────────────────────────────

    /** Observe a single show by ID from Room. */
    fun observeShow(showId: Int): Flow<ShowEntity?>

    /** Observe all shows by media type from Room. */
    fun observeShowsByType(mediaType: String): Flow<List<ShowEntity>>

    /** Observe favorite shows. */
    fun observeFavorites(): Flow<List<ShowEntity>>

    /** Observe watchlist shows. */
    fun observeWatchlist(): Flow<List<ShowEntity>>

    /** Search local cache. */
    fun searchLocal(query: String): Flow<List<ShowEntity>>

    // ── Local mutations ───────────────────────────────────────────

    /** Toggle favorite status for a show. */
    suspend fun toggleFavorite(showId: Int, isFavorite: Boolean)

    /** Toggle watchlist status for a show. */
    suspend fun toggleWatchlist(showId: Int, isInWatchlist: Boolean)
}
