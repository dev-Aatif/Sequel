package dev.sequel.app.testing.synthetic

/**
 * Configuration for deterministic synthetic test dataset generation.
 *
 * @param seed Pseudo-random seed guaranteeing 100% reproducible test runs.
 * @param movieCount Number of movie entities to generate.
 * @param showCount Number of TV show entities to generate.
 * @param seasonsPerShow Range of seasons generated per show.
 * @param episodesPerSeason Range of episodes generated per season.
 * @param watchlistCount Number of items in watchlist.
 * @param reviewCount Number of reviews / ratings to generate.
 * @param watchEventCount Number of watch history events to generate.
 * @param edgeCases Detailed edge case switches (anime, leap days, out-of-order, etc.).
 * @param faults Explicit fault-injection probabilities (0.0 to 1.0).
 */
data class SyntheticDatasetConfig(
    val seed: Long = 42L,
    val movieCount: Int = 30,
    val showCount: Int = 15,
    val seasonsPerShow: IntRange = 1..3,
    val episodesPerSeason: IntRange = 6..10,
    val watchlistCount: Int = 10,
    val reviewCount: Int = 20,
    val watchEventCount: Int = 60,
    val edgeCases: EdgeCaseConfig = EdgeCaseConfig(),
    val faults: FaultInjectionConfig = FaultInjectionConfig()
)

/**
 * Realistic edge-case toggles for realistic domain testing without intentionally corrupting syntax.
 */
data class EdgeCaseConfig(
    val includeAnimeAndSpecials: Boolean = false,
    val includeIrregularNumbering: Boolean = false,
    val includeOutOfOrderWatches: Boolean = false,
    val includeShowLevelWatchedWithPartialEpisodes: Boolean = false,
    val includePartialSeasons: Boolean = false,
    val includeLegitimateRewatches: Boolean = false,
    val includeMultilingualAndEmojiReviews: Boolean = false,
    val includeLeapDaysAndMidnightTimestamps: Boolean = false,
    val includeDuplicateTitlesAndRemakes: Boolean = false,
    val includeRatingScaleVariance: Boolean = false
)

/**
 * Explicit fault-injection parameters for testing parser resiliency, sanitization,
 * and recovery behaviors. Keep default probabilities at 0.0 to ensure normal datasets are valid.
 */
data class FaultInjectionConfig(
    val duplicateRowRate: Double = 0.0,
    val missingIdRate: Double = 0.0,
    val conflictingIdRate: Double = 0.0,
    val missingYearRate: Double = 0.0,
    val missingMetadataRate: Double = 0.0,
    val ambiguousMatchRate: Double = 0.0,
    val malformedCsvSyntaxRate: Double = 0.0,
    val malformedJsonSyntaxRate: Double = 0.0,
    val invalidDateRate: Double = 0.0,
    val invalidNumericRate: Double = 0.0,
    val reorderedCsvColumns: Boolean = false,
    val extraCsvColumns: Boolean = false
)

enum class CorruptedZipType {
    TRUNCATED_BYTES,
    INVALID_HEADER,
    NESTED_ARCHIVE,
    EMPTY_STREAM
}
