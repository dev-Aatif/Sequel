package dev.sequel.app.domain.importer.resolver

import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.dto.TmdbShowDto
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEntity
import dev.sequel.app.domain.importer.core.CanonicalImportRecord
import dev.sequel.app.domain.importer.core.MatchConfidence
import kotlinx.coroutines.delay
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

data class CandidateMediaSummary(
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    val posterPath: String? = null
)

data class ResolvedMediaRecord(
    val originalRecord: CanonicalImportRecord,
    val tmdbId: Int?,
    val resolvedTitle: String,
    val posterPath: String? = null,
    val confidence: MatchConfidence,
    val candidateMatches: List<CandidateMediaSummary> = emptyList()
)

@Singleton
class IdentityResolver @Inject constructor(
    private val showDao: ShowDao,
    private val tmdbApiService: TmdbApiService
) {

    /**
     * Resolves external media records against local Room cache first, then TMDB API,
     * assigning an explicit match confidence score.
     */
    suspend fun resolve(record: CanonicalImportRecord): ResolvedMediaRecord {
        val mediaType = record.mediaType

        // 1. Direct ID match (e.g. from Trakt raw export or Sequel backup)
        val explicitTmdbId = record.identifiers.tmdbId
        if (explicitTmdbId != null && explicitTmdbId > 0) {
            val cached = showDao.getShowById(explicitTmdbId, mediaType)
            if (cached != null) {
                return ResolvedMediaRecord(
                    originalRecord = record,
                    tmdbId = explicitTmdbId,
                    resolvedTitle = cached.title,
                    posterPath = cached.posterPath,
                    confidence = MatchConfidence.EXACT_ID
                )
            } else {
                // Hydrate show metadata from TMDB
                val hydrated = hydrateShowById(explicitTmdbId, mediaType)
                if (hydrated != null) {
                    return ResolvedMediaRecord(
                        originalRecord = record,
                        tmdbId = explicitTmdbId,
                        resolvedTitle = hydrated.title,
                        posterPath = hydrated.posterPath,
                        confidence = MatchConfidence.EXACT_ID
                    )
                }
            }
        }

        // 2. Local database cache search by Title
        val localMatch = showDao.findShowByTitle(record.normalizedTitle, mediaType)
        if (localMatch != null) {
            val localYear = localMatch.firstAirDate?.take(4)?.toIntOrNull()
            val confidence = if (record.year != null && localYear != null && record.year == localYear) {
                MatchConfidence.EXACT_TITLE_YEAR
            } else {
                MatchConfidence.LOCAL_CACHE_MATCH
            }
            return ResolvedMediaRecord(
                originalRecord = record,
                tmdbId = localMatch.id,
                resolvedTitle = localMatch.title,
                posterPath = localMatch.posterPath,
                confidence = confidence
            )
        }

        // 3. Remote TMDB search with retry
        val (candidates, fetchError) = searchTmdb(record.normalizedTitle, mediaType)
        if (fetchError != null || candidates.isEmpty()) {
            return ResolvedMediaRecord(
                originalRecord = record,
                tmdbId = null,
                resolvedTitle = record.normalizedTitle,
                confidence = MatchConfidence.UNRESOLVED
            )
        }

        // 4. Disambiguation & confidence calculation
        val exactYearMatch = if (record.year != null) {
            candidates.find { it.firstAirDate?.take(4) == record.year.toString() }
        } else null

        val chosen: TmdbShowDto
        val confidence: MatchConfidence

        val candidateSummaries = candidates.map { c ->
            CandidateMediaSummary(
                tmdbId = c.id,
                title = (c.title ?: c.name ?: "").trim(),
                year = (c.firstAirDate ?: c.releaseDate)?.take(4)?.toIntOrNull(),
                posterPath = c.posterPath
            )
        }

        if (exactYearMatch != null) {
            chosen = exactYearMatch
            confidence = MatchConfidence.EXACT_TITLE_YEAR
        } else if (candidates.size == 1) {
            chosen = candidates.first()
            confidence = MatchConfidence.FUZZY_MATCH
        } else {
            // Check if first candidate matches title exactly
            val first = candidates.first()
            val candidateTitle = (first.title ?: first.name ?: "").trim()
            if (candidateTitle.equals(record.normalizedTitle, ignoreCase = true)) {
                chosen = first
                confidence = MatchConfidence.FUZZY_MATCH
            } else {
                chosen = first
                confidence = MatchConfidence.AMBIGUOUS
            }
        }

        // Cache candidate to local Room
        val showEntity = chosen.toEntity(fallbackMediaType = mediaType)
        if (showDao.getShowById(showEntity.id, mediaType) == null) {
            showDao.insertShows(listOf(showEntity))
        }

        return ResolvedMediaRecord(
            originalRecord = record,
            tmdbId = showEntity.id,
            resolvedTitle = showEntity.title,
            posterPath = showEntity.posterPath,
            confidence = confidence,
            candidateMatches = if (confidence == MatchConfidence.AMBIGUOUS) candidateSummaries else emptyList()
        )
    }

    private suspend fun hydrateShowById(id: Int, mediaType: String): dev.sequel.app.data.local.entity.ShowEntity? {
        return try {
            val entity = if (mediaType == "tv") {
                val detail = withRetry { tmdbApiService.getTvShowDetail(id) }
                detail.toEntity()
            } else {
                val detail = withRetry { tmdbApiService.getMovieDetail(id) }
                detail.toEntity()
            }
            showDao.insertShows(listOf(entity))
            entity
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun searchTmdb(query: String, mediaType: String): Pair<List<TmdbShowDto>, Exception?> {
        return try {
            val results = withRetry {
                if (mediaType == "tv") tmdbApiService.searchTv(query).results
                else tmdbApiService.searchMovie(query).results
            }
            Pair(results, null)
        } catch (e: Exception) {
            Pair(emptyList(), e)
        }
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var currentDelay = 400L
        val maxRetries = 3
        var retryCount = 0

        while (true) {
            try {
                return block()
            } catch (e: HttpException) {
                if ((e.code() == 429 || e.code() in 500..599) && retryCount < maxRetries) {
                    retryCount++
                    delay(currentDelay)
                    currentDelay *= 2
                } else {
                    throw e
                }
            } catch (e: java.io.IOException) {
                if (retryCount < maxRetries) {
                    retryCount++
                    delay(currentDelay)
                    currentDelay *= 2
                } else {
                    throw e
                }
            }
        }
    }
}
