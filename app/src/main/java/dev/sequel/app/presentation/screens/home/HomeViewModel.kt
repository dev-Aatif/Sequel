package dev.sequel.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.domain.repository.ShowRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEntity
import dev.sequel.app.domain.usecase.GetNextEpisodeUseCase
import dev.sequel.app.presentation.state.BottomSheetUiState
import kotlinx.coroutines.flow.firstOrNull
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import dev.sequel.app.data.local.entity.WatchlistEntity
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tmdbApiService: dev.sequel.app.data.remote.tmdb.TmdbApiService,
    private val showRepository: ShowRepository,
    private val watchlistDao: dev.sequel.app.data.local.dao.WatchlistDao,
    private val watchedEpisodeDao: dev.sequel.app.data.local.dao.WatchedEpisodeDao,
    private val showDao: dev.sequel.app.data.local.dao.ShowDao,
    private val episodeDao: dev.sequel.app.data.local.dao.EpisodeDao,
    private val syncManager: dev.sequel.app.data.sync.SyncManager,
    private val getNextEpisodeUseCase: GetNextEpisodeUseCase
) : ViewModel() {

    private val _mediaType = MutableStateFlow("tv")
    val mediaType: StateFlow<String> = _mediaType.asStateFlow()

    val pagedShows: Flow<PagingData<ShowEntity>> = _mediaType
        .flatMapLatest { type ->
            showRepository.getPagedTrendingShows(type)
        }
        .cachedIn(viewModelScope)

    fun setMediaType(type: String) {
        _mediaType.value = type
    }
    
    val continueWatchingTvShows: StateFlow<List<ShowEntity>> = showDao.observeStartedTvShows()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trendingThisWeekMovies: StateFlow<List<ShowEntity>> = showDao.observeTrendingShows("movie", limit = 6)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isProcessingAction = MutableStateFlow(false)
    val isProcessingAction = _isProcessingAction.asStateFlow()

    private val _bottomSheetState = MutableStateFlow(BottomSheetUiState())
    val bottomSheetState = _bottomSheetState.asStateFlow()

    fun addToWatchlist(show: ShowEntity) {
        if (_isProcessingAction.value) return
        _isProcessingAction.value = true
        viewModelScope.launch {
            try {
                watchlistDao.insertToWatchlist(
                    dev.sequel.app.data.local.entity.WatchlistEntity(
                        tmdbId = show.id,
                        mediaType = if (show.mediaType == "movie") dev.sequel.app.data.local.entity.MediaType.MOVIE else dev.sequel.app.data.local.entity.MediaType.TV,
                        title = show.title,
                        posterPath = show.posterPath
                    )
                )
            } finally {
                _isProcessingAction.value = false
            }
        }
    }

    fun openBottomSheet(show: ShowEntity) {
        _bottomSheetState.value = BottomSheetUiState(show = show, isLoading = true)
        viewModelScope.launch {
            try {
                showDao.insertShow(show)
                val inWatchlist = watchlistDao.observeIsInWatchlist(show.id).firstOrNull() ?: false
                
                if (show.mediaType == "movie") {
                    val watchedList = watchedEpisodeDao.observeWatchedByShow(show.id).firstOrNull() ?: emptyList()
                    val isMovieWatched = watchedList.isNotEmpty()
                    
                    _bottomSheetState.value = BottomSheetUiState(
                        show = show,
                        inWatchlist = inWatchlist,
                        isWatched = isMovieWatched,
                        isLoading = false
                    )
                } else {
                    val progression = getNextEpisodeUseCase(show.id)
                    
                    _bottomSheetState.value = BottomSheetUiState(
                        show = show,
                        inWatchlist = inWatchlist,
                        isWatched = false,
                        isCompleted = progression.isCompleted,
                        nextEpisodeString = progression.nextEpisodeString,
                        nextEpisodeData = progression.nextEpisodeData,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _bottomSheetState.value = BottomSheetUiState(show = show, isLoading = false)
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
                    watchlistDao.removeFromWatchlist(show.id)
                    _bottomSheetState.value = state.copy(inWatchlist = false)
                    onSuccess("Removed from Watchlist")
                } else {
                    watchlistDao.insertToWatchlist(
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
                        watchedEpisodeDao.unwatchAllForShow(show.id)
                        _bottomSheetState.value = state.copy(isWatched = false)
                        onSuccess("Removed from Watched")
                    } else {
                        watchedEpisodeDao.insertWatchedEpisode(
                            WatchedEpisodeEntity(
                                mediaType = MediaType.MOVIE,
                                showId = show.id,
                                episodeId = null,
                                seasonNumber = null,
                                episodeNumber = null,
                                syncStatus = SyncStatus.PENDING
                            )
                        )
                        watchlistDao.removeFromWatchlist(show.id)
                        _bottomSheetState.value = state.copy(isWatched = true, inWatchlist = false)
                        onSuccess("Marked as Watched")
                    }
                } else {
                    val next = state.nextEpisodeData ?: return@launch
                    val seasonDetail = tmdbApiService.getSeasonDetail(show.id, next.seasonNumber)
                    val episodeEntities = seasonDetail.episodes.map { it.toEntity(show.id) }
                    episodeDao.insertEpisodes(episodeEntities)
                    
                    val ep = seasonDetail.episodes.find { it.episodeNumber == next.episodeNumber }
                    
                    if (ep != null) {
                        watchedEpisodeDao.insertWatchedEpisode(
                            WatchedEpisodeEntity(
                                mediaType = MediaType.TV,
                                showId = show.id,
                                episodeId = ep.id,
                                seasonNumber = next.seasonNumber,
                                episodeNumber = next.episodeNumber,
                                syncStatus = SyncStatus.PENDING
                            )
                        )
                        if (!state.inWatchlist) {
                            watchlistDao.insertToWatchlist(
                                WatchlistEntity(
                                    tmdbId = show.id,
                                    mediaType = MediaType.TV,
                                    title = show.title,
                                    posterPath = show.posterPath
                                )
                            )
                        }
                        onSuccess("Marked as Watched")
                        openBottomSheet(show)
                    } else {
                        onSuccess("Episode not found")
                    }
                }
                syncManager.syncWatchedEpisodesNow()
            } catch (e: Exception) {
            } finally {
                _isProcessingAction.value = false
            }
        }
    }
}
