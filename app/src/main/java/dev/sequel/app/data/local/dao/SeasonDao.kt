package dev.sequel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.sequel.app.data.local.entity.SeasonEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SeasonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeasons(seasons: List<SeasonEntity>)

    @Query("SELECT * FROM seasons WHERE show_id = :showId ORDER BY season_number ASC")
    fun observeSeasonsByShow(showId: Int): Flow<List<SeasonEntity>>

    @Query("SELECT * FROM seasons WHERE show_id = :showId ORDER BY season_number ASC")
    suspend fun getSeasonsByShow(showId: Int): List<SeasonEntity>

    @Query("DELETE FROM seasons WHERE show_id = :showId")
    suspend fun deleteSeasonsByShow(showId: Int)
}
