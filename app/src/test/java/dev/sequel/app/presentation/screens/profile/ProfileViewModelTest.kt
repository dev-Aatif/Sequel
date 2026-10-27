package dev.sequel.app.presentation.screens.profile

import app.cash.turbine.test
import dev.sequel.app.data.local.dao.ReviewDao
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.dao.WatchedMovieDao
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.ProfileReviewItem
import dev.sequel.app.data.local.entity.RecentActivityItem
import dev.sequel.app.domain.model.TasteIdentity
import dev.sequel.app.domain.repository.AuthRepository
import dev.sequel.app.domain.repository.SearchRepository
import dev.sequel.app.domain.usecase.ClearMountRushmoreSlotUseCase
import dev.sequel.app.domain.usecase.GenerateTasteIdentityUseCase
import dev.sequel.app.domain.usecase.GetMountRushmoreUseCase
import dev.sequel.app.domain.usecase.SetMountRushmoreSlotUseCase
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val watchedEpisodeDao: WatchedEpisodeDao = mockk(relaxed = true)
    private val watchedMovieDao: WatchedMovieDao = mockk(relaxed = true)
    private val showDao: ShowDao = mockk(relaxed = true)
    private val searchRepository: SearchRepository = mockk(relaxed = true)
    private val getMountRushmoreUseCase: GetMountRushmoreUseCase = mockk(relaxed = true)
    private val setMountRushmoreSlotUseCase: SetMountRushmoreSlotUseCase = mockk(relaxed = true)
    private val clearMountRushmoreSlotUseCase: ClearMountRushmoreSlotUseCase = mockk(relaxed = true)
    private val reviewDao: ReviewDao = mockk(relaxed = true)
    private val generateTasteIdentityUseCase: GenerateTasteIdentityUseCase = mockk(relaxed = true)

    private val tvHistoryFlow = MutableStateFlow<List<RecentActivityItem>>(emptyList())
    private val movieHistoryFlow = MutableStateFlow<List<RecentActivityItem>>(emptyList())
    private val reviewsFlow = MutableStateFlow<List<ProfileReviewItem>>(emptyList())
    private val tasteIdentityFlow = MutableStateFlow<TasteIdentity?>(null)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { authRepository.currentUserEmail } returns "cinephile@sequel.dev"
        every { watchedEpisodeDao.observeTotalEpisodesWatched() } returns MutableStateFlow(10)
        every { watchedMovieDao.observeTotalMoviesWatched() } returns MutableStateFlow(5)
        every { watchedEpisodeDao.observeEpisodeRuntimeMinutes() } returns MutableStateFlow(500)
        every { watchedMovieDao.observeMovieRuntimeMinutes() } returns MutableStateFlow(600)
        every { watchedEpisodeDao.observeRecentActivity(any()) } returns MutableStateFlow(emptyList())
        every { getMountRushmoreUseCase.invoke() } returns MutableStateFlow(emptyList())

        every { watchedEpisodeDao.observeAllWatchedTvActivity() } returns tvHistoryFlow
        every { watchedMovieDao.observeAllWatchedMovieActivity() } returns movieHistoryFlow
        every { reviewDao.observeUserReviews() } returns reviewsFlow
        every { generateTasteIdentityUseCase.invoke() } returns tasteIdentityFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ProfileViewModel(
        authRepository = authRepository,
        watchedEpisodeDao = watchedEpisodeDao,
        watchedMovieDao = watchedMovieDao,
        showDao = showDao,
        searchRepository = searchRepository,
        getMountRushmoreUseCase = getMountRushmoreUseCase,
        setMountRushmoreSlotUseCase = setMountRushmoreSlotUseCase,
        clearMountRushmoreSlotUseCase = clearMountRushmoreSlotUseCase,
        reviewDao = reviewDao,
        generateTasteIdentityUseCase = generateTasteIdentityUseCase
    )

    @Test
    fun watchHistoryItems_filtersByMediaTypeCorrectly() = runTest(testDispatcher) {
        val tvItem = RecentActivityItem(
            historyId = 1L,
            watchedAt = 1000L,
            mediaType = MediaType.TV,
            showId = 10,
            showTitle = "Breaking Bad",
            posterPath = "/bb.jpg",
            seasonNumber = 1,
            episodeNumber = 1,
            userRating = 10f,
            runtime = 50
        )
        val movieItem = RecentActivityItem(
            historyId = 2L,
            watchedAt = 2000L,
            mediaType = MediaType.MOVIE,
            showId = 20,
            showTitle = "Fight Club",
            posterPath = "/fc.jpg",
            seasonNumber = null,
            episodeNumber = null,
            userRating = 9f,
            runtime = 139
        )

        tvHistoryFlow.value = listOf(tvItem)
        movieHistoryFlow.value = listOf(movieItem)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Initial state (Filter: ALL, Sort: DATE_DESC)
            var state = awaitItem()
            while (state.watchHistoryItems.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(2, state.watchHistoryItems.size)
            assertEquals("Fight Club", state.watchHistoryItems[0].showTitle)
            assertEquals("Breaking Bad", state.watchHistoryItems[1].showTitle)

            // Switch to MOVIE filter
            viewModel.setMediaTypeFilter(HistoryMediaTypeFilter.MOVIE)
            state = awaitItem()
            while (state.historyFilter.mediaType != HistoryMediaTypeFilter.MOVIE) {
                state = awaitItem()
            }
            assertEquals(1, state.watchHistoryItems.size)
            assertEquals(MediaType.MOVIE, state.watchHistoryItems[0].mediaType)
            assertEquals("Fight Club", state.watchHistoryItems[0].showTitle)

            // Switch to TV filter
            viewModel.setMediaTypeFilter(HistoryMediaTypeFilter.TV)
            state = awaitItem()
            while (state.historyFilter.mediaType != HistoryMediaTypeFilter.TV) {
                state = awaitItem()
            }
            assertEquals(1, state.watchHistoryItems.size)
            assertEquals(MediaType.TV, state.watchHistoryItems[0].mediaType)
            assertEquals("Breaking Bad", state.watchHistoryItems[0].showTitle)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun watchHistoryItems_sortsCorrectly() = runTest(testDispatcher) {
        val item1 = RecentActivityItem(
            historyId = 1L,
            watchedAt = 1000L,
            mediaType = MediaType.MOVIE,
            showId = 101,
            showTitle = "Film Low Rating Long Runtime",
            posterPath = null,
            userRating = 6f,
            runtime = 180
        )
        val item2 = RecentActivityItem(
            historyId = 2L,
            watchedAt = 3000L,
            mediaType = MediaType.MOVIE,
            showId = 102,
            showTitle = "Film High Rating Short Runtime",
            posterPath = null,
            userRating = 10f,
            runtime = 85
        )
        val item3 = RecentActivityItem(
            historyId = 3L,
            watchedAt = 2000L,
            mediaType = MediaType.MOVIE,
            showId = 103,
            showTitle = "Film Mid Rating Mid Runtime",
            posterPath = null,
            userRating = 8f,
            runtime = 120
        )

        movieHistoryFlow.value = listOf(item1, item2, item3)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Default: DATE_DESC
            var state = awaitItem()
            while (state.watchHistoryItems.size < 3) {
                state = awaitItem()
            }
            assertEquals(listOf(3000L, 2000L, 1000L), state.watchHistoryItems.map { it.watchedAt })

            // Sort: DATE_ASC
            viewModel.setSortOrder(HistorySortOrder.DATE_ASC)
            state = awaitItem()
            while (state.historyFilter.sortOrder != HistorySortOrder.DATE_ASC) {
                state = awaitItem()
            }
            assertEquals(listOf(1000L, 2000L, 3000L), state.watchHistoryItems.map { it.watchedAt })

            // Sort: RATING_DESC
            viewModel.setSortOrder(HistorySortOrder.RATING_DESC)
            state = awaitItem()
            while (state.historyFilter.sortOrder != HistorySortOrder.RATING_DESC) {
                state = awaitItem()
            }
            assertEquals(listOf(10f, 8f, 6f), state.watchHistoryItems.map { it.userRating })

            // Sort: RUNTIME_DESC
            viewModel.setSortOrder(HistorySortOrder.RUNTIME_DESC)
            state = awaitItem()
            while (state.historyFilter.sortOrder != HistorySortOrder.RUNTIME_DESC) {
                state = awaitItem()
            }
            assertEquals(listOf(180, 120, 85), state.watchHistoryItems.map { it.runtime })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun userReviews_filtersByMediaTypeCorrectly() = runTest(testDispatcher) {
        val movieReview = ProfileReviewItem(
            id = 101L,
            mediaId = 550,
            mediaType = "movie",
            title = "Fight Club",
            posterPath = "/fc.jpg",
            firstAirDate = "1999-10-15",
            reviewText = "First rule of Fight Club is you do not talk about Fight Club.",
            rating = 10f,
            isSpoiler = false,
            primaryEmotion = "Absolute Cinema",
            reactionCount = 42
        )
        val tvReview = ProfileReviewItem(
            id = 102L,
            mediaId = 1396,
            mediaType = "tv",
            title = "Breaking Bad",
            posterPath = "/bb.jpg",
            firstAirDate = "2008-01-20",
            seasonNum = 5,
            episodeNum = 14,
            reviewText = "Ozymandias is the peak of television drama.",
            rating = 10f,
            isSpoiler = true,
            primaryEmotion = "Mindfuck",
            reactionCount = 99
        )

        reviewsFlow.value = listOf(movieReview, tvReview)

        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Initial state: Filter is ALL, both reviews present
            var state = awaitItem()
            while (state.userReviews.isEmpty()) {
                state = awaitItem()
            }
            assertEquals(2, state.userReviews.size)
            assertEquals("Fight Club", state.userReviews[0].title)
            assertEquals("Breaking Bad", state.userReviews[1].title)

            // Switch filter to MOVIE
            viewModel.setReviewFilter(ReviewMediaTypeFilter.MOVIE)
            state = awaitItem()
            while (state.reviewFilter != ReviewMediaTypeFilter.MOVIE) {
                state = awaitItem()
            }
            assertEquals(1, state.userReviews.size)
            assertEquals("movie", state.userReviews[0].mediaType)
            assertEquals("Fight Club", state.userReviews[0].title)

            // Switch filter to TV
            viewModel.setReviewFilter(ReviewMediaTypeFilter.TV)
            state = awaitItem()
            while (state.reviewFilter != ReviewMediaTypeFilter.TV) {
                state = awaitItem()
            }
            assertEquals(1, state.userReviews.size)
            assertEquals("tv", state.userReviews[0].mediaType)
            assertEquals("Breaking Bad", state.userReviews[0].title)

            // Switch filter back to ALL
            viewModel.setReviewFilter(ReviewMediaTypeFilter.ALL)
            state = awaitItem()
            while (state.reviewFilter != ReviewMediaTypeFilter.ALL) {
                state = awaitItem()
            }
            assertEquals(2, state.userReviews.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectedTab_switchesBetweenWatchHistoryAndReviews() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Initial state: WATCH_HISTORY
            var state = awaitItem()
            assertEquals(ProfileTab.WATCH_HISTORY, state.selectedTab)

            // Switch to REVIEWS
            viewModel.setSelectedTab(ProfileTab.REVIEWS)
            state = awaitItem()
            while (state.selectedTab != ProfileTab.REVIEWS) {
                state = awaitItem()
            }
            assertEquals(ProfileTab.REVIEWS, state.selectedTab)

            // Switch back to WATCH_HISTORY
            viewModel.setSelectedTab(ProfileTab.WATCH_HISTORY)
            state = awaitItem()
            while (state.selectedTab != ProfileTab.WATCH_HISTORY) {
                state = awaitItem()
            }
            assertEquals(ProfileTab.WATCH_HISTORY, state.selectedTab)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun tasteIdentity_updatesUiStateWhenGenerated() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            var state = awaitItem()
            org.junit.Assert.assertNull(state.tasteIdentity)

            val mockIdentity = TasteIdentity(
                handle = "@cinephile",
                archetype = "Neon Auteurist",
                primaryObsession = "Cyberpunk & Sci-Fi",
                primaryObsessionHours = 42,
                topAuteur = "Denis Villeneuve",
                topAuteurWatchedCount = 5,
                totalHoursLogged = 120
            )

            tasteIdentityFlow.value = mockIdentity
            state = awaitItem()
            while (state.tasteIdentity == null) {
                state = awaitItem()
            }

            assertEquals("Neon Auteurist", state.tasteIdentity?.archetype)
            assertEquals("Cyberpunk & Sci-Fi", state.tasteIdentity?.primaryObsession)
            assertEquals("Denis Villeneuve", state.tasteIdentity?.topAuteur)
            assertEquals(42, state.tasteIdentity?.primaryObsessionHours)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
