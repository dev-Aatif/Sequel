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
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tmdbApiService: dev.sequel.app.data.remote.tmdb.TmdbApiService,
    private val showRepository: ShowRepository,
    private val watchlistDao: dev.sequel.app.data.local.dao.WatchlistDao,
    private val watchedEpisodeDao: dev.sequel.app.data.local.dao.WatchedEpisodeDao,
    private val showDao: dev.sequel.app.data.local.dao.ShowDao,
    private val syncManager: dev.sequel.app.data.sync.SyncManager
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

    private val _loopingMovies = MutableStateFlow<List<ShowEntity>>(emptyList())
    val loopingMovies = _loopingMovies.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val response = tmdbApiService.getTrending(mediaType = "movie", page = 1)
                _loopingMovies.value = response.results.take(6).map { it.toEntity("movie") }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun addToWatchlist(show: ShowEntity) {
        viewModelScope.launch {
            watchlistDao.insertToWatchlist(
                dev.sequel.app.data.local.entity.WatchlistEntity(
                    tmdbId = show.id,
                    mediaType = if (show.mediaType == "movie") dev.sequel.app.data.local.entity.MediaType.MOVIE else dev.sequel.app.data.local.entity.MediaType.TV,
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
                            dev.sequel.app.data.local.entity.WatchedEpisodeEntity(
                                mediaType = dev.sequel.app.data.local.entity.MediaType.TV,
                                showId = show.id,
                                episodeId = firstEpisode.id,
                                seasonNumber = 1,
                                episodeNumber = 1,
                                syncStatus = dev.sequel.app.data.local.entity.SyncStatus.PENDING
                            )
                        )
                    }
                } catch (e: Exception) {
                    return@launch
                }
            } else {
                watchedEpisodeDao.insertWatchedEpisode(
                    dev.sequel.app.data.local.entity.WatchedEpisodeEntity(
                        mediaType = dev.sequel.app.data.local.entity.MediaType.MOVIE,
                        showId = show.id,
                        episodeId = null,
                        seasonNumber = null,
                        episodeNumber = null,
                        syncStatus = dev.sequel.app.data.local.entity.SyncStatus.PENDING
                    )
                )
            }
            syncManager.syncWatchedEpisodesNow()
        }
    }
}
