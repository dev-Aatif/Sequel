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
    private val supabaseSyncService: SupabaseSyncService,
    private val supabaseAuthService: SupabaseAuthService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "sync_watched_episodes"
    }

    override suspend fun doWork(): Result {
        val userId = supabaseAuthService.currentUserId
            ?: return Result.failure() // Not authenticated

        val unsynced = watchedEpisodeDao.getUnsynced()
        if (unsynced.isEmpty()) return Result.success()

        var hasFailures = false

        for (record in unsynced) {
            try {
                val dto = SupabaseWatchedEpisodeDto(
                    id = record.supabaseId, // null for new records, existing UUID for updates
                    userId = userId,
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

        return if (hasFailures) Result.retry() else Result.success()
    }
}
