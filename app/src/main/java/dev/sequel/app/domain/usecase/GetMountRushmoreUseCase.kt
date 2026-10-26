package dev.sequel.app.domain.usecase

import dev.sequel.app.data.local.entity.MountRushmoreEntity
import dev.sequel.app.domain.repository.MountRushmoreRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMountRushmoreUseCase @Inject constructor(
    private val repository: MountRushmoreRepository
) {
    operator fun invoke(): Flow<List<MountRushmoreEntity>> = repository.observeSlots()
}
