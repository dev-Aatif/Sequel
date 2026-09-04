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
import javax.inject.Inject

data class UpNextItem(
    val showId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String?,
    val nextEpisodeName: String?,
    val nextEpisodeId: Int?,
    val seasonNumber: Int?,
    val episodeNumber: Int?
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
    private val syncManager: SyncManager
) : ViewModel() {

    // ── Up Next ────────────────────────────────────────────────────

    private val startedTvShowsFlow = showDao.observeStartedTvShows()

    private val upNextTvFlow = startedTvShowsFlow.flatMapLatest { shows ->
        if (shows.isEmpty()) return@flatMapLatest flowOf(emptyList<UpNextItem>())
        
        val nextEpisodeFlows = shows.map { show ->
            episodeDao.observeNextUnwatchedEpisode(show.id).map { nextEp ->
                if (nextEp != null) {
                    UpNextItem(
                        showId = show.id,
                        mediaType = show.mediaType,
                        title = show.title,
                        posterPath = show.posterPath,
                        nextEpisodeName = "S${nextEp.seasonNumber}E${nextEp.episodeNumber}: ${nextEp.name}",
                        nextEpisodeId = nextEp.id,
                        seasonNumber = nextEp.seasonNumber,
                        episodeNumber = nextEp.episodeNumber
                    )
                } else null // No more unwatched episodes → completed, should be in Watched tab
            }
        }
        combine(nextEpisodeFlows) { items ->
            items.filterNotNull()
        }
    }

    private val upNextMoviesFlow = showDao.observeUnwatchedTrackedMovies().map { movies ->
        movies.map { movie ->
            UpNextItem(
                showId = movie.id,
                mediaType = movie.mediaType,
                title = movie.title,
                posterPath = movie.posterPath,
                nextEpisodeName = null,
                nextEpisodeId = null,
                seasonNumber = null,
                episodeNumber = null
            )
        }
    }

    val upNextItems: StateFlow<List<UpNextItem>> = combine(
        upNextTvFlow,
        upNextMoviesFlow
    ) { tv, movies ->
        (tv + movies).sortedBy { it.title }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
                episodeDao.observeNextUnwatchedEpisode(showId)
            ) { show, watchedEpisodes, nextUnwatched ->
                if (show == null) return@combine null
                val watchedCount = watchedEpisodes.size
                val totalEpisodes = show.numberOfEpisodes
                
                val statusTag = when {
                    // If no unwatched episodes remain and we have episode data → Completed
                    nextUnwatched == null && watchedCount > 0 -> "Completed"
                    // If the show status is "Ended" or "Canceled" and there are unwatched eps
                    show.status in listOf("Ended", "Canceled") && nextUnwatched != null -> "In Progress"
                    // If the show is still airing and user is caught up to latest available
                    show.status == "Returning Series" && nextUnwatched == null -> "Up to Date"
                    // Default: in progress
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
                    watchedEpisodeDao.insertWatchedEpisode(
                        WatchedEpisodeEntity(
                            mediaType = MediaType.TV,
                            showId = item.showId,
                            episodeId = item.nextEpisodeId,
                            seasonNumber = item.seasonNumber,
                            episodeNumber = item.episodeNumber,
                            syncStatus = SyncStatus.PENDING
                        )
                    )
                    // Auto-queue: next episode automatically appears in Up Next
                    // because observeNextUnwatchedEpisode is reactive
                }
            } else {
                watchedEpisodeDao.insertWatchedEpisode(
                    WatchedEpisodeEntity(
                        mediaType = MediaType.MOVIE,
                        showId = item.showId,
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

    private val _currentTab = MutableStateFlow("Up Next")
    val currentTab = _currentTab.asStateFlow()

    fun setTab(tab: String) {
        _currentTab.value = tab
    }
}
