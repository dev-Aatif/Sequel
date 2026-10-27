package dev.sequel.app.domain.importer

import android.content.Context
import android.net.Uri
import com.opencsv.CSVReader
import dev.sequel.app.domain.importer.archive.ZipArchiveExtractor
import dev.sequel.app.domain.importer.util.ImportDateParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Parser for Trakt account data exports.
 * Supports:
 * - Trakt standard CSV history / ratings / watchlist exports
 * - Trakt raw JSON exports (watched_movies.json, watched_shows.json, ratings.json, watchlist.json)
 * - Trakt export ZIP packages containing CSV or JSON files
 */
class TraktExportParser : MediaDataParser {

    private val isoFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
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
            // Check if JSON or CSV
            buffered.mark(256)
            val peekBytes = ByteArray(256)
            val readLen = buffered.read(peekBytes)
            buffered.reset()
            val peekStr = if (readLen > 0) String(peekBytes, 0, readLen, Charsets.UTF_8).trim() else ""

            if (peekStr.startsWith("[") || peekStr.startsWith("{")) {
                parseJsonStream(buffered)
            } else {
                parseCsvStream(buffered)
            }
        }
    }

    private fun parseZipStream(inputStream: InputStream): List<ImportedMediaItem> {
        val results = mutableListOf<ImportedMediaItem>()

        ZipArchiveExtractor.forEachEntry(inputStream) { entryName, data ->
            val lower = entryName.lowercase(Locale.ROOT)
            val subStream = ByteArrayInputStream(data)
            try {
                when {
                    lower.endsWith(".json") -> {
                        results.addAll(parseJsonStream(subStream))
                    }
                    lower.endsWith(".csv") -> {
                        results.addAll(parseCsvStream(subStream))
                    }
                }
            } catch (e: Exception) {
                // Continue with other entries
            }
        }

        if (results.isEmpty()) {
            throw IllegalArgumentException("No recognized Trakt records found inside ZIP archive")
        }

        return deduplicateItems(results)
    }

    private fun parseCsvStream(inputStream: InputStream): List<ImportedMediaItem> {
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
        val csvReader = CSVReader(reader)
        val header = csvReader.readNext() ?: throw IllegalArgumentException("Empty Trakt CSV file")

        val headerMap = header.mapIndexed { idx, col -> col.trim().lowercase(Locale.ROOT) to idx }.toMap()

        val titleIdx = headerMap["title"] ?: headerMap["name"] ?: -1
        if (titleIdx == -1) {
            throw IllegalArgumentException("Invalid Trakt CSV: Missing 'Title' column")
        }

        val yearIdx = headerMap["year"] ?: -1
        val seasonIdx = headerMap["season"] ?: -1
        val episodeIdx = headerMap["episode"] ?: -1
        val typeIdx = headerMap["type"] ?: -1
        val dateIdx = headerMap["watched_at"]
            ?: headerMap["watchedat"]
            ?: headerMap["date"]
            ?: headerMap["rated_at"]
            ?: headerMap["listed_at"]
            ?: -1
        val ratingIdx = headerMap["rating"] ?: -1
        val tmdbIdIdx = headerMap["tmdb_id"] ?: headerMap["tmdb"] ?: -1

        val rows = mutableListOf<ImportedMediaItem>()
        var line: Array<String>? = csvReader.readNext()

        while (line != null) {
            val title = line.getOrNull(titleIdx)?.trim() ?: ""
            if (title.isNotEmpty()) {
                val year = if (yearIdx != -1) line.getOrNull(yearIdx)?.trim()?.toIntOrNull() else null
                val seasonNumber = if (seasonIdx != -1) line.getOrNull(seasonIdx)?.trim()?.toIntOrNull() else null
                val episodeNumber = if (episodeIdx != -1) line.getOrNull(episodeIdx)?.trim()?.toIntOrNull() else null
                val rawType = if (typeIdx != -1) line.getOrNull(typeIdx)?.trim()?.lowercase(Locale.ROOT) else null
                val dateStr = if (dateIdx != -1) line.getOrNull(dateIdx)?.trim() else null
                val rawRating = if (ratingIdx != -1) line.getOrNull(ratingIdx)?.trim()?.toFloatOrNull() else null
                val tmdbId = if (tmdbIdIdx != -1) line.getOrNull(tmdbIdIdx)?.trim() else null

                var mediaType = when (rawType) {
                    "movie" -> "movie"
                    "show", "episode", "season" -> "tv"
                    else -> if (seasonNumber != null || episodeNumber != null) "tv" else "movie"
                }

                val dateResult = ImportDateParser.parse(dateStr)
                val rating = rawRating?.coerceIn(0.5f, 10.0f)
                val isExplicitShowLevel = (rawType == "show" || rawType == "season") && seasonNumber == null && episodeNumber == null

                val recordType = when {
                    rating != null && seasonNumber == null && episodeNumber == null -> ImportRecordType.RATING
                    headerMap.containsKey("listed_at") -> ImportRecordType.WATCHLIST
                    else -> ImportRecordType.WATCHED
                }

                rows.add(
                    ImportedMediaItem(
                        title = title,
                        year = year,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        watchedAt = dateResult.epochMillis,
                        watchDate = dateResult.dateOnlyString,
                        mediaType = mediaType,
                        recordType = recordType,
                        rating = rating,
                        originalRating = rawRating,
                        originalRatingScale = 10.0f,
                        isExplicitShowLevel = isExplicitShowLevel,
                        rawDateString = dateStr,
                        isDateOnly = dateResult.isDateOnly,
                        externalId = tmdbId,
                        externalIdType = if (tmdbId != null) "tmdb" else null
                    )
                )
            }
            line = csvReader.readNext()
        }

        if (rows.isEmpty()) {
            throw IllegalArgumentException("No valid rows found in Trakt CSV")
        }

        return rows
    }

    private fun parseJsonStream(inputStream: InputStream): List<ImportedMediaItem> {
        val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
        val results = mutableListOf<ImportedMediaItem>()

        if (content.startsWith("[")) {
            val array = JSONArray(content)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                results.addAll(parseJsonEntry(obj))
            }
        } else if (content.startsWith("{")) {
            val obj = JSONObject(content)
            results.addAll(parseJsonEntry(obj))
        }

        if (results.isEmpty()) {
            throw IllegalArgumentException("No valid records found in Trakt JSON")
        }

        return results
    }

    private fun parseJsonEntry(obj: JSONObject): List<ImportedMediaItem> {
        val items = mutableListOf<ImportedMediaItem>()

        // 1. Check if watched_movies entry
        if (obj.has("movie") && !obj.has("rating") && !obj.has("listed_at")) {
            val movie = obj.getJSONObject("movie")
            val title = movie.optString("title")
            val year = movie.optInt("year").takeIf { it > 0 }
            val ids = movie.optJSONObject("ids")
            val tmdbId = ids?.optString("tmdb")?.takeIf { it.isNotBlank() && it != "0" }
            val rawDate = obj.optString("last_watched_at")
            val dateResult = ImportDateParser.parse(rawDate)

            if (title.isNotBlank()) {
                items.add(
                    ImportedMediaItem(
                        title = title,
                        year = year,
                        seasonNumber = null,
                        episodeNumber = null,
                        watchedAt = dateResult.epochMillis,
                        watchDate = dateResult.dateOnlyString,
                        rawDateString = rawDate,
                        isDateOnly = dateResult.isDateOnly,
                        mediaType = "movie",
                        recordType = ImportRecordType.WATCHED,
                        externalId = tmdbId,
                        externalIdType = if (tmdbId != null) "tmdb" else null
                    )
                )
            }
        }

        // 2. Check if watched_shows entry
        if (obj.has("show") && !obj.has("rating") && !obj.has("listed_at")) {
            val show = obj.getJSONObject("show")
            val title = show.optString("title")
            val year = show.optInt("year").takeIf { it > 0 }
            val ids = show.optJSONObject("ids")
            val tmdbId = ids?.optString("tmdb")?.takeIf { it.isNotBlank() && it != "0" }
            val seasons = obj.optJSONArray("seasons")

            if (title.isNotBlank()) {
                if (seasons != null && seasons.length() > 0) {
                    for (sIdx in 0 until seasons.length()) {
                        val seasonObj = seasons.getJSONObject(sIdx)
                        val seasonNumber = seasonObj.optInt("number")
                        val episodes = seasonObj.optJSONArray("episodes")
                        if (episodes != null) {
                            for (eIdx in 0 until episodes.length()) {
                                val epObj = episodes.getJSONObject(eIdx)
                                val epNumber = epObj.optInt("number")
                                val rawDate = epObj.optString("last_watched_at")
                                val dateResult = ImportDateParser.parse(rawDate)

                                items.add(
                                    ImportedMediaItem(
                                        title = title,
                                        year = year,
                                        seasonNumber = seasonNumber,
                                        episodeNumber = epNumber,
                                        watchedAt = dateResult.epochMillis,
                                        watchDate = dateResult.dateOnlyString,
                                        rawDateString = rawDate,
                                        isDateOnly = dateResult.isDateOnly,
                                        mediaType = "tv",
                                        recordType = ImportRecordType.WATCHED,
                                        externalId = tmdbId,
                                        externalIdType = if (tmdbId != null) "tmdb" else null
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Mark show as watched (Explicit Show-Level state)
                    val rawDate = obj.optString("last_watched_at")
                    val dateResult = ImportDateParser.parse(rawDate)
                    items.add(
                        ImportedMediaItem(
                            title = title,
                            year = year,
                            seasonNumber = null,
                            episodeNumber = null,
                            watchedAt = dateResult.epochMillis,
                            watchDate = dateResult.dateOnlyString,
                            rawDateString = rawDate,
                            isDateOnly = dateResult.isDateOnly,
                            mediaType = "tv",
                            recordType = ImportRecordType.WATCHED,
                            isExplicitShowLevel = true,
                            externalId = tmdbId,
                            externalIdType = if (tmdbId != null) "tmdb" else null
                        )
                    )
                }
            }
        }

        // 3. Check if ratings entry
        if (obj.has("rating")) {
            val rawRating = obj.optDouble("rating").toFloat()
            val ratingVal = rawRating.coerceIn(0.5f, 10.0f)
            val rawDate = obj.optString("rated_at")
            val dateResult = ImportDateParser.parse(rawDate)
            val type = obj.optString("type")

            val (mediaType, mediaObj) = when {
                obj.has("movie") -> Pair("movie", obj.optJSONObject("movie"))
                obj.has("show") -> Pair("tv", obj.optJSONObject("show"))
                type == "movie" -> Pair("movie", obj.optJSONObject("movie"))
                else -> Pair("tv", obj.optJSONObject("show"))
            }

            if (mediaObj != null) {
                val title = mediaObj.optString("title")
                val year = mediaObj.optInt("year").takeIf { it > 0 }
                val ids = mediaObj.optJSONObject("ids")
                val tmdbId = ids?.optString("tmdb")?.takeIf { it.isNotBlank() && it != "0" }

                val seasonObj = obj.optJSONObject("season")
                val episodeObj = obj.optJSONObject("episode")
                val seasonNum = seasonObj?.optInt("number")
                val episodeNum = episodeObj?.optInt("number")

                if (title.isNotBlank()) {
                    items.add(
                        ImportedMediaItem(
                            title = title,
                            year = year,
                            seasonNumber = seasonNum,
                            episodeNumber = episodeNum,
                            watchedAt = dateResult.epochMillis,
                            watchDate = dateResult.dateOnlyString,
                            rawDateString = rawDate,
                            isDateOnly = dateResult.isDateOnly,
                            mediaType = mediaType,
                            recordType = ImportRecordType.RATING,
                            rating = ratingVal,
                            originalRating = rawRating,
                            originalRatingScale = 10.0f,
                            isExplicitShowLevel = seasonNum == null && episodeNum == null,
                            externalId = tmdbId,
                            externalIdType = if (tmdbId != null) "tmdb" else null
                        )
                    )
                }
            }
        }

        // 4. Check if watchlist entry
        if (obj.has("listed_at")) {
            val rawDate = obj.optString("listed_at")
            val dateResult = ImportDateParser.parse(rawDate)
            val (mediaType, mediaObj) = when {
                obj.has("movie") -> Pair("movie", obj.optJSONObject("movie"))
                obj.has("show") -> Pair("tv", obj.optJSONObject("show"))
                else -> Pair("movie", null)
            }

            if (mediaObj != null) {
                val title = mediaObj.optString("title")
                val year = mediaObj.optInt("year").takeIf { it > 0 }
                val ids = mediaObj.optJSONObject("ids")
                val tmdbId = ids?.optString("tmdb")?.takeIf { it.isNotBlank() && it != "0" }

                if (title.isNotBlank()) {
                    items.add(
                        ImportedMediaItem(
                            title = title,
                            year = year,
                            seasonNumber = null,
                            episodeNumber = null,
                            watchedAt = dateResult.epochMillis,
                            watchDate = dateResult.dateOnlyString,
                            rawDateString = rawDate,
                            isDateOnly = dateResult.isDateOnly,
                            mediaType = mediaType,
                            recordType = ImportRecordType.WATCHLIST,
                            externalId = tmdbId,
                            externalIdType = if (tmdbId != null) "tmdb" else null
                        )
                    )
                }
            }
        }

        return items
    }

    private fun parseDate(str: String?): Long? {
        if (str.isNullOrBlank()) return null
        val cleaned = str.trim()
        for (format in isoFormats) {
            try {
                val d = format.parse(cleaned)
                if (d != null) return d.time
            } catch (e: Exception) {
                // try next
            }
        }
        return null
    }

    private fun deduplicateItems(items: List<ImportedMediaItem>): List<ImportedMediaItem> {
        val unique = mutableListOf<ImportedMediaItem>()
        val seen = mutableSetOf<String>()
        for (item in items) {
            val key = "${item.recordType}_${item.mediaType}_${item.title}_${item.seasonNumber}_${item.episodeNumber}_${item.rating}"
            if (seen.add(key)) {
                unique.add(item)
            }
        }
        return unique
    }
}
