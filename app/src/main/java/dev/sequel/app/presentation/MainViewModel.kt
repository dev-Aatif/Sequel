package dev.sequel.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.domain.repository.AuthRepository
import dev.sequel.app.domain.repository.UserPreferencesRepository
import dev.sequel.app.presentation.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    val authState = authRepository.authStateFlow

    init {
        viewModelScope.launch {
            val isAuthenticated = kotlinx.coroutines.withTimeoutOrNull(2500L) {
                authRepository.authStateFlow.first()
            } ?: authRepository.isAuthenticated

            if (isAuthenticated) {
                _startDestination.value = Screen.Home.route
            } else {
                val isOnboardingCompleted = kotlinx.coroutines.withTimeoutOrNull(2500L) {
                    userPreferencesRepository.isOnboardingCompleted.first()
                } ?: false
                _startDestination.value = if (isOnboardingCompleted) Screen.Login.route else Screen.Onboarding.route
            }
        }
    }

}
