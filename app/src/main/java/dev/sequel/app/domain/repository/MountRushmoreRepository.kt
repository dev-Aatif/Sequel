package dev.sequel.app.domain.repository

import dev.sequel.app.data.local.entity.MountRushmoreEntity
import kotlinx.coroutines.flow.Flow

interface MountRushmoreRepository {
    fun observeSlots(): Flow<List<MountRushmoreEntity>>
    suspend fun getSlot(slotIndex: Int): MountRushmoreEntity?
    suspend fun setSlot(
        slotIndex: Int,
        mediaId: Int,
        mediaType: String,
        title: String,
        posterPath: String?,
        year: String?,
        rating: Float?
    )
    suspend fun clearSlot(slotIndex: Int)
}
