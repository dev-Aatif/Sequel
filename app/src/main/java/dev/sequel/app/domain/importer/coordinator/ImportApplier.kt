package dev.sequel.app.domain.importer.coordinator

import androidx.room.withTransaction
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.*
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchlistEntity
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.domain.importer.ImportRecordType
import dev.sequel.app.domain.importer.checkpoint.CheckpointStatus
import dev.sequel.app.domain.importer.checkpoint.ImportCheckpoint
import dev.sequel.app.domain.importer.checkpoint.ImportCheckpointManager
import dev.sequel.app.domain.importer.core.*
import dev.sequel.app.domain.importer.resolver.ResolvedMediaRecord
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportApplier @Inject constructor(
    private val database: SequelDatabase,
    private val showDao: ShowDao,
    private val seasonDao: SeasonDao,
    private val episodeDao: EpisodeDao,
    private val watchedMovieDao: WatchedMovieDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val watchlistDao: WatchlistDao,
    private val reviewDao: ReviewDao,
    private val tmdbApiService: TmdbApiService,
    private val syncManager: SyncManager,
    val checkpointManager: ImportCheckpointManager? = null
) {

    /**
     * Commits resolved records to the Room database adhering strictly to the conflict resolution policy.
     * Never writes directly to Supabase — triggers domain sync on completion.
     */
    suspend fun apply(
        source: ImportSource,
        resolvedRecords: List<ResolvedMediaRecord>,
        policy: ConflictResolutionPolicy,
        duplicateInSourceCount: Int = 0,
        totalParsedCount: Int? = null,
        checkpointManager: ImportCheckpointManager? = this.checkpointManager,
        fingerprint: String? = null,
        resumeFromCheckpoint: Boolean = true,
        batchSize: Int = 200,
        onProgress: (current: Int, total: Int, currentTitle: String) -> Unit = { _, _, _ -> }
    ): ImportReport {
        val startTime = System.currentTimeMillis()
        val importId = UUID.randomUUID().toString()

        val effectiveCheckpointManager = checkpointManager ?: this.checkpointManager
        val existingCheckpoint = if (effectiveCheckpointManager != null && fingerprint != null && resumeFromCheckpoint) {
            effectiveCheckpointManager.getCheckpoint(fingerprint)?.takeIf { it.status == CheckpointStatus.IN_PROGRESS }
        } else null

        var insertedCount = existingCheckpoint?.insertedCount ?: 0
        var updatedCount = existingCheckpoint?.updatedCount ?: 0
        var skippedCount = existingCheckpoint?.skippedCount ?: 0
        val startIndex = existingCheckpoint?.processedIndex?.coerceIn(0, resolvedRecords.size) ?: 0

        val errors = mutableListOf<ImportErrorDetail>()
        val unmatchedDetails = mutableListOf<ImportErrorDetail>()
        val ambiguousDetails = mutableListOf<ImportConflict>()
        val warnings = mutableListOf<String>()

        if (startIndex > 0) {
            warnings.add("Resumed import from checkpoint at record index $startIndex of ${resolvedRecords.size}")
        }

        val total = totalParsedCount ?: (resolvedRecords.size + duplicateInSourceCount)
        var current = startIndex

        val recordsToProcess = if (startIndex > 0) resolvedRecords.drop(startIndex) else resolvedRecords
        val chunked = recordsToProcess.chunked(batchSize)

        for (chunk in chunked) {
            database.withTransaction {
                for (resolved in chunk) {
                    current++
                    onProgress(current, total, resolved.resolvedTitle)

                    val tmdbId = resolved.tmdbId
                    if (tmdbId == null) {
                        val error = ImportErrorDetail(
                            title = resolved.originalRecord.rawTitle,
                            reason = "Could not resolve title against local database or TMDB API",
                            recoverable = false,
                            rawRecordSummary = "${resolved.originalRecord.source} [${resolved.originalRecord.mediaType}]"
                        )
                        errors.add(error)
                        unmatchedDetails.add(error)
                        continue
                    }

                    if (resolved.confidence == MatchConfidence.AMBIGUOUS) {
                        val candidateNames = resolved.candidateMatches.map { "${it.title} (${it.year ?: "?"})" }
                        ambiguousDetails.add(
                            ImportConflict(
                                recordTitle = resolved.resolvedTitle,
                                mediaType = resolved.originalRecord.mediaType,
                                seasonNumber = resolved.originalRecord.seasonNumber,
                                episodeNumber = resolved.originalRecord.episodeNumber,
                                conflictType = ConflictType.AMBIGUOUS_MATCH,
                                localValue = null,
                                importedValue = resolved.originalRecord.rawTitle,
                                resolutionMessage = "Multiple candidates match this title. Automatically matched to highest-confidence candidate; review recommended.",
                                candidates = candidateNames
                            )
                        )
                    }

                    val rec = resolved.originalRecord
                    val isMovie = rec.mediaType == "movie"

                    try {
                        when (rec.recordType) {
                            ImportRecordType.WATCHED -> {
                                if (isMovie) {
                                    val existing = watchedMovieDao.getWatchedMovie(tmdbId)
                                    val recWatchedAt = rec.watchedAt ?: System.currentTimeMillis()

                                    if (existing != null) {
                                        when (policy) {
                                            ConflictResolutionPolicy.KEEP_EXISTING -> {
                                                skippedCount++
                                            }
                                            ConflictResolutionPolicy.OVERWRITE_LOCAL -> {
                                                watchedMovieDao.upsertWatchedMovie(
                                                    movieId = tmdbId,
                                                    title = resolved.resolvedTitle,
                                                    posterPath = resolved.posterPath,
                                                    watchedAt = recWatchedAt
                                                )
                                                updatedCount++
                                            }
                                            ConflictResolutionPolicy.LATEST_TIMESTAMP -> {
                                                if (rec.watchedAt != null && rec.watchedAt > existing.watchedAt) {
                                                    watchedMovieDao.upsertWatchedMovie(
                                                        movieId = tmdbId,
                                                        title = resolved.resolvedTitle,
                                                        posterPath = resolved.posterPath,
                                                        watchedAt = recWatchedAt
                                                    )
                                                    updatedCount++
                                                } else {
                                                    skippedCount++
                                                }
                                            }
                                            ConflictResolutionPolicy.MERGE_ADDITIVE -> {
                                                skippedCount++
                                            }
                                        }
                                    } else {
                                        watchedMovieDao.upsertWatchedMovie(
                                            movieId = tmdbId,
                                            title = resolved.resolvedTitle,
                                            posterPath = resolved.posterPath,
                                            watchedAt = recWatchedAt
                                        )
                                        insertedCount++
                                    }

                                    // If rating is attached to watch
                                    if (rec.rating != null) {
                                        applyRating(
                                            mediaId = tmdbId,
                                            mediaType = "movie",
                                            seasonNum = null,
                                            episodeNum = null,
                                            rating = rec.rating,
                                            watchedAt = rec.watchedAt,
                                            policy = policy,
                                            fallbackTitle = resolved.resolvedTitle,
                                            fallbackPosterPath = resolved.posterPath
                                        )
                                    }
                                } else {
                                    // TV Show
                                    val isShowLevel = rec.isExplicitShowLevel || (rec.seasonNumber == null && rec.episodeNumber == null)
                                    if (isShowLevel) {
                                        // Explicit show-level watch from TV Time GDPR or Trakt.
                                        // NEVER infer all episodes or create fake S01E01 episode watches!
                                        hydrateShowIfMissing(tmdbId, resolved.resolvedTitle, resolved.posterPath, "tv")

                                        if (rec.rating != null) {
                                            applyRating(
                                                mediaId = tmdbId,
                                                mediaType = "tv",
                                                seasonNum = null,
                                                episodeNum = null,
                                                rating = rec.rating,
                                                watchedAt = rec.watchedAt,
                                                policy = policy,
                                                fallbackTitle = resolved.resolvedTitle,
                                                fallbackPosterPath = resolved.posterPath
                                            )
                                        }
                                        if (!rec.reviewText.isNullOrBlank()) {
                                            applyReview(
                                                mediaId = tmdbId,
                                                mediaType = "tv",
                                                seasonNum = null,
                                                episodeNum = null,
                                                text = rec.reviewText,
                                                isSpoiler = rec.isSpoiler,
                                                watchedAt = rec.watchedAt,
                                                policy = policy,
                                                fallbackTitle = resolved.resolvedTitle,
                                                fallbackPosterPath = resolved.posterPath
                                            )
                                        }
                                        insertedCount++
                                    } else {
                                        // Explicit episode-level watch
                                        val seasonNum = rec.seasonNumber!!
                                        val epNum = rec.episodeNumber!!
                                        val recWatchedAt = rec.watchedAt ?: System.currentTimeMillis()

                                        hydrateTvHierarchyIfMissing(tmdbId, resolved.resolvedTitle, resolved.posterPath, seasonNum)
                                        val episode = episodeDao.getEpisodeBySeasonAndEpisodeNumber(tmdbId, seasonNum, epNum)
                                        val existingEp = watchedEpisodeDao.getWatchedEpisode(tmdbId, seasonNum, epNum)
                                        val tmdbEpId = when {
                                            episode != null && episode.id > 0 -> episode.id
                                            existingEp != null -> existingEp.tmdbEpisodeId
                                            else -> (tmdbId * 100000 + seasonNum * 1000 + epNum)
                                        }

                                        if (existingEp != null) {
                                            when (policy) {
                                                ConflictResolutionPolicy.KEEP_EXISTING -> {
                                                    skippedCount++
                                                }
                                                ConflictResolutionPolicy.OVERWRITE_LOCAL -> {
                                                    watchedEpisodeDao.upsertWatchedEpisode(
                                                        showId = tmdbId,
                                                        tmdbEpisodeId = tmdbEpId,
                                                        seasonNumber = seasonNum,
                                                        episodeNumber = epNum,
                                                        watchedAt = recWatchedAt,
                                                        isSkipped = false
                                                    )
                                                    updatedCount++
                                                }
                                                ConflictResolutionPolicy.LATEST_TIMESTAMP -> {
                                                    if (rec.watchedAt != null && rec.watchedAt > existingEp.watchedAt) {
                                                        watchedEpisodeDao.upsertWatchedEpisode(
                                                            showId = tmdbId,
                                                            tmdbEpisodeId = tmdbEpId,
                                                            seasonNumber = seasonNum,
                                                            episodeNumber = epNum,
                                                            watchedAt = recWatchedAt,
                                                            isSkipped = false
                                                        )
                                                        updatedCount++
                                                    } else {
                                                        skippedCount++
                                                    }
                                                }
                                                ConflictResolutionPolicy.MERGE_ADDITIVE -> {
                                                    skippedCount++
                                                }
                                            }
                                        } else {
                                            watchedEpisodeDao.upsertWatchedEpisode(
                                                showId = tmdbId,
                                                tmdbEpisodeId = tmdbEpId,
                                                seasonNumber = seasonNum,
                                                episodeNumber = epNum,
                                                watchedAt = recWatchedAt,
                                                isSkipped = false
                                            )
                                            insertedCount++
                                        }

                                        if (rec.rating != null) {
                                            applyRating(
                                                mediaId = tmdbId,
                                                mediaType = "tv",
                                                seasonNum = seasonNum,
                                                episodeNum = epNum,
                                                rating = rec.rating,
                                                watchedAt = rec.watchedAt,
                                                policy = policy,
                                                fallbackTitle = resolved.resolvedTitle,
                                                fallbackPosterPath = resolved.posterPath
                                            )
                                        }
                                    }
                                }
                            }
                            ImportRecordType.WATCHLIST -> {
                                val inWatchlist = watchlistDao.isInWatchlist(tmdbId, rec.mediaType)
                                if (inWatchlist) {
                                    skippedCount++
                                } else {
                                    val mediaType = if (isMovie) MediaType.MOVIE else MediaType.TV
                                    watchlistDao.insertToWatchlist(
                                        WatchlistEntity(
                                            tmdbId = tmdbId,
                                            mediaType = mediaType,
                                            title = resolved.resolvedTitle,
                                            posterPath = resolved.posterPath,
                                            addedAt = rec.watchedAt ?: System.currentTimeMillis(),
                                            syncStatus = SyncStatus.PENDING
                                        )
                                    )
                                    insertedCount++
                                }
                            }
                            ImportRecordType.RATING -> {
                                if (rec.rating != null) {
                                    val applied = applyRating(
                                        mediaId = tmdbId,
                                        mediaType = rec.mediaType,
                                        seasonNum = rec.seasonNumber,
                                        episodeNum = rec.episodeNumber,
                                        rating = rec.rating,
                                        watchedAt = rec.watchedAt,
                                        policy = policy,
                                        fallbackTitle = resolved.resolvedTitle,
                                        fallbackPosterPath = resolved.posterPath
                                    )
                                    if (applied) insertedCount++ else skippedCount++
                                }
                            }
                            ImportRecordType.REVIEW -> {
                                if (!rec.reviewText.isNullOrBlank()) {
                                    val applied = applyReview(
                                        mediaId = tmdbId,
                                        mediaType = rec.mediaType,
                                        seasonNum = rec.seasonNumber,
                                        episodeNum = rec.episodeNumber,
                                        text = rec.reviewText,
                                        isSpoiler = rec.isSpoiler,
                                        watchedAt = rec.watchedAt,
                                        policy = policy,
                                        fallbackTitle = resolved.resolvedTitle,
                                        fallbackPosterPath = resolved.posterPath
                                    )
                                    if (applied) insertedCount++ else skippedCount++
                                }
                                if (rec.rating != null) {
                                    applyRating(
                                        mediaId = tmdbId,
                                        mediaType = rec.mediaType,
                                        seasonNum = rec.seasonNumber,
                                        episodeNum = rec.episodeNumber,
                                        rating = rec.rating,
                                        watchedAt = rec.watchedAt,
                                        policy = policy,
                                        fallbackTitle = resolved.resolvedTitle,
                                        fallbackPosterPath = resolved.posterPath
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        errors.add(
                            ImportErrorDetail(
                                title = resolved.resolvedTitle,
                                reason = e.message ?: "Failed to apply database write"
                            )
                        )
                    }
                }
            }

            // Save chunk checkpoint after commit
            if (effectiveCheckpointManager != null && fingerprint != null) {
                val isDone = (current >= resolvedRecords.size)
                effectiveCheckpointManager.saveCheckpoint(
                    ImportCheckpoint(
                        importId = importId,
                        source = source.name,
                        fingerprint = fingerprint,
                        totalRecords = total,
                        processedIndex = current,
                        insertedCount = insertedCount,
                        updatedCount = updatedCount,
                        skippedCount = skippedCount,
                        failedCount = errors.size,
                        policy = policy.name,
                        status = if (isDone) CheckpointStatus.COMPLETED else CheckpointStatus.IN_PROGRESS,
                        lastUpdated = System.currentTimeMillis()
                    )
                )
            }
        }

        // Trigger asynchronous cloud sync after all local writes commit
        if (insertedCount > 0 || updatedCount > 0) {
            syncManager.syncAllNow()
        }

        val duration = System.currentTimeMillis() - startTime
        return ImportReport(
            importId = importId,
            source = source,
            policyApplied = policy,
            totalProcessed = total,
            insertedCount = insertedCount,
            updatedCount = updatedCount,
            skippedIdenticalCount = skippedCount,
            duplicateInSourceCount = duplicateInSourceCount,
            failedCount = errors.size,
            warnings = warnings,
            errors = errors,
            durationMs = duration,
            unresolvableCount = unmatchedDetails.size,
            ambiguousCount = ambiguousDetails.size,
            unmatchedDetails = unmatchedDetails,
            ambiguousDetails = ambiguousDetails
        )
    }

    private suspend fun ensureParentShowExists(
        mediaId: Int,
        mediaType: String,
        title: String,
        posterPath: String?
    ) {
        val normalizedType = if (mediaType.equals("movie", ignoreCase = true)) "movie" else "tv"
        val existing = showDao.getShowById(mediaId, normalizedType)
        if (existing == null) {
            try {
                showDao.insertShow(
                    ShowEntity(
                        id = mediaId,
                        title = title,
                        overview = "",
                        mediaType = normalizedType,
                        posterPath = posterPath,
                        voteAverage = 0.0
                    )
                )
            } catch (_: Exception) {
                // If it already exists or concurrent insert
            }
        }
    }

    private suspend fun applyRating(
        mediaId: Int,
        mediaType: String,
        seasonNum: Int?,
        episodeNum: Int?,
        rating: Float,
        watchedAt: Long?,
        policy: ConflictResolutionPolicy,
        fallbackTitle: String? = null,
        fallbackPosterPath: String? = null
    ): Boolean {
        ensureParentShowExists(mediaId, mediaType, fallbackTitle ?: "Unknown", fallbackPosterPath)
        val existing = reviewDao.getReviewForMediaAndEpisode(mediaId, mediaType, seasonNum, episodeNum)
        if (existing?.rating != null) {
            when (policy) {
                ConflictResolutionPolicy.KEEP_EXISTING -> return false
                ConflictResolutionPolicy.OVERWRITE_LOCAL -> {
                    reviewDao.upsertRating(mediaId, mediaType, seasonNum, episodeNum, rating)
                    return true
                }
                ConflictResolutionPolicy.LATEST_TIMESTAMP -> {
                    val incomingTime = watchedAt ?: System.currentTimeMillis()
                    if (incomingTime >= existing.updatedAt) {
                        reviewDao.upsertRating(mediaId, mediaType, seasonNum, episodeNum, rating)
                        return true
                    }
                    return false
                }
                ConflictResolutionPolicy.MERGE_ADDITIVE -> return false
            }
        } else {
            reviewDao.upsertRating(mediaId, mediaType, seasonNum, episodeNum, rating)
            return true
        }
    }

    private suspend fun applyReview(
        mediaId: Int,
        mediaType: String,
        seasonNum: Int?,
        episodeNum: Int?,
        text: String,
        isSpoiler: Boolean,
        watchedAt: Long?,
        policy: ConflictResolutionPolicy,
        fallbackTitle: String? = null,
        fallbackPosterPath: String? = null
    ): Boolean {
        ensureParentShowExists(mediaId, mediaType, fallbackTitle ?: "Unknown", fallbackPosterPath)
        val existing = reviewDao.getReviewForMediaAndEpisode(mediaId, mediaType, seasonNum, episodeNum)
        if (existing != null && !existing.reviewText.isNullOrBlank()) {
            when (policy) {
                ConflictResolutionPolicy.KEEP_EXISTING -> return false
                ConflictResolutionPolicy.OVERWRITE_LOCAL -> {
                    reviewDao.upsertReviewText(mediaId, mediaType, seasonNum, episodeNum, text, isSpoiler)
                    return true
                }
                ConflictResolutionPolicy.LATEST_TIMESTAMP -> {
                    val incomingTime = watchedAt ?: System.currentTimeMillis()
                    if (incomingTime >= existing.updatedAt) {
                        reviewDao.upsertReviewText(mediaId, mediaType, seasonNum, episodeNum, text, isSpoiler)
                        return true
                    }
                    return false
                }
                ConflictResolutionPolicy.MERGE_ADDITIVE -> return false
            }
        } else {
            reviewDao.upsertReviewText(mediaId, mediaType, seasonNum, episodeNum, text, isSpoiler)
            return true
        }
    }

    private suspend fun hydrateShowIfMissing(showId: Int, title: String, posterPath: String?, mediaType: String = "tv") {
        val normalizedType = if (mediaType.equals("movie", ignoreCase = true)) "movie" else "tv"
        try {
            val existing = showDao.getShowById(showId, normalizedType)
            if (existing == null) {
                val detail = tmdbApiService.getTvShowDetail(showId)
                val entity = with(dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper) {
                    detail.toEntity()
                }
                showDao.insertShow(entity)
            }
        } catch (_: Exception) {
            ensureParentShowExists(showId, normalizedType, title, posterPath)
        }
    }

    private suspend fun hydrateTvHierarchyIfMissing(
        showId: Int,
        title: String,
        posterPath: String?,
        seasonNumber: Int
    ) {
        ensureParentShowExists(showId, "tv", title, posterPath)
        try {
            val existingSeason = seasonDao.getSeasonByNumber(showId, seasonNumber)
            if (existingSeason == null) {
                val detail = tmdbApiService.getSeasonDetail(showId, seasonNumber)
                val seasonEntity = with(dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper) {
                    detail.toSeasonEntity(showId)
                }
                val episodeEntities = with(dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper) {
                    detail.toEpisodeEntities(showId)
                }
                seasonDao.insertSeasons(listOf(seasonEntity))
                episodeDao.insertEpisodes(episodeEntities)
            }
        } catch (_: Exception) {
            // Best effort hydration
        }
    }
}
