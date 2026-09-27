package dev.sequel.app.domain.usecase

import dev.sequel.app.data.remote.tmdb.dto.TmdbNextEpisodeDto
import dev.sequel.app.domain.repository.SeasonRepository
import dev.sequel.app.domain.repository.ShowRepository
import dev.sequel.app.domain.repository.WatchedEpisodeRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

data class EpisodeProgressionState(
    val nextEpisodeString: String,
    val nextEpisodeData: TmdbNextEpisodeDto?,
    val isCompleted: Boolean
)

class GetNextEpisodeUseCase @Inject constructor(
    private val watchedEpisodeRepository: WatchedEpisodeRepository,
    private val seasonRepository: SeasonRepository,
    private val showRepository: ShowRepository
) {
    suspend operator fun invoke(showId: Int): EpisodeProgressionState {
        // 1. Try local Canonical Next Episode
        val localNext = seasonRepository.observeCanonicalNextEpisode(showId).firstOrNull()
        if (localNext != null) {
            return EpisodeProgressionState(
                nextEpisodeString = "Mark S${localNext.seasonNumber}E${localNext.episodeNumber} as Watched",
                nextEpisodeData = TmdbNextEpisodeDto(
                    id = localNext.id,
                    name = localNext.name,
                    seasonNumber = localNext.seasonNumber,
                    episodeNumber = localNext.episodeNumber
                ),
                isCompleted = false
            )
        }

        // 2. If no local next episode, fallback to TMDB to see if there's a next season we haven't cached
        val watchedList = watchedEpisodeRepository.observeWatchedByShow(showId, dev.sequel.app.data.local.entity.MediaType.TV.name.lowercase()).firstOrNull() ?: emptyList()
        
        // Fetch show details from network to ensure we have the latest seasons cached
        showRepository.fetchShowDetail(showId)
        
        // Retrieve the locally cached seasons
        val seasons = seasonRepository.observeSeasons(showId).firstOrNull() ?: emptyList()

        if (watchedList.isEmpty()) {
            // No watched episodes and no local next? Must be S1E1.
            return EpisodeProgressionState(
                nextEpisodeString = "Mark S1E1 as Watched",
                nextEpisodeData = TmdbNextEpisodeDto(id = 0, name = "", seasonNumber = 1, episodeNumber = 1),
                isCompleted = false
            )
        }

        val highest = watchedList.maxWithOrNull(compareBy({ it.seasonNumber ?: 0 }, { it.episodeNumber ?: 0 }))
        val hSeason = highest?.seasonNumber ?: 1
        val hEpisode = highest?.episodeNumber ?: 1

        val seasonSummary = seasons.find { it.seasonNumber == hSeason }
        if (seasonSummary != null) {
            if (hEpisode < seasonSummary.episodeCount) {
                // There is a next episode in this season on TMDB that we didn't cache locally!
                return EpisodeProgressionState(
                    nextEpisodeString = "Mark S${hSeason}E${hEpisode + 1} as Watched",
                    nextEpisodeData = TmdbNextEpisodeDto(
                        id = 0,
                        name = "",
                        seasonNumber = hSeason,
                        episodeNumber = hEpisode + 1
                    ),
                    isCompleted = false
                )
            } else {
                // Next season
                val nextSeasonSummary = seasons.find { it.seasonNumber == hSeason + 1 }
                if (nextSeasonSummary != null && nextSeasonSummary.episodeCount > 0) {
                    return EpisodeProgressionState(
                        nextEpisodeString = "Mark S${hSeason + 1}E1 as Watched",
                        nextEpisodeData = TmdbNextEpisodeDto(
                            id = 0,
                            name = "",
                            seasonNumber = hSeason + 1,
                            episodeNumber = 1
                        ),
                        isCompleted = false
                    )
                }
            }
        }

        return EpisodeProgressionState(
            nextEpisodeString = "Completed",
            nextEpisodeData = null,
            isCompleted = true
        )
    }
}
