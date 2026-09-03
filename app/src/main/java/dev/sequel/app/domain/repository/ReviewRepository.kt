package dev.sequel.app.domain.repository

import dev.sequel.app.data.local.entity.ReviewEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for show reviews.
 */
interface ReviewRepository {

    /** Submit or update a review. Saves to Room and triggers sync. */
    suspend fun submitReview(
        mediaId: Int,
        seasonNum: Int? = null,
        episodeNum: Int? = null,
        reviewText: String?,
        rating: Int?,
        isSpoiler: Boolean
    )

    /** Observe the review for a specific media (movie or show). */
    fun observeReviewForMedia(mediaId: Int): Flow<ReviewEntity?>

    /** Observe the review for a specific episode. */
    fun observeReviewForEpisode(mediaId: Int, seasonNum: Int, episodeNum: Int): Flow<ReviewEntity?>

    /** Observe all user reviews. */
    fun observeAllReviews(): Flow<List<ReviewEntity>>

    /** Delete a review. */
    suspend fun deleteReview(mediaId: Int, seasonNum: Int? = null, episodeNum: Int? = null)

    /** Fetch community reviews from Supabase. */
    suspend fun getCommunityReviews(mediaId: Int, seasonNum: Int? = null, episodeNum: Int? = null): List<dev.sequel.app.data.remote.supabase.dto.SupabaseReviewDto>
}
