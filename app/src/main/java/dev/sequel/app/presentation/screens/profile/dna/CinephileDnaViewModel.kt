package dev.sequel.app.presentation.screens.profile.dna

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.domain.model.TasteIdentity
import dev.sequel.app.domain.usecase.GenerateTasteIdentityUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CinephileDnaViewModel @Inject constructor(
    generateTasteIdentityUseCase: GenerateTasteIdentityUseCase
) : ViewModel() {

    val tasteIdentity: StateFlow<TasteIdentity?> = generateTasteIdentityUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
}
