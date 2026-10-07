package dev.sequel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchedMovieEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchedMovieDao {

    // ── Inserts ───────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWatchedMovie(movie: WatchedMovieEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWatchedMovies(movies: List<WatchedMovieEntity>)

    // ── Updates ───────────────────────────────────────────────────

    @Update
    suspend fun updateWatchedMovie(movie: WatchedMovieEntity)

    @Query("UPDATE watched_movies SET sync_status = :status WHERE tmdb_movie_id = :movieId")
    suspend fun updateSyncStatus(movieId: Int, status: SyncStatus)

    @Query("UPDATE watched_movies SET sync_status = :status, supabase_id = :supabaseId WHERE tmdb_movie_id = :movieId AND sync_status = 'PENDING'")
    suspend fun markAsSynced(movieId: Int, status: SyncStatus = SyncStatus.SYNCED, supabaseId: String)

    @androidx.room.Transaction
    suspend fun markAsSyncedTransaction(updates: List<Pair<Int, String>>) {
        updates.forEach { markAsSynced(it.first, SyncStatus.SYNCED, it.second) }
    }

    @Query("""
        INSERT INTO watched_movies (tmdb_movie_id, title, poster_path, watched_at, sync_status)
        VALUES (:movieId, :title, :posterPath, :watchedAt, 'PENDING')
        ON CONFLICT(tmdb_movie_id) DO UPDATE SET
            sync_status = 'PENDING',
            watched_at = :watchedAt,
            title = :title,
            poster_path = :posterPath
    """)
    suspend fun upsertWatchedMovie(
        movieId: Int,
        title: String,
        posterPath: String?,
        watchedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        INSERT INTO watched_movies (tmdb_movie_id, title, poster_path, watched_at, sync_status, supabase_id)
        VALUES (:movieId, :title, :posterPath, :watchedAt, 'SYNCED', :supabaseId)
        ON CONFLICT(tmdb_movie_id) DO UPDATE SET
            sync_status = CASE WHEN sync_status = 'DELETED' THEN 'DELETED' WHEN sync_status = 'PENDING' AND watched_at >= :watchedAt THEN 'PENDING' ELSE 'SYNCED' END,
            supabase_id = :supabaseId,
            watched_at = CASE WHEN sync_status = 'DELETED' THEN watched_at WHEN sync_status = 'PENDING' AND watched_at >= :watchedAt THEN watched_at ELSE :watchedAt END,
            title = CASE WHEN sync_status = 'DELETED' THEN title ELSE :title END,
            poster_path = CASE WHEN sync_status = 'DELETED' THEN poster_path ELSE :posterPath END
    """)
    suspend fun upsertWatchedMoviePull(
        movieId: Int,
        title: String,
        posterPath: String?,
        watchedAt: Long,
        supabaseId: String?
    )

    @androidx.room.Transaction
    suspend fun upsertWatchedMoviesPullTransaction(movies: List<WatchedMovieEntity>) {
        movies.forEach {
            upsertWatchedMoviePull(
                movieId = it.tmdbMovieId,
                title = it.title,
                posterPath = it.posterPath,
                watchedAt = it.watchedAt,
                supabaseId = it.supabaseId
            )
        }
    }

    // ── Queries (reactive) ────────────────────────────────────────

    @Query("SELECT * FROM watched_movies WHERE sync_status != 'DELETED' ORDER BY watched_at DESC LIMIT :limit")
    fun observeRecentlyWatchedMovies(limit: Int = 20): Flow<List<WatchedMovieEntity>>

    @Query("SELECT COUNT(*) FROM watched_movies WHERE sync_status != 'DELETED'")
    fun observeTotalMoviesWatched(): Flow<Int>

    @Query("SELECT * FROM watched_movies WHERE sync_status != 'DELETED' ORDER BY title ASC")
    fun observeAllWatchedMovies(): Flow<List<WatchedMovieEntity>>

    // ── Queries (suspend) ─────────────────────────────────────────

    @Query("SELECT * FROM watched_movies WHERE sync_status = :status")
    suspend fun getByStatus(status: SyncStatus): List<WatchedMovieEntity>

    @Query("SELECT * FROM watched_movies WHERE sync_status IN ('PENDING', 'DELETED')")
    suspend fun getUnsynced(): List<WatchedMovieEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM watched_movies WHERE tmdb_movie_id = :movieId AND sync_status != 'DELETED')")
    suspend fun isMovieWatched(movieId: Int): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM watched_movies WHERE tmdb_movie_id = :movieId AND sync_status != 'DELETED')")
    fun observeIsMovieWatched(movieId: Int): Flow<Boolean>

    // ── Deletes ───────────────────────────────────────────────────

    @Query("UPDATE watched_movies SET sync_status = 'DELETED' WHERE tmdb_movie_id = :movieId")
    suspend fun unwatchMovie(movieId: Int)

    @Query("DELETE FROM watched_movies WHERE tmdb_movie_id = :movieId")
    suspend fun deleteMovieById(movieId: Int)

    // ── Runtime Stats ─────────────────────────────────────────────

    @Query("""
        SELECT COALESCE(SUM(s.runtime), 0) 
        FROM watched_movies wm 
        JOIN shows s ON wm.tmdb_movie_id = s.id AND s.media_type = 'movie'
        WHERE wm.sync_status != 'DELETED'
    """)
    fun observeMovieRuntimeMinutes(): Flow<Int>
}
