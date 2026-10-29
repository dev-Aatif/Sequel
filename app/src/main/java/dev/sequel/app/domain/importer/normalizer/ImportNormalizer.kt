package dev.sequel.app.domain.importer.normalizer

import dev.sequel.app.domain.importer.core.CanonicalImportRecord
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

data class NormalizationResult(
    val records: List<CanonicalImportRecord>,
    val duplicateInSourceCount: Int = 0
)

@Singleton
class ImportNormalizer @Inject constructor() {

    private val yearPattern = Regex("""^(.*?)\s*[\(\[](\d{4})[\)\]]$""")

    /**
     * Normalizes a stream of raw canonical records applying global business rules uniformly,
     * tracking duplicate records present in the source input.
     */
    fun normalizeWithStats(records: List<CanonicalImportRecord>): NormalizationResult {
        val now = System.currentTimeMillis()
        val futureThreshold = now + (24 * 60 * 60 * 1000L) // +24h grace period for clock drift

        val deduplicated = mutableListOf<CanonicalImportRecord>()
        val seen = mutableSetOf<String>()
        var duplicateInSourceCount = 0

        for (rec in records) {
            val (cleanedTitle, extractedYear) = cleanTitleAndExtractYear(rec.rawTitle)
            val effectiveYear = rec.year ?: extractedYear

            val effectiveMediaType = when {
                rec.seasonNumber != null || rec.episodeNumber != null -> "tv"
                rec.mediaType.equals("show", true) || rec.mediaType.equals("tv", true) || rec.mediaType.equals("series", true) -> "tv"
                else -> "movie"
            }

            val validWatchedAt = rec.watchedAt?.let { ts ->
                if (ts <= 0L || ts > futureThreshold) null else ts
            }

            val normalizedRating = rec.rating?.let { raw ->
                if (raw <= 0f) null
                else {
                    // Round to nearest 0.5 step to align with Red Pill rating slider
                    val stepped = (raw * 2.0f).roundToInt() / 2.0f
                    stepped.coerceIn(0.5f, 10.0f)
                }
            }

            val cleanedReview = rec.reviewText?.trim()?.takeIf { it.isNotBlank() }

            val normalized = rec.copy(
                normalizedTitle = cleanedTitle,
                year = effectiveYear,
                mediaType = effectiveMediaType,
                watchedAt = validWatchedAt,
                rating = normalizedRating,
                reviewText = cleanedReview
            )

            // Deduplication key preserves legitimate multiple watch events across distinct dates
            val dateComponent = normalized.watchDate ?: normalized.watchedAt?.toString() ?: "no_date"
            val key = "${normalized.source}_${normalized.recordType}_${normalized.mediaType}_${normalized.normalizedTitle.lowercase()}_${normalized.year}_${normalized.seasonNumber}_${normalized.episodeNumber}_${dateComponent}_${normalized.isRewatch}"
            if (seen.add(key)) {
                deduplicated.add(normalized)
            } else {
                duplicateInSourceCount++
            }
        }

        return NormalizationResult(deduplicated, duplicateInSourceCount)
    }

    /**
     * Backward-compatible shorthand returning only the normalized records.
     */
    fun normalize(records: List<CanonicalImportRecord>): List<CanonicalImportRecord> {
        return normalizeWithStats(records).records
    }

    private fun cleanTitleAndExtractYear(raw: String): Pair<String, Int?> {
        val trimmed = raw.trim().trim('"', '\'')
        val match = yearPattern.matchEntire(trimmed)
        return if (match != null) {
            val titlePart = match.groupValues[1].trim()
            val yearPart = match.groupValues[2].toIntOrNull()
            Pair(titlePart, yearPart)
        } else {
            Pair(trimmed, null)
        }
    }
}
