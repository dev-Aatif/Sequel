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

    @Query("SELECT * FROM watched_episodes WHERE show_id = :showId AND sync_status != 'DELETED' ORDER BY season_number ASC, episode_number ASC")
    fun observeWatchedByShow(showId: Int): Flow<List<WatchedEpisodeEntity>>

    @Query("SELECT * FROM watched_episodes WHERE show_id = :showId AND season_number = :seasonNumber AND sync_status != 'DELETED' ORDER BY episode_number ASC")
    fun observeWatchedBySeason(showId: Int, seasonNumber: Int): Flow<List<WatchedEpisodeEntity>>

    @Query("SELECT * FROM watched_episodes WHERE sync_status != 'DELETED' ORDER BY watched_at DESC LIMIT :limit")
    fun observeRecentlyWatched(limit: Int = 20): Flow<List<WatchedEpisodeEntity>>

    // ── Queries (suspend) ─────────────────────────────────────────

    @Query("SELECT * FROM watched_episodes WHERE sync_status = :status")
    suspend fun getByStatus(status: SyncStatus): List<WatchedEpisodeEntity>

    @Query("SELECT * FROM watched_episodes WHERE sync_status IN ('PENDING', 'DELETED')")
    suspend fun getUnsynced(): List<WatchedEpisodeEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM watched_episodes WHERE episode_id = :episodeId AND sync_status != 'DELETED')")
    suspend fun isEpisodeWatched(episodeId: Int): Boolean

    @Query("SELECT COUNT(*) FROM watched_episodes WHERE show_id = :showId AND sync_status != 'DELETED'")
    suspend fun getWatchedCountForShow(showId: Int): Int

    @Query("SELECT COUNT(*) FROM watched_episodes WHERE show_id = :showId AND season_number = :seasonNumber AND sync_status != 'DELETED'")
    suspend fun getWatchedCountForSeason(showId: Int, seasonNumber: Int): Int

    // ── Deletes ───────────────────────────────────────────────────

    @Query("UPDATE watched_episodes SET sync_status = 'DELETED' WHERE episode_id = :episodeId")
    suspend fun unwatchEpisode(episodeId: Int)

    @Query("UPDATE watched_episodes SET sync_status = 'DELETED' WHERE show_id = :showId")
    suspend fun unwatchAllForShow(showId: Int)

    @Query("DELETE FROM watched_episodes WHERE id = :id")
    suspend fun deleteEpisodeById(id: Long)

    // ── Stats Queries ─────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM watched_episodes WHERE media_type = 'TV' AND sync_status != 'DELETED'")
    fun observeTotalEpisodesWatched(): Flow<Int>

    @Query("SELECT COUNT(*) FROM watched_episodes WHERE media_type = 'MOVIE' AND sync_status != 'DELETED'")
    fun observeTotalMoviesWatched(): Flow<Int>

    @Query("""
        SELECT 
            (SELECT COALESCE(SUM(e.runtime), 0) FROM watched_episodes we JOIN episodes e ON we.episode_id = e.id WHERE we.media_type = 'TV' AND we.sync_status != 'DELETED') +
            (SELECT COALESCE(SUM(s.runtime), 0) FROM watched_episodes we JOIN shows s ON we.show_id = s.id WHERE we.media_type = 'MOVIE' AND we.sync_status != 'DELETED')
    """)
    fun observeTotalRuntimeMinutes(): Flow<Int>

    // ── Watched Tab Queries ─────────────────────────────────────────

    /** Get all distinct show IDs that have at least one watched episode (TV only). */
    @Query("""
        SELECT DISTINCT we.show_id FROM watched_episodes we 
        WHERE we.media_type = 'TV' AND we.sync_status != 'DELETED'
    """)
    fun observeWatchedTvShowIds(): Flow<List<Int>>

    /** Get all watched movies (show_id from watched_episodes where media_type = MOVIE). */
    @Query("""
        SELECT DISTINCT we.show_id FROM watched_episodes we 
        WHERE we.media_type = 'MOVIE' AND we.sync_status != 'DELETED'
    """)
    fun observeWatchedMovieIds(): Flow<List<Int>>
}
