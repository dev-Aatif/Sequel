package dev.sequel.app.domain.repository

import dev.sequel.app.data.local.entity.ReviewEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for show reviews.
 */
interface ReviewRepository {

    /** Submit or update a review for a show. Saves to Room and triggers sync. */
    suspend fun submitReview(showId: Int, rating: Int, reviewText: String?)

    /** Observe the review for a specific show. */
    fun observeReview(showId: Int): Flow<ReviewEntity?>

    /** Observe all user reviews. */
    fun observeAllReviews(): Flow<List<ReviewEntity>>

    /** Delete a review for a show. */
    suspend fun deleteReview(showId: Int)
}
