package dev.sequel.app.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.local.dao.EpisodeDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.dao.WatchlistDao
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import dev.sequel.app.data.local.entity.WatchlistEntity
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.dto.TmdbNextEpisodeDto
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEntity
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.domain.usecase.GetNextEpisodeUseCase
import dev.sequel.app.presentation.state.BottomSheetUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    data class Error(val message: String) : SearchUiState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val tmdbApiService: TmdbApiService,
    private val watchlistDao: WatchlistDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val showDao: ShowDao,
    private val episodeDao: EpisodeDao,
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
        _activeGenreId,
        _retryTrigger
    ) { query, genreId, retryCount ->
        Triple(query, genreId, retryCount)
    }
        .debounce(500L)
        .distinctUntilChanged()
        .flatMapLatest { (query, genreId, _) ->
            if (query.isBlank()) {
                flow { emit(SearchUiState.Idle) }
            } else {
                flow {
                    emit(SearchUiState.Loading)
                    try {
                        if (genreId != null) {
                            coroutineScope {
                                val tvDef1 = async { tmdbApiService.discoverTv(withGenres = genreId.toString(), page = 1) }
                                val tvDef2 = async { tmdbApiService.discoverTv(withGenres = genreId.toString(), page = 2) }
                                val movDef1 = async { tmdbApiService.discoverMovies(withGenres = genreId.toString(), page = 1) }
                                val movDef2 = async { tmdbApiService.discoverMovies(withGenres = genreId.toString(), page = 2) }
                                
                                val tvResults = (tvDef1.await().results + tvDef2.await().results)
                                    .map { it.copy(mediaType = "tv").toEntity("tv") }
                                val movResults = (movDef1.await().results + movDef2.await().results)
                                    .map { it.copy(mediaType = "movie").toEntity("movie") }
                                
                                // Interleave to show a mix of TV and Movies
                                val combined = tvResults.zip(movResults) { tv, movie -> listOf(tv, movie) }
                                    .flatten() + tvResults.drop(movResults.size) + movResults.drop(tvResults.size)
                                    
                                emit(SearchUiState.Success(combined))
                            }
                        } else {
                            val response = tmdbApiService.searchMulti(query.trim())
                            val results = response.results
                                .filter { it.mediaType == "tv" || it.mediaType == "movie" }
                                .map { it.toEntity(fallbackMediaType = "movie") }
                            emit(SearchUiState.Success(results))
                        }
                    } catch (e: Exception) {
                        val friendlyMessage = when {
                            e is java.net.UnknownHostException || e is java.net.ConnectException -> "Couldn't connect. Check your internet connection."
                            e is java.net.SocketTimeoutException -> "The request took too long. Please try again."
                            e is retrofit2.HttpException -> "Something went wrong while loading results."
                            else -> "Something went wrong. Please try again."
                        }
                        emit(SearchUiState.Error(friendlyMessage))
                    }
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
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
        _bottomSheetState.value = BottomSheetUiState(show = show, isLoading = true)
        viewModelScope.launch {
            try {
                // Ensure foreign key constraints won't fail for watched episodes
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
                // If it fails, degrade gracefully
                _bottomSheetState.value = BottomSheetUiState(
                    show = show,
                    isLoading = false
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
                        
                        onSuccess("Marked as Watched")
                        // Refresh the bottom sheet state to show the *next* next episode
                        openBottomSheet(show)
                    } else {
                        onSuccess("Episode not found")
                    }
                }
                syncManager.syncWatchedEpisodesNow()
            } catch (e: Exception) {
                // Fail gracefully
            } finally {
                _isProcessingAction.value = false
            }
        }
    }

    fun skipEpisodeAction(onSuccess: (String) -> Unit = {}) {
        if (_isProcessingAction.value) return
        val state = _bottomSheetState.value
        val show = state.show ?: return
        val next = state.nextEpisodeData ?: return

        _isProcessingAction.value = true
        viewModelScope.launch {
            try {
                if (show.mediaType == "tv") {
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
                                syncStatus = SyncStatus.PENDING,
                                isSkipped = true
                            )
                        )
                        onSuccess("Skipped Episode")
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
