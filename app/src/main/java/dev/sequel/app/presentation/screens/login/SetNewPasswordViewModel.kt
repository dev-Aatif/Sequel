package dev.sequel.app.presentation.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.domain.repository.AuthRepository
import dev.sequel.app.domain.error.AppError
import dev.sequel.app.domain.error.toAppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetNewPasswordUiState(
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val success: Boolean = false
)

@HiltViewModel
class SetNewPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetNewPasswordUiState())
    val uiState: StateFlow<SetNewPasswordUiState> = _uiState.asStateFlow()

    fun updatePassword(password: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            authRepository.updatePassword(password).fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, success = true) }
                },
                onFailure = { throwable ->
                    _uiState.update { it.copy(isLoading = false, error = throwable.toAppError()) }
                }
            )
        }
    }
}
