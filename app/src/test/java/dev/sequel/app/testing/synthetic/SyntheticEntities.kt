package dev.sequel.app.testing.synthetic

/**
 * Synthetic representation of a Movie entity.
 */
data class SyntheticMovie(
    val id: Int,
    val title: String,
    val year: Int?,
    val tmdbId: Int?,
    val imdbId: String?,
    val overview: String = "",
    val posterPath: String? = null,
    val runtime: Int? = null,
    val isAmbiguous: Boolean = false,
    val isRemake: Boolean = false
)

/**
 * Synthetic representation of a TV Show entity.
 */
data class SyntheticShow(
    val id: Int,
    val title: String,
    val year: Int?,
    val tmdbId: Int?,
    val imdbId: String?,
    val tvdbId: Int? = null,
    val isAnime: Boolean = false,
    val isAmbiguous: Boolean = false,
    val seasons: List<SyntheticSeason> = emptyList()
)

/**
 * Synthetic representation of a TV Season.
 */
data class SyntheticSeason(
    val showId: Int,
    val seasonNumber: Int,
    val isSpecial: Boolean = false,
    val isSplitSeason: Boolean = false,
    val splitPart: Int? = null,
    val episodes: List<SyntheticEpisode> = emptyList()
)

/**
 * Synthetic representation of an Episode.
 */
data class SyntheticEpisode(
    val showId: Int,
    val episodeId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val tmdbEpisodeId: Int?,
    val isSpecial: Boolean = false,
    val fractionalEpisodeNumber: Float? = null
)

/**
 * Synthetic watch history event.
 */
data class SyntheticWatchEvent(
    val eventId: String,
    val mediaType: String, // "movie" or "tv"
    val showOrMovieId: Int,
    val title: String,
    val year: Int?,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val watchedAt: Long,
    val watchDate: String, // "YYYY-MM-DD"
    val isRewatch: Boolean = false,
    val isShowLevelOnly: Boolean = false
)

/**
 * Synthetic user rating or written review.
 */
data class SyntheticReview(
    val mediaId: Int,
    val mediaType: String,
    val title: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val rating: Float?,
    val originalRatingScale: Float = 10.0f,
    val reviewText: String?,
    val isSpoiler: Boolean = false,
    val updatedAt: Long
)

/**
 * Synthetic watchlist bookmark.
 */
data class SyntheticWatchlistItem(
    val tmdbId: Int?,
    val mediaType: String,
    val title: String,
    val year: Int?,
    val addedAt: Long
)

/**
 * Audit record of an intentionally injected fault or edge-case.
 */
data class InjectedFaultRecord(
    val faultType: String,
    val entityType: String,
    val identifier: String,
    val description: String
)

/**
 * Human-readable generation and failure reproduction report.
 */
data class SyntheticGenerationReport(
    val seed: Long,
    val config: SyntheticDatasetConfig,
    val totalMovies: Int,
    val totalShows: Int,
    val totalEpisodes: Int,
    val totalWatchEvents: Int,
    val totalReviews: Int,
    val totalWatchlistItems: Int,
    val injectedFaults: List<InjectedFaultRecord>,
    val reproductionSnippet: String
) {
    override fun toString(): String = buildString {
        appendLine("═══════════════════════════════════════════════════════════════════════════")
        appendLine("          SEQUEL SYNTHETIC DATASET GENERATION & FAILURE REPORT              ")
        appendLine("═══════════════════════════════════════════════════════════════════════════")
        appendLine("Seed: $seed")
        appendLine("Entities Generated:")
        appendLine("  • Movies: $totalMovies")
        appendLine("  • Shows: $totalShows")
        appendLine("  • Episodes: $totalEpisodes")
        appendLine("  • Watch Events: $totalWatchEvents")
        appendLine("  • Reviews/Ratings: $totalReviews")
        appendLine("  • Watchlist Items: $totalWatchlistItems")
        appendLine()
        appendLine("Injected Faults (${injectedFaults.size}):")
        if (injectedFaults.isEmpty()) {
            appendLine("  (None — referentially valid pristine dataset)")
        } else {
            injectedFaults.forEach { f ->
                appendLine("  • [${f.faultType}] on ${f.entityType} '${f.identifier}': ${f.description}")
            }
        }
        appendLine()
        appendLine("To Reproduce This Dataset in a Test:")
        appendLine(reproductionSnippet)
        appendLine("═══════════════════════════════════════════════════════════════════════════")
    }
}
