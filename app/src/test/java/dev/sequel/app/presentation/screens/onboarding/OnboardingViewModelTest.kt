package dev.sequel.app.presentation.screens.onboarding

import dev.sequel.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakePreferencesRepository: FakeUserPreferencesRepository
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakePreferencesRepository = FakeUserPreferencesRepository()
        viewModel = OnboardingViewModel(fakePreferencesRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasFirstPageAndDefaultSelections() {
        val state = viewModel.uiState.value
        assertEquals(0, state.currentPage)
        assertEquals(0, state.selectedPreviewCardIndex)
        assertEquals(9.5f, state.selectedRating, 0.01f)
    }

    @Test
    fun onPageChanged_updatesCurrentPage() {
        viewModel.onPageChanged(2)
        assertEquals(2, viewModel.uiState.value.currentPage)
    }

    @Test
    fun selectPreviewCard_updatesCardIndex() {
        viewModel.selectPreviewCard(1)
        assertEquals(1, viewModel.uiState.value.selectedPreviewCardIndex)
    }

    @Test
    fun selectRating_updatesRatingValue() {
        viewModel.selectRating(10.0f)
        assertEquals(10.0f, viewModel.uiState.value.selectedRating, 0.01f)
    }

    @Test
    fun completeOnboarding_setsPreferenceAndEmitsNavigateToLogin() = runTest(testDispatcher) {
        viewModel.completeOnboarding()
        advanceUntilIdle()

        assertTrue(fakePreferencesRepository.isCompleted)
        val event = viewModel.events.first()
        assertEquals(OnboardingEvent.NavigateToLogin, event)
    }
}

private class FakeUserPreferencesRepository : UserPreferencesRepository {
    var isCompleted: Boolean = false
    private val _flow = MutableStateFlow(false)
    override val isOnboardingCompleted: Flow<Boolean> = _flow

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        isCompleted = completed
        _flow.value = completed
    }
}
