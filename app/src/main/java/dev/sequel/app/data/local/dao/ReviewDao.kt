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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: ReviewEntity): Long

    // ── Updates ───────────────────────────────────────────────────

    @Update
    suspend fun updateReview(review: ReviewEntity)

    @Query("UPDATE reviews SET sync_status = :status, supabase_id = :supabaseId WHERE id = :id")
    suspend fun markAsSynced(id: Long, status: SyncStatus = SyncStatus.SYNCED, supabaseId: String)

    // ── Queries (reactive) ────────────────────────────────────────

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId")
    fun observeReviewForMedia(mediaId: Int): Flow<ReviewEntity?>

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND season_num = :seasonNum AND episode_num = :episodeNum")
    fun observeReviewForEpisode(mediaId: Int, seasonNum: Int, episodeNum: Int): Flow<ReviewEntity?>

    @Query("SELECT * FROM reviews ORDER BY updated_at DESC")
    fun observeAllReviews(): Flow<List<ReviewEntity>>

    // ── Queries (suspend) ─────────────────────────────────────────

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId")
    suspend fun getReviewForMedia(mediaId: Int): ReviewEntity?

    @Query("SELECT * FROM reviews WHERE media_id = :mediaId AND season_num = :seasonNum AND episode_num = :episodeNum")
    suspend fun getReviewForEpisode(mediaId: Int, seasonNum: Int, episodeNum: Int): ReviewEntity?

    @Query("SELECT * FROM reviews WHERE sync_status != 'SYNCED'")
    suspend fun getUnsynced(): List<ReviewEntity>

    // ── Deletes ───────────────────────────────────────────────────

    @Query("DELETE FROM reviews WHERE media_id = :mediaId AND (season_num = :seasonNum OR (:seasonNum IS NULL AND season_num IS NULL)) AND (episode_num = :episodeNum OR (:episodeNum IS NULL AND episode_num IS NULL))")
    suspend fun deleteReviewForMedia(mediaId: Int, seasonNum: Int? = null, episodeNum: Int? = null)
}
