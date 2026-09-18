package dev.sequel.app.presentation.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

import dev.sequel.app.domain.error.AppError
import dev.sequel.app.domain.error.toAppError

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * UI state for the Login / Sign-Up screen.
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSignUpMode: Boolean = false,
    val isLoading: Boolean = false,
    val error: AppError? = null,
    val isPasswordVisible: Boolean = false
)

/**
 * One-shot events emitted from the ViewModel.
 */
sealed interface LoginEvent {
    data object NavigateToHome : LoginEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value.trim(), error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, error = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleMode() {
        _uiState.update {
            it.copy(
                isSignUpMode = !it.isSignUpMode,
                error = null,
                confirmPassword = ""
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isLoading) return

        // ── Validation ──────────────────────────────────────────
        if (state.email.isBlank()) {
            _uiState.update { it.copy(error = AppError.Validation("Email is required")) }
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _uiState.update { it.copy(error = AppError.Validation("Please enter a valid email")) }
            return
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(error = AppError.Validation("Password must be at least 6 characters")) }
            return
        }
        if (state.isSignUpMode && state.password != state.confirmPassword) {
            _uiState.update { it.copy(error = AppError.Validation("Passwords do not match")) }
            return
        }

        // ── Execute auth call ───────────────────────────────────
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val result = if (state.isSignUpMode) {
                authRepository.signUp(state.email, state.password)
            } else {
                authRepository.signIn(state.email, state.password)
            }

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(LoginEvent.NavigateToHome)
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.toAppError()
                        )
                    }
                }
            )
        }
    }
}
