package dev.sequel.app.domain.backup

import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates Sequel backup integrity, schema versions, and entity boundaries
 * before executing any database transactions.
 *
 * Guarantees:
 * - Reject future or non-positive schema versions with clear actionable errors.
 * - Enforce non-negative IDs, valid media types, and rating bounds (0.5..10.0).
 * - Enforce Mount Rushmore slot bounds (0..3).
 * - Protect user privacy by rejecting backups containing sensitive authentication credentials.
 */
@Singleton
class BackupValidator @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Validates a raw JSON backup string before decoding or restoring.
     */
    fun validateJson(rawJson: String): BackupValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. Secret / token scanning
        val sensitivePatterns = listOf(
            "\"access_token\"",
            "\"refresh_token\"",
            "\"token\":",
            "\"password\":",
            "\"client_secret\"",
            "\"supabase_id\":"
        )
        for (pattern in sensitivePatterns) {
            if (rawJson.contains(pattern, ignoreCase = true)) {
                errors.add("Security violation: Backup contains sensitive authentication field matching '$pattern'")
            }
        }

        // 2. Syntax validation & decode
        val backup = try {
            json.decodeFromString<SequelBackupModel>(rawJson)
        } catch (e: Exception) {
            return BackupValidationReport(
                isValid = false,
                schemaVersion = -1,
                errors = listOf("Malformed JSON backup syntax: ${e.message}")
            )
        }

        // 3. Delegate to model validation
        val modelReport = validate(backup)
        errors.addAll(modelReport.errors)
        warnings.addAll(modelReport.warnings)

        return BackupValidationReport(
            isValid = errors.isEmpty(),
            schemaVersion = backup.schemaVersion,
            summary = modelReport.summary,
            errors = errors.distinct(),
            warnings = warnings.distinct()
        )
    }

    /**
     * Validates an in-memory [SequelBackupModel] instance.
     */
    fun validate(backup: SequelBackupModel): BackupValidationReport {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Schema version check
        if (backup.schemaVersion <= 0) {
            errors.add("Invalid schema version: ${backup.schemaVersion}. Schema version must be positive.")
        } else if (backup.schemaVersion > SequelBackupModel.CURRENT_SCHEMA_VERSION) {
            errors.add(
                "Backup schema version v${backup.schemaVersion} is newer than current supported version v${SequelBackupModel.CURRENT_SCHEMA_VERSION}. Please update Sequel to restore this file."
            )
        }

        if (backup.exportedAt <= 0) {
            warnings.add("Backup contains missing or non-positive exportedAt timestamp: ${backup.exportedAt}")
        }

        // Validate Shows
        backup.shows.forEachIndexed { index, show ->
            if (show.id <= 0) {
                errors.add("Show at index $index has invalid ID: ${show.id}")
            }
            if (show.title.isBlank()) {
                errors.add("Show at index $index has blank title")
            }
            if (show.mediaType.isBlank()) {
                errors.add("Show '${show.title}' has blank mediaType")
            }
        }

        // Validate Watched Movies
        backup.watchedMovies.forEachIndexed { index, movie ->
            if (movie.tmdbMovieId <= 0) {
                errors.add("Watched movie at index $index has invalid tmdbMovieId: ${movie.tmdbMovieId}")
            }
            if (movie.title.isBlank()) {
                errors.add("Watched movie at index $index has blank title")
            }
            if (movie.watchedAt <= 0) {
                warnings.add("Watched movie '${movie.title}' has non-positive watchedAt timestamp")
            }
        }

        // Validate Watched Episodes
        backup.watchedEpisodes.forEachIndexed { index, episode ->
            if (episode.showId <= 0) {
                errors.add("Watched episode at index $index has invalid showId: ${episode.showId}")
            }
            if (episode.tmdbEpisodeId <= 0) {
                errors.add("Watched episode at index $index has invalid tmdbEpisodeId: ${episode.tmdbEpisodeId}")
            }
            if (episode.seasonNumber < 0) {
                errors.add("Watched episode at index $index has negative seasonNumber: ${episode.seasonNumber}")
            }
            if (episode.episodeNumber < 0) {
                errors.add("Watched episode at index $index has negative episodeNumber: ${episode.episodeNumber}")
            }
        }

        // Validate Watchlist
        backup.watchlist.forEachIndexed { index, item ->
            if (item.tmdbId <= 0) {
                errors.add("Watchlist item at index $index has invalid tmdbId: ${item.tmdbId}")
            }
            if (item.title.isBlank()) {
                errors.add("Watchlist item at index $index has blank title")
            }
            if (item.mediaType.isBlank()) {
                errors.add("Watchlist item '${item.title}' has blank mediaType")
            }
        }

        // Validate Reviews & Ratings
        backup.reviews.forEachIndexed { index, review ->
            if (review.mediaId <= 0) {
                errors.add("Review at index $index has invalid mediaId: ${review.mediaId}")
            }
            if (review.mediaType.isBlank()) {
                errors.add("Review at index $index has blank mediaType")
            }
            if (review.rating != null && (review.rating < 0.5f || review.rating > 10.0f)) {
                errors.add("Review at index $index has out-of-range rating: ${review.rating} (valid scale is 0.5 - 10.0)")
            }
            if (review.seasonNum != null && review.seasonNum < 0) {
                errors.add("Review at index $index has negative seasonNum: ${review.seasonNum}")
            }
            if (review.episodeNum != null && review.episodeNum < 0) {
                errors.add("Review at index $index has negative episodeNum: ${review.episodeNum}")
            }
        }

        // Validate Mount Rushmore
        backup.mountRushmore.forEachIndexed { index, item ->
            if (item.slotIndex !in 0..3) {
                errors.add("Mount Rushmore item at index $index has invalid slotIndex: ${item.slotIndex} (must be 0, 1, 2, or 3)")
            }
            if (item.mediaId <= 0) {
                errors.add("Mount Rushmore item at index $index has invalid mediaId: ${item.mediaId}")
            }
            if (item.title.isBlank()) {
                errors.add("Mount Rushmore item at index $index has blank title")
            }
        }

        return BackupValidationReport(
            isValid = errors.isEmpty(),
            schemaVersion = backup.schemaVersion,
            summary = backup.toSummary(),
            errors = errors,
            warnings = warnings
        )
    }
}
