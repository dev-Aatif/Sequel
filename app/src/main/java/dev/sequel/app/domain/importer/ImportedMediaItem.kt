package dev.sequel.app.domain.importer

enum class ImportRecordType {
    WATCHED,
    WATCHLIST,
    RATING,
    REVIEW
}

data class ImportedMediaItem(
    val title: String,
    val year: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val watchedAt: Long? = null,
    val watchDate: String? = null,
    val mediaType: String, // "tv" or "movie"
    val recordType: ImportRecordType = ImportRecordType.WATCHED,
    val rating: Float? = null, // Normalized to 0.5 - 10.0 scale
    val originalRating: Float? = null,
    val originalRatingScale: Float? = null,
    val reviewText: String? = null,
    val isSpoiler: Boolean = false,
    val isRewatch: Boolean = false,
    val isExplicitShowLevel: Boolean = false,
    val tags: List<String> = emptyList(),
    val rawDateString: String? = null,
    val isDateOnly: Boolean = false,
    val externalId: String? = null,
    val externalIdType: String? = null // "tmdb", "tvdb", "imdb", "trakt", "letterboxd"
)
