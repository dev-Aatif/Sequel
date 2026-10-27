package dev.sequel.app.domain.importer.conflict

import dev.sequel.app.data.local.dao.ReviewDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.dao.WatchedMovieDao
import dev.sequel.app.data.local.dao.WatchlistDao
import dev.sequel.app.domain.importer.ImportRecordType
import dev.sequel.app.domain.importer.core.*
import dev.sequel.app.domain.importer.resolver.ResolvedMediaRecord
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConflictAnalyzer @Inject constructor(
    private val watchedMovieDao: WatchedMovieDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val watchlistDao: WatchlistDao,
    private val reviewDao: ReviewDao
) {

    suspend fun analyze(
        source: ImportSource,
        resolvedRecords: List<ResolvedMediaRecord>
    ): ImportPreview {
        var newMovies = 0
        var newEpisodes = 0
        var newWatchlist = 0
        var newRatings = 0
        var newReviews = 0
        var identicalSkipped = 0

        val conflicts = mutableListOf<ImportConflict>()
        val confidenceMap = mutableMapOf<MatchConfidence, Int>()
        val unresolvableList = mutableListOf<ImportErrorDetail>()
        val ambiguousList = mutableListOf<ImportConflict>()

        for (resolved in resolvedRecords) {
            val confidence = resolved.confidence
            confidenceMap[confidence] = (confidenceMap[confidence] ?: 0) + 1

            val tmdbId = resolved.tmdbId
            if (tmdbId == null) {
                // Unresolved record
                unresolvableList.add(
                    ImportErrorDetail(
                        title = resolved.resolvedTitle,
                        reason = "No matching title or ID found in local cache or TMDB.",
                        recoverable = false,
                        rawRecordSummary = "${resolved.originalRecord.source} [${resolved.originalRecord.mediaType}]"
                    )
                )
                continue
            }

            if (confidence == MatchConfidence.AMBIGUOUS) {
                val candidateNames = resolved.candidateMatches.map { "${it.title} (${it.year ?: "?"})" }
                val ambConflict = ImportConflict(
                    recordTitle = resolved.resolvedTitle,
                    mediaType = resolved.originalRecord.mediaType,
                    seasonNumber = resolved.originalRecord.seasonNumber,
                    episodeNumber = resolved.originalRecord.episodeNumber,
                    conflictType = ConflictType.AMBIGUOUS_MATCH,
                    localValue = null,
                    importedValue = resolved.originalRecord.rawTitle,
                    resolutionMessage = "Multiple candidates found. First match selected; review recommended.",
                    candidates = candidateNames
                )
                ambiguousList.add(ambConflict)
                conflicts.add(ambConflict)
            }

            val rec = resolved.originalRecord
            val isMovie = rec.mediaType == "movie"

            when (rec.recordType) {
                ImportRecordType.WATCHED -> {
                    if (isMovie) {
                        val isWatched = watchedMovieDao.isMovieWatched(tmdbId)
                        if (isWatched) {
                            // Already watched locally
                            conflicts.add(
                                ImportConflict(
                                    recordTitle = resolved.resolvedTitle,
                                    mediaType = "movie",
                                    conflictType = ConflictType.DUPLICATE_IDENTICAL,
                                    localValue = "Watched",
                                    importedValue = "Watched at ${rec.watchDate ?: rec.watchedAt ?: "Unknown"}",
                                    resolutionMessage = "Movie is already marked as watched locally."
                                )
                            )
                            identicalSkipped++
                        } else {
                            newMovies++
                        }
                    } else {
                        // TV show/episode watch
                        if (rec.seasonNumber != null && rec.episodeNumber != null) {
                            newEpisodes++
                        } else {
                            // Show-level entry
                            newMovies++
                        }
                    }
                }
                ImportRecordType.WATCHLIST -> {
                    val inWatchlist = watchlistDao.isInWatchlist(tmdbId, rec.mediaType)
                    if (inWatchlist) {
                        identicalSkipped++
                        conflicts.add(
                            ImportConflict(
                                recordTitle = resolved.resolvedTitle,
                                mediaType = rec.mediaType,
                                conflictType = ConflictType.DUPLICATE_IDENTICAL,
                                localValue = "In Watchlist",
                                importedValue = "In Watchlist",
                                resolutionMessage = "Title is already in your watchlist."
                            )
                        )
                    } else {
                        newWatchlist++
                    }
                }
                ImportRecordType.RATING -> {
                    val existingReview = reviewDao.getRatingRow(tmdbId, rec.mediaType, rec.seasonNumber)
                    val localRating = existingReview?.rating
                    if (localRating != null) {
                        if (localRating == rec.rating) {
                            identicalSkipped++
                        } else {
                            conflicts.add(
                                ImportConflict(
                                    recordTitle = resolved.resolvedTitle,
                                    mediaType = rec.mediaType,
                                    seasonNumber = rec.seasonNumber,
                                    episodeNumber = rec.episodeNumber,
                                    conflictType = ConflictType.RATING_DIVERGENCE,
                                    localValue = "Rating: $localRating",
                                    importedValue = "Rating: ${rec.rating} (original: ${rec.originalRating ?: rec.rating})",
                                    resolutionMessage = "Rating differs from local rating ($localRating vs ${rec.rating})."
                                )
                            )
                        }
                    } else {
                        newRatings++
                    }
                }
                ImportRecordType.REVIEW -> {
                    val existingReview = reviewDao.getReviewForMedia(tmdbId, rec.mediaType)
                    if (existingReview != null && !existingReview.reviewText.isNullOrBlank()) {
                        conflicts.add(
                            ImportConflict(
                                recordTitle = resolved.resolvedTitle,
                                mediaType = rec.mediaType,
                                conflictType = ConflictType.REVIEW_DIVERGENCE,
                                localValue = existingReview.reviewText,
                                importedValue = rec.reviewText,
                                resolutionMessage = "Local written review exists for this title."
                            )
                        )
                    } else {
                        newReviews++
                    }
                }
            }
        }

        val uniqueTitles = resolvedRecords.map { it.resolvedTitle }.distinct().size

        return ImportPreview(
            source = source,
            totalRecordsParsed = resolvedRecords.size,
            uniqueTitlesCount = uniqueTitles,
            newMoviesCount = newMovies,
            newEpisodesCount = newEpisodes,
            newWatchlistCount = newWatchlist,
            newRatingsCount = newRatings,
            newReviewsCount = newReviews,
            identicalSkippedCount = identicalSkipped,
            conflicts = conflicts,
            confidenceDistribution = confidenceMap,
            unresolvableCount = unresolvableList.size,
            ambiguousCount = ambiguousList.size,
            unresolvableRecords = unresolvableList,
            ambiguousRecords = ambiguousList
        )
    }
}
