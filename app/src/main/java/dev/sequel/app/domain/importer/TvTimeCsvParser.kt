package dev.sequel.app.domain.importer

import android.content.Context
import android.net.Uri
import com.opencsv.CSVReader
import dev.sequel.app.domain.importer.archive.ZipArchiveExtractor
import dev.sequel.app.domain.importer.util.ImportDateParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * High-reliability parser for TV Time data exports.
 * Supports:
 * - Official TV Time GDPR ZIP archives (containing tracking-prod-records.csv, seen_episode.csv, followed_tv_show.csv)
 * - Standalone tracking-prod-records(-v2).csv
 * - Standalone seen_episode.csv / followed_tv_show.csv
 * - Community/legacy TV Time export CSVs
 */
class TvTimeCsvParser : MediaDataParser {

    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    )

    override suspend fun parse(context: Context, uri: Uri): List<ImportedMediaItem> = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open file at $uri")
        inputStream.use { stream ->
            parseStream(stream)
        }
    }

    override suspend fun parseStream(inputStream: InputStream): List<ImportedMediaItem> = withContext(Dispatchers.IO) {
        val buffered = if (inputStream.markSupported()) inputStream else inputStream.buffered()
        if (ZipArchiveExtractor.isZipStream(buffered)) {
            parseZipStream(buffered)
        } else {
            parseSingleCsv(buffered)
        }
    }

    private fun parseZipStream(inputStream: InputStream): List<ImportedMediaItem> {
        val results = mutableListOf<ImportedMediaItem>()

        ZipArchiveExtractor.forEachEntry(inputStream) { entryName, data ->
            val lower = entryName.lowercase(Locale.ROOT)
            val subStream = ByteArrayInputStream(data)
            try {
                when {
                    lower.contains("tracking-prod-records") -> {
                        results.addAll(parseTrackingProdRecords(subStream))
                    }
                    lower.contains("seen_episode") -> {
                        results.addAll(parseSeenEpisodes(subStream))
                    }
                    lower.contains("followed_tv_show") -> {
                        results.addAll(parseFollowedTvShows(subStream))
                    }
                    lower.endsWith(".csv") -> {
                        // Fallback generic CSV
                        results.addAll(parseSingleCsv(subStream))
                    }
                }
            } catch (e: Exception) {
                // Continue parsing other entries if one fails
            }
        }

        if (results.isEmpty()) {
            throw IllegalArgumentException("No valid TV Time records found inside ZIP archive")
        }

        return deduplicateItems(results)
    }

    fun parseSingleCsv(inputStream: InputStream): List<ImportedMediaItem> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val csvReader = CSVReader(reader)
        val header = csvReader.readNext() ?: throw IllegalArgumentException("Empty file")

        val headerMap = header.mapIndexed { idx, col -> col.trim().lowercase(Locale.ROOT) to idx }.toMap()

        // Check if this is tracking-prod-records
        if (headerMap.containsKey("entity_type") || headerMap.containsKey("alpha_range_key")) {
            return parseTrackingProdRecordsWithHeader(csvReader, headerMap)
        }

        // Check if this is followed_tv_show
        if (!headerMap.containsKey("episode_season_number") && !headerMap.containsKey("season_number") &&
            !headerMap.containsKey("episode_number") && (headerMap.containsKey("tv_show_name") || headerMap.containsKey("show_name"))
        ) {
            return parseFollowedTvShowsWithHeader(csvReader, headerMap)
        }

        // Standard seen_episode / general TV Time CSV
        val titleIdx = headerMap["tv_show_name"]
            ?: headerMap["movie_name"]
            ?: headerMap["title"]
            ?: headerMap["name"]
            ?: headerMap["show_name"]
            ?: headerMap.entries.firstOrNull { it.key.contains("name") }?.value
            ?: -1

        if (titleIdx == -1) {
            throw IllegalArgumentException("Invalid TV Time CSV: Missing Title/Name column")
        }

        val seasonIdx = headerMap["episode_season_number"]
            ?: headerMap["season_number"]
            ?: headerMap["season"]
            ?: headerMap.entries.firstOrNull { it.key.contains("season") }?.value
            ?: -1

        val episodeIdx = headerMap["episode_number"]
            ?: headerMap["episode"]
            ?: headerMap.entries.firstOrNull { it.key.contains("episode") && !it.key.contains("season") }?.value
            ?: -1

        val dateIdx = headerMap["created_at"]
            ?: headerMap["updated_at"]
            ?: headerMap["watched_at"]
            ?: headerMap["date"]
            ?: -1

        val ratingIdx = headerMap["rating"] ?: -1

        val rows = mutableListOf<ImportedMediaItem>()
        var line: Array<String>? = csvReader.readNext()

        while (line != null) {
            val title = line.getOrNull(titleIdx)?.trim() ?: ""
            if (title.isNotEmpty()) {
                val seasonNumber = if (seasonIdx != -1) line.getOrNull(seasonIdx)?.trim()?.toIntOrNull() else null
                val episodeNumber = if (episodeIdx != -1) line.getOrNull(episodeIdx)?.trim()?.toIntOrNull() else null
                val dateStr = if (dateIdx != -1) line.getOrNull(dateIdx)?.trim() else null

                val mediaType = if (seasonNumber != null || episodeNumber != null) "tv" else "movie"
                val dateResult = ImportDateParser.parse(dateStr)

                val rawRating = if (ratingIdx != -1) line.getOrNull(ratingIdx)?.trim()?.toFloatOrNull() else null
                val rating = rawRating?.let {
                    // Normalize rating: If TV Time rating is 1..5, scale to 10; if 1..10, keep
                    if (it <= 5.0f && it > 0f) it * 2.0f else it
                }?.coerceIn(0.5f, 10.0f)
                val originalScale = if (rawRating != null && rawRating <= 5.0f && rawRating > 0f) 5.0f else 10.0f

                rows.add(
                    ImportedMediaItem(
                        title = title,
                        year = null,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        watchedAt = dateResult.epochMillis,
                        watchDate = dateResult.dateOnlyString,
                        rawDateString = dateStr,
                        isDateOnly = dateResult.isDateOnly,
                        mediaType = mediaType,
                        recordType = ImportRecordType.WATCHED,
                        rating = rating,
                        originalRating = rawRating,
                        originalRatingScale = originalScale,
                        isExplicitShowLevel = mediaType == "tv" && seasonNumber == null && episodeNumber == null
                    )
                )
            }
            line = csvReader.readNext()
        }

        if (rows.isEmpty()) {
            throw IllegalArgumentException("No valid rows found in TV Time CSV")
        }

        return rows
    }

    private fun parseTrackingProdRecords(inputStream: InputStream): List<ImportedMediaItem> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val csvReader = CSVReader(reader)
        val header = csvReader.readNext() ?: return emptyList()
        val headerMap = header.mapIndexed { idx, col -> col.trim().lowercase(Locale.ROOT) to idx }.toMap()
        return parseTrackingProdRecordsWithHeader(csvReader, headerMap)
    }

    private fun parseTrackingProdRecordsWithHeader(
        csvReader: CSVReader,
        headerMap: Map<String, Int>
    ): List<ImportedMediaItem> {
        val entityTypeIdx = headerMap["entity_type"] ?: -1
        val titleIdx = headerMap["movie_name"]
            ?: headerMap["tv_show_name"]
            ?: headerMap["title"]
            ?: headerMap["name"]
            ?: headerMap["alpha_range_key"]
            ?: -1
        val seasonIdx = headerMap["episode_season_number"] ?: headerMap["season_number"] ?: headerMap["season"] ?: -1
        val episodeIdx = headerMap["episode_number"] ?: headerMap["episode"] ?: -1
        val dateIdx = headerMap["created_at"] ?: headerMap["updated_at"] ?: headerMap["watched_at"] ?: -1
        val typeActionIdx = headerMap["type"] ?: headerMap["action"] ?: -1
        val ratingIdx = headerMap["rating"] ?: -1

        val rows = mutableListOf<ImportedMediaItem>()
        var line: Array<String>? = csvReader.readNext()

        while (line != null) {
            val rawTitle = if (titleIdx != -1) line.getOrNull(titleIdx)?.trim() else null
            val entityType = if (entityTypeIdx != -1) line.getOrNull(entityTypeIdx)?.trim()?.lowercase(Locale.ROOT) else null
            val action = if (typeActionIdx != -1) line.getOrNull(typeActionIdx)?.trim()?.lowercase(Locale.ROOT) else "watch"

            if (!rawTitle.isNullOrEmpty()) {
                val isWatchlist = action == "towatch" || action == "follow"
                val seasonNumber = if (seasonIdx != -1) line.getOrNull(seasonIdx)?.trim()?.toIntOrNull() else null
                val episodeNumber = if (episodeIdx != -1) line.getOrNull(episodeIdx)?.trim()?.toIntOrNull() else null
                val dateStr = if (dateIdx != -1) line.getOrNull(dateIdx)?.trim() else null

                val mediaType = when {
                    entityType == "movie" -> "movie"
                    entityType == "show" || entityType == "series" || entityType == "episode" -> "tv"
                    seasonNumber != null || episodeNumber != null -> "tv"
                    else -> "movie"
                }

                val dateResult = ImportDateParser.parse(dateStr)
                val rawRating = if (ratingIdx != -1) line.getOrNull(ratingIdx)?.trim()?.toFloatOrNull() else null
                val rating = rawRating?.let { if (it <= 5.0f && it > 0f) it * 2.0f else it }?.coerceIn(0.5f, 10.0f)
                val originalScale = if (rawRating != null && rawRating <= 5.0f && rawRating > 0f) 5.0f else 10.0f
                val isExplicitShowLevel = entityType == "show" || entityType == "series" || (mediaType == "tv" && seasonNumber == null && episodeNumber == null)

                rows.add(
                    ImportedMediaItem(
                        title = rawTitle,
                        year = null,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        watchedAt = dateResult.epochMillis,
                        watchDate = dateResult.dateOnlyString,
                        rawDateString = dateStr,
                        isDateOnly = dateResult.isDateOnly,
                        mediaType = mediaType,
                        recordType = if (isWatchlist) ImportRecordType.WATCHLIST else ImportRecordType.WATCHED,
                        rating = rating,
                        originalRating = rawRating,
                        originalRatingScale = originalScale,
                        isExplicitShowLevel = isExplicitShowLevel
                    )
                )
            }
            line = csvReader.readNext()
        }
        return rows
    }

    private fun parseSeenEpisodes(inputStream: InputStream): List<ImportedMediaItem> =
        parseSingleCsv(inputStream)

    private fun parseFollowedTvShows(inputStream: InputStream): List<ImportedMediaItem> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val csvReader = CSVReader(reader)
        val header = csvReader.readNext() ?: return emptyList()
        val headerMap = header.mapIndexed { idx, col -> col.trim().lowercase(Locale.ROOT) to idx }.toMap()
        return parseFollowedTvShowsWithHeader(csvReader, headerMap)
    }

    private fun parseFollowedTvShowsWithHeader(
        csvReader: CSVReader,
        headerMap: Map<String, Int>
    ): List<ImportedMediaItem> {
        val titleIdx = headerMap["tv_show_name"]
            ?: headerMap["show_name"]
            ?: headerMap["name"]
            ?: headerMap["title"]
            ?: 0
        val dateIdx = headerMap["created_at"] ?: headerMap["updated_at"] ?: -1

        val rows = mutableListOf<ImportedMediaItem>()
        var line: Array<String>? = csvReader.readNext()
        while (line != null) {
            val title = line.getOrNull(titleIdx)?.trim() ?: ""
            if (title.isNotEmpty()) {
                val dateStr = if (dateIdx != -1) line.getOrNull(dateIdx)?.trim() else null
                val dateResult = ImportDateParser.parse(dateStr)
                val addedAt = dateResult.epochMillis ?: System.currentTimeMillis()

                rows.add(
                    ImportedMediaItem(
                        title = title,
                        year = null,
                        seasonNumber = null,
                        episodeNumber = null,
                        watchedAt = addedAt,
                        watchDate = dateResult.dateOnlyString,
                        rawDateString = dateStr,
                        isDateOnly = dateResult.isDateOnly,
                        mediaType = "tv",
                        recordType = ImportRecordType.WATCHLIST,
                        isExplicitShowLevel = true
                    )
                )
            }
            line = csvReader.readNext()
        }
        return rows
    }

    private fun parseTimestamp(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        val cleaned = str.trim()
        for (format in dateFormats) {
            try {
                val d = format.parse(cleaned)
                if (d != null) return d.time
            } catch (e: Exception) {
                // try next format
            }
        }
        return null
    }

    private fun deduplicateItems(items: List<ImportedMediaItem>): List<ImportedMediaItem> {
        val unique = mutableListOf<ImportedMediaItem>()
        val seen = mutableSetOf<String>()
        for (item in items) {
            val key = "${item.recordType}_${item.mediaType}_${item.title}_${item.seasonNumber}_${item.episodeNumber}_${item.watchedAt}"
            if (seen.add(key)) {
                unique.add(item)
            }
        }
        return unique
    }
}
