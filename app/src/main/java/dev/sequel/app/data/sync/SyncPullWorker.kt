package dev.sequel.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sequel.app.data.local.dao.ReviewDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.dao.WatchlistDao
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.ReviewEntity
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import dev.sequel.app.data.local.entity.WatchlistEntity
import dev.sequel.app.data.remote.supabase.SupabaseAuthService
import dev.sequel.app.data.remote.supabase.SupabaseSyncService

@HiltWorker
class SyncPullWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val supabaseAuthService: SupabaseAuthService,
    private val supabaseSyncService: SupabaseSyncService,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val reviewDao: ReviewDao,
    private val watchlistDao: WatchlistDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val userId = supabaseAuthService.awaitUserId() ?: return Result.failure()
        var hasFailures = false

        try {
            // 1. Fetch & Hydrate Watched Episodes
            val remoteWatched = supabaseSyncService.fetchAllWatchedEpisodes(userId)
            val watchedEntities = remoteWatched.map { dto ->
                WatchedEpisodeEntity(
                    supabaseId = dto.id,
                    mediaType = if (dto.mediaType.equals("movie", ignoreCase = true)) MediaType.MOVIE else MediaType.TV,
                    showId = dto.tmdbShowId,
                    episodeId = dto.tmdbEpisodeId,
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
            hasFailures = true
        }

        try {
            // 2. Fetch & Hydrate Reviews
            val remoteReviews = supabaseSyncService.fetchAllReviews(userId)
            val reviewEntities = remoteReviews.map { dto ->
                ReviewEntity(
                    supabaseId = dto.id,
                    mediaId = dto.mediaId,
                    mediaType = dto.mediaType ?: (if (dto.seasonNum != null) "tv" else "movie"),
                    seasonNum = dto.seasonNum,
                    episodeNum = dto.episodeNum,
                    reviewText = dto.reviewText,
                    rating = dto.vibeEmoji?.toIntOrNull(),
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
            hasFailures = true
        }

        try {
            // 3. Fetch & Hydrate Watchlist
            val remoteWatchlist = supabaseSyncService.fetchAllWatchlist(userId)
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
            hasFailures = true
        }

        return if (hasFailures) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "sync_pull_worker"
    }
}
