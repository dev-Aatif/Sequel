package dev.sequel.app.domain.usecase

import dev.sequel.app.domain.repository.MountRushmoreRepository
import javax.inject.Inject

class SetMountRushmoreSlotUseCase @Inject constructor(
    private val repository: MountRushmoreRepository
) {
    suspend operator fun invoke(
        slotIndex: Int,
        mediaId: Int,
        mediaType: String,
        title: String,
        posterPath: String?,
        year: String?,
        rating: Float?
    ) {
        require(slotIndex in 0..3) { "Slot index must be between 0 and 3, got $slotIndex" }
        repository.setSlot(
            slotIndex = slotIndex,
            mediaId = mediaId,
            mediaType = mediaType,
            title = title,
            posterPath = posterPath,
            year = year,
            rating = rating
        )
    }
}
