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

    @androidx.room.Transaction
    suspend fun upsertReview(newReview: ReviewEntity) {
        val existing = getReviewForMediaAndEpisode(newReview.mediaId, newReview.mediaType.name, newReview.seasonNum, newReview.episodeNum)
        if (existing != null) {
            val merged = newReview.copy(
                id = existing.id,
                reviewText = newReview.reviewText ?: existing.reviewText,
                createdAt = existing.createdAt
            )
            updateReview(merged)
        } else {
            insertReviewInternal(newReview)
        }
    }

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND (season_num = :seasonNum OR (season_num IS NULL AND :seasonNum IS NULL)) AND (episode_num = :episodeNum OR (episode_num IS NULL AND :episodeNum IS NULL))")
    suspend fun getReviewForMediaAndEpisode(mediaId: Int, mediaType: String, seasonNum: Int?, episodeNum: Int?): ReviewEntity?

    // ── Updates ───────────────────────────────────────────────────

    @Update
    suspend fun updateReview(review: ReviewEntity)

    @Query("UPDATE reviews SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: SyncStatus)

    @Query("UPDATE reviews SET sync_status = :status, supabase_id = :supabaseId WHERE id = :id")
    suspend fun markAsSynced(id: Long, status: SyncStatus = SyncStatus.SYNCED, supabaseId: String)

    // ── Queries (reactive) ────────────────────────────────────────

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType")
    fun observeReviewForMedia(mediaId: Int, mediaType: String): Flow<ReviewEntity?>

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

    // ── Deletes ───────────────────────────────────────────────────

    @Query("DELETE FROM reviews WHERE media_id = :mediaId AND media_type = :mediaType AND (season_num = :seasonNum OR (:seasonNum IS NULL AND season_num IS NULL)) AND (episode_num = :episodeNum OR (:episodeNum IS NULL AND episode_num IS NULL))")
    suspend fun deleteReviewForMedia(mediaId: Int, mediaType: String, seasonNum: Int? = null, episodeNum: Int? = null)

    @Query("UPDATE reviews SET sync_status = 'DELETED' WHERE id = :id")
    suspend fun markReviewDeleted(id: Long)
}
