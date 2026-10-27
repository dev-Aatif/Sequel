package dev.sequel.app.data.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.*
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchlistEntity
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEntity
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.domain.importer.*
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.util.Locale

@HiltWorker
class MediaImportWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tmdbApiService: TmdbApiService,
    private val database: SequelDatabase,
    private val showDao: ShowDao,
    private val seasonDao: SeasonDao,
    private val episodeDao: EpisodeDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val watchedMovieDao: WatchedMovieDao,
    private val watchlistDao: WatchlistDao,
    private val reviewDao: ReviewDao,
    private val syncManager: SyncManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "media_import_worker"
        const val KEY_URI = "key_uri"
        const val KEY_SOURCE = "key_source"

        // Progress keys
        const val KEY_PROGRESS_STATE = "progress_state"
        const val KEY_MESSAGE = "message"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_SHOW_NAME = "show_name"
        const val KEY_IMPORTED_COUNT = "imported_count"

        const val STATE_PARSING = "PARSING"
        const val STATE_IMPORTING = "IMPORTING"
    }

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_URI) ?: return Result.failure()
        val source = inputData.getString(KEY_SOURCE)?.lowercase(Locale.ROOT) ?: "tvtime"
        val uri = Uri.parse(uriString)

        val parser: MediaDataParser = when (source) {
            "trakt" -> TraktExportParser()
            "letterboxd" -> LetterboxdExportParser()
            "sequel" -> SequelBackupParser()
            else -> TvTimeCsvParser()
        }

        setProgress(
            workDataOf(
                KEY_PROGRESS_STATE to STATE_PARSING,
                KEY_MESSAGE to "Reading and parsing export file..."
            )
        )

        val items: List<ImportedMediaItem>
        try {
            items = parser.parse(appContext, uri)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            cleanupTempFile(uri)
            return Result.failure(workDataOf(KEY_MESSAGE to (e.message ?: "Failed to parse file")))
        }

        val groupedByShow = items.groupBy { Pair(it.title, it.mediaType) }
        val groupedList = groupedByShow.toList()
        val totalShows = groupedList.size

        val prefs = appContext.getSharedPreferences("media_import_prefs", Context.MODE_PRIVATE)
        val prefsKey = "last_index_$uriString"
        val startIndex = prefs.getInt(prefsKey, 0)

        var currentShowIndex = startIndex
        var totalImported = 0
        val skippedShows = mutableListOf<String>()

        for (i in startIndex until groupedList.size) {
            val (groupKey, records) = groupedList[i]
            val showName = groupKey.first
            val mediaType = groupKey.second
            currentShowIndex = i + 1

            setProgress(
                workDataOf(
                    KEY_PROGRESS_STATE to STATE_IMPORTING,
                    KEY_CURRENT to currentShowIndex,
                    KEY_TOTAL to totalShows,
                    KEY_SHOW_NAME to showName
                )
            )

            try {
                // Throttle to respect TMDB API limits
                delay(35)

                val isMovie = mediaType == "movie"
                if (isMovie) {
                    val year = records.firstOrNull { it.year != null }?.year
                    val movieId = resolveAndUpsertMovie(showName, year)
                    if (movieId != null) {
                        val cachedMovie = showDao.getShowById(movieId, "movie")
                        val movieTitle = cachedMovie?.title ?: showName
                        val posterPath = cachedMovie?.posterPath

                        database.withTransaction {
                            for (rec in records) {
                                when (rec.recordType) {
                                    ImportRecordType.WATCHLIST -> {
                                        watchlistDao.insertToWatchlist(
                                            WatchlistEntity(
                                                tmdbId = movieId,
                                                mediaType = MediaType.MOVIE,
                                                title = movieTitle,
                                                posterPath = posterPath,
                                                addedAt = rec.watchedAt ?: System.currentTimeMillis(),
                                                syncStatus = SyncStatus.PENDING
                                            )
                                        )
                                    }
                                    ImportRecordType.WATCHED -> {
                                        watchedMovieDao.upsertWatchedMovie(
                                            movieId = movieId,
                                            title = movieTitle,
                                            posterPath = posterPath,
                                            watchedAt = rec.watchedAt ?: System.currentTimeMillis()
                                        )
                                        // If rating attached to watched row
                                        if (rec.rating != null) {
                                            reviewDao.upsertRating(
                                                mediaId = movieId,
                                                mediaType = "movie",
                                                seasonNum = null,
                                                episodeNum = null,
                                                rating = rec.rating
                                            )
                                        }
                                    }
                                    ImportRecordType.RATING -> {
                                        if (rec.rating != null) {
                                            reviewDao.upsertRating(
                                                mediaId = movieId,
                                                mediaType = "movie",
                                                seasonNum = null,
                                                episodeNum = null,
                                                rating = rec.rating
                                            )
                                        }
                                    }
                                    ImportRecordType.REVIEW -> {
                                        if (!rec.reviewText.isNullOrBlank()) {
                                            reviewDao.upsertReviewText(
                                                mediaId = movieId,
                                                mediaType = "movie",
                                                seasonNum = null,
                                                episodeNum = null,
                                                text = rec.reviewText,
                                                isSpoiler = rec.isSpoiler
                                            )
                                        }
                                        if (rec.rating != null) {
                                            reviewDao.upsertRating(
                                                mediaId = movieId,
                                                mediaType = "movie",
                                                seasonNum = null,
                                                episodeNum = null,
                                                rating = rec.rating
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        totalImported += records.size
                    } else {
                        skippedShows.add(showName)
                    }
                } else {
                    // TV Show processing
                    val year = records.firstOrNull { it.year != null }?.year
                    val showId = resolveAndUpsertShow(showName, year)
                    if (showId != null) {
                        val cachedShow = showDao.getShowById(showId, "tv")
                        val showTitle = cachedShow?.title ?: showName
                        val posterPath = cachedShow?.posterPath

                        val episodesBySeason = records.filter { it.seasonNumber != null }.groupBy { it.seasonNumber!! }

                        // Process watchlist entries that might not have season/episode
                        val watchlistEntries = records.filter { it.recordType == ImportRecordType.WATCHLIST }
                        if (watchlistEntries.isNotEmpty()) {
                            for (wl in watchlistEntries) {
                                watchlistDao.insertToWatchlist(
                                    WatchlistEntity(
                                        tmdbId = showId,
                                        mediaType = MediaType.TV,
                                        title = showTitle,
                                        posterPath = posterPath,
                                        addedAt = wl.watchedAt ?: System.currentTimeMillis(),
                                        syncStatus = SyncStatus.PENDING
                                    )
                                )
                            }
                        }

                        // Process show-level ratings / reviews (where seasonNumber is null)
                        val showRatings = records.filter { it.seasonNumber == null && (it.rating != null || !it.reviewText.isNullOrBlank()) }
                        for (sr in showRatings) {
                            if (sr.rating != null) {
                                reviewDao.upsertRating(
                                    mediaId = showId,
                                    mediaType = "tv",
                                    seasonNum = null,
                                    episodeNum = null,
                                    rating = sr.rating
                                )
                            }
                            if (!sr.reviewText.isNullOrBlank()) {
                                reviewDao.upsertReviewText(
                                    mediaId = showId,
                                    mediaType = "tv",
                                    seasonNum = null,
                                    episodeNum = null,
                                    text = sr.reviewText,
                                    isSpoiler = sr.isSpoiler
                                )
                            }
                        }

                        // Process episodes per season
                        for ((seasonNum, seasonRecs) in episodesBySeason) {
                            try {
                                val seasonDetail = withRetry {
                                    tmdbApiService.getSeasonDetail(showId, seasonNum)
                                }

                                val seasonEntity = with(dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper) {
                                    seasonDetail.toSeasonEntity(showId)
                                }
                                seasonDao.insertSeasons(listOf(seasonEntity))

                                val episodeEntities = with(dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper) {
                                    seasonDetail.toEpisodeEntities(showId)
                                }
                                episodeDao.insertEpisodes(episodeEntities)

                                val episodeMap = seasonDetail.episodes.associateBy { it.episodeNumber }

                                database.withTransaction {
                                    for (rec in seasonRecs) {
                                        val epNum = rec.episodeNumber ?: continue
                                        val tmdbEpisode = episodeMap[epNum]
                                        if (tmdbEpisode != null) {
                                            watchedEpisodeDao.upsertWatchedEpisode(
                                                showId = showId,
                                                tmdbEpisodeId = tmdbEpisode.id,
                                                seasonNumber = seasonNum,
                                                episodeNumber = epNum,
                                                watchedAt = rec.watchedAt ?: System.currentTimeMillis(),
                                                isSkipped = false
                                            )
                                            if (rec.rating != null) {
                                                reviewDao.upsertRating(
                                                    mediaId = showId,
                                                    mediaType = "tv",
                                                    seasonNum = seasonNum,
                                                    episodeNum = epNum,
                                                    rating = rec.rating
                                                )
                                            }
                                        }
                                    }
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // Skip this season on network error or missing data
                            }
                        }
                        totalImported += records.size
                    } else {
                        skippedShows.add(showName)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                skippedShows.add(showName)
            } finally {
                prefs.edit().putInt(prefsKey, currentShowIndex).apply()
            }
        }

        // Trigger Supabase Sync on completed batches
        if (totalImported > 0) {
            syncManager.syncAllNow()
        }

        cleanupTempFile(uri)
        prefs.edit().remove(prefsKey).apply()

        return Result.success(
            workDataOf(
                KEY_IMPORTED_COUNT to totalImported,
                "skipped_count" to skippedShows.size
            )
        )
    }

    private suspend fun resolveAndUpsertShow(showName: String, year: Int?): Int? {
        // 1. Check local Room database cache first
        val localShow = showDao.findShowByTitle(showName, "tv")
        if (localShow != null) {
            return localShow.id
        }

        // 2. Fetch from TMDB
        val searchResult = withRetry { tmdbApiService.searchTv(showName) }
        val results = searchResult.results
        if (results.isEmpty()) return null

        // If release year was supplied (e.g. from Trakt/Letterboxd), match against air date
        val matchedShow = if (year != null) {
            results.find { it.firstAirDate?.take(4) == year.toString() } ?: results.first()
        } else {
            results.first()
        }

        val showEntity = matchedShow.toEntity(fallbackMediaType = "tv")
        val existing = showDao.getShowById(showEntity.id, "tv")
        if (existing == null) {
            showDao.insertShows(listOf(showEntity))
        }

        return showEntity.id
    }

    private suspend fun resolveAndUpsertMovie(movieName: String, year: Int?): Int? {
        // 1. Check local Room database cache first
        val localMovie = showDao.findShowByTitle(movieName, "movie")
        if (localMovie != null) {
            return localMovie.id
        }

        // 2. Fetch from TMDB
        val searchResult = withRetry { tmdbApiService.searchMovie(movieName) }
        val results = searchResult.results
        if (results.isEmpty()) return null

        // Disambiguate by release year if present
        val matchedMovie = if (year != null) {
            results.find { it.firstAirDate?.take(4) == year.toString() } ?: results.first()
        } else {
            results.first()
        }

        val movieEntity = matchedMovie.toEntity(fallbackMediaType = "movie")
        val existing = showDao.getShowById(movieEntity.id, "movie")
        if (existing == null) {
            showDao.insertShows(listOf(movieEntity))
        }

        return movieEntity.id
    }

    private fun cleanupTempFile(uri: Uri) {
        if (uri.scheme == "file" && uri.path != null) {
            try {
                val file = java.io.File(uri.path!!)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ignore cleanup failures
            }
        }
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        var currentDelay = 500L
        val maxRetries = 3
        var retryCount = 0

        while (true) {
            try {
                return block()
            } catch (e: HttpException) {
                if (e.code() == 429 && retryCount < maxRetries) {
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
