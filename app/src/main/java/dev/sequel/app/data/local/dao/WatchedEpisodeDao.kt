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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWatchedEpisode(watchedEpisode: WatchedEpisodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWatchedEpisodes(watchedEpisodes: List<WatchedEpisodeEntity>)

    // ── Updates ───────────────────────────────────────────────────

    @Update
    suspend fun updateWatchedEpisode(watchedEpisode: WatchedEpisodeEntity)

    @Query("UPDATE watched_episodes SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: SyncStatus)

    @Query("UPDATE watched_episodes SET sync_status = :status, supabase_id = :supabaseId WHERE id = :id AND sync_status = 'PENDING'")
    suspend fun markAsSynced(id: Long, status: SyncStatus = SyncStatus.SYNCED, supabaseId: String)

    @androidx.room.Transaction
    suspend fun markAsSyncedTransaction(updates: List<Pair<Long, String>>) {
        updates.forEach { markAsSynced(it.first, SyncStatus.SYNCED, it.second) }
    }

    @Query("""
        INSERT INTO watched_episodes (media_type, show_id, episode_id, season_number, episode_number, watched_at, sync_status, is_skipped)
        VALUES (:mediaType, :showId, :episodeId, :seasonNumber, :episodeNumber, :watchedAt, 'PENDING', :isSkipped)
        ON CONFLICT(show_id, media_type, season_number, episode_number) DO UPDATE SET
            sync_status = 'PENDING',
            watched_at = :watchedAt,
            is_skipped = :isSkipped
    """)
    suspend fun upsertWatchedEpisode(
        mediaType: dev.sequel.app.data.local.entity.MediaType,
        showId: Int,
        episodeId: Int?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        watchedAt: Long = System.currentTimeMillis(),
        isSkipped: Boolean = false
    )

    @Query("""
        INSERT INTO watched_episodes (media_type, show_id, episode_id, season_number, episode_number, watched_at, sync_status, supabase_id, is_skipped)
        VALUES (:mediaType, :showId, :episodeId, :seasonNumber, :episodeNumber, :watchedAt, 'SYNCED', :supabaseId, :isSkipped)
        ON CONFLICT(show_id, media_type, season_number, episode_number) DO UPDATE SET
            sync_status = CASE WHEN sync_status = 'DELETED' THEN 'DELETED' WHEN sync_status = 'PENDING' AND watched_at >= :watchedAt THEN 'PENDING' ELSE 'SYNCED' END,
            supabase_id = :supabaseId,
            watched_at = CASE WHEN sync_status = 'DELETED' THEN watched_at WHEN sync_status = 'PENDING' AND watched_at >= :watchedAt THEN watched_at ELSE :watchedAt END,
            is_skipped = CASE WHEN sync_status = 'DELETED' THEN is_skipped WHEN sync_status = 'PENDING' AND watched_at >= :watchedAt THEN is_skipped ELSE :isSkipped END
    """)
    suspend fun upsertWatchedEpisodePull(
        mediaType: dev.sequel.app.data.local.entity.MediaType,
        showId: Int,
        episodeId: Int?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        watchedAt: Long,
        supabaseId: String?,
        isSkipped: Boolean = false
    )

    @androidx.room.Transaction
    suspend fun upsertWatchedEpisodesPullTransaction(episodes: List<WatchedEpisodeEntity>) {
        episodes.forEach {
            upsertWatchedEpisodePull(
                mediaType = it.mediaType,
                showId = it.showId,
                episodeId = it.episodeId,
                seasonNumber = it.seasonNumber,
                episodeNumber = it.episodeNumber,
                watchedAt = it.watchedAt,
                supabaseId = it.supabaseId,
                isSkipped = it.isSkipped
            )
        }
    }

    // ── Queries (reactive) ────────────────────────────────────────

    @Query("SELECT * FROM watched_episodes WHERE show_id = :showId AND media_type = :mediaType AND sync_status != 'DELETED' ORDER BY season_number ASC, episode_number ASC")
    fun observeWatchedByShow(showId: Int, mediaType: String): Flow<List<WatchedEpisodeEntity>>

    @Query("SELECT * FROM watched_episodes WHERE show_id = :showId AND season_number = :seasonNumber AND sync_status != 'DELETED' ORDER BY episode_number ASC")
    fun observeWatchedBySeason(showId: Int, seasonNumber: Int): Flow<List<WatchedEpisodeEntity>>

    @Query("SELECT * FROM watched_episodes WHERE sync_status != 'DELETED' ORDER BY watched_at DESC LIMIT :limit")
    fun observeRecentlyWatched(limit: Int = 20): Flow<List<WatchedEpisodeEntity>>

    @Query("""
        SELECT 
            we.id as historyId,
            we.watched_at as watchedAt,
            we.media_type as mediaType,
            we.show_id as showId,
            s.title as showTitle,
            s.poster_path as posterPath,
            we.season_number as seasonNumber,
            we.episode_number as episodeNumber
        FROM watched_episodes we
        INNER JOIN shows s ON we.show_id = s.id
        WHERE we.sync_status != 'DELETED'
        ORDER BY we.watched_at DESC
        LIMIT :limit
    """)
    fun observeRecentActivity(limit: Int = 20): Flow<List<dev.sequel.app.data.local.entity.RecentActivityItem>>

    // ── Queries (suspend) ─────────────────────────────────────────

    @Query("SELECT * FROM watched_episodes WHERE sync_status = :status")
    suspend fun getByStatus(status: SyncStatus): List<WatchedEpisodeEntity>

    @Query("SELECT * FROM watched_episodes WHERE sync_status IN ('PENDING', 'DELETED')")
    suspend fun getUnsynced(): List<WatchedEpisodeEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM watched_episodes WHERE episode_id = :episodeId AND sync_status != 'DELETED')")
    suspend fun isEpisodeWatched(episodeId: Int): Boolean

    @Query("SELECT COUNT(*) FROM watched_episodes WHERE show_id = :showId AND media_type = :mediaType AND sync_status != 'DELETED'")
    suspend fun getWatchedCountForShow(showId: Int, mediaType: String): Int

    @Query("SELECT COUNT(*) FROM watched_episodes WHERE show_id = :showId AND season_number = :seasonNumber AND sync_status != 'DELETED'")
    suspend fun getWatchedCountForSeason(showId: Int, seasonNumber: Int): Int

    // ── Deletes ───────────────────────────────────────────────────

    @Query("UPDATE watched_episodes SET sync_status = 'DELETED' WHERE episode_id = :episodeId")
    suspend fun unwatchEpisode(episodeId: Int)

    @Query("UPDATE watched_episodes SET sync_status = 'DELETED' WHERE show_id = :showId AND season_number = :seasonNumber")
    suspend fun unwatchSeason(showId: Int, seasonNumber: Int)

    @Query("UPDATE watched_episodes SET sync_status = 'DELETED' WHERE show_id = :showId AND media_type = :mediaType")
    suspend fun unwatchAllForShow(showId: Int, mediaType: String)

    @Query("DELETE FROM watched_episodes WHERE id = :id")
    suspend fun deleteEpisodeById(id: Long)

    // ── Stats Queries ─────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM watched_episodes WHERE media_type = 'tv' AND sync_status != 'DELETED'")
    fun observeTotalEpisodesWatched(): Flow<Int>

    @Query("SELECT COUNT(*) FROM watched_episodes WHERE media_type = 'movie' AND sync_status != 'DELETED'")
    fun observeTotalMoviesWatched(): Flow<Int>

    @Query("""
        SELECT 
            (SELECT COALESCE(SUM(e.runtime), 0) FROM watched_episodes we JOIN episodes e ON we.episode_id = e.id WHERE we.media_type = 'tv' AND we.sync_status != 'DELETED') +
            (SELECT COALESCE(SUM(s.runtime), 0) FROM watched_episodes we JOIN shows s ON we.show_id = s.id WHERE we.media_type = 'movie' AND we.sync_status != 'DELETED')
    """)
    fun observeTotalRuntimeMinutes(): Flow<Int>

    // ── Watched Tab Queries ─────────────────────────────────────────

    /** Get all distinct show IDs that have at least one watched episode (TV only). */
    @Query("""
        SELECT DISTINCT we.show_id FROM watched_episodes we 
        WHERE we.media_type = 'tv' AND we.sync_status != 'DELETED'
    """)
    fun observeWatchedTvShowIds(): Flow<List<Int>>

    /** Get all watched movies (show_id from watched_episodes where media_type = MOVIE). */
    @Query("""
        SELECT DISTINCT we.show_id FROM watched_episodes we 
        WHERE we.media_type = 'movie' AND we.sync_status != 'DELETED'
    """)
    fun observeWatchedMovieIds(): Flow<List<Int>>
}
