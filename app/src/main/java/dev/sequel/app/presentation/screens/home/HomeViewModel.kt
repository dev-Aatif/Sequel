package dev.sequel.app.presentation.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.domain.repository.ShowRepository
import dev.sequel.app.domain.repository.SeasonRepository
import dev.sequel.app.domain.repository.WatchedEpisodeRepository
import dev.sequel.app.domain.repository.WatchlistRepository
import dev.sequel.app.domain.repository.SyncRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.sequel.app.domain.usecase.GetNextEpisodeUseCase
import dev.sequel.app.presentation.state.BottomSheetEpisodeInfo
import dev.sequel.app.presentation.state.BottomSheetShowInfo
import dev.sequel.app.presentation.state.BottomSheetUiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.WatchlistEntity
import javax.inject.Inject
import dev.sequel.app.domain.error.toAppError
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

sealed interface HomeUiEvent {
    data class ShowToast(val message: String) : HomeUiEvent
    data class ShowSnackbar(val message: String) : HomeUiEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val showRepository: ShowRepository,
    private val seasonRepository: SeasonRepository,
    private val watchlistRepository: WatchlistRepository,
    private val watchedEpisodeRepository: WatchedEpisodeRepository,
    private val syncRepository: SyncRepository,
    private val getNextEpisodeUseCase: GetNextEpisodeUseCase
) : ViewModel() {

    private val _uiEvent = Channel<HomeUiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _mediaType = MutableStateFlow(savedStateHandle.get<String>("mediaType") ?: MediaType.TV.name.lowercase())
    val mediaType: StateFlow<String> = _mediaType.asStateFlow()

    val pagedShows: Flow<PagingData<ShowEntity>> = _mediaType
        .flatMapLatest { type ->
            showRepository.getPagedTrendingShows(type)
        }
        .cachedIn(viewModelScope)

    fun setMediaType(type: String) {
        savedStateHandle["mediaType"] = type
        _mediaType.value = type
    }
    
    val continueWatchingTvShows: StateFlow<List<ShowEntity>> = showRepository.observeStartedTvShows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasAnyTrackingHistory: StateFlow<Boolean?> = combine(
        watchlistRepository.observeWatchlist(),
        showRepository.observeStartedTvShows(),
        watchedEpisodeRepository.observeTotalMoviesWatched()
    ) { watchlist, startedTv, moviesWatched ->
        watchlist.isNotEmpty() || startedTv.isNotEmpty() || moviesWatched > 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isProcessingAction = MutableStateFlow(false)
    val isProcessingAction = _isProcessingAction.asStateFlow()

    private val _bottomSheetState = MutableStateFlow(BottomSheetUiState())
    val bottomSheetState = _bottomSheetState.asStateFlow()

    fun addToWatchlist(show: ShowEntity) {
        if (_isProcessingAction.value) return
        _isProcessingAction.value = true
        viewModelScope.launch {
            try {
                watchlistRepository.insertToWatchlist(
                    WatchlistEntity(
                        tmdbId = show.id,
                        mediaType = if (show.mediaType == MediaType.MOVIE.name.lowercase()) MediaType.MOVIE else MediaType.TV,
                        title = show.title,
                        posterPath = show.posterPath
                    )
                )
                showRepository.updateWatchlistStatus(show.id, show.mediaType, true)
                _uiEvent.send(HomeUiEvent.ShowToast("Added to Watchlist"))
            } catch (e: Exception) {
                _uiEvent.send(HomeUiEvent.ShowToast("Action failed: ${e.toAppError().message}"))
            } finally {
                _isProcessingAction.value = false
            }
        }
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
                showRepository.insertShow(show)
                val inWatchlist = watchlistRepository.observeIsInWatchlist(show.id, show.mediaType).firstOrNull() ?: false
                
                if (show.mediaType == MediaType.MOVIE.name.lowercase()) {
                    val watchedList = watchedEpisodeRepository.observeWatchedByShow(show.id, show.mediaType).firstOrNull() ?: emptyList()
                    val isMovieWatched = watchedList.isNotEmpty()
                    
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
                _bottomSheetState.value = BottomSheetUiState(show = showInfo, isLoading = false)
            }
        }
    }

    fun toggleWatchlist() {
        if (_isProcessingAction.value) return
        
        val state = _bottomSheetState.value
        val show = state.show ?: return
        
        _isProcessingAction.value = true
        viewModelScope.launch {
            try {
                if (state.inWatchlist) {
                    watchlistRepository.removeFromWatchlist(show.id, show.mediaType)
                    showRepository.updateWatchlistStatus(show.id, show.mediaType, false)
                    _bottomSheetState.value = state.copy(inWatchlist = false)
                    _uiEvent.send(HomeUiEvent.ShowToast("Removed from Watchlist"))
                } else {
                    watchlistRepository.insertToWatchlist(
                        WatchlistEntity(
                            tmdbId = show.id,
                            mediaType = if (show.mediaType == MediaType.MOVIE.name.lowercase()) MediaType.MOVIE else MediaType.TV,
                            title = show.title,
                            posterPath = show.posterPath
                        )
                    )
                    showRepository.updateWatchlistStatus(show.id, show.mediaType, true)
                    _bottomSheetState.value = state.copy(inWatchlist = true)
                    _uiEvent.send(HomeUiEvent.ShowToast("Added to Watchlist"))
                }
                syncRepository.syncWatchlistNow()
            } catch (e: Exception) {
                _uiEvent.send(HomeUiEvent.ShowToast("Action failed: ${e.toAppError().message}"))
            } finally {
                _isProcessingAction.value = false
            }
        }
    }

    fun toggleWatched() {
        if (_isProcessingAction.value) return
        
        val state = _bottomSheetState.value
        val show = state.show ?: return
        
        _isProcessingAction.value = true
        viewModelScope.launch {
            try {
                if (show.mediaType == MediaType.MOVIE.name.lowercase()) {
                    if (state.isWatched) {
                        watchedEpisodeRepository.unwatchAllForShow(show.id, show.mediaType)
                        _bottomSheetState.value = state.copy(isWatched = false)
                        _uiEvent.send(HomeUiEvent.ShowToast("Removed from Watched"))
                    } else {
                        watchedEpisodeRepository.upsertWatchedEpisode(
                            mediaType = MediaType.MOVIE,
                            showId = show.id,
                            episodeId = null,
                            seasonNumber = null,
                            episodeNumber = null
                        )
                        watchlistRepository.removeFromWatchlist(show.id, show.mediaType)
                        showRepository.updateWatchlistStatus(show.id, show.mediaType, false)
                        _bottomSheetState.value = state.copy(isWatched = true, inWatchlist = false)
                        _uiEvent.send(HomeUiEvent.ShowToast("Marked as Watched"))
                    }
                } else {
                    val next = state.nextEpisodeData ?: return@launch
                    var ep = seasonRepository.getEpisodesBySeason(show.id, next.seasonNumber)
                        .find { it.episodeNumber == next.episodeNumber }
                    
                    if (ep == null) {
                        try {
                            val episodeEntities = seasonRepository.fetchSeasonDetail(show.id, next.seasonNumber).getOrNull()
                            ep = episodeEntities?.find { it.episodeNumber == next.episodeNumber }
                        } catch (e: Exception) {
                            // Offline, ignore
                        }
                    }
                    
                    if (ep != null) {
                        watchedEpisodeRepository.upsertWatchedEpisode(
                            mediaType = MediaType.TV,
                            showId = show.id,
                            episodeId = ep.id,
                            seasonNumber = next.seasonNumber,
                            episodeNumber = next.episodeNumber
                        )
                    } else {
                        watchedEpisodeRepository.upsertWatchedEpisode(
                            mediaType = MediaType.TV,
                            showId = show.id,
                            episodeId = null,
                            seasonNumber = next.seasonNumber,
                            episodeNumber = next.episodeNumber
                        )
                    }
                    _uiEvent.send(HomeUiEvent.ShowToast("Marked as Watched"))
                    
                    val updatedShow = showRepository.observeShow(show.id, show.mediaType).firstOrNull()
                    if (updatedShow != null) {
                        openBottomSheet(updatedShow)
                    }
                }
                syncRepository.syncWatchedEpisodesNow()
                syncRepository.syncWatchlistNow()
            } catch (e: Exception) {
                _uiEvent.send(HomeUiEvent.ShowToast("Action failed: ${e.toAppError().message}"))
            } finally {
                _isProcessingAction.value = false
            }
        }
    }

    fun skipEpisodeAction() {
        if (_isProcessingAction.value) return
        val state = _bottomSheetState.value
        val show = state.show ?: return
        val next = state.nextEpisodeData ?: return

        _isProcessingAction.value = true
        viewModelScope.launch {
            try {
                if (show.mediaType == MediaType.TV.name.lowercase()) {
                    var ep = seasonRepository.getEpisodesBySeason(show.id, next.seasonNumber)
                        .find { it.episodeNumber == next.episodeNumber }
                        
                    if (ep == null) {
                        try {
                            val episodeEntities = seasonRepository.fetchSeasonDetail(show.id, next.seasonNumber).getOrNull()
                            ep = episodeEntities?.find { it.episodeNumber == next.episodeNumber }
                        } catch (e: Exception) {
                            // Offline
                        }
                    }
                    if (ep != null) {
                        watchedEpisodeRepository.upsertWatchedEpisode(
                            mediaType = MediaType.TV,
                            showId = show.id,
                            episodeId = ep.id,
                            seasonNumber = next.seasonNumber,
                            episodeNumber = next.episodeNumber,
                            isSkipped = true
                        )
                    } else {
                        watchedEpisodeRepository.upsertWatchedEpisode(
                            mediaType = MediaType.TV,
                            showId = show.id,
                            episodeId = null,
                            seasonNumber = next.seasonNumber,
                            episodeNumber = next.episodeNumber,
                            isSkipped = true
                        )
                    }
                    _uiEvent.send(HomeUiEvent.ShowToast("Skipped Episode"))
                    
                    val updatedShow = showRepository.observeShow(show.id, show.mediaType).firstOrNull()
                    if (updatedShow != null) {
                        openBottomSheet(updatedShow)
                    }
                }
                syncRepository.syncWatchedEpisodesNow()
            } catch (e: Exception) {
                _uiEvent.send(HomeUiEvent.ShowToast("Action failed: ${e.toAppError().message}"))
            } finally {
                _isProcessingAction.value = false
            }
        }
    }
}

