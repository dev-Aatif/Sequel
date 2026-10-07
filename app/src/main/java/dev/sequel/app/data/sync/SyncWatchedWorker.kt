package dev.sequel.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.dao.WatchedMovieDao
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.remote.supabase.SupabaseAuthService
import dev.sequel.app.data.remote.supabase.SupabaseSyncService
import dev.sequel.app.data.remote.supabase.dto.SupabaseWatchedEpisodeDto
import dev.sequel.app.data.remote.supabase.dto.SupabaseWatchedMovieDto

/**
 * WorkManager worker that syncs unsynced watched movies and episodes to Supabase.
 *
 * Flow:
 * 1. Query Room for records with sync_status != SYNCED
 * 2. Map to Supabase DTOs
 * 3. Upsert to Supabase
 * 4. On success, update Room record with SYNCED status + supabase_id
 * 5. On failure, mark as FAILED for retry
 */
@HiltWorker
class SyncWatchedWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val watchedMovieDao: WatchedMovieDao,
    private val supabaseSyncService: SupabaseSyncService,
    private val supabaseAuthService: SupabaseAuthService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "sync_watched"
    }

    override suspend fun doWork(): Result {
        val userId = supabaseAuthService.awaitUserId()
            ?: return Result.failure() // Not authenticated

        var hasFailures = false

        // 1. Sync Watched Episodes
        try {
            val unsyncedEpisodes = watchedEpisodeDao.getUnsynced()
            val toDelete = unsyncedEpisodes.filter { it.syncStatus == SyncStatus.DELETED }
            val toUpsert = unsyncedEpisodes.filter { it.syncStatus != SyncStatus.DELETED }

            // Handle deletes
            for (record in toDelete) {
                try {
                    if (record.supabaseId != null) {
                        supabaseSyncService.deleteWatchedEpisode(record.supabaseId)
                    }
                    watchedEpisodeDao.deleteEpisodeById(record.id)
                } catch (e: Exception) {
                    hasFailures = true
                }
            }

            // Handle bulk upserts
            if (toUpsert.isNotEmpty()) {
                val dtos = toUpsert.map { record ->
                    SupabaseWatchedEpisodeDto(
                        id = record.supabaseId,
                        userId = userId,
                        tmdbShowId = record.showId,
                        tmdbEpisodeId = record.tmdbEpisodeId,
                        seasonNumber = record.seasonNumber,
                        episodeNumber = record.episodeNumber,
                        watchedAt = record.watchedAt,
                        isSkipped = record.isSkipped
                    )
                }

                // Chunking to avoid massive requests (e.g., 500 at a time)
                val chunkedDtos = dtos.chunked(500)
                for (chunk in chunkedDtos) {
                    val results = supabaseSyncService.upsertWatchedEpisodes(chunk)
                    
                    val updates = mutableListOf<Pair<Long, String>>()
                    for (result in results) {
                        val supabaseId = result.id ?: continue
                        val localMatch = toUpsert.find { 
                            it.showId == result.tmdbShowId && 
                            it.tmdbEpisodeId == result.tmdbEpisodeId &&
                            it.seasonNumber == result.seasonNumber &&
                            it.episodeNumber == result.episodeNumber
                        }
                        if (localMatch != null) {
                            updates.add(Pair(localMatch.id, supabaseId))
                        }
                    }
                    if (updates.isNotEmpty()) {
                        watchedEpisodeDao.markAsSyncedTransaction(updates)
                    }
                }
            }
        } catch (e: Exception) {
            hasFailures = true
        }

        // 2. Sync Watched Movies
        try {
            val unsyncedMovies = watchedMovieDao.getUnsynced()
            val toDeleteMovies = unsyncedMovies.filter { it.syncStatus == SyncStatus.DELETED }
            val toUpsertMovies = unsyncedMovies.filter { it.syncStatus != SyncStatus.DELETED }

            // Handle deletes
            for (record in toDeleteMovies) {
                try {
                    if (record.supabaseId != null) {
                        supabaseSyncService.deleteWatchedMovie(record.supabaseId)
                    }
                    watchedMovieDao.deleteMovieById(record.tmdbMovieId)
                } catch (e: Exception) {
                    hasFailures = true
                }
            }

            // Handle bulk upserts
            if (toUpsertMovies.isNotEmpty()) {
                val dtos = toUpsertMovies.map { record ->
                    SupabaseWatchedMovieDto(
                        id = record.supabaseId,
                        userId = userId,
                        tmdbMovieId = record.tmdbMovieId,
                        title = record.title,
                        posterPath = record.posterPath,
                        watchedAt = record.watchedAt
                    )
                }

                // Chunking to avoid massive requests
                val chunkedDtos = dtos.chunked(500)
                for (chunk in chunkedDtos) {
                    val results = supabaseSyncService.upsertWatchedMovies(chunk)
                    
                    val updates = mutableListOf<Pair<Int, String>>()
                    for (result in results) {
                        val supabaseId = result.id ?: continue
                        val localMatch = toUpsertMovies.find { 
                            it.tmdbMovieId == result.tmdbMovieId
                        }
                        if (localMatch != null) {
                            updates.add(Pair(localMatch.tmdbMovieId, supabaseId))
                        }
                    }
                    if (updates.isNotEmpty()) {
                        watchedMovieDao.markAsSyncedTransaction(updates)
                    }
                }
            }
        } catch (e: Exception) {
            hasFailures = true
        }

        return if (hasFailures) Result.retry() else Result.success()
    }
}
