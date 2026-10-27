package dev.sequel.app.domain.importer.adapter

import dev.sequel.app.domain.importer.*
import dev.sequel.app.domain.importer.core.CanonicalImportRecord
import dev.sequel.app.domain.importer.core.ExternalIdentifiers
import dev.sequel.app.domain.importer.core.ImportSource
import dev.sequel.app.domain.importer.core.SourceProvenance
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

interface SourceAdapter {
    val source: ImportSource
    fun canHandle(source: ImportSource): Boolean = this.source == source
    suspend fun parse(inputStream: InputStream, filenameHint: String? = null): List<CanonicalImportRecord>
}

@Singleton
class TvTimeSourceAdapter @Inject constructor() : SourceAdapter {
    override val source: ImportSource = ImportSource.TV_TIME
    private val parser = TvTimeCsvParser()

    override suspend fun parse(inputStream: InputStream, filenameHint: String?): List<CanonicalImportRecord> {
        val items = parser.parseStream(inputStream)
        return items.map { it.toCanonical(ImportSource.TV_TIME) }
    }
}

@Singleton
class TraktSourceAdapter @Inject constructor() : SourceAdapter {
    override val source: ImportSource = ImportSource.TRAKT
    private val parser = TraktExportParser()

    override suspend fun parse(inputStream: InputStream, filenameHint: String?): List<CanonicalImportRecord> {
        val items = parser.parseStream(inputStream)
        return items.map { it.toCanonical(ImportSource.TRAKT) }
    }
}

@Singleton
class LetterboxdSourceAdapter @Inject constructor() : SourceAdapter {
    override val source: ImportSource = ImportSource.LETTERBOXD
    private val parser = LetterboxdExportParser()

    override suspend fun parse(inputStream: InputStream, filenameHint: String?): List<CanonicalImportRecord> {
        val buffered = if (inputStream.markSupported()) inputStream else inputStream.buffered()
        val items = if (dev.sequel.app.domain.importer.archive.ZipArchiveExtractor.isZipStream(buffered)) {
            parser.parseStream(buffered)
        } else {
            parser.parseSingleCsv(buffered, filenameHint)
        }
        return items.map { it.toCanonical(ImportSource.LETTERBOXD) }
    }
}

@Singleton
class SequelBackupSourceAdapter @Inject constructor() : SourceAdapter {
    override val source: ImportSource = ImportSource.SEQUEL_BACKUP
    private val parser = SequelBackupParser()

    override suspend fun parse(inputStream: InputStream, filenameHint: String?): List<CanonicalImportRecord> {
        val items = parser.parseStream(inputStream)
        return items.map { it.toCanonical(ImportSource.SEQUEL_BACKUP) }
    }
}

fun ImportedMediaItem.toCanonical(source: ImportSource): CanonicalImportRecord {
    val tmdbId = if (externalIdType == "tmdb") externalId?.toIntOrNull() else null
    val imdbId = if (externalIdType == "imdb") externalId else null
    val traktId = if (externalIdType == "trakt") externalId?.toIntOrNull() else null
    val tvdbId = if (externalIdType == "tvdb") externalId?.toIntOrNull() else null
    val letterboxdUri = if (externalIdType == "letterboxd") externalId else null

    return CanonicalImportRecord(
        rawTitle = title,
        normalizedTitle = title.trim(),
        year = year,
        mediaType = mediaType.lowercase(),
        recordType = recordType,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        watchedAt = watchedAt,
        watchDate = watchDate,
        rating = rating,
        originalRating = originalRating,
        originalRatingScale = originalRatingScale,
        reviewText = reviewText,
        isSpoiler = isSpoiler,
        isRewatch = isRewatch,
        isExplicitShowLevel = isExplicitShowLevel,
        tags = tags,
        identifiers = ExternalIdentifiers(
            tmdbId = tmdbId,
            imdbId = imdbId,
            tvdbId = tvdbId,
            traktId = traktId,
            letterboxdUri = letterboxdUri
        ),
        source = source,
        provenance = SourceProvenance(
            source = source,
            sourceRecordId = externalId,
            rawDateString = rawDateString,
            isDateOnly = isDateOnly,
            rawRating = originalRating,
            rawRatingScale = originalRatingScale
        )
    )
}
