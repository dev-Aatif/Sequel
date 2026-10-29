package dev.sequel.app.domain.backup

import androidx.room.withTransaction
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.*
import dev.sequel.app.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
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
    private val mountRushmoreDao: MountRushmoreDao,
    private val backupValidator: BackupValidator = BackupValidator(),
    private val backupMigrator: BackupMigrator = BackupMigrator()
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Generates a complete, schema-versioned JSON backup of all local Sequel user data.
     * Backups contain zero secrets, access tokens, passwords, or remote user UUIDs.
     * Supports cooperative coroutine cancellation.
     */
    suspend fun exportJsonBackup(): String = withContext(Dispatchers.IO) {
        val shows = showDao.getAllShows().map { s ->
            currentCoroutineContext().ensureActive()
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
            currentCoroutineContext().ensureActive()
            WatchedMovieBackupDto(
                tmdbMovieId = m.tmdbMovieId,
                title = m.title,
                posterPath = m.posterPath,
                watchedAt = m.watchedAt,
                syncStatus = m.syncStatus.name
            )
        }

        val watchedEpisodes = watchedEpisodeDao.getAllWatchedEpisodes().map { e ->
            currentCoroutineContext().ensureActive()
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
            currentCoroutineContext().ensureActive()
            WatchlistBackupDto(
                tmdbId = w.tmdbId,
                mediaType = w.mediaType.name.lowercase(),
                title = w.title,
                posterPath = w.posterPath,
                addedAt = w.addedAt
            )
        }

        val reviews = reviewDao.getAllReviews().map { r ->
            currentCoroutineContext().ensureActive()
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
                updatedAt = r.updatedAt,
                originalRating = r.rating,
                originalRatingScale = 10.0f
            )
        }

        val rushmore = mountRushmoreDao.getAllSlots().map { m ->
            currentCoroutineContext().ensureActive()
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
     * Safely exports backup JSON to a file by writing to a temporary file first (.tmp)
     * and atomically renaming on completion. In case of failure or cancellation,
     * the temporary file is deleted so corrupt/incomplete files are never exposed.
     */
    suspend fun exportJsonBackupToFile(targetFile: File): File = withContext(Dispatchers.IO) {
        val parentDir = targetFile.parentFile ?: File(".")
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        val tempFile = File(parentDir, "${targetFile.name}.${System.currentTimeMillis()}.tmp")
        try {
            val jsonContent = exportJsonBackup()
            tempFile.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                writer.write(jsonContent)
                writer.flush()
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            targetFile
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }
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
                currentCoroutineContext().ensureActive()
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
                currentCoroutineContext().ensureActive()
                epWriter.write("${e.showId},${e.seasonNumber},${e.episodeNumber},${e.tmdbEpisodeId},${e.watchedAt},${if (e.isSkipped) 1 else 0}\n")
            }
            epWriter.flush()
            zip.closeEntry()

            // 3. Watchlist CSV
            zip.putNextEntry(ZipEntry("sequel_watchlist.csv"))
            val wlWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            wlWriter.write("tmdb_id,media_type,title,added_at\n")
            watchlistDao.getAllWatchlist().forEach { w ->
                currentCoroutineContext().ensureActive()
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
                currentCoroutineContext().ensureActive()
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
     * Safely exports CSV ZIP to a file using temporary file staging.
     */
    suspend fun exportCsvZipToFile(targetFile: File): File = withContext(Dispatchers.IO) {
        val parentDir = targetFile.parentFile ?: File(".")
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        val tempFile = File(parentDir, "${targetFile.name}.${System.currentTimeMillis()}.tmp")
        try {
            val zipBytes = exportCsvZip()
            tempFile.outputStream().use { stream ->
                stream.write(zipBytes)
                stream.flush()
            }
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            targetFile
        } catch (e: Exception) {
            if (tempFile.exists()) {
                tempFile.delete()
            }
            throw e
        }
    }

    /**
     * Restores user data from a versioned Sequel JSON backup into Room within a clean transaction.
     * Validates schema version and entity contracts before modifying the database.
     * Supports [RestoreMode.REPLACE_CLEAN] (full replace) and [RestoreMode.MERGE_EXISTING] (additive merge).
     * Automatically rolls back on any transaction failure.
     */
    suspend fun restoreJsonBackup(
        jsonString: String,
        mode: RestoreMode = RestoreMode.MERGE_EXISTING
    ): BackupRestoreResult = withContext(Dispatchers.IO) {
        // 1. Migrate raw JSON to CURRENT_SCHEMA_VERSION if legacy
        val migratedJson = backupMigrator.migrate(jsonString)

        // 2. Validate JSON syntax and structure before executing DB changes
        val validationReport = backupValidator.validateJson(migratedJson)
        if (!validationReport.isValid) {
            throw IllegalArgumentException("Backup validation failed: ${validationReport.errors.joinToString("; ")}")
        }

        val backup = try {
            json.decodeFromString<SequelBackupModel>(migratedJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Sequel backup format: ${e.message}")
        }

        var showsCount = 0
        var moviesCount = 0
        var episodesCount = 0
        var watchlistCount = 0
        var reviewsCount = 0
        var rushmoreCount = 0

        database.withTransaction {
            if (mode == RestoreMode.REPLACE_CLEAN) {
                val db = database.openHelper.writableDatabase
                db.execSQL("DELETE FROM mount_rushmore")
                db.execSQL("DELETE FROM reviews")
                db.execSQL("DELETE FROM watchlist")
                db.execSQL("DELETE FROM watched_episodes")
                db.execSQL("DELETE FROM watched_movies")
                db.execSQL("DELETE FROM shows")
            }

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
