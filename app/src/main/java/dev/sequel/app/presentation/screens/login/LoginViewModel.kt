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
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Login / Sign-Up screen.
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSignUpMode: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isPasswordVisible: Boolean = false,
    val isCheckingSession: Boolean = true
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

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            val isAuthenticated = authRepository.authStateFlow.first()
            if (isAuthenticated) {
                _events.emit(LoginEvent.NavigateToHome)
            } else {
                _uiState.update { it.copy(isCheckingSession = false) }
            }
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value.trim(), errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null) }
    }

    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    fun toggleMode() {
        _uiState.update {
            it.copy(
                isSignUpMode = !it.isSignUpMode,
                errorMessage = null,
                confirmPassword = ""
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isLoading) return

        // ── Validation ──────────────────────────────────────────
        if (state.email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email is required") }
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _uiState.update { it.copy(errorMessage = "Please enter a valid email") }
            return
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
            return
        }
        if (state.isSignUpMode && state.password != state.confirmPassword) {
            _uiState.update { it.copy(errorMessage = "Passwords do not match") }
            return
        }

        // ── Execute auth call ───────────────────────────────────
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = if (state.isSignUpMode) {
                authRepository.signUp(state.email, state.password)
            } else {
                authRepository.signIn(state.email, state.password)
            }

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.emit(LoginEvent.NavigateToHome)
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.localizedMessage
                                ?: "Authentication failed. Please try again."
                        )
                    }
                }
            )
        }
    }
}
