package dev.sequel.app.domain.importer

import android.content.Context
import android.net.Uri
import dev.sequel.app.domain.backup.SequelBackupModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream

/**
 * Parser for Sequel JSON backup files, allowing users to import items via the Media Import pipeline.
 */
class SequelBackupParser : MediaDataParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun parse(context: Context, uri: Uri): List<ImportedMediaItem> = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open file at $uri")
        inputStream.use { stream ->
            parseStream(stream)
        }
    }

    override suspend fun parseStream(inputStream: InputStream): List<ImportedMediaItem> = withContext(Dispatchers.IO) {
        val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val backup = try {
            json.decodeFromString<SequelBackupModel>(content)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid Sequel backup file: ${e.message}")
        }

        val showMap = backup.shows.associateBy { it.id }
        val results = mutableListOf<ImportedMediaItem>()

        // 1. Watched Movies
        backup.watchedMovies.forEach { m ->
            results.add(
                ImportedMediaItem(
                    title = m.title,
                    year = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    watchedAt = m.watchedAt,
                    watchDate = m.watchDate,
                    mediaType = "movie",
                    recordType = ImportRecordType.WATCHED,
                    externalId = m.tmdbMovieId.toString(),
                    externalIdType = "tmdb"
                )
            )
        }

        // 2. Watched Episodes
        backup.watchedEpisodes.forEach { e ->
            val showTitle = showMap[e.showId]?.title ?: "Show ${e.showId}"
            results.add(
                ImportedMediaItem(
                    title = showTitle,
                    year = null,
                    seasonNumber = e.seasonNumber,
                    episodeNumber = e.episodeNumber,
                    watchedAt = e.watchedAt,
                    watchDate = e.watchDate,
                    mediaType = "tv",
                    recordType = ImportRecordType.WATCHED,
                    externalId = e.showId.toString(),
                    externalIdType = "tmdb"
                )
            )
        }

        // 3. Watchlist
        backup.watchlist.forEach { w ->
            results.add(
                ImportedMediaItem(
                    title = w.title,
                    year = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    watchedAt = w.addedAt,
                    mediaType = w.mediaType,
                    recordType = ImportRecordType.WATCHLIST,
                    externalId = w.tmdbId.toString(),
                    externalIdType = "tmdb"
                )
            )
        }

        // 4. Reviews & Ratings
        backup.reviews.forEach { r ->
            val showTitle = showMap[r.mediaId]?.title ?: "Media ${r.mediaId}"
            results.add(
                ImportedMediaItem(
                    title = showTitle,
                    year = null,
                    seasonNumber = r.seasonNum,
                    episodeNumber = r.episodeNum,
                    watchedAt = r.updatedAt,
                    mediaType = r.mediaType,
                    recordType = if (!r.reviewText.isNullOrBlank()) ImportRecordType.REVIEW else ImportRecordType.RATING,
                    rating = r.rating,
                    originalRating = r.originalRating ?: r.rating,
                    originalRatingScale = r.originalRatingScale ?: 10.0f,
                    reviewText = r.reviewText,
                    isSpoiler = r.isSpoiler,
                    externalId = r.mediaId.toString(),
                    externalIdType = "tmdb"
                )
            )
        }

        if (results.isEmpty()) {
            throw IllegalArgumentException("No media records found in Sequel backup file")
        }

        results
    }
}
