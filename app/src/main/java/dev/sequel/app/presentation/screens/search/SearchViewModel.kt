package dev.sequel.app.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.local.entity.WatchlistEntity
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.domain.error.AppError
import dev.sequel.app.domain.error.toAppError
import dev.sequel.app.domain.repository.SearchRepository
import dev.sequel.app.domain.repository.SeasonRepository
import dev.sequel.app.domain.repository.ShowRepository
import dev.sequel.app.domain.repository.WatchedEpisodeRepository
import dev.sequel.app.domain.repository.WatchlistRepository
import dev.sequel.app.domain.usecase.GetNextEpisodeUseCase
import dev.sequel.app.presentation.state.BottomSheetEpisodeInfo
import dev.sequel.app.presentation.state.BottomSheetShowInfo
import dev.sequel.app.presentation.state.BottomSheetUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: List<ShowEntity>) : SearchUiState
    data class Error(val error: AppError) : SearchUiState
}

private data class SearchParameters(
    val query: String,
    val filter: String,
    val genreId: Int?,
    val retryCount: Int
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val watchlistRepository: WatchlistRepository,
    private val watchedEpisodeRepository: WatchedEpisodeRepository,
    private val watchedMovieDao: dev.sequel.app.data.local.dao.WatchedMovieDao,
    private val showRepository: ShowRepository,
    private val seasonRepository: SeasonRepository,
    private val syncManager: SyncManager,
    private val getNextEpisodeUseCase: GetNextEpisodeUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchFilter = MutableStateFlow("All") // "All", "TV Shows", "Movies"
    val searchFilter = _searchFilter.asStateFlow()

    private val _isProcessingAction = MutableStateFlow(false)
    val isProcessingAction = _isProcessingAction.asStateFlow()

    private val _retryTrigger = MutableStateFlow(0)
    private val _activeGenreId = MutableStateFlow<Int?>(null)

    private val _bottomSheetState = MutableStateFlow(BottomSheetUiState())
    val bottomSheetState = _bottomSheetState.asStateFlow()

    val searchState: StateFlow<SearchUiState> = combine(
        _searchQuery,
        _searchFilter,
        _activeGenreId,
        _retryTrigger
    ) { query, filter, genreId, retryCount ->
        SearchParameters(query, filter, genreId, retryCount)
    }
        .debounce { params ->
            if (params.query.isBlank() && params.genreId == null) 0L else 500L
        }
        .distinctUntilChanged()
        .flatMapLatest { params ->
            if (params.query.isBlank() && params.genreId == null) {
                flow { emit(SearchUiState.Idle) }
            } else {
                flow {
                    emit(SearchUiState.Loading)
                    try {
                        val results = if (params.genreId != null) {
                            searchRepository.discover(params.genreId, params.filter)
                        } else {
                            searchRepository.search(params.query, params.filter)
                        }
                        emit(SearchUiState.Success(results))
                    } catch (e: Exception) {
                        emit(SearchUiState.Error(e.toAppError()))
                    }
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            SearchUiState.Idle
        )

    fun onQueryChange(query: String) {
        _activeGenreId.value = null
        _searchQuery.value = query
    }

    fun onCategoryClick(name: String, genreId: Int) {
        _activeGenreId.value = genreId
        _searchQuery.value = name
    }

    fun retrySearch() {
        _retryTrigger.value += 1
    }

    fun onFilterChange(filter: String) {
        _searchFilter.value = filter
    }

    fun openBottomSheet(show: ShowEntity) {
        val showInfo = BottomSheetShowInfo(
            id = show.id,
            title = show.title,
            overview = show.overview,
            posterPath = show.posterPath,
            backdropPath = show.backdropPath,
            mediaType = show.mediaType,
            rating = show.voteAverage,
            genreIds = show.genreIds
        )
        _bottomSheetState.value = BottomSheetUiState(show = showInfo, isLoading = true)
        viewModelScope.launch {
            try {
                // Ensure foreign key constraints won't fail for watched episodes
                showRepository.insertShow(show)
                
                val inWatchlist = watchlistRepository.observeIsInWatchlist(show.id, show.mediaType).firstOrNull() ?: false
                
                if (show.mediaType == "movie") {
                    val isMovieWatched = watchedMovieDao.observeIsMovieWatched(show.id).firstOrNull() ?: false
                    
                    _bottomSheetState.value = BottomSheetUiState(
                        show = showInfo,
                        inWatchlist = inWatchlist,
                        isWatched = isMovieWatched,
                        isLoading = false
                    )
                } else {
                    val progression = getNextEpisodeUseCase(show.id)
                    val episodeInfo = progression.nextEpisodeData?.let {
                        BottomSheetEpisodeInfo(
                            title = it.name,
                            episodeNumber = it.episodeNumber,
                            seasonNumber = it.seasonNumber,
                            overview = "",
                            stillPath = null
                        )
                    }
                    
                    _bottomSheetState.value = BottomSheetUiState(
                        show = showInfo,
                        inWatchlist = inWatchlist,
                        isWatched = false,
                        isCompleted = progression.isCompleted,
                        nextEpisodeString = progression.nextEpisodeString,
                        nextEpisodeData = episodeInfo,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                // If it fails, degrade gracefully
                _bottomSheetState.value = BottomSheetUiState(
                    show = showInfo,
                    isLoading = false,
                    hasError = true
                )
            }
        }
    }

    fun toggleWatchlist(onSuccess: (String) -> Unit = {}) {
        if (_isProcessingAction.value) return
        
        val state = _bottomSheetState.value
        val show = state.show ?: return
        
        _isProcessingAction.value = true
        viewModelScope.launch {
            try {
                if (state.inWatchlist) {
                    watchlistRepository.removeFromWatchlist(show.id, show.mediaType)
                    _bottomSheetState.value = state.copy(inWatchlist = false)
                    onSuccess("Removed from Watchlist")
                } else {
                    watchlistRepository.insertToWatchlist(
                        WatchlistEntity(
                            tmdbId = show.id,
                            mediaType = if (show.mediaType == "movie") MediaType.MOVIE else MediaType.TV,
                            title = show.title,
                            posterPath = show.posterPath
                        )
                    )
                    _bottomSheetState.value = state.copy(inWatchlist = true)
                    onSuccess("Added to Watchlist")
                }
                syncManager.syncWatchlistNow()
            } finally {
                _isProcessingAction.value = false
            }
        }
    }

    fun toggleWatched(onSuccess: (String) -> Unit = {}) {
        if (_isProcessingAction.value) return
        
        val state = _bottomSheetState.value
        val show = state.show ?: return
        
        _isProcessingAction.value = true
        viewModelScope.launch {
            try {
                if (show.mediaType == "movie") {
                    if (state.isWatched) {
                        watchedMovieDao.unwatchMovie(show.id)
                        _bottomSheetState.value = state.copy(isWatched = false)
                        onSuccess("Removed from Watched")
                    } else {
                        watchedMovieDao.upsertWatchedMovie(
                            movieId = show.id,
                            title = show.title,
                            posterPath = show.posterPath
                        )
                        watchlistRepository.removeFromWatchlist(show.id, show.mediaType)
                        _bottomSheetState.value = state.copy(isWatched = true, inWatchlist = false)
                        onSuccess("Marked as Watched")
                    }
                } else {
                    if (state.hasError) {
                        onSuccess("Cannot determine next episode while offline")
                        return@launch
                    }
                    val next = state.nextEpisodeData ?: run {
                        onSuccess("No unwatched episodes available")
                        return@launch
                    }
                    var ep = seasonRepository.getEpisodesBySeason(show.id, next.seasonNumber)
                        .find { it.episodeNumber == next.episodeNumber }
                        
                    if (ep == null) {
                        try {
                            val seasonDetail = seasonRepository.fetchSeasonDetail(show.id, next.seasonNumber).getOrNull()
                            ep = seasonDetail?.find { it.episodeNumber == next.episodeNumber }
                        } catch (e: Exception) {
                            // Network failure
                        }
                    }
                    
                    if (ep != null) {
                        watchedEpisodeRepository.upsertWatchedEpisode(
                            showId = show.id,
                            tmdbEpisodeId = ep.id,
                            seasonNumber = next.seasonNumber,
                            episodeNumber = next.episodeNumber
                        )
                    } 
                        
                    onSuccess("Marked as Watched")

                    // Proactively fetch next season if necessary
                    val progression = getNextEpisodeUseCase(show.id)
                    if (!progression.isCompleted && progression.nextEpisodeData != null) {
                        val nextEp = progression.nextEpisodeData
                        val existing = seasonRepository.getEpisodesBySeason(show.id, nextEp.seasonNumber)
                        if (existing.isEmpty()) {
                            try {
                                seasonRepository.fetchSeasonDetail(show.id, nextEp.seasonNumber)
                            } catch (e: Exception) {
                                // Silently fail
                            }
                        }
                    }

                    // Refresh the bottom sheet state to show the *next* next episode
                    // Note: need to re-fetch ShowEntity since openBottomSheet requires it.
                    // Actually, we can fetch from showRepository.
                    val updatedShow = showRepository.observeShow(show.id, show.mediaType).firstOrNull()
                    if (updatedShow != null) {
                        openBottomSheet(updatedShow)
                    }
                }
                syncManager.syncWatchedEpisodesNow()
                syncManager.syncWatchlistNow()
            } catch (e: Exception) {
                onSuccess("Action failed: ${e.toAppError().message}")
            } finally {
                _isProcessingAction.value = false
            }
        }
    }

    fun skipEpisodeAction(onSuccess: (String) -> Unit = {}) {
        if (_isProcessingAction.value) return
        val state = _bottomSheetState.value
        val show = state.show ?: return
        if (state.hasError) {
            onSuccess("Cannot determine next episode while offline")
            return
        }
        val next = state.nextEpisodeData ?: return

        _isProcessingAction.value = true
        viewModelScope.launch {
            try {
                if (show.mediaType == "tv") {
                    var ep = seasonRepository.getEpisodesBySeason(show.id, next.seasonNumber)
                        .find { it.episodeNumber == next.episodeNumber }
                        
                    if (ep == null) {
                        try {
                            val seasonDetail = seasonRepository.fetchSeasonDetail(show.id, next.seasonNumber).getOrNull()
                            ep = seasonDetail?.find { it.episodeNumber == next.episodeNumber }
                        } catch (e: Exception) {
                            // Network failure
                        }
                    }
                    
                    if (ep != null) {
                        watchedEpisodeRepository.upsertWatchedEpisode(
                            showId = show.id,
                            tmdbEpisodeId = ep.id,
                            seasonNumber = next.seasonNumber,
                            episodeNumber = next.episodeNumber,
                            isSkipped = true
                        )
                    } 
                    onSuccess("Skipped Episode")
                    val updatedShow = showRepository.observeShow(show.id, show.mediaType).firstOrNull()
                    if (updatedShow != null) {
                        openBottomSheet(updatedShow)
                    }
                }
                syncManager.syncWatchedEpisodesNow()
            } catch (e: Exception) {
                onSuccess("Action failed: ${e.toAppError().message}")
            } finally {
                _isProcessingAction.value = false
            }
        }
    }
}
