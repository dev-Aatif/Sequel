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
 * Parser for Letterboxd exports.
 * Supports:
 * - Full account export ZIP archives containing watched.csv, ratings.csv, reviews.csv, diary.csv, watchlist.csv
 * - Standalone individual CSV files matching Letterboxd schemas
 */
class LetterboxdExportParser : MediaDataParser {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

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
            when {
                lower.endsWith("reviews.csv") -> {
                    results.addAll(parseReviewsCsv(subStream))
                }
                lower.endsWith("ratings.csv") -> {
                    results.addAll(parseRatingsCsv(subStream))
                }
                lower.endsWith("diary.csv") -> {
                    results.addAll(parseDiaryCsv(subStream))
                }
                lower.endsWith("watchlist.csv") -> {
                    results.addAll(parseWatchlistCsv(subStream))
                }
                lower.endsWith("watched.csv") -> {
                    results.addAll(parseWatchedCsv(subStream))
                }
            }
        }

        if (results.isEmpty()) {
            throw IllegalArgumentException("No recognized Letterboxd CSV files found inside ZIP archive")
        }

        return deduplicateAndMerge(results)
    }

    fun parseSingleCsv(inputStream: InputStream, filenameHint: String? = null): List<ImportedMediaItem> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val csvReader = CSVReader(reader)
        val header = csvReader.readNext() ?: throw IllegalArgumentException("Empty CSV file")

        val headerMap = header.mapIndexed { idx, col -> col.trim().lowercase(Locale.ROOT) to idx }.toMap()

        val hasName = headerMap.containsKey("name") || headerMap.containsKey("title")
        if (!hasName) {
            throw IllegalArgumentException("Invalid Letterboxd CSV: Missing 'Name' column in header")
        }

        val hasReview = headerMap.containsKey("review")
        val hasRating = headerMap.containsKey("rating")
        val hasRewatch = headerMap.containsKey("rewatch") || headerMap.containsKey("watched date")
        val isWatchlist = filenameHint?.lowercase(Locale.ROOT)?.contains("watchlist") == true

        val items = mutableListOf<ImportedMediaItem>()
        var row = csvReader.readNext()

        val nameIdx = headerMap["name"] ?: headerMap["title"] ?: -1
        val yearIdx = headerMap["year"] ?: -1
        val dateIdx = headerMap["date"] ?: -1
        val watchedDateIdx = headerMap["watched date"] ?: -1
        val ratingIdx = headerMap["rating"] ?: -1
        val reviewIdx = headerMap["review"] ?: -1
        val spoilerIdx = headerMap["spoiler"] ?: -1
        val rewatchIdx = headerMap["rewatch"] ?: -1
        val tagsIdx = headerMap["tags"] ?: -1
        val uriIdx = headerMap["letterboxd uri"] ?: -1

        while (row != null) {
            val title = row.getOrNull(nameIdx)?.trim() ?: ""
            if (title.isNotEmpty()) {
                val year = if (yearIdx != -1) row.getOrNull(yearIdx)?.trim()?.toIntOrNull() else null
                val uri = if (uriIdx != -1) row.getOrNull(uriIdx)?.trim() else null

                // Determine watch/log date
                val dateStr = if (watchedDateIdx != -1) row.getOrNull(watchedDateIdx)?.trim() else null
                val fallbackDateStr = if (dateIdx != -1) row.getOrNull(dateIdx)?.trim() else null
                val effectiveDateStr = if (!dateStr.isNullOrBlank()) dateStr else fallbackDateStr

                val dateResult = ImportDateParser.parse(effectiveDateStr)

                // Rating conversion: Letterboxd 0.5-5.0 -> Sequel 1.0-10.0
                val rawRating = if (ratingIdx != -1) row.getOrNull(ratingIdx)?.trim()?.toFloatOrNull() else null
                val normalizedRating = rawRating?.let { (it * 2.0f).coerceIn(0.5f, 10.0f) }

                val reviewText = if (reviewIdx != -1) row.getOrNull(reviewIdx)?.trim() else null
                val isSpoiler = if (spoilerIdx != -1) {
                    val s = row.getOrNull(spoilerIdx)?.trim()?.lowercase(Locale.ROOT)
                    s == "true" || s == "yes" || s == "1"
                } else false

                val isRewatch = if (rewatchIdx != -1) {
                    val rw = row.getOrNull(rewatchIdx)?.trim()?.lowercase(Locale.ROOT)
                    rw == "yes" || rw == "true"
                } else false

                val tagsList = if (tagsIdx != -1) {
                    row.getOrNull(tagsIdx)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
                } else emptyList()

                val recordType = when {
                    isWatchlist -> ImportRecordType.WATCHLIST
                    !reviewText.isNullOrBlank() -> ImportRecordType.REVIEW
                    filenameHint?.lowercase(Locale.ROOT)?.contains("watched") == true -> ImportRecordType.WATCHED
                    filenameHint?.lowercase(Locale.ROOT)?.contains("diary") == true -> ImportRecordType.WATCHED
                    filenameHint?.lowercase(Locale.ROOT)?.contains("ratings") == true -> ImportRecordType.RATING
                    normalizedRating != null && !hasRewatch -> ImportRecordType.RATING
                    else -> ImportRecordType.WATCHED
                }

                items.add(
                    ImportedMediaItem(
                        title = title,
                        year = year,
                        seasonNumber = null,
                        episodeNumber = null,
                        watchedAt = dateResult.epochMillis,
                        watchDate = dateResult.dateOnlyString,
                        mediaType = "movie",
                        recordType = recordType,
                        rating = normalizedRating,
                        originalRating = rawRating,
                        originalRatingScale = 5.0f,
                        reviewText = reviewText,
                        isSpoiler = isSpoiler,
                        isRewatch = isRewatch,
                        tags = tagsList,
                        rawDateString = effectiveDateStr,
                        isDateOnly = dateResult.isDateOnly,
                        externalId = uri,
                        externalIdType = if (uri != null) "letterboxd" else null
                    )
                )
            }
            row = csvReader.readNext()
        }

        if (items.isEmpty()) {
            throw IllegalArgumentException("No valid movie rows found in Letterboxd CSV")
        }
        return items
    }

    private fun parseWatchedCsv(inputStream: InputStream): List<ImportedMediaItem> =
        parseSingleCsv(inputStream, "watched.csv")

    private fun parseDiaryCsv(inputStream: InputStream): List<ImportedMediaItem> =
        parseSingleCsv(inputStream, "diary.csv")

    private fun parseRatingsCsv(inputStream: InputStream): List<ImportedMediaItem> =
        parseSingleCsv(inputStream, "ratings.csv")

    private fun parseReviewsCsv(inputStream: InputStream): List<ImportedMediaItem> =
        parseSingleCsv(inputStream, "reviews.csv")

    private fun parseWatchlistCsv(inputStream: InputStream): List<ImportedMediaItem> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val csvReader = CSVReader(reader)
        val header = csvReader.readNext() ?: return emptyList()
        val headerMap = header.mapIndexed { idx, col -> col.trim().lowercase(Locale.ROOT) to idx }.toMap()

        val nameIdx = headerMap["name"] ?: headerMap["title"] ?: return emptyList()
        val yearIdx = headerMap["year"] ?: -1
        val dateIdx = headerMap["date"] ?: -1
        val uriIdx = headerMap["letterboxd uri"] ?: -1

        val list = mutableListOf<ImportedMediaItem>()
        var row = csvReader.readNext()
        while (row != null) {
            val title = row.getOrNull(nameIdx)?.trim() ?: ""
            if (title.isNotEmpty()) {
                val year = if (yearIdx != -1) row.getOrNull(yearIdx)?.trim()?.toIntOrNull() else null
                val uri = if (uriIdx != -1) row.getOrNull(uriIdx)?.trim() else null
                val dateStr = if (dateIdx != -1) row.getOrNull(dateIdx)?.trim() else null
                val dateResult = ImportDateParser.parse(dateStr)
                val addedAt = dateResult.epochMillis ?: System.currentTimeMillis()

                list.add(
                    ImportedMediaItem(
                        title = title,
                        year = year,
                        seasonNumber = null,
                        episodeNumber = null,
                        watchedAt = addedAt,
                        watchDate = dateResult.dateOnlyString,
                        rawDateString = dateStr,
                        isDateOnly = dateResult.isDateOnly,
                        mediaType = "movie",
                        recordType = ImportRecordType.WATCHLIST,
                        externalId = uri,
                        externalIdType = if (uri != null) "letterboxd" else null
                    )
                )
            }
            row = csvReader.readNext()
        }
        return list
    }

    private fun parseDate(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        return try {
            dateFormat.parse(str)?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun deduplicateAndMerge(items: List<ImportedMediaItem>): List<ImportedMediaItem> {
        // Group by title and year to avoid creating orphan duplicates across watched, ratings, and reviews
        // while preserving all record types
        val merged = mutableListOf<ImportedMediaItem>()
        val seen = mutableSetOf<String>()

        for (item in items) {
            val key = "${item.recordType}_${item.title}_${item.year}_${item.watchedAt}_${item.reviewText?.hashCode()}"
            if (seen.add(key)) {
                merged.add(item)
            }
        }
        return merged
    }
}
