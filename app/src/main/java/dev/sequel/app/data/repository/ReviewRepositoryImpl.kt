package dev.sequel.app.data.repository

import dev.sequel.app.data.local.dao.ReviewDao
import dev.sequel.app.data.local.entity.ReviewEntity
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.domain.repository.ReviewRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReviewRepositoryImpl @Inject constructor(
    private val reviewDao: ReviewDao,
    private val syncManager: SyncManager,
    private val supabaseSyncService: dev.sequel.app.data.remote.supabase.SupabaseSyncService
) : ReviewRepository {

    override suspend fun submitReview(
        mediaId: Int,
        seasonNum: Int?,
        episodeNum: Int?,
        reviewText: String?,
        vibeEmoji: String?,
        isSpoiler: Boolean
    ) {
        val existing = if (seasonNum != null && episodeNum != null) {
            reviewDao.getReviewForEpisode(mediaId, seasonNum, episodeNum)
        } else {
            reviewDao.getReviewForMedia(mediaId)
        }

        val entity = if (existing != null) {
            existing.copy(
                reviewText = reviewText,
                vibeEmoji = vibeEmoji,
                isSpoiler = isSpoiler,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING
            )
        } else {
            ReviewEntity(
                mediaId = mediaId,
                seasonNum = seasonNum,
                episodeNum = episodeNum,
                reviewText = reviewText,
                vibeEmoji = vibeEmoji,
                isSpoiler = isSpoiler,
                syncStatus = SyncStatus.PENDING
            )
        }

        if (existing != null) {
            reviewDao.updateReview(entity)
        } else {
            reviewDao.insertReview(entity)
        }

        // Trigger immediate sync attempt
        syncManager.syncReviewsNow()
    }

    override fun observeReviewForMedia(mediaId: Int): Flow<ReviewEntity?> =
        reviewDao.observeReviewForMedia(mediaId)

    override fun observeReviewForEpisode(mediaId: Int, seasonNum: Int, episodeNum: Int): Flow<ReviewEntity?> =
        reviewDao.observeReviewForEpisode(mediaId, seasonNum, episodeNum)

    override fun observeAllReviews(): Flow<List<ReviewEntity>> =
        reviewDao.observeAllReviews()

    override suspend fun deleteReview(mediaId: Int, seasonNum: Int?, episodeNum: Int?) {
        reviewDao.deleteReviewForMedia(mediaId, seasonNum, episodeNum)
    }

    override suspend fun getCommunityReviews(
        mediaId: Int,
        seasonNum: Int?,
        episodeNum: Int?
    ): List<dev.sequel.app.data.remote.supabase.dto.SupabaseReviewDto> {
        return supabaseSyncService.fetchReviewsForMedia(mediaId, seasonNum, episodeNum)
    }
}
