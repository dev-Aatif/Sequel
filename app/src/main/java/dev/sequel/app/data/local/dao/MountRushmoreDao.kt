package dev.sequel.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.sequel.app.data.local.entity.MountRushmoreEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MountRushmoreDao {

    @Query("SELECT * FROM mount_rushmore ORDER BY slot_index ASC")
    fun observeAllSlots(): Flow<List<MountRushmoreEntity>>

    @Query("SELECT * FROM mount_rushmore ORDER BY slot_index ASC")
    fun observeSlots(): Flow<List<MountRushmoreEntity>>

    @Query("SELECT * FROM mount_rushmore WHERE slot_index = :slotIndex")
    suspend fun getSlot(slotIndex: Int): MountRushmoreEntity?

    @Query("SELECT * FROM mount_rushmore ORDER BY slot_index ASC")
    suspend fun getAllSlots(): List<MountRushmoreEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSlot(slot: MountRushmoreEntity)

    @Query("DELETE FROM mount_rushmore WHERE slot_index = :slotIndex")
    suspend fun deleteSlot(slotIndex: Int)

    @Query("DELETE FROM mount_rushmore")
    suspend fun clearAllSlots()
}
