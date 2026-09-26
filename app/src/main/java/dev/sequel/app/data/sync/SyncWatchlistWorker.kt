package dev.sequel.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sequel.app.data.local.dao.WatchlistDao
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.remote.supabase.SupabaseAuthService
import dev.sequel.app.data.remote.supabase.SupabaseSyncService

@HiltWorker
class SyncWatchlistWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val watchlistDao: WatchlistDao,
    private val supabaseSyncService: SupabaseSyncService,
    private val supabaseAuthService: SupabaseAuthService
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val userId = supabaseAuthService.awaitUserId() ?: return Result.failure()
        var hasFailures = false

        try {
            val pendingWatchlist = watchlistDao.getPendingWatchlist()
            if (pendingWatchlist.isNotEmpty()) {
                val toUpsert = pendingWatchlist.filter { it.syncStatus != SyncStatus.DELETED }
                val toDelete = pendingWatchlist.filter { it.syncStatus == SyncStatus.DELETED }

                if (toUpsert.isNotEmpty()) {
                    try {
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
                        val updates = toUpsert.map { Pair(it.tmdbId, it.mediaType.name.lowercase()) }
                        watchlistDao.markWatchlistSyncedTransaction(updates)
                    } catch (e: Exception) {
                        hasFailures = true
                    }
                }

                for (deleted in toDelete) {
                    try {
                        supabaseSyncService.deleteFromWatchlist(userId, deleted.tmdbId)
                        watchlistDao.deleteWatchlistById(deleted.tmdbId, deleted.mediaType.name.lowercase())
                    } catch (e: Exception) {
                        hasFailures = true
                    }
                }
            }
        } catch (e: Exception) {
            hasFailures = true
        }

        return if (hasFailures) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "sync_watchlist_work"
    }
}
