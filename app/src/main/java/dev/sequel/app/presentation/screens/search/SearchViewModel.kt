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
import dev.sequel.app.domain.error.AppError
import dev.sequel.app.domain.error.toAppError

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val results: List<ShowEntity>) : SearchUiState
    data class Error(val error: AppError) : SearchUiState
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
        _searchFilter,
        _activeGenreId,
        _retryTrigger
    ) { query, filter, genreId, retryCount ->
        arrayOf(query, filter, genreId, retryCount)
    }
        .debounce(500L)
        .distinctUntilChanged { old, new -> old.contentEquals(new) }
        .flatMapLatest { params ->
            val query = params[0] as String
            val filter = params[1] as String
            val genreId = params[2] as Int?
            
            if (query.isBlank()) {
                flow { emit(SearchUiState.Idle) }
            } else {
                flow {
                    emit(SearchUiState.Loading)
                    try {
                        if (genreId != null) {
                            coroutineScope {
                                val combined = if (filter == "TV Shows") {
                                    val tvDef1 = async { tmdbApiService.discoverTv(withGenres = genreId.toString(), page = 1) }
                                    val tvDef2 = async { tmdbApiService.discoverTv(withGenres = genreId.toString(), page = 2) }
                                    (tvDef1.await().results + tvDef2.await().results).map { it.copy(mediaType = "tv").toEntity("tv") }
                                } else if (filter == "Movies") {
                                    val movDef1 = async { tmdbApiService.discoverMovies(withGenres = genreId.toString(), page = 1) }
                                    val movDef2 = async { tmdbApiService.discoverMovies(withGenres = genreId.toString(), page = 2) }
                                    (movDef1.await().results + movDef2.await().results).map { it.copy(mediaType = "movie").toEntity("movie") }
                                } else {
                                    val tvDef1 = async { tmdbApiService.discoverTv(withGenres = genreId.toString(), page = 1) }
                                    val tvDef2 = async { tmdbApiService.discoverTv(withGenres = genreId.toString(), page = 2) }
                                    val movDef1 = async { tmdbApiService.discoverMovies(withGenres = genreId.toString(), page = 1) }
                                    val movDef2 = async { tmdbApiService.discoverMovies(withGenres = genreId.toString(), page = 2) }
                                    val tvResults = (tvDef1.await().results + tvDef2.await().results).map { it.copy(mediaType = "tv").toEntity("tv") }
                                    val movResults = (movDef1.await().results + movDef2.await().results).map { it.copy(mediaType = "movie").toEntity("movie") }
                                    tvResults.zip(movResults) { tv, movie -> listOf(tv, movie) }.flatten() + tvResults.drop(movResults.size) + movResults.drop(tvResults.size)
                                }
                                emit(SearchUiState.Success(combined))
                            }
                        } else {
                            val results = if (filter == "TV Shows") {
                                tmdbApiService.searchTv(query.trim()).results.map { it.copy(mediaType = "tv").toEntity("tv") }
                            } else if (filter == "Movies") {
                                tmdbApiService.searchMovie(query.trim()).results.map { it.copy(mediaType = "movie").toEntity("movie") }
                            } else {
                                tmdbApiService.searchMulti(query.trim()).results
                                    .filter { it.mediaType == "tv" || it.mediaType == "movie" }
                                    .map { it.toEntity(fallbackMediaType = "movie") }
                            }
                            emit(SearchUiState.Success(results))
                        }
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
                syncManager.syncWatchedEpisodesNow()
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
                                episodeId = -1,
                                seasonNumber = -1,
                                episodeNumber = -1,
                                syncStatus = SyncStatus.PENDING
                            )
                        )
                        watchlistDao.removeFromWatchlist(show.id)
                        _bottomSheetState.value = state.copy(isWatched = true, inWatchlist = false)
                        onSuccess("Marked as Watched")
                    }
                } else {
                    val next = state.nextEpisodeData ?: return@launch
                    var ep = episodeDao.getEpisodesBySeason(show.id, next.seasonNumber)
                        .find { it.episodeNumber == next.episodeNumber }
                        
                    if (ep == null) {
                        val seasonDetail = tmdbApiService.getSeasonDetail(show.id, next.seasonNumber)
                        val episodeEntities = seasonDetail.episodes.map { it.toEntity(show.id) }
                        episodeDao.insertEpisodes(episodeEntities)
                        ep = seasonDetail.episodes.find { it.episodeNumber == next.episodeNumber }?.toEntity(show.id)
                    }
                    
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
        val next = state.nextEpisodeData ?: return

        _isProcessingAction.value = true
        viewModelScope.launch {
            try {
                if (show.mediaType == "tv") {
                    var ep = episodeDao.getEpisodesBySeason(show.id, next.seasonNumber)
                        .find { it.episodeNumber == next.episodeNumber }
                        
                    if (ep == null) {
                        val seasonDetail = tmdbApiService.getSeasonDetail(show.id, next.seasonNumber)
                        val episodeEntities = seasonDetail.episodes.map { it.toEntity(show.id) }
                        episodeDao.insertEpisodes(episodeEntities)
                        ep = seasonDetail.episodes.find { it.episodeNumber == next.episodeNumber }?.toEntity(show.id)
                    }
                    
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
                onSuccess("Action failed: ${e.toAppError().message}")
            } finally {
                _isProcessingAction.value = false
            }
        }
    }
}
