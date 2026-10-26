package dev.sequel.app.data.repository

import dev.sequel.app.data.local.dao.MountRushmoreDao
import dev.sequel.app.data.local.entity.MountRushmoreEntity
import dev.sequel.app.domain.repository.MountRushmoreRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MountRushmoreRepositoryImpl @Inject constructor(
    private val dao: MountRushmoreDao
) : MountRushmoreRepository {

    override fun observeSlots(): Flow<List<MountRushmoreEntity>> = dao.observeAllSlots()

    override suspend fun getSlot(slotIndex: Int): MountRushmoreEntity? = dao.getSlot(slotIndex)

    override suspend fun setSlot(
        slotIndex: Int,
        mediaId: Int,
        mediaType: String,
        title: String,
        posterPath: String?,
        year: String?,
        rating: Float?
    ) {
        val entity = MountRushmoreEntity(
            slotIndex = slotIndex,
            mediaId = mediaId,
            mediaType = mediaType,
            title = title,
            posterPath = posterPath,
            year = year,
            rating = rating,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertSlot(entity)
    }

    override suspend fun clearSlot(slotIndex: Int) {
        dao.deleteSlot(slotIndex)
    }
}
