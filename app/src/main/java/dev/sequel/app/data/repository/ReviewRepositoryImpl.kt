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
    private val syncManager: SyncManager
) : ReviewRepository {

    override suspend fun submitReview(showId: Int, rating: Int, reviewText: String?) {
        // Check if review already exists (update vs insert)
        val existing = reviewDao.getReviewForShow(showId)

        val entity = if (existing != null) {
            existing.copy(
                rating = rating,
                reviewText = reviewText,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING
            )
        } else {
            ReviewEntity(
                showId = showId,
                rating = rating,
                reviewText = reviewText,
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

    override fun observeReview(showId: Int): Flow<ReviewEntity?> =
        reviewDao.observeReviewForShow(showId)

    override fun observeAllReviews(): Flow<List<ReviewEntity>> =
        reviewDao.observeAllReviews()

    override suspend fun deleteReview(showId: Int) {
        reviewDao.deleteReviewForShow(showId)
    }
}
