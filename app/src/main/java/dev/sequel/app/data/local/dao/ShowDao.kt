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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertShow(show: ShowEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertShows(shows: List<ShowEntity>)

    // ── Updates ───────────────────────────────────────────────────

    @Update
    suspend fun updateShow(show: ShowEntity)

    @Query("UPDATE shows SET title = :title, overview = :overview, poster_path = :posterPath, backdrop_path = :backdropPath, vote_average = :voteAverage, last_updated = :lastUpdated WHERE id = :id AND media_type = :mediaType")
    suspend fun updateShowApiData(id: Int, mediaType: String, title: String, overview: String, posterPath: String?, backdropPath: String?, voteAverage: Double, lastUpdated: Long)


    @Query("UPDATE shows SET is_favorite = :isFavorite WHERE id = :showId AND media_type = :mediaType")
    suspend fun updateFavoriteStatus(showId: Int, mediaType: String, isFavorite: Boolean)

    @Query("UPDATE shows SET is_in_watchlist = :isInWatchlist WHERE id = :showId AND media_type = :mediaType")
    suspend fun updateWatchlistStatus(showId: Int, mediaType: String, isInWatchlist: Boolean)

    // ── Queries (reactive) ────────────────────────────────────────

    @Query("SELECT * FROM shows WHERE id = :showId AND media_type = :mediaType")
    fun observeShowById(showId: Int, mediaType: String): Flow<ShowEntity?>

    @Query("SELECT * FROM shows WHERE media_type = :mediaType ORDER BY last_updated DESC")
    fun observeShowsByType(mediaType: String): Flow<List<ShowEntity>>

    @Query("SELECT * FROM shows WHERE is_favorite = 1 ORDER BY title ASC")
    fun observeFavorites(): Flow<List<ShowEntity>>

    @Query("SELECT * FROM shows WHERE is_in_watchlist = 1 ORDER BY title ASC")
    fun observeWatchlist(): Flow<List<ShowEntity>>

    @Query("SELECT * FROM shows WHERE title LIKE '%' || :query || '%' ORDER BY vote_average DESC")
    fun searchShows(query: String): Flow<List<ShowEntity>>

    @Query("SELECT s.* FROM shows s INNER JOIN trending_shows t ON s.id = t.showId WHERE t.mediaType = :mediaType ORDER BY t.page ASC, t.position ASC LIMIT :limit")
    fun observeTrendingShows(mediaType: String, limit: Int): Flow<List<ShowEntity>>

    @Query("""
        SELECT s.* FROM shows s
        WHERE s.media_type = 'tv' 
        AND EXISTS (SELECT 1 FROM watched_episodes w WHERE w.show_id = s.id)
    """)
    fun observeStartedTvShows(): Flow<List<ShowEntity>>

    @Query("""
        WITH WatchedCount AS (
            SELECT show_id, 
                   COUNT(episode_id) AS watched_count
            FROM watched_episodes
            WHERE episode_id IS NOT NULL AND sync_status != 'DELETED'
            GROUP BY show_id
        ),
        LatestWatched AS (
            SELECT show_id, season_number, episode_number
            FROM watched_episodes we1
            WHERE episode_id IS NOT NULL AND sync_status != 'DELETED'
              AND NOT EXISTS (
                  SELECT 1 FROM watched_episodes we2
                  WHERE we2.show_id = we1.show_id
                    AND we2.episode_id IS NOT NULL AND we2.sync_status != 'DELETED'
                    AND (we2.season_number > we1.season_number OR (we2.season_number = we1.season_number AND we2.episode_number > we1.episode_number))
              )
            GROUP BY show_id
        )
        SELECT s.*, 
               e.id AS ep_id, e.show_id AS ep_show_id, e.name AS ep_name, e.season_number AS ep_season_number, e.episode_number AS ep_episode_number, e.overview AS ep_overview, e.still_path AS ep_still_path, e.runtime AS ep_runtime, e.air_date AS ep_air_date,
               COALESCE(wc.watched_count, 0) AS watchedCount
        FROM shows s
        LEFT JOIN WatchedCount wc ON s.id = wc.show_id
        LEFT JOIN LatestWatched lw ON s.id = lw.show_id
        LEFT JOIN episodes e ON e.id = (
            SELECT id FROM episodes e2
            WHERE e2.show_id = s.id
            AND e2.id NOT IN (
                SELECT episode_id FROM watched_episodes we WHERE we.show_id = s.id AND we.episode_id IS NOT NULL AND we.sync_status != 'DELETED'
            )
            AND (
                e2.season_number > lw.season_number 
                OR (e2.season_number = lw.season_number AND e2.episode_number > lw.episode_number)
            )
            ORDER BY e2.season_number ASC, e2.episode_number ASC
            LIMIT 1
        )
        WHERE s.id IN (
            SELECT DISTINCT show_id FROM watched_episodes WHERE media_type = 'TV' AND sync_status != 'DELETED'
        )
        ORDER BY s.title ASC
    """)
    fun observeUpNextShows(): Flow<List<dev.sequel.app.data.local.entity.UpNextShowTuple>>

    @Query("""
        SELECT s.* FROM shows s
        WHERE s.media_type = 'movie' AND (s.is_in_watchlist = 1 OR s.is_favorite = 1)
        AND NOT EXISTS (SELECT 1 FROM watched_episodes w WHERE w.show_id = s.id)
    """)
    fun observeUnwatchedTrackedMovies(): Flow<List<ShowEntity>>

    // ── Queries (suspend) ─────────────────────────────────────────

    @Query("""
        SELECT s.* FROM shows s
        WHERE s.media_type = 'tv' 
        AND EXISTS (SELECT 1 FROM watched_episodes w WHERE w.show_id = s.id)
    """)
    suspend fun getStartedTvShows(): List<ShowEntity>

    @Query("SELECT * FROM shows WHERE id = :showId AND media_type = :mediaType")
    suspend fun getShowById(showId: Int, mediaType: String): ShowEntity?

    // ── Deletes ───────────────────────────────────────────────────

    @Query("DELETE FROM shows WHERE id = :showId AND media_type = :mediaType")
    suspend fun deleteShow(showId: Int, mediaType: String)

    @Query("DELETE FROM shows WHERE media_type = :mediaType AND is_favorite = 0 AND is_in_watchlist = 0")
    suspend fun clearNonTrackedShows(mediaType: String)

    @Query("DELETE FROM shows WHERE media_type = :mediaType")
    suspend fun clearShowsByMediaType(mediaType: String)

    // ── Paging ────────────────────────────────────────────────────

    @androidx.room.Query("SELECT s.* FROM shows s INNER JOIN trending_shows t ON s.id = t.showId WHERE t.mediaType = :mediaType ORDER BY t.page ASC, t.position ASC")
    fun getPagingShows(mediaType: String): androidx.paging.PagingSource<Int, ShowEntity>

    @Query("SELECT COUNT(*) FROM shows WHERE media_type = :mediaType")
    suspend fun getShowsCountByMediaType(mediaType: String): Int

    // ── Watched Tab ───────────────────────────────────────────────

    @Query("""
        SELECT s.*,
               (SELECT COUNT(we.id) FROM watched_episodes we WHERE we.show_id = s.id AND we.episode_id IS NOT NULL AND we.sync_status != 'DELETED') AS watchedCount,
               EXISTS (
                   SELECT 1 FROM episodes e 
                   WHERE e.show_id = s.id 
                   AND e.id NOT IN (
                       SELECT episode_id FROM watched_episodes we2 
                       WHERE we2.show_id = s.id AND we2.episode_id IS NOT NULL AND we2.sync_status != 'DELETED'
                   )
               ) AS hasUnwatchedEpisodes
        FROM shows s
        WHERE s.id IN (SELECT DISTINCT show_id FROM watched_episodes WHERE media_type = 'TV' AND sync_status != 'DELETED')
        ORDER BY s.title ASC
    """)
    fun observeWatchedTvShows(): Flow<List<dev.sequel.app.data.local.entity.WatchedTvShowTuple>>

    @Query("""
        SELECT s.*
        FROM shows s
        WHERE s.id IN (SELECT DISTINCT show_id FROM watched_episodes WHERE media_type = 'MOVIE' AND sync_status != 'DELETED')
        ORDER BY s.title ASC
    """)
    fun observeWatchedMovies(): Flow<List<ShowEntity>>
}
