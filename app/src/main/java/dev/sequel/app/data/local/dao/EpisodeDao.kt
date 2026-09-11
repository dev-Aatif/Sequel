package dev.sequel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.sequel.app.data.local.entity.EpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("SELECT * FROM episodes WHERE show_id = :showId AND season_number = :seasonNumber ORDER BY episode_number ASC")
    fun observeEpisodesBySeason(showId: Int, seasonNumber: Int): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE show_id = :showId AND season_number = :seasonNumber ORDER BY episode_number ASC")
    suspend fun getEpisodesBySeason(showId: Int, seasonNumber: Int): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    suspend fun getEpisodeById(episodeId: Int): EpisodeEntity?

    @Query("SELECT COUNT(*) FROM episodes WHERE show_id = :showId")
    suspend fun getEpisodeCountForShow(showId: Int): Int

    @Query("DELETE FROM episodes WHERE show_id = :showId AND season_number = :seasonNumber")
    suspend fun deleteEpisodesBySeason(showId: Int, seasonNumber: Int)

    @Query("""
        SELECT * FROM episodes 
        WHERE show_id = :showId 
        AND (
            season_number > (SELECT COALESCE(MAX(season_number), 0) FROM watched_episodes WHERE show_id = :showId AND episode_id IS NOT NULL)
            OR (
                season_number = (SELECT COALESCE(MAX(season_number), 0) FROM watched_episodes WHERE show_id = :showId AND episode_id IS NOT NULL)
                AND episode_number > (
                    SELECT COALESCE(MAX(episode_number), 0) FROM watched_episodes 
                    WHERE show_id = :showId AND episode_id IS NOT NULL 
                    AND season_number = (SELECT MAX(season_number) FROM watched_episodes WHERE show_id = :showId AND episode_id IS NOT NULL)
                )
            )
        )
        ORDER BY season_number ASC, episode_number ASC 
        LIMIT 1
    """)
    fun observeCanonicalNextEpisode(showId: Int): Flow<EpisodeEntity?>
}
