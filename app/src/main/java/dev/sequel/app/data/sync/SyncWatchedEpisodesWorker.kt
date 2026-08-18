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
            } catch (e: Exception) {
                hasFailures = true
                watchedEpisodeDao.updateSyncStatus(record.id, SyncStatus.FAILED)
            }
        }

        // 2. Sync Watchlist
        try {
            val pendingWatchlist = watchlistDao.getPendingWatchlist()
            if (pendingWatchlist.isNotEmpty()) {
                val dtos = pendingWatchlist.map { entity ->
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
                watchlistDao.markWatchlistSynced(pendingWatchlist.map { it.tmdbId })
            }
        } catch (e: Exception) {
            hasFailures = true
        }

        return if (hasFailures) Result.retry() else Result.success()
    }
}
