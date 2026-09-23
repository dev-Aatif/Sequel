package dev.sequel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.sequel.app.data.local.entity.WatchlistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToWatchlist(entity: WatchlistEntity)

    @Query("UPDATE watchlist SET sync_status = 'DELETED' WHERE tmdb_id = :tmdbId AND media_type = :mediaType")
    suspend fun removeFromWatchlist(tmdbId: Int, mediaType: String)

    @Query("DELETE FROM watchlist WHERE tmdb_id = :tmdbId AND media_type = :mediaType")
    suspend fun deleteWatchlistById(tmdbId: Int, mediaType: String)

    @Query("""
        SELECT * FROM watchlist w
        WHERE sync_status != 'DELETED' 
        AND NOT EXISTS (
            SELECT 1 FROM watched_episodes we WHERE we.show_id = w.tmdb_id AND we.sync_status != 'DELETED'
        )
        ORDER BY added_at DESC
    """)
    fun observeWatchlist(): Flow<List<WatchlistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE tmdb_id = :tmdbId AND media_type = :mediaType AND sync_status != 'DELETED')")
    fun observeIsInWatchlist(tmdbId: Int, mediaType: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE tmdb_id = :tmdbId AND media_type = :mediaType AND sync_status != 'DELETED')")
    suspend fun isInWatchlist(tmdbId: Int, mediaType: String): Boolean

    @Query("SELECT * FROM watchlist WHERE media_type = 'tv' AND sync_status != 'DELETED'")
    suspend fun getAllWatchlistTvShows(): List<WatchlistEntity>

    @Query("SELECT * FROM watchlist WHERE sync_status IN ('PENDING', 'DELETED')")
    suspend fun getPendingWatchlist(): List<WatchlistEntity>

    @Query("UPDATE watchlist SET sync_status = 'SYNCED' WHERE tmdb_id IN (:tmdbIds)")
    suspend fun markWatchlistSynced(tmdbIds: List<Int>)
}
