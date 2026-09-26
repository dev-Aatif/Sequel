package dev.sequel.app.data.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEntity
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.domain.importer.ImportedMediaItem
import dev.sequel.app.domain.importer.TraktExportParser
import dev.sequel.app.domain.importer.TvTimeCsvParser
import kotlinx.coroutines.delay
import retrofit2.HttpException

@HiltWorker
class MediaImportWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tmdbApiService: TmdbApiService,
    private val showDao: ShowDao,
    private val seasonDao: dev.sequel.app.data.local.dao.SeasonDao,
    private val episodeDao: dev.sequel.app.data.local.dao.EpisodeDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
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
        val source = inputData.getString(KEY_SOURCE) ?: "tvtime"
        val uri = Uri.parse(uriString)

        val parser = if (source == "trakt") TraktExportParser() else TvTimeCsvParser()

        setProgress(
            workDataOf(
                KEY_PROGRESS_STATE to STATE_PARSING,
                KEY_MESSAGE to "Reading file..."
            )
        )

        val items: List<ImportedMediaItem>
        try {
            items = parser.parse(appContext, uri)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (uri.scheme == "file" && uri.path != null) {
                val file = java.io.File(uri.path!!)
                if (file.exists()) {
                    file.delete()
                }
            }
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
        
        // Keep track of shows we couldn't resolve
        val skippedShows = mutableListOf<String>()

        for (i in startIndex until groupedList.size) {
            val (groupKey, episodes) = groupedList[i]
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
                // Throttle to avoid TMDB 429 Rate Limit
                delay(50)

                val isMovie = mediaType == "movie"
                if (isMovie) {
                    val movieId = resolveAndUpsertMovie(showName)
                    if (movieId != null) {
                        val entities = episodes.map { ep ->
                            WatchedEpisodeEntity(
                                mediaType = MediaType.MOVIE,
                                showId = movieId,
                                episodeId = -1,
                                seasonNumber = -1,
                                episodeNumber = -1,
                                syncStatus = SyncStatus.PENDING,
                                watchedAt = ep.watchedAt ?: System.currentTimeMillis()
                            )
                        }
                        if (entities.isNotEmpty()) {
                            entities.chunked(200).forEach { chunk ->
                                watchedEpisodeDao.insertWatchedEpisodes(chunk)
                            }
                            totalImported += entities.size
                        }
                    } else {
                        skippedShows.add(showName)
                    }
                } else {
                    val showId = resolveAndUpsertShow(showName)
                    if (showId != null) {
                        val entities = mutableListOf<WatchedEpisodeEntity>()
                        val episodesBySeason = episodes.groupBy { it.seasonNumber }

                        for ((seasonNum, seasonEps) in episodesBySeason) {
                            if (seasonNum == null) continue
                            try {
                                // Retry block for season details
                                val seasonDetail = withRetry {
                                    tmdbApiService.getSeasonDetail(showId, seasonNum)
                                }
                                
                                val seasonEntity = with(dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper) { seasonDetail.toSeasonEntity(showId) }
                                seasonDao.insertSeasons(listOf(seasonEntity))

                                val episodeEntities = with(dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper) { seasonDetail.toEpisodeEntities(showId) }
                                episodeDao.insertEpisodes(episodeEntities)

                                val episodeMap = seasonDetail.episodes.associateBy { it.episodeNumber }

                                for (ep in seasonEps) {
                                    val tmdbEpisode = episodeMap[ep.episodeNumber]
                                    if (tmdbEpisode != null) {
                                        entities.add(
                                            WatchedEpisodeEntity(
                                                mediaType = MediaType.TV,
                                                showId = showId,
                                                episodeId = tmdbEpisode.id,
                                                seasonNumber = ep.seasonNumber,
                                                episodeNumber = ep.episodeNumber,
                                                syncStatus = SyncStatus.PENDING,
                                                watchedAt = ep.watchedAt ?: System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                // Skip this season if network error or missing data
                            }
                        }

                        if (entities.isNotEmpty()) {
                            // Batch insert in chunks of 200 to prevent SQLite variable limits
                            entities.chunked(200).forEach { chunk ->
                                watchedEpisodeDao.insertWatchedEpisodes(chunk)
                            }
                            totalImported += entities.size
                        }
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

        // Trigger Supabase Sync
        if (totalImported > 0) {
            syncManager.syncWatchedEpisodesNow()
        }

        if (uri.scheme == "file" && uri.path != null) {
            val file = java.io.File(uri.path!!)
            if (file.exists()) {
                file.delete()
            }
        }

        prefs.edit().remove(prefsKey).apply()

        return Result.success(
            workDataOf(
                KEY_IMPORTED_COUNT to totalImported,
                "skipped_count" to skippedShows.size
            )
        )
    }

    private suspend fun resolveAndUpsertShow(showName: String): Int? {
        val searchResult = withRetry { tmdbApiService.searchTv(showName) }
        val tmdbShow = searchResult.results.firstOrNull() ?: return null
        
        // Convert to entity and upsert BEFORE inserting episodes to satisfy Foreign Key constraints
        val showEntity = tmdbShow.toEntity(fallbackMediaType = "tv")
        
        // Check if we need to update or if it's missing
        val existingShow = showDao.getShowById(showEntity.id, "tv")
        if (existingShow == null) {
            showDao.insertShows(listOf(showEntity))
        } else {
            // Already exists, we can optionally update it, but for import we just ensure it exists
        }
        
        return showEntity.id
    }

    private suspend fun resolveAndUpsertMovie(movieName: String): Int? {
        val searchResult = withRetry { tmdbApiService.searchMovie(movieName) }
        val tmdbMovie = searchResult.results.firstOrNull() ?: return null
        
        // Convert to entity and upsert BEFORE inserting episodes to satisfy Foreign Key constraints
        val movieEntity = tmdbMovie.toEntity(fallbackMediaType = "movie")
        
        // Check if we need to update or if it's missing
        val existingMovie = showDao.getShowById(movieEntity.id, "movie")
        if (existingMovie == null) {
            showDao.insertShows(listOf(movieEntity))
        }
        
        return movieEntity.id
    }

    /** Helper for exponential backoff on HTTP 429 Too Many Requests */
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
            } catch (e: Exception) {
                throw e
            }
        }
    }
}
