package dev.sequel.app.domain.importer.core

enum class ConflictType {
    DUPLICATE_IDENTICAL,
    TIMESTAMP_DIVERGENCE,
    RATING_DIVERGENCE,
    REVIEW_DIVERGENCE,
    STATUS_DIVERGENCE,
    AMBIGUOUS_MATCH,
    UNRESOLVED_TITLE
}

data class ImportConflict(
    val recordTitle: String,
    val mediaType: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val conflictType: ConflictType,
    val localValue: String?,
    val importedValue: String?,
    val resolutionMessage: String,
    val candidates: List<String> = emptyList()
)

data class ImportPreview(
    val source: ImportSource,
    val totalRecordsParsed: Int,
    val uniqueTitlesCount: Int,
    val newMoviesCount: Int,
    val newEpisodesCount: Int,
    val newWatchlistCount: Int,
    val newRatingsCount: Int,
    val newReviewsCount: Int,
    val identicalSkippedCount: Int,
    val conflicts: List<ImportConflict>,
    val confidenceDistribution: Map<MatchConfidence, Int>,
    val unresolvableCount: Int = 0,
    val ambiguousCount: Int = 0,
    val unresolvableRecords: List<ImportErrorDetail> = emptyList(),
    val ambiguousRecords: List<ImportConflict> = emptyList()
)

data class ImportErrorDetail(
    val title: String,
    val reason: String,
    val recoverable: Boolean = false,
    val rawRecordSummary: String? = null
)

data class ImportReport(
    val importId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: ImportSource,
    val policyApplied: ConflictResolutionPolicy,
    val totalProcessed: Int,
    val insertedCount: Int,
    val updatedCount: Int,
    val skippedIdenticalCount: Int,
    val duplicateInSourceCount: Int = 0,
    val failedCount: Int,
    val warnings: List<String> = emptyList(),
    val errors: List<ImportErrorDetail> = emptyList(),
    val durationMs: Long,
    val unresolvableCount: Int = 0,
    val ambiguousCount: Int = 0,
    val unmatchedDetails: List<ImportErrorDetail> = emptyList(),
    val ambiguousDetails: List<ImportConflict> = emptyList()
)
