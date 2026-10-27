package dev.sequel.app.domain.backup

import androidx.room.withTransaction
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.*
import dev.sequel.app.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SequelBackupService @Inject constructor(
    private val database: SequelDatabase,
    private val showDao: ShowDao,
    private val watchedMovieDao: WatchedMovieDao,
    private val watchedEpisodeDao: WatchedEpisodeDao,
    private val watchlistDao: WatchlistDao,
    private val reviewDao: ReviewDao,
    private val mountRushmoreDao: MountRushmoreDao
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Generates a complete, schema-versioned JSON backup of all local Sequel user data.
     */
    suspend fun exportJsonBackup(): String = withContext(Dispatchers.IO) {
        val shows = showDao.getAllShows().map { s ->
            ShowBackupDto(
                id = s.id,
                title = s.title,
                overview = s.overview,
                posterPath = s.posterPath,
                backdropPath = s.backdropPath,
                firstAirDate = s.firstAirDate,
                voteAverage = s.voteAverage,
                genreIds = s.genreIds,
                status = s.status,
                mediaType = s.mediaType,
                numberOfSeasons = s.numberOfSeasons,
                numberOfEpisodes = s.numberOfEpisodes,
                runtime = s.runtime,
                genresDisplay = s.genresDisplay,
                episodeRuntime = s.episodeRuntime,
                contentRating = s.contentRating
            )
        }

        val watchedMovies = watchedMovieDao.getAllWatchedMovies().map { m ->
            WatchedMovieBackupDto(
                tmdbMovieId = m.tmdbMovieId,
                title = m.title,
                posterPath = m.posterPath,
                watchedAt = m.watchedAt,
                syncStatus = m.syncStatus.name
            )
        }

        val watchedEpisodes = watchedEpisodeDao.getAllWatchedEpisodes().map { e ->
            WatchedEpisodeBackupDto(
                showId = e.showId,
                mediaType = e.mediaType.name.lowercase(),
                tmdbEpisodeId = e.tmdbEpisodeId,
                seasonNumber = e.seasonNumber,
                episodeNumber = e.episodeNumber,
                watchedAt = e.watchedAt,
                isSkipped = e.isSkipped,
                syncStatus = e.syncStatus.name
            )
        }

        val watchlist = watchlistDao.getAllWatchlist().map { w ->
            WatchlistBackupDto(
                tmdbId = w.tmdbId,
                mediaType = w.mediaType.name.lowercase(),
                title = w.title,
                posterPath = w.posterPath,
                addedAt = w.addedAt
            )
        }

        val reviews = reviewDao.getAllReviews().map { r ->
            ReviewBackupDto(
                mediaId = r.mediaId,
                mediaType = r.mediaType,
                seasonNum = r.seasonNum,
                episodeNum = r.episodeNum,
                reviewText = r.reviewText,
                rating = r.rating,
                isSpoiler = r.isSpoiler,
                isRatingOnly = r.isRatingOnly,
                createdAt = r.createdAt,
                updatedAt = r.updatedAt
            )
        }

        val rushmore = mountRushmoreDao.getAllSlots().map { m ->
            MountRushmoreBackupDto(
                slotIndex = m.slotIndex,
                mediaId = m.mediaId,
                mediaType = m.mediaType,
                title = m.title,
                posterPath = m.posterPath,
                year = m.year,
                rating = m.rating,
                updatedAt = m.updatedAt
            )
        }

        val backup = SequelBackupModel(
            schemaVersion = SequelBackupModel.CURRENT_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = "1.0.0",
            shows = shows,
            watchedMovies = watchedMovies,
            watchedEpisodes = watchedEpisodes,
            watchlist = watchlist,
            reviews = reviews,
            mountRushmore = rushmore
        )

        json.encodeToString(backup)
    }

    /**
     * Generates a ZIP archive containing standard CSV exports for interoperability
     * with spreadsheets, Trakt, and Letterboxd.
     */
    suspend fun exportCsvZip(): ByteArray = withContext(Dispatchers.IO) {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // 1. Watched Movies CSV
            zip.putNextEntry(ZipEntry("sequel_watched_movies.csv"))
            val movieWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            movieWriter.write("tmdb_id,title,watched_at\n")
            watchedMovieDao.getAllWatchedMovies().forEach { m ->
                val escapedTitle = escapeCsv(m.title)
                movieWriter.write("${m.tmdbMovieId},$escapedTitle,${m.watchedAt}\n")
            }
            movieWriter.flush()
            zip.closeEntry()

            // 2. Watched Episodes CSV
            zip.putNextEntry(ZipEntry("sequel_watched_episodes.csv"))
            val epWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            epWriter.write("show_id,season_number,episode_number,tmdb_episode_id,watched_at,is_skipped\n")
            watchedEpisodeDao.getAllWatchedEpisodes().forEach { e ->
                epWriter.write("${e.showId},${e.seasonNumber},${e.episodeNumber},${e.tmdbEpisodeId},${e.watchedAt},${if (e.isSkipped) 1 else 0}\n")
            }
            epWriter.flush()
            zip.closeEntry()

            // 3. Watchlist CSV
            zip.putNextEntry(ZipEntry("sequel_watchlist.csv"))
            val wlWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            wlWriter.write("tmdb_id,media_type,title,added_at\n")
            watchlistDao.getAllWatchlist().forEach { w ->
                val escapedTitle = escapeCsv(w.title)
                wlWriter.write("${w.tmdbId},${w.mediaType.name.lowercase()},$escapedTitle,${w.addedAt}\n")
            }
            wlWriter.flush()
            zip.closeEntry()

            // 4. Reviews & Ratings CSV
            zip.putNextEntry(ZipEntry("sequel_reviews_and_ratings.csv"))
            val revWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            revWriter.write("media_id,media_type,season_number,episode_number,rating,review,is_spoiler,updated_at\n")
            reviewDao.getAllReviews().forEach { r ->
                val escapedReview = escapeCsv(r.reviewText ?: "")
                val spoilerInt = if (r.isSpoiler) 1 else 0
                revWriter.write("${r.mediaId},${r.mediaType},${r.seasonNum ?: ""},${r.episodeNum ?: ""},${r.rating ?: ""},$escapedReview,$spoilerInt,${r.updatedAt}\n")
            }
            revWriter.flush()
            zip.closeEntry()
        }
        out.toByteArray()
    }

    /**
     * Restores user data from a versioned Sequel JSON backup into Room within a clean transaction.
     */
    suspend fun restoreJsonBackup(jsonString: String): BackupRestoreResult = withContext(Dispatchers.IO) {
        val backup = try {
            json.decodeFromString<SequelBackupModel>(jsonString)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Sequel backup format: ${e.message}")
        }

        if (backup.schemaVersion > SequelBackupModel.CURRENT_SCHEMA_VERSION) {
            throw IllegalArgumentException("Backup was created with a newer version of Sequel (Schema v${backup.schemaVersion}). Please update Sequel to restore this file.")
        }

        var showsCount = 0
        var moviesCount = 0
        var episodesCount = 0
        var watchlistCount = 0
        var reviewsCount = 0
        var rushmoreCount = 0

        database.withTransaction {
            // 1. Shows must be inserted first to satisfy Foreign Key constraints
            if (backup.shows.isNotEmpty()) {
                val showEntities = backup.shows.map { s ->
                    ShowEntity(
                        id = s.id,
                        title = s.title,
                        overview = s.overview,
                        posterPath = s.posterPath,
                        backdropPath = s.backdropPath,
                        firstAirDate = s.firstAirDate,
                        voteAverage = s.voteAverage,
                        genreIds = s.genreIds,
                        status = s.status,
                        mediaType = s.mediaType,
                        numberOfSeasons = s.numberOfSeasons,
                        numberOfEpisodes = s.numberOfEpisodes,
                        runtime = s.runtime,
                        genresDisplay = s.genresDisplay,
                        episodeRuntime = s.episodeRuntime,
                        contentRating = s.contentRating
                    )
                }
                showDao.insertShows(showEntities)
                showsCount = showEntities.size
            }

            // 2. Watched Movies
            if (backup.watchedMovies.isNotEmpty()) {
                backup.watchedMovies.forEach { m ->
                    watchedMovieDao.upsertWatchedMovie(
                        movieId = m.tmdbMovieId,
                        title = m.title,
                        posterPath = m.posterPath,
                        watchedAt = m.watchedAt
                    )
                }
                moviesCount = backup.watchedMovies.size
            }

            // 3. Watched Episodes
            if (backup.watchedEpisodes.isNotEmpty()) {
                backup.watchedEpisodes.forEach { e ->
                    watchedEpisodeDao.upsertWatchedEpisode(
                        showId = e.showId,
                        tmdbEpisodeId = e.tmdbEpisodeId,
                        seasonNumber = e.seasonNumber,
                        episodeNumber = e.episodeNumber,
                        watchedAt = e.watchedAt,
                        isSkipped = e.isSkipped
                    )
                }
                episodesCount = backup.watchedEpisodes.size
            }

            // 4. Watchlist
            if (backup.watchlist.isNotEmpty()) {
                backup.watchlist.forEach { w ->
                    val mediaType = if (w.mediaType.equals("movie", ignoreCase = true)) MediaType.MOVIE else MediaType.TV
                    watchlistDao.insertToWatchlist(
                        WatchlistEntity(
                            tmdbId = w.tmdbId,
                            mediaType = mediaType,
                            title = w.title,
                            posterPath = w.posterPath,
                            addedAt = w.addedAt,
                            syncStatus = SyncStatus.PENDING
                        )
                    )
                }
                watchlistCount = backup.watchlist.size
            }

            // 5. Reviews and Ratings
            if (backup.reviews.isNotEmpty()) {
                backup.reviews.forEach { r ->
                    if (r.rating != null) {
                        reviewDao.upsertRating(
                            mediaId = r.mediaId,
                            mediaType = r.mediaType,
                            seasonNum = r.seasonNum,
                            episodeNum = r.episodeNum,
                            rating = r.rating
                        )
                    }
                    if (!r.reviewText.isNullOrBlank()) {
                        reviewDao.upsertReviewText(
                            mediaId = r.mediaId,
                            mediaType = r.mediaType,
                            seasonNum = r.seasonNum,
                            episodeNum = r.episodeNum,
                            text = r.reviewText,
                            isSpoiler = r.isSpoiler
                        )
                    }
                }
                reviewsCount = backup.reviews.size
            }

            // 6. Mount Rushmore
            if (backup.mountRushmore.isNotEmpty()) {
                backup.mountRushmore.forEach { m ->
                    mountRushmoreDao.upsertSlot(
                        MountRushmoreEntity(
                            slotIndex = m.slotIndex,
                            mediaId = m.mediaId,
                            mediaType = m.mediaType,
                            title = m.title,
                            posterPath = m.posterPath,
                            year = m.year,
                            rating = m.rating,
                            updatedAt = m.updatedAt
                        )
                    )
                }
                rushmoreCount = backup.mountRushmore.size
            }
        }

        BackupRestoreResult(
            totalRestored = showsCount + moviesCount + episodesCount + watchlistCount + reviewsCount + rushmoreCount,
            showsCount = showsCount,
            moviesCount = moviesCount,
            episodesCount = episodesCount,
            watchlistCount = watchlistCount,
            reviewsCount = reviewsCount,
            mountRushmoreCount = rushmoreCount
        )
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
