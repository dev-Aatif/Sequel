package dev.sequel.app.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sequel.app.data.local.dao.ReviewDao
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.dao.WatchedMovieDao
import dev.sequel.app.data.local.dao.WatchlistDao
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.ReviewEntity
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import dev.sequel.app.data.local.entity.WatchedMovieEntity
import dev.sequel.app.data.local.entity.WatchlistEntity
import dev.sequel.app.data.remote.supabase.SupabaseAuthService
import dev.sequel.app.data.remote.supabase.SupabaseSyncService
import dev.sequel.app.domain.repository.ShowRepository
import kotlinx.coroutines.delay

@HiltWorker
class SyncPullWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val supabaseAuthService: SupabaseAuthService,
    private val supabaseSyncService: SupabaseSyncService,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val watchedMovieDao: WatchedMovieDao,
    private val reviewDao: ReviewDao,
    private val watchlistDao: WatchlistDao,
    private val showDao: ShowDao,
    private val showRepository: ShowRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val userId = supabaseAuthService.awaitUserId() ?: return Result.failure()
        var hasFailures = false

        // ── Phase 1: Download all remote data from Supabase ─────────

        val remoteWatchedMovies = try {
            supabaseSyncService.fetchAllWatchedMovies(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch watched movies from Supabase", e)
            hasFailures = true
            emptyList()
        }

        val remoteWatchedEpisodes = try {
            supabaseSyncService.fetchAllWatchedEpisodes(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch watched episodes from Supabase", e)
            hasFailures = true
            emptyList()
        }

        val remoteReviews = try {
            supabaseSyncService.fetchAllReviews(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch reviews from Supabase", e)
            hasFailures = true
            emptyList()
        }

        val remoteWatchlist = try {
            supabaseSyncService.fetchAllWatchlist(userId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch watchlist from Supabase", e)
            hasFailures = true
            emptyList()
        }

        // ── Phase 2: Hydrate missing show metadata from TMDB ────────
        // watched_episodes have a FK to shows(id, media_type).
        // We MUST insert the parent show rows BEFORE inserting children.
        // watched_movies carry their own title+poster, so they render immediately
        // without needing a shows table entry.

        val allReferencedShows = mutableSetOf<Pair<Int, String>>() // (tmdbId, mediaType)

        remoteWatchedEpisodes.forEach { dto ->
            allReferencedShows.add(Pair(dto.tmdbShowId, "tv"))
        }
        remoteReviews.forEach { dto ->
            val mt = dto.mediaType ?: (if (dto.seasonNum != null) "tv" else "movie")
            allReferencedShows.add(Pair(dto.mediaId, mt))
        }

        // Filter to only those missing from the local shows table
        val missingShows = allReferencedShows.filter { (showId, mediaType) ->
            showDao.getShowById(showId, mediaType) == null
        }

        if (missingShows.isNotEmpty()) {
            Log.d(TAG, "Hydrating ${missingShows.size} missing show(s) from TMDB before inserting children")
            missingShows.chunked(5).forEach { chunk ->
                for ((showId, mediaType) in chunk) {
                    try {
                        when (mediaType) {
                            "tv" -> showRepository.fetchShowDetail(showId)
                            "movie" -> showRepository.fetchMovieDetail(showId)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to fetch TMDB metadata for $mediaType/$showId", e)
                        // Don't set hasFailures here — the child insert will simply
                        // fail with FK violation and we'll catch that below.
                    }
                }
                delay(500) // Respect TMDB rate limits
            }
        }

        // ── Phase 3: Insert children into Room ──────────────────────

        // 3a. Watched Movies — carry title+poster, render immediately
        if (remoteWatchedMovies.isNotEmpty()) {
            try {
                val movieEntities = remoteWatchedMovies.map { dto ->
                    WatchedMovieEntity(
                        supabaseId = dto.id,
                        tmdbMovieId = dto.tmdbMovieId,
                        title = dto.title,
                        posterPath = dto.posterPath,
                        watchedAt = dto.watchedAt,
                        syncStatus = SyncStatus.SYNCED
                    )
                }
                watchedMovieDao.upsertWatchedMoviesPullTransaction(movieEntities)

                // Handle server deletes: local SYNCED records that are missing from remote
                val remoteMovieIds = movieEntities.mapNotNull { it.supabaseId }.toSet()
                val localSyncedMovies = watchedMovieDao.getByStatus(SyncStatus.SYNCED)
                val toDeleteMoviesLocally = localSyncedMovies.filter { it.supabaseId != null && it.supabaseId !in remoteMovieIds }
                toDeleteMoviesLocally.forEach { watchedMovieDao.deleteMovieById(it.tmdbMovieId) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert watched movies into Room", e)
                hasFailures = true
            }
        }

        // 3b. Watched Episodes (TV only)
        if (remoteWatchedEpisodes.isNotEmpty()) {
            try {
                val watchedEntities = remoteWatchedEpisodes.map { dto ->
                    WatchedEpisodeEntity(
                        supabaseId = dto.id,
                        mediaType = MediaType.TV,
                        showId = dto.tmdbShowId,
                        tmdbEpisodeId = dto.tmdbEpisodeId,
                        seasonNumber = dto.seasonNumber,
                        episodeNumber = dto.episodeNumber,
                        watchedAt = dto.watchedAt,
                        syncStatus = SyncStatus.SYNCED,
                        isSkipped = dto.isSkipped
                    )
                }
                watchedEpisodeDao.upsertWatchedEpisodesPullTransaction(watchedEntities)

                // Handle server deletes: local SYNCED records that are missing from remote
                val remoteIds = watchedEntities.mapNotNull { it.supabaseId }.toSet()
                val localSynced = watchedEpisodeDao.getByStatus(SyncStatus.SYNCED)
                val toDeleteLocally = localSynced.filter { it.supabaseId != null && it.supabaseId !in remoteIds }
                toDeleteLocally.forEach { watchedEpisodeDao.deleteEpisodeById(it.id) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert watched episodes into Room", e)
                hasFailures = true
            }
        }

        // 3c. Reviews
        if (remoteReviews.isNotEmpty()) {
            try {
                val reviewEntities = remoteReviews.map { dto ->
                    ReviewEntity(
                        supabaseId = dto.id,
                        mediaId = dto.mediaId,
                        mediaType = dto.mediaType ?: (if (dto.seasonNum != null) "tv" else "movie"),
                        seasonNum = dto.seasonNum,
                        episodeNum = dto.episodeNum,
                        reviewText = dto.reviewText,
                        rating = dto.rating,
                        isRatingOnly = dto.reviewText.isNullOrBlank() && dto.rating != null,
                        isSpoiler = dto.isSpoiler ?: false,
                        updatedAt = dto.updatedAt ?: 0L,
                        syncStatus = SyncStatus.SYNCED
                    )
                }
                reviewDao.upsertReviewsPullTransaction(reviewEntities)

                // Handle server deletes for reviews
                val remoteRevIds = reviewEntities.mapNotNull { it.supabaseId }.toSet()
                val localSyncedReviews = reviewDao.getByStatus(SyncStatus.SYNCED)
                val toDeleteRevLocally = localSyncedReviews.filter { it.supabaseId != null && it.supabaseId !in remoteRevIds }
                toDeleteRevLocally.forEach { reviewDao.deleteReviewById(it.id) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert reviews into Room", e)
                hasFailures = true
            }
        }

        // 3d. Watchlist (no FK to shows, so this always worked)
        if (remoteWatchlist.isNotEmpty()) {
            try {
                val watchlistEntities = remoteWatchlist.map { dto ->
                    WatchlistEntity(
                        tmdbId = dto.tmdbId,
                        mediaType = if (dto.mediaType.equals("movie", ignoreCase = true)) MediaType.MOVIE else MediaType.TV,
                        title = dto.title,
                        posterPath = dto.posterPath,
                        addedAt = dto.addedAt,
                        syncStatus = SyncStatus.SYNCED
                    )
                }
                watchlistDao.upsertWatchlistPullTransaction(watchlistEntities)

                // Handle server deletes for watchlist
                val remoteWatchlistKeys = watchlistEntities.map { Pair(it.tmdbId, it.mediaType.name.lowercase()) }.toSet()
                val localSyncedWatchlist = watchlistDao.getByStatus(SyncStatus.SYNCED)
                val toDeleteWLocally = localSyncedWatchlist.filter { Pair(it.tmdbId, it.mediaType.name.lowercase()) !in remoteWatchlistKeys }
                toDeleteWLocally.forEach { watchlistDao.deleteWatchlistById(it.tmdbId, it.mediaType.name.lowercase()) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert watchlist into Room", e)
                hasFailures = true
            }
        }

        return if (hasFailures) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "sync_pull_worker"
        private const val TAG = "SyncPullWorker"
    }
}
