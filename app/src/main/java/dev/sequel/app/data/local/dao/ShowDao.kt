package dev.sequel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.sequel.app.data.local.entity.ShowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShowDao {

    // ── Inserts ───────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShow(show: ShowEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShows(shows: List<ShowEntity>)

    // ── Updates ───────────────────────────────────────────────────

    @Update
    suspend fun updateShow(show: ShowEntity)

    @Query("UPDATE shows SET is_favorite = :isFavorite WHERE id = :showId")
    suspend fun updateFavoriteStatus(showId: Int, isFavorite: Boolean)

    @Query("UPDATE shows SET is_in_watchlist = :isInWatchlist WHERE id = :showId")
    suspend fun updateWatchlistStatus(showId: Int, isInWatchlist: Boolean)

    // ── Queries (reactive) ────────────────────────────────────────

    @Query("SELECT * FROM shows WHERE id = :showId")
    fun observeShowById(showId: Int): Flow<ShowEntity?>

    @Query("SELECT * FROM shows WHERE media_type = :mediaType ORDER BY last_updated DESC")
    fun observeShowsByType(mediaType: String): Flow<List<ShowEntity>>

    @Query("SELECT * FROM shows WHERE is_favorite = 1 ORDER BY title ASC")
    fun observeFavorites(): Flow<List<ShowEntity>>

    @Query("SELECT * FROM shows WHERE is_in_watchlist = 1 ORDER BY title ASC")
    fun observeWatchlist(): Flow<List<ShowEntity>>

    @Query("SELECT * FROM shows WHERE title LIKE '%' || :query || '%' ORDER BY vote_average DESC")
    fun searchShows(query: String): Flow<List<ShowEntity>>

    // ── Queries (suspend) ─────────────────────────────────────────

    @Query("SELECT * FROM shows WHERE id = :showId")
    suspend fun getShowById(showId: Int): ShowEntity?

    // ── Deletes ───────────────────────────────────────────────────

    @Query("DELETE FROM shows WHERE id = :showId")
    suspend fun deleteShow(showId: Int)

    @Query("DELETE FROM shows WHERE media_type = :mediaType AND is_favorite = 0 AND is_in_watchlist = 0")
    suspend fun clearNonTrackedShows(mediaType: String)

    @Query("DELETE FROM shows")
    suspend fun clearAllShows()

    // ── Paging ────────────────────────────────────────────────────

    @androidx.room.Query("SELECT * FROM shows")
    fun getPagingShows(): androidx.paging.PagingSource<Int, ShowEntity>
}
