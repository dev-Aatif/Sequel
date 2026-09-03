package dev.sequel.app.presentation.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.dao.WatchlistDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import dev.sequel.app.data.local.entity.WatchlistEntity
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEntity
import dev.sequel.app.data.sync.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val syncManager: SyncManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchFilter = MutableStateFlow("All") // "All", "TV Shows", "Movies"
    val searchFilter = _searchFilter.asStateFlow()

    val searchState: StateFlow<SearchUiState> = _searchQuery
        .debounce(500L)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flow { emit(SearchUiState.Idle) }
            } else {
                flow {
                    emit(SearchUiState.Loading)
                    try {
                        val response = tmdbApiService.searchMulti(query)
                        val results = response.results
                            .filter { it.mediaType == "tv" || it.mediaType == "movie" }
                            .map { it.toEntity(fallbackMediaType = "movie") }
                        emit(SearchUiState.Success(results))
                    } catch (e: Exception) {
                        emit(SearchUiState.Error(e.localizedMessage ?: "Unknown error"))
                    }
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SearchUiState.Idle
        )

    fun onQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onFilterChange(filter: String) {
        _searchFilter.value = filter
    }

    fun addToWatchlist(show: ShowEntity) {
        viewModelScope.launch {
            watchlistDao.insertToWatchlist(
                WatchlistEntity(
                    tmdbId = show.id,
                    mediaType = if (show.mediaType == "movie") MediaType.MOVIE else MediaType.TV,
                    title = show.title,
                    posterPath = show.posterPath
                )
            )
        }
    }

    fun markAsWatched(show: ShowEntity) {
        viewModelScope.launch {
            if (show.mediaType == "tv") {
                try {
                    val seasonDetail = tmdbApiService.getSeasonDetail(show.id, 1)
                    val firstEpisode = seasonDetail.episodes.firstOrNull()
                    if (firstEpisode != null) {
                        watchedEpisodeDao.insertWatchedEpisode(
                            WatchedEpisodeEntity(
                                mediaType = MediaType.TV,
                                showId = show.id,
                                episodeId = firstEpisode.id,
                                seasonNumber = 1,
                                episodeNumber = 1,
                                syncStatus = SyncStatus.PENDING
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Fail gracefully if S1 cannot be fetched to prevent corrupted state (-1).
                    return@launch
                }
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
            }
            syncManager.syncWatchedEpisodesNow()
        }
    }
}
