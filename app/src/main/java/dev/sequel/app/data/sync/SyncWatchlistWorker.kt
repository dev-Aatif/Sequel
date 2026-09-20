package dev.sequel.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.remote.supabase.SupabaseAuthService
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.first

@HiltWorker
class SyncWatchlistWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val showDao: ShowDao,
    private val supabaseClient: SupabaseClient,
    private val authService: SupabaseAuthService
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val client = supabaseClient
        val userId = authService.currentUserId ?: return Result.failure()

        return try {
            val watchlistShows = showDao.observeWatchlist().first()
            val watchlistShowIds = watchlistShows.map { it.id }

            // Sync with Supabase (simplified for now to match the existing patterns)
            // First fetch existing records for this user
            val remoteRecords = client.from("watchlist")
                .select { filter { eq("user_id", userId) } }
                .decodeList<WatchlistRecord>()

            val remoteShowIds = remoteRecords.map { it.tmdbId }.toSet()

            // Calculate diffs
            val toInsert = watchlistShowIds.filter { it !in remoteShowIds }
            val toDelete = remoteShowIds.filter { it !in watchlistShowIds }

            // Insert new records
            if (toInsert.isNotEmpty()) {
                val recordsToInsert = toInsert.map { WatchlistRecord(userId = userId, tmdbId = it) }
                client.from("watchlist").insert(recordsToInsert)
            }

            // Delete removed records
            if (toDelete.isNotEmpty()) {
                client.from("watchlist").delete {
                    filter {
                        eq("user_id", userId)
                        isIn("tmdb_id", toDelete)
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "sync_watchlist_work"
    }
}

@kotlinx.serialization.Serializable
data class WatchlistRecord(
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    @kotlinx.serialization.SerialName("tmdb_id") val tmdbId: Int
)
