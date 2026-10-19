package dev.sequel.app.domain.usecase

// Removed DTO import
import dev.sequel.app.domain.repository.SeasonRepository
import dev.sequel.app.domain.repository.ShowRepository
import dev.sequel.app.domain.repository.WatchedEpisodeRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

data class NextEpisodeProgressionData(
    val id: Int,
    val name: String,
    val seasonNumber: Int,
    val episodeNumber: Int
)

data class EpisodeProgressionState(
    val nextEpisodeString: String,
    val nextEpisodeData: NextEpisodeProgressionData?,
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
                nextEpisodeData = NextEpisodeProgressionData(
                    id = localNext.id,
                    name = localNext.name,
                    seasonNumber = localNext.seasonNumber,
                    episodeNumber = localNext.episodeNumber
                ),
                isCompleted = false
            )
        }

        // 2. If no local next episode, fallback to TMDB to see if there's a next season we haven't cached
        val watchedList = watchedEpisodeRepository.observeWatchedByShow(showId).firstOrNull() ?: emptyList()
        
        // Fetch show details from network to ensure we have the latest seasons cached
        showRepository.fetchShowDetail(showId)
        
        // Retrieve the locally cached seasons
        val seasons = seasonRepository.observeSeasons(showId).firstOrNull() ?: emptyList()

        if (watchedList.isEmpty()) {
            // No watched episodes and no local next? Must be S1E1.
            return EpisodeProgressionState(
                nextEpisodeString = "Mark S1E1 as Watched",
                nextEpisodeData = NextEpisodeProgressionData(id = 0, name = "", seasonNumber = 1, episodeNumber = 1),
                isCompleted = false
            )
        }

        // 3. Scan every season in sequence (excluding specials Season 0) to find unhandled episodes
        val regularSeasons = seasons.filter { it.seasonNumber > 0 }.sortedBy { it.seasonNumber }
        for (seasonSummary in regularSeasons) {
            val watchedInSeason = watchedList
                .filter { it.seasonNumber == seasonSummary.seasonNumber }
                .map { it.episodeNumber }
                .toSet()

            val missingEpNumber = (1..seasonSummary.episodeCount).firstOrNull { it !in watchedInSeason }
            if (missingEpNumber != null) {
                return EpisodeProgressionState(
                    nextEpisodeString = "Mark S${seasonSummary.seasonNumber}E$missingEpNumber as Watched",
                    nextEpisodeData = NextEpisodeProgressionData(
                        id = 0,
                        name = "",
                        seasonNumber = seasonSummary.seasonNumber,
                        episodeNumber = missingEpNumber
                    ),
                    isCompleted = false
                )
            }
        }

        return EpisodeProgressionState(
            nextEpisodeString = "Completed",
            nextEpisodeData = null,
            isCompleted = true
        )
    }
}
