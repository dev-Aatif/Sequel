package dev.sequel.app.presentation.screens.profile.dna

import app.cash.turbine.test
import dev.sequel.app.domain.model.TasteIdentity
import dev.sequel.app.domain.usecase.GenerateTasteIdentityUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CinephileDnaViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val generateTasteIdentityUseCase: GenerateTasteIdentityUseCase = mockk(relaxed = true)
    private val tasteFlow = MutableStateFlow<TasteIdentity?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { generateTasteIdentityUseCase() } returns tasteFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun tasteIdentity_emitsNullInitiallyAndUpdatesWhenGenerated() = runTest(testDispatcher) {
        val viewModel = CinephileDnaViewModel(generateTasteIdentityUseCase)

        viewModel.tasteIdentity.test {
            assertNull(awaitItem())

            val mockIdentity = TasteIdentity(
                handle = "@neo",
                archetype = "Neon Auteurist",
                primaryObsession = "Cyberpunk & Sci-Fi",
                primaryObsessionHours = 48,
                topAuteur = "Denis Villeneuve",
                topAuteurWatchedCount = 5,
                totalHoursLogged = 120,
                topAuteurTitles = listOf("Dune", "Blade Runner 2049"),
                totalTitlesWatched = 15
            )

            tasteFlow.value = mockIdentity
            val emitted = awaitItem()
            assertEquals("@neo", emitted?.handle)
            assertEquals("Neon Auteurist", emitted?.archetype)
            assertEquals("Denis Villeneuve", emitted?.topAuteur)
            assertEquals(listOf("Dune", "Blade Runner 2049"), emitted?.topAuteurTitles)
            assertEquals(15, emitted?.totalTitlesWatched)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
