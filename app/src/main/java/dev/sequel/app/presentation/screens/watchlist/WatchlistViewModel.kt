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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val showDao: ShowDao,
    private val episodeDao: EpisodeDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val syncManager: SyncManager
) : ViewModel() {

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
                } else null
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
}
