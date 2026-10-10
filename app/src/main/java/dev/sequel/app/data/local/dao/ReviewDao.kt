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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReviewInternal(review: ReviewEntity): Long

    // ── Unified Operations ─────────────────────────

    @Query("UPDATE reviews SET rating = :rating, updated_at = :updatedAt, sync_status = 'PENDING' WHERE media_id = :mediaId AND media_type = :mediaType AND COALESCE(season_num, -1) = COALESCE(:seasonNum, -1) AND COALESCE(episode_num, -1) = COALESCE(:episodeNum, -1)")
    suspend fun updateRatingOnly(mediaId: Int, mediaType: String, seasonNum: Int?, episodeNum: Int?, rating: Int, updatedAt: Long): Int

    @androidx.room.Transaction
    suspend fun upsertRating(mediaId: Int, mediaType: String, seasonNum: Int?, episodeNum: Int?, rating: Int) {
        val updatedRows = updateRatingOnly(mediaId, mediaType, seasonNum, episodeNum, rating, System.currentTimeMillis())
        if (updatedRows == 0) {
            val id = insertReviewInternal(
                ReviewEntity(
                    mediaId = mediaId,
                    mediaType = mediaType,
                    seasonNum = seasonNum,
                    episodeNum = episodeNum,
                    reviewText = null,
                    rating = rating,
                    isRatingOnly = false,
                    syncStatus = SyncStatus.PENDING
                )
            )
            if (id == -1L) {
                updateRatingOnly(mediaId, mediaType, seasonNum, episodeNum, rating, System.currentTimeMillis())
            }
        }
    }

    @Query("UPDATE reviews SET review_text = :text, is_spoiler = :isSpoiler, is_rating_only = 0, updated_at = :updatedAt, sync_status = 'PENDING' WHERE media_id = :mediaId AND media_type = :mediaType AND COALESCE(season_num, -1) = COALESCE(:seasonNum, -1) AND COALESCE(episode_num, -1) = COALESCE(:episodeNum, -1)")
    suspend fun updateReviewTextOnly(mediaId: Int, mediaType: String, seasonNum: Int?, episodeNum: Int?, text: String, isSpoiler: Boolean, updatedAt: Long): Int

    @androidx.room.Transaction
    suspend fun upsertReviewText(mediaId: Int, mediaType: String, seasonNum: Int?, episodeNum: Int?, text: String, isSpoiler: Boolean) {
        val updatedRows = updateReviewTextOnly(mediaId, mediaType, seasonNum, episodeNum, text, isSpoiler, System.currentTimeMillis())
        if (updatedRows == 0) {
            val id = insertReviewInternal(
                ReviewEntity(
                    mediaId = mediaId,
                    mediaType = mediaType,
                    seasonNum = seasonNum,
                    episodeNum = episodeNum,
                    reviewText = text,
                    rating = null,
                    isSpoiler = isSpoiler,
                    isRatingOnly = false,
                    syncStatus = SyncStatus.PENDING
                )
            )
            if (id == -1L) {
                updateReviewTextOnly(mediaId, mediaType, seasonNum, episodeNum, text, isSpoiler, System.currentTimeMillis())
            }
        }
    }

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND season_num IS NULL AND episode_num IS NULL AND sync_status != 'DELETED' LIMIT 1")
    suspend fun getRatingRow(mediaId: Int, mediaType: String): ReviewEntity?

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND season_num IS NULL AND episode_num IS NULL AND sync_status != 'DELETED' LIMIT 1")
    fun observeRatingForMedia(mediaId: Int, mediaType: String): Flow<ReviewEntity?>

    @Query("UPDATE reviews SET rating = NULL, sync_status = 'PENDING' WHERE media_id = :mediaId AND media_type = :mediaType AND COALESCE(season_num, -1) = COALESCE(:seasonNum, -1) AND COALESCE(episode_num, -1) = COALESCE(:episodeNum, -1)")
    suspend fun deleteRating(mediaId: Int, mediaType: String, seasonNum: Int?, episodeNum: Int?)

    @Query("UPDATE reviews SET rating = NULL, updated_at = :updatedAt WHERE media_id = :mediaId AND media_type = :mediaType")
    suspend fun clearRating(mediaId: Int, mediaType: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND rating IS NULL AND (review_text IS NULL OR review_text = '')")
    suspend fun deleteEmptyReviews(mediaId: Int, mediaType: String)

    // ── Review Operations (multiple per show) ────────────────────

    @Query("UPDATE reviews SET review_text = :text, is_spoiler = :isSpoiler, updated_at = :updatedAt, sync_status = 'PENDING' WHERE id = :id")
    suspend fun updateReviewText(id: Long, text: String, isSpoiler: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND sync_status != 'DELETED' ORDER BY created_at DESC")
    fun observeReviewsForMedia(mediaId: Int, mediaType: String): Flow<List<ReviewEntity>>

    // ── Legacy: used by sync pull ────────────────────────────────

    @androidx.room.Transaction
    suspend fun upsertReviewPull(newReview: ReviewEntity) {
        val existing = getReviewForMediaAndEpisodeIncludingDeleted(newReview.mediaId, newReview.mediaType, newReview.seasonNum, newReview.episodeNum)
        if (existing != null) {
            if (existing.syncStatus == SyncStatus.DELETED || existing.syncStatus == SyncStatus.PENDING) return
            if (existing.syncStatus == SyncStatus.SYNCED || newReview.updatedAt >= existing.updatedAt) {
                val merged = newReview.copy(id = existing.id, createdAt = existing.createdAt)
                updateReview(merged)
            }
        } else {
            insertReviewInternal(newReview)
        }
    }

    @androidx.room.Transaction
    suspend fun upsertReviewsPullTransaction(reviews: List<ReviewEntity>) {
        reviews.forEach { upsertReviewPull(it) }
    }

    @Query("SELECT * FROM reviews WHERE supabase_id = :supabaseId LIMIT 1")
    suspend fun getReviewBySupabaseId(supabaseId: String): ReviewEntity?

    // ── General Queries ──────────────────────────────────────────

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND COALESCE(season_num, -1) = COALESCE(:seasonNum, -1) AND COALESCE(episode_num, -1) = COALESCE(:episodeNum, -1) AND sync_status != 'DELETED' LIMIT 1")
    suspend fun getReviewForMediaAndEpisode(mediaId: Int, mediaType: String, seasonNum: Int?, episodeNum: Int?): ReviewEntity?

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND COALESCE(season_num, -1) = COALESCE(:seasonNum, -1) AND COALESCE(episode_num, -1) = COALESCE(:episodeNum, -1) LIMIT 1")
    suspend fun getReviewForMediaAndEpisodeIncludingDeleted(mediaId: Int, mediaType: String, seasonNum: Int?, episodeNum: Int?): ReviewEntity?

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

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND season_num = :seasonNum AND episode_num = :episodeNum AND sync_status != 'DELETED'")
    fun observeReviewForEpisode(mediaId: Int, mediaType: String, seasonNum: Int, episodeNum: Int): Flow<ReviewEntity?>

    @Query("SELECT * FROM reviews WHERE sync_status != 'DELETED' ORDER BY updated_at DESC")
    fun observeAllReviews(): Flow<List<ReviewEntity>>

    // ── Queries (suspend) ─────────────────────────────────────────

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND sync_status != 'DELETED'")
    suspend fun getReviewForMedia(mediaId: Int, mediaType: String): ReviewEntity?

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND season_num = :seasonNum AND episode_num = :episodeNum AND sync_status != 'DELETED'")
    suspend fun getReviewForEpisode(mediaId: Int, mediaType: String, seasonNum: Int, episodeNum: Int): ReviewEntity?

    @Query("SELECT * FROM reviews WHERE sync_status != 'SYNCED'")
    suspend fun getUnsynced(): List<ReviewEntity>

    @Query("SELECT * FROM reviews WHERE sync_status = :status")
    suspend fun getByStatus(status: SyncStatus): List<ReviewEntity>

    // ── Deletes ───────────────────────────────────────────────────

    @Query("DELETE FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND COALESCE(season_num, -1) = COALESCE(:seasonNum, -1) AND COALESCE(episode_num, -1) = COALESCE(:episodeNum, -1)")
    suspend fun deleteReviewForMedia(mediaId: Int, mediaType: String, seasonNum: Int? = null, episodeNum: Int? = null)

    @Query("UPDATE reviews SET sync_status = 'DELETED' WHERE id = :id")
    suspend fun markReviewDeleted(id: Long)
    
    @Query("DELETE FROM reviews WHERE id = :id")
    suspend fun deleteReviewById(id: Long)
}
