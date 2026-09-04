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

    @Query("SELECT * FROM remote_keys WHERE showId = :showId")
    suspend fun remoteKeysShowId(showId: Int): RemoteKeys?

    @Query("DELETE FROM remote_keys WHERE showId = :showId")
    suspend fun deleteByShowId(showId: Int)

    @Query("DELETE FROM remote_keys WHERE showId IN (SELECT id FROM shows WHERE media_type = :mediaType)")
    suspend fun clearRemoteKeysByMediaType(mediaType: String)

    @Query("DELETE FROM remote_keys")
    suspend fun clearRemoteKeys()
}
