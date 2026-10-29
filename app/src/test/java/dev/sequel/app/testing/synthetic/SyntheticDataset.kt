package dev.sequel.app.testing.synthetic

import dev.sequel.app.domain.backup.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Container holding a deterministically generated synthetic dataset.
 * Provides serialization methods for all supported import source formats
 * (TV Time, Letterboxd, Trakt, Sequel Backup) and corrupted test fixtures.
 */
data class SyntheticDataset(
    val seed: Long,
    val config: SyntheticDatasetConfig,
    val movies: List<SyntheticMovie>,
    val shows: List<SyntheticShow>,
    val watchEvents: List<SyntheticWatchEvent>,
    val reviews: List<SyntheticReview>,
    val watchlist: List<SyntheticWatchlistItem>,
    val injectedFaults: List<InjectedFaultRecord>,
    val report: SyntheticGenerationReport
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val standardDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ─────────────────────────────────────────────────────────────────
    // 1. TV TIME ADAPTER FIXTURES
    // ─────────────────────────────────────────────────────────────────

    /**
     * Serializes dataset into official TV Time GDPR package ZIP:
     * - `tracking-prod-records.csv`
     * - `seen_episode.csv`
     * - `followed_tv_show.csv`
     */
    fun toTvTimeGdprZip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // 1. tracking-prod-records.csv
            zip.putNextEntry(ZipEntry("tracking-prod-records.csv"))
            val trackingWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            val trackingHeaders = if (config.faults.extraCsvColumns) {
                "entity_type,movie_name,tv_show_name,episode_season_number,episode_number,type,created_at,rating,custom_extra_col\n"
            } else {
                "entity_type,movie_name,tv_show_name,episode_season_number,episode_number,type,created_at,rating\n"
            }
            trackingWriter.write(trackingHeaders)

            // Emit watch events
            watchEvents.forEach { ev ->
                val entityType = if (ev.mediaType == "movie") "movie" else "episode"
                val movieName = if (ev.mediaType == "movie") escapeCsv(ev.title) else ""
                val showName = if (ev.mediaType == "tv") escapeCsv(ev.title) else ""
                val sNum = ev.seasonNumber?.toString() ?: ""
                val eNum = ev.episodeNumber?.toString() ?: ""
                val dateStr = standardDateFormat.format(ev.watchedAt)
                val extra = if (config.faults.extraCsvColumns) ",extra_data" else ""

                val line = "$entityType,$movieName,$showName,$sNum,$eNum,watch,$dateStr,$extra\n"
                trackingWriter.write(line)

                // Inject duplicate rows if configured
                if (config.faults.duplicateRowRate > 0.0 && (ev.showOrMovieId % 10 == 0)) {
                    trackingWriter.write(line)
                }
            }

            // Emit watchlist follows
            watchlist.forEach { wl ->
                val entityType = if (wl.mediaType == "movie") "movie" else "show"
                val movieName = if (wl.mediaType == "movie") escapeCsv(wl.title) else ""
                val showName = if (wl.mediaType == "tv") escapeCsv(wl.title) else ""
                val dateStr = standardDateFormat.format(wl.addedAt)
                val extra = if (config.faults.extraCsvColumns) ",extra_data" else ""
                trackingWriter.write("$entityType,$movieName,$showName,,,follow,$dateStr,$extra\n")
            }
            trackingWriter.flush()
            zip.closeEntry()

            // 2. seen_episode.csv
            zip.putNextEntry(ZipEntry("seen_episode.csv"))
            val seenWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            seenWriter.write("tv_show_name,season_number,episode_number,updated_at\n")
            watchEvents.filter { it.mediaType == "tv" && it.seasonNumber != null && it.episodeNumber != null }.forEach { ep ->
                val dateStr = standardDateFormat.format(ep.watchedAt)
                seenWriter.write("${escapeCsv(ep.title)},${ep.seasonNumber},${ep.episodeNumber},$dateStr\n")
            }
            seenWriter.flush()
            zip.closeEntry()

            // 3. followed_tv_show.csv
            zip.putNextEntry(ZipEntry("followed_tv_show.csv"))
            val followWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            followWriter.write("tv_show_name,created_at\n")
            watchlist.filter { it.mediaType == "tv" }.forEach { wl ->
                val dateStr = standardDateFormat.format(wl.addedAt)
                followWriter.write("${escapeCsv(wl.title)},$dateStr\n")
            }
            followWriter.flush()
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /**
     * Serializes dataset into a flat TV Time CSV:
     * `tv_show_name,episode_season_number,episode_number,created_at`
     */
    fun toTvTimeFlatCsv(): String = buildString {
        if (config.faults.reorderedCsvColumns) {
            appendLine("episode_number,episode_season_number,created_at,tv_show_name")
            watchEvents.filter { it.mediaType == "tv" }.forEach { ev ->
                appendLine("${ev.episodeNumber ?: ""},${ev.seasonNumber ?: ""},${standardDateFormat.format(ev.watchedAt)},${escapeCsv(ev.title)}")
            }
        } else {
            appendLine("tv_show_name,episode_season_number,episode_number,created_at")
            watchEvents.filter { it.mediaType == "tv" }.forEach { ev ->
                appendLine("${escapeCsv(ev.title)},${ev.seasonNumber ?: ""},${ev.episodeNumber ?: ""},${standardDateFormat.format(ev.watchedAt)}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. LETTERBOXD ADAPTER FIXTURES
    // ─────────────────────────────────────────────────────────────────

    /**
     * Serializes dataset into official Letterboxd ZIP account export:
     * - `watched.csv`
     * - `diary.csv`
     * - `ratings.csv`
     * - `reviews.csv`
     * - `watchlist.csv`
     */
    fun toLetterboxdZip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // 1. watched.csv
            zip.putNextEntry(ZipEntry("watched.csv"))
            val wWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            wWriter.write("Date,Name,Year,Letterboxd URI\n")
            watchEvents.filter { it.mediaType == "movie" && !it.isRewatch }.forEach { ev ->
                val yearStr = ev.year?.toString() ?: ""
                wWriter.write("${ev.watchDate},${escapeCsv(ev.title)},$yearStr,https://boxd.it/${ev.showOrMovieId}\n")
            }
            wWriter.flush()
            zip.closeEntry()

            // 2. diary.csv
            zip.putNextEntry(ZipEntry("diary.csv"))
            val dWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            dWriter.write("Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date\n")
            watchEvents.filter { it.mediaType == "movie" }.forEach { ev ->
                val yearStr = ev.year?.toString() ?: ""
                val rewatchStr = if (ev.isRewatch) "Yes" else ""
                val rating = reviews.firstOrNull { it.mediaId == ev.showOrMovieId && it.mediaType == "movie" }?.rating?.let { (it / 2.0f).coerceIn(0.5f, 5.0f) } ?: ""
                dWriter.write("${ev.watchDate},${escapeCsv(ev.title)},$yearStr,https://boxd.it/${ev.showOrMovieId},$rating,$rewatchStr,,${ev.watchDate}\n")
            }
            dWriter.flush()
            zip.closeEntry()

            // 3. ratings.csv
            zip.putNextEntry(ZipEntry("ratings.csv"))
            val rWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            rWriter.write("Date,Name,Year,Letterboxd URI,Rating\n")
            reviews.filter { it.mediaType == "movie" && it.rating != null }.forEach { r ->
                val movie = movies.firstOrNull { it.id == r.mediaId }
                val yearStr = movie?.year?.toString() ?: ""
                val dateStr = standardDateFormat.format(r.updatedAt).take(10)
                val letterboxdRating = (r.rating!! / 2.0f).coerceIn(0.5f, 5.0f)
                rWriter.write("$dateStr,${escapeCsv(r.title)},$yearStr,https://boxd.it/${r.mediaId},$letterboxdRating\n")
            }
            rWriter.flush()
            zip.closeEntry()

            // 4. reviews.csv
            zip.putNextEntry(ZipEntry("reviews.csv"))
            val revWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            revWriter.write("Date,Name,Year,Letterboxd URI,Rating,Review,Spoiler\n")
            reviews.filter { it.mediaType == "movie" && !it.reviewText.isNullOrBlank() }.forEach { rev ->
                val movie = movies.firstOrNull { it.id == rev.mediaId }
                val yearStr = movie?.year?.toString() ?: ""
                val dateStr = standardDateFormat.format(rev.updatedAt).take(10)
                val ratingStr = rev.rating?.let { (it / 2.0f).coerceIn(0.5f, 5.0f) } ?: ""
                val spoilerStr = if (rev.isSpoiler) "True" else "False"
                revWriter.write("$dateStr,${escapeCsv(rev.title)},$yearStr,https://boxd.it/${rev.mediaId},$ratingStr,${escapeCsv(rev.reviewText ?: "")},$spoilerStr\n")
            }
            revWriter.flush()
            zip.closeEntry()

            // 5. watchlist.csv
            zip.putNextEntry(ZipEntry("watchlist.csv"))
            val wlWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            wlWriter.write("Date,Name,Year,Letterboxd URI\n")
            watchlist.filter { it.mediaType == "movie" }.forEach { wl ->
                val yearStr = wl.year?.toString() ?: ""
                val dateStr = standardDateFormat.format(wl.addedAt).take(10)
                wlWriter.write("$dateStr,${escapeCsv(wl.title)},$yearStr,https://boxd.it/${wl.tmdbId ?: 0}\n")
            }
            wlWriter.flush()
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /**
     * Serializes single Letterboxd CSV file.
     */
    fun toLetterboxdSingleCsv(name: String = "watched.csv"): String = buildString {
        when (name.lowercase()) {
            "ratings.csv" -> {
                appendLine("Date,Name,Year,Letterboxd URI,Rating")
                reviews.filter { it.mediaType == "movie" && it.rating != null }.forEach { r ->
                    val movie = movies.firstOrNull { it.id == r.mediaId }
                    val yearStr = movie?.year?.toString() ?: ""
                    val dateStr = standardDateFormat.format(r.updatedAt).take(10)
                    val letterboxdRating = (r.rating!! / 2.0f).coerceIn(0.5f, 5.0f)
                    appendLine("$dateStr,${escapeCsv(r.title)},$yearStr,https://boxd.it/${r.mediaId},$letterboxdRating")
                }
            }
            "diary.csv" -> {
                appendLine("Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date")
                watchEvents.filter { it.mediaType == "movie" }.forEach { ev ->
                    val yearStr = ev.year?.toString() ?: ""
                    val rewatchStr = if (ev.isRewatch) "Yes" else ""
                    val rating = reviews.firstOrNull { it.mediaId == ev.showOrMovieId }?.rating?.let { (it / 2.0f).coerceIn(0.5f, 5.0f) } ?: ""
                    appendLine("${ev.watchDate},${escapeCsv(ev.title)},$yearStr,https://boxd.it/${ev.showOrMovieId},$rating,$rewatchStr,,${ev.watchDate}")
                }
            }
            else -> {
                appendLine("Date,Name,Year,Letterboxd URI")
                watchEvents.filter { it.mediaType == "movie" && !it.isRewatch }.forEach { ev ->
                    val yearStr = ev.year?.toString() ?: ""
                    appendLine("${ev.watchDate},${escapeCsv(ev.title)},$yearStr,https://boxd.it/${ev.showOrMovieId}")
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. TRAKT ADAPTER FIXTURES
    // ─────────────────────────────────────────────────────────────────

    /**
     * Serializes dataset into standard Trakt history CSV:
     * `Title,Year,Season,Episode,Type,WatchedAt`
     */
    fun toTraktCsv(): String = buildString {
        if (config.faults.reorderedCsvColumns) {
            appendLine("WatchedAt,Type,Season,Episode,Year,Title")
            watchEvents.forEach { ev ->
                val dateStr = isoFormat.format(ev.watchedAt)
                val typeStr = if (ev.mediaType == "movie") "movie" else "episode"
                val sNum = ev.seasonNumber?.toString() ?: ""
                val eNum = ev.episodeNumber?.toString() ?: ""
                val yearStr = ev.year?.toString() ?: ""
                appendLine("$dateStr,$typeStr,$sNum,$eNum,$yearStr,${escapeCsv(ev.title)}")
            }
        } else {
            appendLine("Title,Year,Season,Episode,Type,WatchedAt")
            watchEvents.forEach { ev ->
                val dateStr = isoFormat.format(ev.watchedAt)
                val typeStr = if (ev.mediaType == "movie") "movie" else "episode"
                val sNum = ev.seasonNumber?.toString() ?: ""
                val eNum = ev.episodeNumber?.toString() ?: ""
                val yearStr = ev.year?.toString() ?: ""
                appendLine("${escapeCsv(ev.title)},$yearStr,$sNum,$eNum,$typeStr,$dateStr")
            }
        }
    }

    /**
     * Serializes dataset into official Trakt JSON export archive:
     * - `watched_movies.json`
     * - `watched_shows.json`
     * - `ratings.json`
     * - `watchlist.json`
     */
    fun toTraktExportZip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // 1. watched_movies.json
            zip.putNextEntry(ZipEntry("watched_movies.json"))
            val movieJsonList = watchEvents.filter { it.mediaType == "movie" }.map { ev ->
                """
                {
                    "movie": {
                        "title": "${escapeJson(ev.title)}",
                        "year": ${ev.year ?: "null"},
                        "ids": { "tmdb": ${ev.tmdbId ?: "null"}, "imdb": ${if (ev.imdbId != null) "\"${ev.imdbId}\"" else "null"} }
                    },
                    "last_watched_at": "${isoFormat.format(ev.watchedAt)}"
                }
                """.trimIndent()
            }
            val movieWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            movieWriter.write("[\n" + movieJsonList.joinToString(",\n") + "\n]")
            movieWriter.flush()
            zip.closeEntry()

            // 2. watched_shows.json
            zip.putNextEntry(ZipEntry("watched_shows.json"))
            val showJsonList = shows.map { s ->
                val watchedEpsForShow = watchEvents.filter { it.mediaType == "tv" && it.showOrMovieId == s.id }
                val seasonsGrouped = watchedEpsForShow.groupBy { it.seasonNumber ?: 1 }
                val seasonsJson = seasonsGrouped.map { (sNum, eps) ->
                    val epJson = eps.map { ep ->
                        """{ "number": ${ep.episodeNumber ?: 1}, "last_watched_at": "${isoFormat.format(ep.watchedAt)}" }"""
                    }.joinToString(", ")
                    """{ "number": $sNum, "episodes": [ $epJson ] }"""
                }.joinToString(", ")

                """
                {
                    "show": {
                        "title": "${escapeJson(s.title)}",
                        "year": ${s.year ?: "null"},
                        "ids": { "tmdb": ${s.tmdbId ?: "null"}, "imdb": ${if (s.imdbId != null) "\"${s.imdbId}\"" else "null"} }
                    },
                    "seasons": [ $seasonsJson ]
                }
                """.trimIndent()
            }
            val showWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            showWriter.write("[\n" + showJsonList.joinToString(",\n") + "\n]")
            showWriter.flush()
            zip.closeEntry()

            // 3. ratings.json
            zip.putNextEntry(ZipEntry("ratings.json"))
            val ratingJsonList = reviews.filter { it.rating != null }.map { r ->
                val typeKey = if (r.mediaType == "movie") "movie" else "show"
                """
                {
                    "rating": ${r.rating?.toInt() ?: 8},
                    "rated_at": "${isoFormat.format(r.updatedAt)}",
                    "$typeKey": {
                        "title": "${escapeJson(r.title)}",
                        "ids": { "tmdb": ${r.mediaId * 10} }
                    }
                }
                """.trimIndent()
            }
            val ratingWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            ratingWriter.write("[\n" + ratingJsonList.joinToString(",\n") + "\n]")
            ratingWriter.flush()
            zip.closeEntry()

            // 4. watchlist.json
            zip.putNextEntry(ZipEntry("watchlist.json"))
            val wlJsonList = watchlist.map { wl ->
                val typeKey = if (wl.mediaType == "movie") "movie" else "show"
                """
                {
                    "listed_at": "${isoFormat.format(wl.addedAt)}",
                    "$typeKey": {
                        "title": "${escapeJson(wl.title)}",
                        "year": ${wl.year ?: "null"},
                        "ids": { "tmdb": ${wl.tmdbId ?: "null"} }
                    }
                }
                """.trimIndent()
            }
            val wlWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            wlWriter.write("[\n" + wlJsonList.joinToString(",\n") + "\n]")
            wlWriter.flush()
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. SEQUEL BACKUP NATIVE FIXTURES
    // ─────────────────────────────────────────────────────────────────

    /**
     * Serializes dataset into a full-fidelity Sequel JSON backup (`schemaVersion = 1`).
     */
    fun toSequelBackupJson(): String {
        val showDtos = shows.map { s ->
            ShowBackupDto(
                id = s.id,
                title = s.title,
                mediaType = "tv",
                firstAirDate = "${s.year ?: 2020}-01-01",
                numberOfSeasons = s.seasons.size,
                numberOfEpisodes = s.seasons.sumOf { it.episodes.size }
            )
        } + movies.map { m ->
            ShowBackupDto(
                id = m.id,
                title = m.title,
                mediaType = "movie",
                firstAirDate = "${m.year ?: 2020}-01-01",
                runtime = m.runtime
            )
        }

        val watchedMovieDtos = watchEvents.filter { it.mediaType == "movie" }.map { ev ->
            WatchedMovieBackupDto(
                tmdbMovieId = ev.showOrMovieId,
                title = ev.title,
                watchedAt = ev.watchedAt,
                watchDate = ev.watchDate,
                watchCount = if (ev.isRewatch) 2 else 1
            )
        }

        val watchedEpisodeDtos = watchEvents.filter { it.mediaType == "tv" && it.seasonNumber != null && it.episodeNumber != null }.map { ev ->
            WatchedEpisodeBackupDto(
                showId = ev.showOrMovieId,
                tmdbEpisodeId = ev.showOrMovieId * 100 + (ev.seasonNumber ?: 1) * 10 + (ev.episodeNumber ?: 1),
                seasonNumber = ev.seasonNumber ?: 1,
                episodeNumber = ev.episodeNumber ?: 1,
                watchedAt = ev.watchedAt,
                watchDate = ev.watchDate
            )
        }

        val watchlistDtos = watchlist.map { wl ->
            WatchlistBackupDto(
                tmdbId = wl.tmdbId ?: 9999,
                mediaType = wl.mediaType,
                title = wl.title,
                addedAt = wl.addedAt
            )
        }

        val reviewDtos = reviews.map { r ->
            ReviewBackupDto(
                mediaId = r.mediaId,
                mediaType = r.mediaType,
                seasonNum = r.seasonNumber,
                episodeNum = r.episodeNumber,
                reviewText = r.reviewText,
                rating = r.rating,
                isSpoiler = r.isSpoiler,
                isRatingOnly = r.reviewText.isNullOrBlank(),
                updatedAt = r.updatedAt,
                originalRating = r.rating,
                originalRatingScale = r.originalRatingScale
            )
        }

        val backup = SequelBackupModel(
            schemaVersion = SequelBackupModel.CURRENT_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = "1.0.0",
            shows = showDtos,
            watchedMovies = watchedMovieDtos,
            watchedEpisodes = watchedEpisodeDtos,
            watchlist = watchlistDtos,
            reviews = reviewDtos,
            mountRushmore = emptyList()
        )

        return json.encodeToString(backup)
    }

    // ─────────────────────────────────────────────────────────────────
    // 5. INTENTIONALLY CORRUPTED FIXTURES (FAULT INJECTION)
    // ─────────────────────────────────────────────────────────────────

    /**
     * Produces explicitly corrupted ZIP archives for testing resilience.
     */
    fun toCorruptedZip(type: CorruptedZipType): ByteArray {
        return when (type) {
            CorruptedZipType.TRUNCATED_BYTES -> {
                val validZip = toLetterboxdZip()
                validZip.copyOfRange(0, validZip.size / 2) // Cut in half
            }
            CorruptedZipType.INVALID_HEADER -> {
                byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x50, 0x4B, 0x00, 0x00)
            }
            CorruptedZipType.EMPTY_STREAM -> {
                ByteArray(0)
            }
            CorruptedZipType.NESTED_ARCHIVE -> {
                val innerZip = toLetterboxdZip()
                val out = ByteArrayOutputStream()
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("inner_archive.zip"))
                    zip.write(innerZip)
                    zip.closeEntry()
                }
                out.toByteArray()
            }
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
