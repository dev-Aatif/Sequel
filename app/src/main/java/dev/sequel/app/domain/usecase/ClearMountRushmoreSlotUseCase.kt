package dev.sequel.app.domain.usecase

import dev.sequel.app.domain.repository.MountRushmoreRepository
import javax.inject.Inject

class ClearMountRushmoreSlotUseCase @Inject constructor(
    private val repository: MountRushmoreRepository
) {
    suspend operator fun invoke(slotIndex: Int) {
        require(slotIndex in 0..3) { "Slot index must be between 0 and 3, got $slotIndex" }
        repository.clearSlot(slotIndex)
    }
}
