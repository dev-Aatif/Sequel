package dev.sequel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrendingShowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trendingShows: List<dev.sequel.app.data.local.entity.TrendingShowEntity>)

    @Query("DELETE FROM trending_shows WHERE mediaType = :mediaType")
    suspend fun clearTrendingByMediaType(mediaType: String)
}
