package dev.sequel.app.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sequel.app.data.local.dao.ReviewDao
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.remote.supabase.SupabaseAuthService
import dev.sequel.app.data.remote.supabase.SupabaseSyncService
import dev.sequel.app.data.remote.supabase.dto.SupabaseReviewDto

/**
 * WorkManager worker that syncs unsynced reviews to Supabase.
 */
@HiltWorker
class SyncReviewsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val reviewDao: ReviewDao,
    private val supabaseSyncService: SupabaseSyncService,
    private val supabaseAuthService: SupabaseAuthService
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "sync_reviews"
    }

    override suspend fun doWork(): Result {
        val userId = supabaseAuthService.awaitUserId()
            ?: return Result.failure()

        val unsynced = reviewDao.getUnsynced()
        if (unsynced.isEmpty()) return Result.success()

        var hasFailures = false

        for (record in unsynced) {
            try {
                if (record.syncStatus == SyncStatus.DELETED) {
                    if (record.supabaseId != null) {
                        supabaseSyncService.deleteReview(record.supabaseId)
                    }
                    reviewDao.deleteReviewById(record.id)
                } else {
                    val dto = SupabaseReviewDto(
                        id = record.supabaseId,
                        userId = userId,
                        mediaId = record.mediaId,
                        mediaType = record.mediaType,
                        seasonNum = record.seasonNum,
                        episodeNum = record.episodeNum,
                        reviewText = record.reviewText,
                        vibeEmoji = record.rating?.toString(),
                        isSpoiler = record.isSpoiler,
                        updatedAt = record.updatedAt
                    )

                    val supabaseId = supabaseSyncService.upsertReview(dto)
                    reviewDao.markAsSynced(
                        id = record.id,
                        supabaseId = supabaseId
                    )
                }
            } catch (e: Exception) {
                hasFailures = true
            }
        }

        return if (hasFailures) Result.retry() else Result.success()
    }
}
