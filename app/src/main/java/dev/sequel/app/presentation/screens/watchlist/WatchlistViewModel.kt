package dev.sequel.app.presentation.screens.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.dao.EpisodeDao
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import dev.sequel.app.data.sync.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEntity
import dev.sequel.app.domain.usecase.GetNextEpisodeUseCase
import dev.sequel.app.presentation.state.BottomSheetUiState
import dev.sequel.app.data.local.entity.WatchlistEntity
import dev.sequel.app.domain.error.AppError
import dev.sequel.app.domain.error.toAppError
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

data class UpNextItem(
    val showId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String?,
    val nextEpisodeName: String?,
    val nextEpisodeId: Int?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val watchedEpisodeCount: Int,
    val totalEpisodes: Int
)

/**
 * Represents an item in the "Watched" tab.
 */
data class WatchedItem(
    val showId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String?,
    val statusTag: String, // "Completed", "Up to Date", "In Progress", "Watched" (for movies)
    val episodesWatched: Int,
    val totalEpisodes: Int?
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val showDao: ShowDao,
    private val episodeDao: EpisodeDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val watchlistDao: dev.sequel.app.data.local.dao.WatchlistDao,
    private val syncManager: SyncManager,
    private val getNextEpisodeUseCase: GetNextEpisodeUseCase,
    private val tmdbApiService: dev.sequel.app.data.remote.tmdb.TmdbApiService,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {

    // ── Up Next ────────────────────────────────────────────────────

    private val upNextTvFlow = showDao.observeUpNextShows().map { tuples ->
        tuples.mapNotNull { tuple ->
            if (tuple.nextEpisode != null) {
                UpNextItem(
                    showId = tuple.show.id,
                    mediaType = tuple.show.mediaType,
                    title = tuple.show.title,
                    posterPath = tuple.show.posterPath,
                    nextEpisodeName = "S${tuple.nextEpisode.seasonNumber}E${tuple.nextEpisode.episodeNumber}: ${tuple.nextEpisode.name}",
                    nextEpisodeId = tuple.nextEpisode.id,
                    seasonNumber = tuple.nextEpisode.seasonNumber,
                    episodeNumber = tuple.nextEpisode.episodeNumber,
                    watchedEpisodeCount = tuple.watchedCount,
                    totalEpisodes = tuple.show.numberOfEpisodes ?: 0
                )
            } else null
        }
    }

    val upNextItems: StateFlow<List<UpNextItem>> = upNextTvFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Watchlist (Plan to Watch) ──────────────────────────────────

    val planToWatchItems = watchlistDao.observeWatchlist().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // ── Watched Tab ────────────────────────────────────────────────

    private val watchedTvShowsFlow = watchedEpisodeDao.observeWatchedTvShowIds().flatMapLatest { ids ->
        if (ids.isEmpty()) return@flatMapLatest flowOf(emptyList<WatchedItem>())
        
        val itemFlows = ids.map { showId ->
            combine(
                showDao.observeShowById(showId),
                watchedEpisodeDao.observeWatchedByShow(showId),
                episodeDao.observeCanonicalNextEpisode(showId)
            ) { show, watchedEpisodes, nextUnwatched ->
                if (show == null) return@combine null
                val watchedCount = watchedEpisodes.size
                val totalEpisodes = show.numberOfEpisodes
                
                val isCompleted = totalEpisodes != null && totalEpisodes > 0 && watchedCount >= totalEpisodes
                
                val statusTag = when {
                    isCompleted -> "Completed"
                    show.status in listOf("Ended", "Canceled") && nextUnwatched != null -> "In Progress"
                    show.status == "Returning Series" && nextUnwatched == null -> "Up to Date"
                    else -> "In Progress"
                }
                
                WatchedItem(
                    showId = show.id,
                    mediaType = "tv",
                    title = show.title,
                    posterPath = show.posterPath,
                    statusTag = statusTag,
                    episodesWatched = watchedCount,
                    totalEpisodes = totalEpisodes
                )
            }
        }
        combine(itemFlows) { items -> items.filterNotNull() }
    }

    private val watchedMoviesFlow = watchedEpisodeDao.observeWatchedMovieIds().flatMapLatest { ids ->
        if (ids.isEmpty()) return@flatMapLatest flowOf(emptyList<WatchedItem>())
        
        val itemFlows = ids.map { showId ->
            showDao.observeShowById(showId).map { show ->
                if (show == null) return@map null
                WatchedItem(
                    showId = show.id,
                    mediaType = "movie",
                    title = show.title,
                    posterPath = show.posterPath,
                    statusTag = "Watched",
                    episodesWatched = 1,
                    totalEpisodes = null
                )
            }
        }
        combine(itemFlows) { items -> items.filterNotNull() }
    }

    val watchedTvItems: StateFlow<List<WatchedItem>> = watchedTvShowsFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val watchedMovieItems: StateFlow<List<WatchedItem>> = watchedMoviesFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // ── Actions ────────────────────────────────────────────────────

    fun markAsWatched(item: UpNextItem) {
        viewModelScope.launch {
            if (item.mediaType == "tv") {
                if (item.nextEpisodeId != null) {
                    watchedEpisodeDao.upsertWatchedEpisode(
                        mediaType = MediaType.TV,
                        showId = item.showId,
                        episodeId = item.nextEpisodeId,
                        seasonNumber = item.seasonNumber,
                        episodeNumber = item.episodeNumber
                    )
                    watchlistDao.removeFromWatchlist(item.showId)
                }
            } else {
                watchedEpisodeDao.upsertWatchedEpisode(
                    mediaType = MediaType.MOVIE,
                    showId = item.showId,
                    episodeId = -1,
                    seasonNumber = -1,
                    episodeNumber = -1
                )
                watchlistDao.removeFromWatchlist(item.showId)
            }
            syncManager.syncWatchedEpisodesNow()
            
            // Proactively fetch next season if necessary
            if (item.mediaType == "tv" && item.seasonNumber != null && item.episodeNumber != null) {
                val nextEp = episodeDao.getNextEpisodeInSeason(item.showId, item.seasonNumber, item.episodeNumber)
                if (nextEp == null) {
                    val nextSeasonNumber = item.seasonNumber + 1
                    val existing = episodeDao.getEpisodesBySeason(item.showId, nextSeasonNumber)
                    if (existing.isEmpty()) {
                        try {
                            val seasonDetail = tmdbApiService.getSeasonDetail(item.showId, nextSeasonNumber)
                            val episodeEntities = seasonDetail.episodes.map { it.toEntity(item.showId) }
                            episodeDao.insertEpisodes(episodeEntities)
                        } catch (e: Exception) {
                            // Silently fail, it will retry next time
                        }
                    }
                }
            }
        }
    }

    fun skipEpisode(item: UpNextItem) {
        viewModelScope.launch {
            if (item.mediaType == "tv" && item.nextEpisodeId != null) {
                watchedEpisodeDao.upsertWatchedEpisode(
                    mediaType = MediaType.TV,
                    showId = item.showId,
                    episodeId = item.nextEpisodeId,
                    seasonNumber = item.seasonNumber,
                    episodeNumber = item.episodeNumber,
                    isSkipped = true
                )
                syncManager.syncWatchedEpisodesNow()
            }
        }
    }

    private val _currentTab = MutableStateFlow(savedStateHandle.get<String>("current_tab") ?: "Up Next")
    val currentTab = _currentTab.asStateFlow()

    fun setTab(tab: String) {
        _currentTab.value = tab
        savedStateHandle["current_tab"] = tab
    }

    fun removeFromWatchlist(tmdbId: Int) {
        viewModelScope.launch {
            watchlistDao.removeFromWatchlist(tmdbId)
            syncManager.syncWatchlistNow()
        }
    }

    // ── Bottom Sheet ────────────────────────────────────────────────

    private val _isProcessingAction = MutableStateFlow(false)
    val isProcessingAction = _isProcessingAction.asStateFlow()

    private val _bottomSheetState = MutableStateFlow(BottomSheetUiState())
    val bottomSheetState = _bottomSheetState.asStateFlow()

    fun openBottomSheet(showId: Int, mediaType: String) {
        _bottomSheetState.value = BottomSheetUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val show = showDao.getShowById(showId) ?: return@launch
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
                _bottomSheetState.value = BottomSheetUiState(isLoading = false)
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
                        watchedEpisodeDao.upsertWatchedEpisode(
                            mediaType = MediaType.MOVIE,
                            showId = show.id,
                            episodeId = -1,
                            seasonNumber = -1,
                            episodeNumber = -1
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
                        watchedEpisodeDao.upsertWatchedEpisode(
                            mediaType = MediaType.TV,
                            showId = show.id,
                            episodeId = ep.id,
                            seasonNumber = next.seasonNumber,
                            episodeNumber = next.episodeNumber
                        )
                        watchlistDao.removeFromWatchlist(show.id)
                        onSuccess("Marked as Watched")
                        
                        // Proactively fetch next season if necessary
                        val progression = getNextEpisodeUseCase(show.id)
                        if (!progression.isCompleted && progression.nextEpisodeData != null) {
                            val nextEp = progression.nextEpisodeData
                            val existing = episodeDao.getEpisodesBySeason(show.id, nextEp.seasonNumber)
                            if (existing.isEmpty()) {
                                try {
                                    val seasonDetail = tmdbApiService.getSeasonDetail(show.id, nextEp.seasonNumber)
                                    val episodeEntities = seasonDetail.episodes.map { it.toEntity(show.id) }
                                    episodeDao.insertEpisodes(episodeEntities)
                                } catch (e: Exception) {
                                    // Silently fail
                                }
                            }
                        }
                        
                        openBottomSheet(show.id, show.mediaType)
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
                        watchedEpisodeDao.upsertWatchedEpisode(
                            mediaType = MediaType.TV,
                            showId = show.id,
                            episodeId = ep.id,
                            seasonNumber = next.seasonNumber,
                            episodeNumber = next.episodeNumber,
                            isSkipped = true
                        )
                        onSuccess("Skipped Episode")
                        openBottomSheet(show.id, show.mediaType)
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
