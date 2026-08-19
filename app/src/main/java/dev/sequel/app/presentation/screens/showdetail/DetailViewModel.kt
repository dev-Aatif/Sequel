package dev.sequel.app.presentation.screens.showdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.dao.EpisodeDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.data.remote.tmdb.dto.TmdbSeasonDetailDto
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEntity
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEpisodeEntities

import dev.sequel.app.domain.repository.ShowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI model for a single episode row in the detail screen.
 */
data class EpisodeUi(
    val id: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val overview: String?,
    val stillPath: String?,
    val airDate: String?,
    val runtime: Int?,
    val isWatched: Boolean
)

/**
 * UI model for a season section in the detail screen.
 */
data class SeasonUi(
    val seasonNumber: Int,
    val name: String,
    val episodes: List<EpisodeUi>
)

/**
 * UI state for the Show Detail screen.
 */
sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(
        val show: ShowEntity,
        val seasons: List<SeasonUi>,
        val isMovieWatched: Boolean = false,
        val dropOffInsight: String? = null
    ) : DetailUiState
    data class Error(val message: String) : DetailUiState
}


@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val showRepository: ShowRepository,
    private val tmdbApiService: TmdbApiService,
    private val episodeDao: EpisodeDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val syncManager: SyncManager,
    private val supabaseSyncService: dev.sequel.app.data.remote.supabase.SupabaseSyncService
) : ViewModel() {

    private val showId: Int = savedStateHandle.get<Int>("showId")!!
    private val mediaType: String = savedStateHandle.get<String>("mediaType") ?: "tv"

    private val _detailState = MutableStateFlow<DetailInternalState>(DetailInternalState.Loading)

    /** Set of watched episode IDs, observed from Room reactively. */
    private val watchedFlow = watchedEpisodeDao.observeWatchedByShow(showId)

    /**
     * Combines the fetched show+season data with the reactive watched-episode flow
     * so the UI always reflects the latest watched state without re-fetching.
     */
    val uiState: StateFlow<DetailUiState> = combine(
        _detailState,
        watchedFlow
    ) { internal, watchedList ->
        when (internal) {
            is DetailInternalState.Loading -> DetailUiState.Loading
            is DetailInternalState.Error -> DetailUiState.Error(internal.message)
            is DetailInternalState.Loaded -> {
                val watchedIds = watchedList.map { it.episodeId }.toSet()
                val isMovieWatched = internal.show.mediaType == "movie" && watchedList.isNotEmpty()
                DetailUiState.Success(
                    show = internal.show,
                    isMovieWatched = isMovieWatched,
                    dropOffInsight = internal.dropOffInsight,
                    seasons = internal.seasonDetails.map { seasonDetail ->
                        SeasonUi(
                            seasonNumber = seasonDetail.seasonNumber,
                            name = seasonDetail.name,
                            episodes = seasonDetail.episodes.map { ep ->
                                EpisodeUi(
                                    id = ep.id,
                                    seasonNumber = ep.seasonNumber,
                                    episodeNumber = ep.episodeNumber,
                                    name = ep.name,
                                    overview = ep.overview.ifBlank { null },
                                    stillPath = ep.stillPath,
                                    airDate = ep.airDate,
                                    runtime = ep.runtime,
                                    isWatched = ep.id in watchedIds
                                )
                            }
                        )
                    }
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailUiState.Loading)

    init {
        loadShowDetail()
    }

    fun loadShowDetail() {
        _detailState.value = DetailInternalState.Loading
        viewModelScope.launch {
            try {
                // 1. Fetch show detail (caches show + season summaries in Room)
                val showResult = if (mediaType == "movie") {
                    showRepository.fetchMovieDetail(showId)
                } else {
                    showRepository.fetchShowDetail(showId)
                }

                val show = showResult.getOrThrow()

                if (mediaType == "movie") {
                    // Movies don't have seasons/episodes
                    _detailState.value = DetailInternalState.Loaded(
                        show = show,
                        seasonDetails = emptyList()
                    )
                    return@launch
                }

                // 2. Fetch each season's full episode list from TMDB
                val showDetail = tmdbApiService.getTvShowDetail(showId)
                val seasonDetails = showDetail.seasons
                    .filter { it.seasonNumber > 0 } // exclude "Specials" (season 0)
                    .map { seasonSummary ->
                        val detail = tmdbApiService.getSeasonDetail(showId, seasonSummary.seasonNumber)
                        // Cache episodes to Room
                        episodeDao.insertEpisodes(detail.toEpisodeEntities(showId))
                        detail
                    }

                val dropOff = if (mediaType == "tv") calculateDropOff(showId) else null

                _detailState.value = DetailInternalState.Loaded(
                    show = show,
                    seasonDetails = seasonDetails,
                    dropOffInsight = dropOff
                )
            } catch (e: Exception) {
                _detailState.value = DetailInternalState.Error(
                    e.localizedMessage ?: "Failed to load show details"
                )
            }
        }
    }

    /**
     * Toggle an episode's watched status.
     * Inserts or deletes the WatchedEpisodeEntity in Room with syncStatus = PENDING.
     */
    fun toggleEpisodeWatched(episode: EpisodeUi) {
        viewModelScope.launch {
            if (episode.isWatched) {
                watchedEpisodeDao.unwatchEpisode(episode.id)
            } else {
                watchedEpisodeDao.insertWatchedEpisode(
                    WatchedEpisodeEntity(
                        mediaType = dev.sequel.app.data.local.entity.MediaType.TV,
                        episodeId = episode.id,
                        showId = showId,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        syncStatus = SyncStatus.PENDING
                    )
                )
            }
            // Trigger background sync to upload the change to Supabase
            syncManager.syncWatchedEpisodesNow()
        }
    }

    /**
     * Toggle a movie's watched status.
     * Inserts or deletes the WatchedEpisodeEntity with null episode fields.
     */
    fun toggleMovieWatched(isWatched: Boolean) {
        viewModelScope.launch {
            if (isWatched) {
                watchedEpisodeDao.unwatchAllForShow(showId)
            } else {
                watchedEpisodeDao.insertWatchedEpisode(
                    WatchedEpisodeEntity(
                        mediaType = dev.sequel.app.data.local.entity.MediaType.MOVIE,
                        showId = showId,
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

    private suspend fun calculateDropOff(showId: Int): String? {
        try {
            val history = supabaseSyncService.fetchCommunityWatchHistoryForShow(showId)
            if (history.isEmpty()) return null
            
            // Group by (season, episode) and count unique users
            val episodeCounts = history
                .filter { it.seasonNumber != null && it.episodeNumber != null }
                .groupBy { Pair(it.seasonNumber!!, it.episodeNumber!!) }
                .mapValues { (_, episodes) -> episodes.map { it.userId }.distinct().count() }
                
            if (episodeCounts.size < 2) return null
            
            // Sort by season and episode
            val sortedEpisodes = episodeCounts.keys.sortedWith(compareBy({ it.first }, { it.second }))
            
            var maxDropPercentage = 0.0
            var maxDropEpisode: Pair<Int, Int>? = null
            
            for (i in 0 until sortedEpisodes.size - 1) {
                val currentEp = sortedEpisodes[i]
                val nextEp = sortedEpisodes[i+1]
                val currentCount = episodeCounts[currentEp] ?: 0
                val nextCount = episodeCounts[nextEp] ?: 0
                
                if (currentCount > 0 && nextCount < currentCount) {
                    val drop = (currentCount - nextCount).toDouble() / currentCount
                    if (drop > maxDropPercentage && currentCount > 2) { // Minimum 3 users to be relevant
                        maxDropPercentage = drop
                        maxDropEpisode = currentEp
                    }
                }
            }
            
            if (maxDropPercentage >= 0.2 && maxDropEpisode != null) {
                val percentageInt = (maxDropPercentage * 100).toInt()
                return "$percentageInt% of viewers dropped off after S${maxDropEpisode.first}E${maxDropEpisode.second}"
            }
        } catch (e: Exception) {
            // Ignore errors for insight calculation
        }
        return null
    }
}

/**
 * Internal state that holds the raw fetched data before combining with watched status.
 */
private sealed interface DetailInternalState {
    data object Loading : DetailInternalState
    data class Loaded(
        val show: ShowEntity,
        val seasonDetails: List<TmdbSeasonDetailDto>,
        val dropOffInsight: String? = null
    ) : DetailInternalState
    data class Error(val message: String) : DetailInternalState
}
