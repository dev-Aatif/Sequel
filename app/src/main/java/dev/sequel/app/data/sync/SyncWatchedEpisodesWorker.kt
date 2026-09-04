package dev.sequel.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.remote.supabase.SupabaseAuthService
import dev.sequel.app.data.remote.supabase.SupabaseSyncService
import dev.sequel.app.data.remote.supabase.dto.SupabaseWatchedEpisodeDto

/**
 * WorkManager worker that syncs unsynced watched episodes to Supabase.
 *
 * Flow:
 * 1. Query Room for records with sync_status != SYNCED
 * 2. Map to Supabase DTOs
 * 3. Upsert to Supabase
 * 4. On success, update Room record with SYNCED status + supabase_id
 * 5. On failure, mark as FAILED for retry
 */
@HiltWorker
class SyncWatchedEpisodesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val watchlistDao: dev.sequel.app.data.local.dao.WatchlistDao,
    private val reviewDao: dev.sequel.app.data.local.dao.ReviewDao,
    private val supabaseSyncService: SupabaseSyncService,
    private val supabaseAuthService: SupabaseAuthService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "sync_watched_episodes"
    }

    override suspend fun doWork(): Result {
        val userId = supabaseAuthService.currentUserId
            ?: return Result.failure() // Not authenticated

        var hasFailures = false

        // 1. Sync Watched Episodes
        val unsyncedEpisodes = watchedEpisodeDao.getUnsynced()
        for (record in unsyncedEpisodes) {
            try {
                if (record.syncStatus == SyncStatus.DELETED) {
                    if (record.supabaseId != null) {
                        supabaseSyncService.deleteWatchedEpisode(record.supabaseId)
                    }
                    watchedEpisodeDao.deleteEpisodeById(record.id)
                } else {
                    val dto = SupabaseWatchedEpisodeDto(
                        id = record.supabaseId,
                        userId = userId,
                        mediaType = record.mediaType.name.lowercase(),
                        tmdbShowId = record.showId,
                        tmdbEpisodeId = record.episodeId,
                        seasonNumber = record.seasonNumber,
                        episodeNumber = record.episodeNumber,
                        watchedAt = record.watchedAt
                    )

                    val supabaseId = supabaseSyncService.upsertWatchedEpisode(dto)
                    watchedEpisodeDao.markAsSynced(
                        id = record.id,
                        supabaseId = supabaseId
                    )
                }
            } catch (e: Exception) {
                if (runAttemptCount > 3) {
                    watchedEpisodeDao.updateSyncStatus(record.id, SyncStatus.FAILED)
                } else {
                    hasFailures = true
                }
            }
        }

        // 2. Sync Watchlist
        try {
            val pendingWatchlist = watchlistDao.getPendingWatchlist()
            if (pendingWatchlist.isNotEmpty()) {
                val toUpsert = pendingWatchlist.filter { it.syncStatus != SyncStatus.DELETED }
                val toDelete = pendingWatchlist.filter { it.syncStatus == SyncStatus.DELETED }

                if (toUpsert.isNotEmpty()) {
                    val dtos = toUpsert.map { entity ->
                        dev.sequel.app.data.remote.supabase.dto.SupabaseWatchlistDto(
                            userId = userId,
                            tmdbId = entity.tmdbId,
                            mediaType = entity.mediaType.name.lowercase(),
                            title = entity.title,
                            posterPath = entity.posterPath,
                            addedAt = entity.addedAt
                        )
                    }
                    supabaseSyncService.upsertWatchlist(dtos)
                    watchlistDao.markWatchlistSynced(toUpsert.map { it.tmdbId })
                }

                for (deleted in toDelete) {
                    supabaseSyncService.deleteFromWatchlist(userId, deleted.tmdbId)
                    watchlistDao.deleteWatchlistById(deleted.tmdbId)
                }
            }
        } catch (e: Exception) {
            if (runAttemptCount <= 3) {
                hasFailures = true
            }
        }

        // 3. Sync Reviews
        try {
            val unsyncedReviews = reviewDao.getUnsynced()
            for (record in unsyncedReviews) {
                try {
                    val dto = dev.sequel.app.data.remote.supabase.dto.SupabaseReviewDto(
                        id = record.supabaseId,
                        userId = userId,
                        mediaId = record.mediaId,
                        seasonNum = record.seasonNum,
                        episodeNum = record.episodeNum,
                        reviewText = record.reviewText,
                        vibeEmoji = record.rating?.toString(),
                        isSpoiler = record.isSpoiler
                        // createdAt can remain null for upsert so DB uses current timestamp
                    )
                    val supabaseId = supabaseSyncService.upsertReview(dto)
                    reviewDao.markAsSynced(
                        id = record.id,
                        supabaseId = supabaseId
                    )
                } catch (e: Exception) {
                    if (runAttemptCount > 3) {
                        reviewDao.updateSyncStatus(record.id, SyncStatus.FAILED)
                    } else {
                        hasFailures = true
                    }
                }
            }
        } catch (e: Exception) {
            if (runAttemptCount <= 3) {
                hasFailures = true
            }
        }

        return if (hasFailures) Result.retry() else Result.success()
    }
}
