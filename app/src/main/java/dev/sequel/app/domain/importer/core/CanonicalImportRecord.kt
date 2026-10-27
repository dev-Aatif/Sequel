package dev.sequel.app.domain.importer.core

import dev.sequel.app.domain.importer.ImportRecordType

data class ExternalIdentifiers(
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val tvdbId: Int? = null,
    val traktId: Int? = null,
    val letterboxdUri: String? = null
)

data class SourceProvenance(
    val source: ImportSource = ImportSource.UNKNOWN,
    val sourceRecordId: String? = null,
    val rawDateString: String? = null,
    val isDateOnly: Boolean = false,
    val rawRating: Float? = null,
    val rawRatingScale: Float? = null
)

data class CanonicalImportRecord(
    val rawTitle: String,
    val normalizedTitle: String = rawTitle,
    val year: Int? = null,
    val mediaType: String, // "movie" or "tv"
    val recordType: ImportRecordType = ImportRecordType.WATCHED,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val watchedAt: Long? = null,
    val watchDate: String? = null, // "YYYY-MM-DD"
    val rating: Float? = null, // Normalized to 0.5 - 10.0 scale
    val originalRating: Float? = null,
    val originalRatingScale: Float? = null,
    val reviewText: String? = null,
    val isSpoiler: Boolean = false,
    val isRewatch: Boolean = false,
    val isExplicitShowLevel: Boolean = false,
    val tags: List<String> = emptyList(),
    val identifiers: ExternalIdentifiers = ExternalIdentifiers(),
    val source: ImportSource = ImportSource.UNKNOWN,
    val provenance: SourceProvenance = SourceProvenance(source = source)
)
