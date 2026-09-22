package dev.sequel.app.presentation.screens.seasondetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.dao.EpisodeDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEpisodeEntities
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.presentation.screens.showdetail.EpisodeUi
import dev.sequel.app.presentation.screens.showdetail.SeasonUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.sequel.app.domain.error.AppError
import dev.sequel.app.domain.error.toAppError

sealed interface SeasonDetailUiState {
    data object Loading : SeasonDetailUiState
    data class Success(val season: SeasonUi) : SeasonDetailUiState
    data class Error(val error: AppError) : SeasonDetailUiState
}

@HiltViewModel
class SeasonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val episodeDao: EpisodeDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val tmdbApiService: TmdbApiService,
    private val syncManager: SyncManager
) : ViewModel() {

    private val showId: Int = savedStateHandle.get<Int>("showId") ?: -1
    private val seasonNumber: Int = savedStateHandle.get<Int>("seasonNumber") ?: -1

    private val _loadingError = MutableStateFlow<AppError?>(null)

    val uiState: StateFlow<SeasonDetailUiState> = combine(
        episodeDao.observeEpisodesBySeason(showId, seasonNumber),
        watchedEpisodeDao.observeWatchedByShow(showId, "tv"),
        _loadingError
    ) { episodes, watchedList, error ->
        if (error != null) {
            SeasonDetailUiState.Error(error)
        } else if (episodes.isEmpty()) {
            SeasonDetailUiState.Loading
        } else {
            val watchedMap = watchedList.associateBy { it.episodeId }
            val seasonUi = SeasonUi(
                seasonNumber = seasonNumber,
                name = "Season $seasonNumber",
                episodeCount = episodes.size,
                watchedCount = episodes.count { watchedMap.containsKey(it.id) && watchedMap[it.id]?.isSkipped != true },
                episodes = episodes.map { ep ->
                    EpisodeUi(
                        id = ep.id,
                        seasonNumber = ep.seasonNumber,
                        episodeNumber = ep.episodeNumber,
                        name = ep.name,
                        overview = ep.overview?.takeIf { it.isNotBlank() },
                        stillPath = ep.stillPath,
                        airDate = ep.airDate,
                        runtime = ep.runtime,
                        isWatched = watchedMap.containsKey(ep.id) && watchedMap[ep.id]?.isSkipped != true,
                        isSkipped = watchedMap[ep.id]?.isSkipped == true
                    )
                }
            )
            SeasonDetailUiState.Success(seasonUi)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SeasonDetailUiState.Loading)

    init {
        fetchEpisodesIfNeeded()
    }

    fun retry() {
        _loadingError.value = null
        fetchEpisodesIfNeeded()
    }

    private fun fetchEpisodesIfNeeded() {
        if (showId == -1 || seasonNumber == -1) {
            _loadingError.value = AppError.Validation("Invalid arguments")
            return
        }
        viewModelScope.launch {
            try {
                // Only fetch if empty locally
                val localEpisodes = episodeDao.getEpisodesBySeason(showId, seasonNumber)
                if (localEpisodes.isEmpty()) {
                    val detail = tmdbApiService.getSeasonDetail(showId, seasonNumber)
                    episodeDao.insertEpisodes(detail.toEpisodeEntities(showId))
                }
            } catch (e: Exception) {
                // If local data exists, it will just show that. Otherwise error.
                if (episodeDao.getEpisodesBySeason(showId, seasonNumber).isEmpty()) {
                    _loadingError.value = e.toAppError()
                }
            }
        }
    }

    fun toggleEpisodeWatched(episode: EpisodeUi) {
        viewModelScope.launch {
            if (episode.isWatched) {
                watchedEpisodeDao.unwatchEpisode(episode.id)
            } else {
                watchedEpisodeDao.insertWatchedEpisode(
                    WatchedEpisodeEntity(
                        mediaType = MediaType.TV,
                        episodeId = episode.id,
                        showId = showId,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        syncStatus = SyncStatus.PENDING
                    )
                )
            }
            syncManager.syncWatchedEpisodesNow()
        }
    }

    fun markSeasonWatched(episodes: List<EpisodeUi>) {
        if (episodes.isEmpty()) return
        
        viewModelScope.launch {
            val entities = episodes.map { episode ->
                WatchedEpisodeEntity(
                    mediaType = MediaType.TV,
                    episodeId = episode.id,
                    showId = showId,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    syncStatus = SyncStatus.PENDING
                )
            }
            watchedEpisodeDao.insertWatchedEpisodes(entities)
            syncManager.syncWatchedEpisodesNow()
        }
    }

    fun unwatchSeason(seasonNumber: Int) {
        viewModelScope.launch {
            watchedEpisodeDao.unwatchSeason(showId, seasonNumber)
            syncManager.syncWatchedEpisodesNow()
        }
    }
}
