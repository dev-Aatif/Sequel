package dev.sequel.app.domain.usecase

import dev.sequel.app.data.local.entity.MountRushmoreEntity
import dev.sequel.app.domain.repository.MountRushmoreRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class MountRushmoreUseCaseTest {

    private lateinit var fakeRepository: FakeMountRushmoreRepository
    private lateinit var getMountRushmoreUseCase: GetMountRushmoreUseCase
    private lateinit var setMountRushmoreSlotUseCase: SetMountRushmoreSlotUseCase
    private lateinit var clearMountRushmoreSlotUseCase: ClearMountRushmoreSlotUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeMountRushmoreRepository()
        getMountRushmoreUseCase = GetMountRushmoreUseCase(fakeRepository)
        setMountRushmoreSlotUseCase = SetMountRushmoreSlotUseCase(fakeRepository)
        clearMountRushmoreSlotUseCase = ClearMountRushmoreSlotUseCase(fakeRepository)
    }

    @Test
    fun getMountRushmoreUseCase_observesSlotsFromRepository() = runTest {
        val initialSlots = getMountRushmoreUseCase().first()
        assertEquals(0, initialSlots.size)

        setMountRushmoreSlotUseCase(
            slotIndex = 0,
            mediaId = 101,
            mediaType = "movie",
            title = "Inception",
            posterPath = "/inception.jpg",
            year = "2010",
            rating = 8.8f
        )

        val updatedSlots = getMountRushmoreUseCase().first()
        assertEquals(1, updatedSlots.size)
        assertEquals("Inception", updatedSlots[0].title)
        assertEquals(0, updatedSlots[0].slotIndex)
        assertEquals(101, updatedSlots[0].mediaId)
    }

    @Test
    fun setMountRushmoreSlotUseCase_validIndices_succeeds() = runTest {
        for (slotIndex in 0..3) {
            setMountRushmoreSlotUseCase(
                slotIndex = slotIndex,
                mediaId = 200 + slotIndex,
                mediaType = "movie",
                title = "Favorite Title #$slotIndex",
                posterPath = "/poster_$slotIndex.jpg",
                year = "202${slotIndex}",
                rating = 9.0f + slotIndex * 0.1f
            )

            val slot = fakeRepository.getSlot(slotIndex)
            assertNotNull(slot)
            assertEquals("Favorite Title #$slotIndex", slot?.title)
            assertEquals(slotIndex, slot?.slotIndex)
        }

        val allSlots = getMountRushmoreUseCase().first()
        assertEquals(4, allSlots.size)
    }

    @Test
    fun setMountRushmoreSlotUseCase_negativeIndex_throwsIllegalArgumentException() = runTest {
        var threw = false
        try {
            setMountRushmoreSlotUseCase(
                slotIndex = -1,
                mediaId = 1,
                mediaType = "movie",
                title = "Invalid Negative",
                posterPath = null,
                year = null,
                rating = null
            )
        } catch (e: IllegalArgumentException) {
            threw = true
            assertEquals("Slot index must be between 0 and 3, got -1", e.message)
        }
        org.junit.Assert.assertTrue("Expected IllegalArgumentException was not thrown", threw)
    }

    @Test
    fun setMountRushmoreSlotUseCase_indexExceeding3_throwsIllegalArgumentException() = runTest {
        var threw = false
        try {
            setMountRushmoreSlotUseCase(
                slotIndex = 4,
                mediaId = 1,
                mediaType = "movie",
                title = "Invalid Exceeded",
                posterPath = null,
                year = null,
                rating = null
            )
        } catch (e: IllegalArgumentException) {
            threw = true
            assertEquals("Slot index must be between 0 and 3, got 4", e.message)
        }
        org.junit.Assert.assertTrue("Expected IllegalArgumentException was not thrown", threw)
    }

    @Test
    fun clearMountRushmoreSlotUseCase_validSlot_removesSlot() = runTest {
        setMountRushmoreSlotUseCase(
            slotIndex = 2,
            mediaId = 300,
            mediaType = "tv",
            title = "Breaking Bad",
            posterPath = "/bb.jpg",
            year = "2008",
            rating = 9.5f
        )
        assertNotNull(fakeRepository.getSlot(2))

        clearMountRushmoreSlotUseCase(2)
        assertNull(fakeRepository.getSlot(2))
    }

    @Test
    fun clearMountRushmoreSlotUseCase_invalidSlot_throwsIllegalArgumentException() = runTest {
        var threw = false
        try {
            clearMountRushmoreSlotUseCase(5)
        } catch (e: IllegalArgumentException) {
            threw = true
            assertEquals("Slot index must be between 0 and 3, got 5", e.message)
        }
        org.junit.Assert.assertTrue("Expected IllegalArgumentException was not thrown", threw)
    }
}

/**
 * In-memory test double for [MountRushmoreRepository].
 */
private class FakeMountRushmoreRepository : MountRushmoreRepository {
    private val slotsMap = mutableMapOf<Int, MountRushmoreEntity>()
    private val slotsFlow = MutableStateFlow<List<MountRushmoreEntity>>(emptyList())

    override fun observeSlots(): Flow<List<MountRushmoreEntity>> = slotsFlow

    override suspend fun getSlot(slotIndex: Int): MountRushmoreEntity? = slotsMap[slotIndex]

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
        slotsMap[slotIndex] = entity
        slotsFlow.value = slotsMap.values.sortedBy { it.slotIndex }
    }

    override suspend fun clearSlot(slotIndex: Int) {
        slotsMap.remove(slotIndex)
        slotsFlow.value = slotsMap.values.sortedBy { it.slotIndex }
    }
}
