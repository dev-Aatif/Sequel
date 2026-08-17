package dev.sequel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedEpisodeDao {

    // ── Inserts ───────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchedEpisode(watchedEpisode: WatchedEpisodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchedEpisodes(watchedEpisodes: List<WatchedEpisodeEntity>)

    // ── Updates ───────────────────────────────────────────────────

    @Update
    suspend fun updateWatchedEpisode(watchedEpisode: WatchedEpisodeEntity)

    @Query("UPDATE watched_episodes SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: SyncStatus)

    @Query("UPDATE watched_episodes SET sync_status = :status, supabase_id = :supabaseId WHERE id = :id")
    suspend fun markAsSynced(id: Long, status: SyncStatus = SyncStatus.SYNCED, supabaseId: String)

    // ── Queries (reactive) ────────────────────────────────────────

    @Query("SELECT * FROM watched_episodes WHERE show_id = :showId ORDER BY season_number ASC, episode_number ASC")
    fun observeWatchedByShow(showId: Int): Flow<List<WatchedEpisodeEntity>>

    @Query("SELECT * FROM watched_episodes WHERE show_id = :showId AND season_number = :seasonNumber ORDER BY episode_number ASC")
    fun observeWatchedBySeason(showId: Int, seasonNumber: Int): Flow<List<WatchedEpisodeEntity>>

    @Query("SELECT * FROM watched_episodes ORDER BY watched_at DESC LIMIT :limit")
    fun observeRecentlyWatched(limit: Int = 20): Flow<List<WatchedEpisodeEntity>>

    // ── Queries (suspend) ─────────────────────────────────────────

    @Query("SELECT * FROM watched_episodes WHERE sync_status = :status")
    suspend fun getByStatus(status: SyncStatus): List<WatchedEpisodeEntity>

    @Query("SELECT * FROM watched_episodes WHERE sync_status != 'SYNCED'")
    suspend fun getUnsynced(): List<WatchedEpisodeEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM watched_episodes WHERE episode_id = :episodeId)")
    suspend fun isEpisodeWatched(episodeId: Int): Boolean

    @Query("SELECT COUNT(*) FROM watched_episodes WHERE show_id = :showId")
    suspend fun getWatchedCountForShow(showId: Int): Int

    @Query("SELECT COUNT(*) FROM watched_episodes WHERE show_id = :showId AND season_number = :seasonNumber")
    suspend fun getWatchedCountForSeason(showId: Int, seasonNumber: Int): Int

    // ── Deletes ───────────────────────────────────────────────────

    @Query("DELETE FROM watched_episodes WHERE episode_id = :episodeId")
    suspend fun unwatchEpisode(episodeId: Int)

    @Query("DELETE FROM watched_episodes WHERE show_id = :showId")
    suspend fun unwatchAllForShow(showId: Int)
}
