package dev.sequel.app.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.dao.ReviewDao
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.dao.WatchedMovieDao
import dev.sequel.app.data.local.entity.MountRushmoreEntity
import dev.sequel.app.data.local.entity.ProfileReviewItem
import dev.sequel.app.data.local.entity.RecentActivityItem
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.domain.model.TasteIdentity
import dev.sequel.app.domain.repository.AuthRepository
import dev.sequel.app.domain.repository.SearchRepository
import dev.sequel.app.domain.usecase.ClearMountRushmoreSlotUseCase
import dev.sequel.app.domain.usecase.GenerateTasteIdentityUseCase
import dev.sequel.app.domain.usecase.GetMountRushmoreUseCase
import dev.sequel.app.domain.usecase.SetMountRushmoreSlotUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryMediaTypeFilter {
    ALL, MOVIE, TV
}

enum class HistorySortOrder {
    DATE_DESC, DATE_ASC, RATING_DESC, RUNTIME_DESC
}

enum class ProfileTab {
    WATCH_HISTORY, REVIEWS
}

enum class ReviewMediaTypeFilter {
    ALL, MOVIE, TV
}

data class WatchHistoryFilter(
    val mediaType: HistoryMediaTypeFilter = HistoryMediaTypeFilter.ALL,
    val sortOrder: HistorySortOrder = HistorySortOrder.DATE_DESC
)

data class MountRushmoreSlotUi(
    val slotIndex: Int,
    val item: MountRushmoreEntity? = null
) {
    val isFilled: Boolean get() = item != null
}

data class ProfileUiState(
    val email: String? = null,
    val totalRuntimeMinutes: Int = 0,
    val totalEpisodesWatched: Int = 0,
    val totalMoviesWatched: Int = 0,
    val recentActivity: List<RecentActivityItem> = emptyList(),
    val watchHistoryItems: List<RecentActivityItem> = emptyList(),
    val historyFilter: WatchHistoryFilter = WatchHistoryFilter(),
    val mountRushmore: List<MountRushmoreSlotUi> = (0..3).map { MountRushmoreSlotUi(it) },
    val selectedTab: ProfileTab = ProfileTab.WATCH_HISTORY,
    val reviewFilter: ReviewMediaTypeFilter = ReviewMediaTypeFilter.ALL,
    val userReviews: List<ProfileReviewItem> = emptyList(),
    val tasteIdentity: TasteIdentity? = null
) {
    val watchTimeFormatted: String
        get() {
            if (totalRuntimeMinutes == 0) return "0m"
            val totalHours = totalRuntimeMinutes / 60
            val minutes = totalRuntimeMinutes % 60
            val months = totalHours / (24 * 30)
            val days = (totalHours % (24 * 30)) / 24
            val hours = totalHours % 24

            return buildString {
                if (months > 0) {
                    append("${months}mo ")
                    if (days > 0) append("${days}d")
                } else if (days > 0) {
                    append("${days}d ")
                    if (hours > 0) append("${hours}h")
                } else {
                    if (hours > 0) append("${hours}h ")
                    if (minutes > 0 || hours == 0) append("${minutes}m")
                }
            }.trim()
        }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val watchedMovieDao: WatchedMovieDao,
    private val showDao: ShowDao,
    private val searchRepository: SearchRepository,
    private val getMountRushmoreUseCase: GetMountRushmoreUseCase,
    private val setMountRushmoreSlotUseCase: SetMountRushmoreSlotUseCase,
    private val clearMountRushmoreSlotUseCase: ClearMountRushmoreSlotUseCase,
    private val reviewDao: ReviewDao,
    private val generateTasteIdentityUseCase: GenerateTasteIdentityUseCase
) : ViewModel() {

    val tasteIdentity: StateFlow<TasteIdentity?> = generateTasteIdentityUseCase()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

    val mountRushmore: StateFlow<List<MountRushmoreSlotUi>> = getMountRushmoreUseCase()
        .map { list ->
            (0..3).map { index ->
                MountRushmoreSlotUi(
                    slotIndex = index,
                    item = list.find { it.slotIndex == index }
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            (0..3).map { MountRushmoreSlotUi(it) }
        )

    private val _historyFilter = MutableStateFlow(WatchHistoryFilter())
    val historyFilter: StateFlow<WatchHistoryFilter> = _historyFilter.asStateFlow()

    private val allHistoryItemsFlow = combine(
        watchedEpisodeDao.observeAllWatchedTvActivity(),
        watchedMovieDao.observeAllWatchedMovieActivity()
    ) { tvList, movieList ->
        tvList + movieList
    }

    private val historyDataFlow = combine(
        allHistoryItemsFlow,
        _historyFilter
    ) { allItems, filter ->
        val filtered = when (filter.mediaType) {
            HistoryMediaTypeFilter.ALL -> allItems
            HistoryMediaTypeFilter.MOVIE -> allItems.filter { it.mediaType == dev.sequel.app.data.local.entity.MediaType.MOVIE }
            HistoryMediaTypeFilter.TV -> allItems.filter { it.mediaType == dev.sequel.app.data.local.entity.MediaType.TV }
        }

        val sorted = when (filter.sortOrder) {
            HistorySortOrder.DATE_DESC -> filtered.sortedByDescending { it.watchedAt }
            HistorySortOrder.DATE_ASC -> filtered.sortedBy { it.watchedAt }
            HistorySortOrder.RATING_DESC -> filtered.sortedWith(
                compareByDescending<RecentActivityItem> { it.userRating ?: -1f }
                    .thenByDescending { it.watchedAt }
            )
            HistorySortOrder.RUNTIME_DESC -> filtered.sortedWith(
                compareByDescending<RecentActivityItem> { it.runtime ?: 0 }
                    .thenByDescending { it.watchedAt }
            )
        }
        sorted to filter
    }

    val watchHistoryItems: StateFlow<List<RecentActivityItem>> = historyDataFlow
        .map { it.first }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    private val profileStatsFlow = combine(
        watchedEpisodeDao.observeTotalEpisodesWatched(),
        watchedMovieDao.observeTotalMoviesWatched(),
        watchedEpisodeDao.observeEpisodeRuntimeMinutes(),
        watchedMovieDao.observeMovieRuntimeMinutes(),
        watchedEpisodeDao.observeRecentActivity(limit = 10)
    ) { episodes, movies, episodeRuntime, movieRuntime, recentActivity ->
        ProfileUiState(
            email = authRepository.currentUserEmail,
            totalEpisodesWatched = episodes,
            totalMoviesWatched = movies,
            totalRuntimeMinutes = episodeRuntime + movieRuntime,
            recentActivity = recentActivity
        )
    }

    private val statsAndRushmoreFlow = combine(
        profileStatsFlow,
        mountRushmore
    ) { stats, rushSlots ->
        stats to rushSlots
    }

    private val _selectedTab = MutableStateFlow(ProfileTab.WATCH_HISTORY)
    val selectedTab: StateFlow<ProfileTab> = _selectedTab.asStateFlow()

    private val _reviewFilter = MutableStateFlow(ReviewMediaTypeFilter.ALL)
    val reviewFilter: StateFlow<ReviewMediaTypeFilter> = _reviewFilter.asStateFlow()

    private val reviewsDataFlow = combine(
        reviewDao.observeUserReviews(),
        _reviewFilter
    ) { allReviews, filter ->
        val filtered = when (filter) {
            ReviewMediaTypeFilter.ALL -> allReviews
            ReviewMediaTypeFilter.MOVIE -> allReviews.filter { it.mediaType.equals("movie", ignoreCase = true) }
            ReviewMediaTypeFilter.TV -> allReviews.filter { it.mediaType.equals("tv", ignoreCase = true) }
        }
        filtered to filter
    }

    val userReviews: StateFlow<List<ProfileReviewItem>> = reviewsDataFlow
        .map { it.first }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    val uiState: StateFlow<ProfileUiState> = combine(
        statsAndRushmoreFlow,
        historyDataFlow,
        reviewsDataFlow,
        _selectedTab,
        tasteIdentity
    ) { (stats, rushSlots), (historyItems, historyFilter), (reviews, reviewFilter), selectedTab, identity ->
        stats.copy(
            mountRushmore = rushSlots,
            watchHistoryItems = historyItems,
            recentActivity = historyItems,
            historyFilter = historyFilter,
            selectedTab = selectedTab,
            reviewFilter = reviewFilter,
            userReviews = reviews,
            tasteIdentity = identity
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ProfileUiState(email = authRepository.currentUserEmail)
    )

    fun setSelectedTab(tab: ProfileTab) {
        _selectedTab.value = tab
    }

    fun setReviewFilter(filter: ReviewMediaTypeFilter) {
        _reviewFilter.value = filter
    }

    fun setMediaTypeFilter(filter: HistoryMediaTypeFilter) {
        _historyFilter.value = _historyFilter.value.copy(mediaType = filter)
    }

    fun setSortOrder(order: HistorySortOrder) {
        _historyFilter.value = _historyFilter.value.copy(sortOrder = order)
    }

    // ── Picker / Search State ──────────────────────────────────────────────
    private val _pickerSlot = MutableStateFlow<Int?>(null)
    val pickerSlot: StateFlow<Int?> = _pickerSlot.asStateFlow()

    private val _pickerQuery = MutableStateFlow("")
    val pickerQuery: StateFlow<String> = _pickerQuery.asStateFlow()

    private val _pickerSearching = MutableStateFlow(false)
    val pickerSearching: StateFlow<Boolean> = _pickerSearching.asStateFlow()

    private val _pickerResults = MutableStateFlow<List<ShowEntity>>(emptyList())
    val pickerResults: StateFlow<List<ShowEntity>> = _pickerResults.asStateFlow()

    private var searchJob: Job? = null

    fun openPickerForSlot(slotIndex: Int) {
        _pickerSlot.value = slotIndex
        _pickerQuery.value = ""
        _pickerResults.value = emptyList()
        _pickerSearching.value = false
    }

    fun closePicker() {
        searchJob?.cancel()
        _pickerSlot.value = null
        _pickerQuery.value = ""
        _pickerResults.value = emptyList()
        _pickerSearching.value = false
    }

    fun onPickerQueryChanged(query: String) {
        _pickerQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _pickerResults.value = emptyList()
            _pickerSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            _pickerSearching.value = true
            try {
                val results = searchRepository.search(query.trim(), "All")
                _pickerResults.value = results
            } catch (e: Exception) {
                // Offline fallback to local cached shows
                val local = showDao.searchShows(query.trim()).firstOrNull() ?: emptyList()
                _pickerResults.value = local
            } finally {
                _pickerSearching.value = false
            }
        }
    }

    fun selectSlotMedia(slotIndex: Int, show: ShowEntity) {
        viewModelScope.launch {
            setMountRushmoreSlotUseCase(
                slotIndex = slotIndex,
                mediaId = show.id,
                mediaType = show.mediaType,
                title = show.title,
                posterPath = show.posterPath,
                year = show.firstAirDate?.take(4),
                rating = show.voteAverage.toFloat()
            )
            closePicker()
        }
    }

    fun clearSlot(slotIndex: Int) {
        viewModelScope.launch {
            clearMountRushmoreSlotUseCase(slotIndex)
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
