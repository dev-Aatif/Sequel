package dev.sequel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.sequel.app.data.local.entity.RemoteKeys

@Dao
interface RemoteKeysDao {

    @Query("SELECT COUNT(*) FROM remote_keys")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(remoteKey: List<RemoteKeys>)

    @Query("SELECT * FROM remote_keys WHERE showId = :showId AND mediaType = :mediaType")
    suspend fun remoteKeysShowId(showId: Int, mediaType: String): RemoteKeys?

    @Query("DELETE FROM remote_keys WHERE showId = :showId AND mediaType = :mediaType")
    suspend fun deleteByShowId(showId: Int, mediaType: String)

    @Query("DELETE FROM remote_keys WHERE mediaType = :mediaType")
    suspend fun clearRemoteKeysByMediaType(mediaType: String)

    @Query("DELETE FROM remote_keys")
    suspend fun clearRemoteKeys()
}
