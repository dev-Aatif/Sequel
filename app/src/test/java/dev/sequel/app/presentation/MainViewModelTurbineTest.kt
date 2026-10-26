package dev.sequel.app.presentation

import app.cash.turbine.test
import dev.sequel.app.domain.repository.AuthRepository
import dev.sequel.app.domain.repository.UserPreferencesRepository
import dev.sequel.app.presentation.navigation.Screen
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * JVM unit test demonstrating Turbine StateFlow assertions alongside MockK.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTurbineTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startDestination_whenAuthenticated_emitsHomeScreen() = runTest(testDispatcher) {
        val authStateFlow = MutableStateFlow(true)
        every { authRepository.authStateFlow } returns authStateFlow
        every { authRepository.isAuthenticated } returns true

        val viewModel = MainViewModel(authRepository, userPreferencesRepository)

        viewModel.startDestination.test {
            assertEquals(null, awaitItem())

            advanceUntilIdle()

            assertEquals(Screen.Home.route, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun startDestination_whenNotAuthenticatedAndOnboardingCompleted_emitsLoginScreen() = runTest(testDispatcher) {
        val authStateFlow = MutableStateFlow(false)
        val onboardingFlow = MutableStateFlow(true)
        every { authRepository.authStateFlow } returns authStateFlow
        every { authRepository.isAuthenticated } returns false
        every { userPreferencesRepository.isOnboardingCompleted } returns onboardingFlow

        val viewModel = MainViewModel(authRepository, userPreferencesRepository)

        viewModel.startDestination.test {
            assertEquals(null, awaitItem())

            advanceUntilIdle()

            assertEquals(Screen.Login.route, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun startDestination_whenNotAuthenticatedAndOnboardingIncomplete_emitsOnboardingScreen() = runTest(testDispatcher) {
        val authStateFlow = MutableStateFlow(false)
        val onboardingFlow = MutableStateFlow(false)
        every { authRepository.authStateFlow } returns authStateFlow
        every { authRepository.isAuthenticated } returns false
        every { userPreferencesRepository.isOnboardingCompleted } returns onboardingFlow

        val viewModel = MainViewModel(authRepository, userPreferencesRepository)

        viewModel.startDestination.test {
            assertEquals(null, awaitItem())

            advanceUntilIdle()

            assertEquals(Screen.Onboarding.route, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
