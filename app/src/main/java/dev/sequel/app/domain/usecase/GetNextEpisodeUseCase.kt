package dev.sequel.app.domain.usecase

import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.dto.TmdbNextEpisodeDto
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

data class EpisodeProgressionState(
    val nextEpisodeString: String,
    val nextEpisodeData: TmdbNextEpisodeDto?,
    val isCompleted: Boolean
)

class GetNextEpisodeUseCase @Inject constructor(
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val tmdbApiService: TmdbApiService
) {
    suspend operator fun invoke(showId: Int): EpisodeProgressionState {
        val watchedList = watchedEpisodeDao.observeWatchedByShow(showId).firstOrNull() ?: emptyList()
        val detail = tmdbApiService.getTvShowDetail(showId)

        var nextString = "Mark S1E1 as Watched"
        var nextData: TmdbNextEpisodeDto? = TmdbNextEpisodeDto(id = 0, name = "", seasonNumber = 1, episodeNumber = 1)
        var isCompleted = false

        if (watchedList.isNotEmpty()) {
            val highest = watchedList.maxWithOrNull(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }))
            if (highest != null) {
                val hSeason = highest.seasonNumber ?: 1
                val hEpisode = highest.episodeNumber ?: 1

                val seasonSummary = detail.seasons.find { it.seasonNumber == hSeason }
                if (seasonSummary != null) {
                    if (hEpisode < seasonSummary.episodeCount) {
                        nextString = "Mark S${hSeason}E${hEpisode + 1} as Watched"
                        nextData = TmdbNextEpisodeDto(
                            id = 0, 
                            name = "", 
                            seasonNumber = hSeason, 
                            episodeNumber = hEpisode + 1
                        )
                    } else {
                        val nextSeasonSummary = detail.seasons.find { it.seasonNumber == hSeason + 1 }
                        if (nextSeasonSummary != null && nextSeasonSummary.episodeCount > 0) {
                            nextString = "Mark S${hSeason + 1}E1 as Watched"
                            nextData = TmdbNextEpisodeDto(
                                id = 0, 
                                name = "", 
                                seasonNumber = hSeason + 1, 
                                episodeNumber = 1
                            )
                        } else {
                            nextString = "Completed"
                            nextData = null
                            isCompleted = true
                        }
                    }
                }
            }
        }

        return EpisodeProgressionState(
            nextEpisodeString = nextString,
            nextEpisodeData = nextData,
            isCompleted = isCompleted
        )
    }
}
