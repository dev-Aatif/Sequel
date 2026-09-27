package dev.sequel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.sequel.app.data.local.entity.ReviewEntity
import dev.sequel.app.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewDao {

    // ── Inserts ───────────────────────────────────────────────────

    @Insert
    suspend fun insertReviewInternal(review: ReviewEntity): Long

    // ── Rating Operations (one per show) ─────────────────────────

    /**
     * Upsert a rating for a show. Finds existing rating-only row and updates,
     * or inserts a new one. Ratings are always is_rating_only = true.
     */
    @androidx.room.Transaction
    suspend fun upsertRating(mediaId: Int, mediaType: String, rating: Int) {
        val existing = getRatingRow(mediaId, mediaType)
        if (existing != null) {
            updateRating(existing.id, rating, System.currentTimeMillis())
        } else {
            insertReviewInternal(
                ReviewEntity(
                    mediaId = mediaId,
                    mediaType = mediaType,
                    reviewText = null,
                    rating = rating,
                    isRatingOnly = true,
                    syncStatus = SyncStatus.PENDING
                )
            )
        }
    }

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND is_rating_only = 1 AND sync_status != 'DELETED' LIMIT 1")
    suspend fun getRatingRow(mediaId: Int, mediaType: String): ReviewEntity?

    @Query("UPDATE reviews SET rating = :rating, updated_at = :updatedAt, sync_status = 'PENDING' WHERE id = :id")
    suspend fun updateRating(id: Long, rating: Int, updatedAt: Long)

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND is_rating_only = 1 AND sync_status != 'DELETED' LIMIT 1")
    fun observeRatingForMedia(mediaId: Int, mediaType: String): Flow<ReviewEntity?>

    @Query("UPDATE reviews SET sync_status = 'DELETED' WHERE media_id = :mediaId AND media_type = :mediaType AND is_rating_only = 1")
    suspend fun deleteRating(mediaId: Int, mediaType: String)

    // ── Review Operations (multiple per show) ────────────────────

    /**
     * Insert a new text review. Always creates a new row.
     */
    @androidx.room.Transaction
    suspend fun insertReview(review: ReviewEntity): Long {
        return insertReviewInternal(review.copy(isRatingOnly = false))
    }

    /**
     * Update an existing text review's content.
     */
    @Query("UPDATE reviews SET review_text = :text, is_spoiler = :isSpoiler, updated_at = :updatedAt, sync_status = 'PENDING' WHERE id = :id")
    suspend fun updateReviewText(id: Long, text: String, isSpoiler: Boolean, updatedAt: Long = System.currentTimeMillis())

    /**
     * Get all text reviews for a specific media (excluding rating-only rows).
     */
    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND is_rating_only = 0 AND sync_status != 'DELETED' ORDER BY created_at DESC")
    fun observeReviewsForMedia(mediaId: Int, mediaType: String): Flow<List<ReviewEntity>>

    // ── Legacy: used by sync pull ────────────────────────────────

    @androidx.room.Transaction
    suspend fun upsertReviewPull(newReview: ReviewEntity) {
        if (newReview.isRatingOnly) {
            // For rating rows, match by media_id + media_type + is_rating_only
            val existing = getRatingRow(newReview.mediaId, newReview.mediaType)
            if (existing != null) {
                if (existing.syncStatus == SyncStatus.DELETED) return
                if (existing.syncStatus == SyncStatus.SYNCED || newReview.updatedAt >= existing.updatedAt) {
                    val merged = newReview.copy(id = existing.id, createdAt = existing.createdAt)
                    updateReview(merged)
                }
            } else {
                insertReviewInternal(newReview)
            }
        } else {
            // For text reviews, match by supabase_id
            if (newReview.supabaseId != null) {
                val existing = getReviewBySupabaseId(newReview.supabaseId)
                if (existing != null) {
                    if (existing.syncStatus == SyncStatus.DELETED) return
                    if (existing.syncStatus == SyncStatus.SYNCED || newReview.updatedAt >= existing.updatedAt) {
                        val merged = newReview.copy(id = existing.id, createdAt = existing.createdAt)
                        updateReview(merged)
                    }
                } else {
                    insertReviewInternal(newReview)
                }
            } else {
                insertReviewInternal(newReview)
            }
        }
    }

    @androidx.room.Transaction
    suspend fun upsertReviewsPullTransaction(reviews: List<ReviewEntity>) {
        reviews.forEach { upsertReviewPull(it) }
    }

    @Query("SELECT * FROM reviews WHERE supabase_id = :supabaseId LIMIT 1")
    suspend fun getReviewBySupabaseId(supabaseId: String): ReviewEntity?

    // ── General Queries ──────────────────────────────────────────

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND (season_num = :seasonNum OR (season_num IS NULL AND :seasonNum IS NULL)) AND (episode_num = :episodeNum OR (episode_num IS NULL AND :episodeNum IS NULL))")
    suspend fun getReviewForMediaAndEpisode(mediaId: Int, mediaType: String, seasonNum: Int?, episodeNum: Int?): ReviewEntity?

    // ── Updates ───────────────────────────────────────────────────

    @Update
    suspend fun updateReview(review: ReviewEntity)

    @Query("UPDATE reviews SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: SyncStatus)

    @Query("UPDATE reviews SET sync_status = :status, supabase_id = :supabaseId WHERE id = :id AND sync_status = 'PENDING'")
    suspend fun markAsSynced(id: Long, status: SyncStatus = SyncStatus.SYNCED, supabaseId: String)

    @androidx.room.Transaction
    suspend fun markAsSyncedTransaction(updates: List<Pair<Long, String>>) {
        updates.forEach { markAsSynced(it.first, SyncStatus.SYNCED, it.second) }
    }

    // ── Queries (reactive) ────────────────────────────────────────

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND season_num = :seasonNum AND episode_num = :episodeNum")
    fun observeReviewForEpisode(mediaId: Int, mediaType: String, seasonNum: Int, episodeNum: Int): Flow<ReviewEntity?>

    @Query("SELECT * FROM reviews ORDER BY updated_at DESC")
    fun observeAllReviews(): Flow<List<ReviewEntity>>

    // ── Queries (suspend) ─────────────────────────────────────────

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType")
    suspend fun getReviewForMedia(mediaId: Int, mediaType: String): ReviewEntity?

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND season_num = :seasonNum AND episode_num = :episodeNum")
    suspend fun getReviewForEpisode(mediaId: Int, mediaType: String, seasonNum: Int, episodeNum: Int): ReviewEntity?

    @Query("SELECT * FROM reviews WHERE sync_status != 'SYNCED'")
    suspend fun getUnsynced(): List<ReviewEntity>

    @Query("SELECT * FROM reviews WHERE sync_status = :status")
    suspend fun getByStatus(status: SyncStatus): List<ReviewEntity>

    // ── Deletes ───────────────────────────────────────────────────

    @Query("DELETE FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND (season_num = :seasonNum OR (:seasonNum IS NULL AND season_num IS NULL)) AND (episode_num = :episodeNum OR (:episodeNum IS NULL AND episode_num IS NULL))")
    suspend fun deleteReviewForMedia(mediaId: Int, mediaType: String, seasonNum: Int? = null, episodeNum: Int? = null)

    @Query("UPDATE reviews SET sync_status = 'DELETED' WHERE id = :id")
    suspend fun markReviewDeleted(id: Long)
    
    @Query("DELETE FROM reviews WHERE id = :id")
    suspend fun deleteReviewById(id: Long)
}
