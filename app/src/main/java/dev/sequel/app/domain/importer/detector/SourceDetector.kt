package dev.sequel.app.domain.importer.detector

import com.opencsv.CSVReader
import dev.sequel.app.domain.importer.archive.ZipArchiveExtractor
import dev.sequel.app.domain.importer.core.ArchiveType
import dev.sequel.app.domain.importer.core.ImportSource
import dev.sequel.app.domain.importer.core.SourceMetadata
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader
import java.util.Locale
import java.util.zip.ZipInputStream

import javax.inject.Inject
import javax.inject.Singleton

data class DetectionResult(
    val source: ImportSource,
    val archiveType: ArchiveType,
    val isValid: Boolean,
    val validationErrors: List<String> = emptyList(),
    val metadata: SourceMetadata
)

@Singleton
class SourceDetector @Inject constructor() {

    /**
     * Inspects the input stream and optional filename hint to detect the exact export platform.
     * Guaranteed to reset or preserve the stream state if markSupported is true.
     */
    fun detect(inputStream: InputStream, filenameHint: String? = null): DetectionResult {
        val buffered = if (inputStream.markSupported()) inputStream else BufferedInputStream(inputStream)
        buffered.mark(1024 * 1024) // 1MB buffer window for header inspection

        val errors = mutableListOf<String>()

        // 1. Check for ZIP
        if (ZipArchiveExtractor.isZipStream(buffered)) {
            val zipResult = inspectZip(buffered, filenameHint)
            buffered.reset()
            return zipResult
        }

        // 2. Peek text snippet
        val peekBytes = ByteArray(4096)
        val readBytes = buffered.read(peekBytes)
        buffered.reset()

        if (readBytes <= 0) {
            return DetectionResult(
                source = ImportSource.UNKNOWN,
                archiveType = ArchiveType.UNKNOWN,
                isValid = false,
                validationErrors = listOf("File is empty"),
                metadata = SourceMetadata(fileName = filenameHint)
            )
        }

        val snippet = String(peekBytes, 0, readBytes, Charsets.UTF_8).trim()

        // 3. Check for JSON
        if (snippet.startsWith("{") || snippet.startsWith("[")) {
            val jsonResult = inspectJson(snippet, filenameHint)
            buffered.reset()
            return jsonResult
        }

        // 4. Check for CSV
        val csvResult = inspectCsv(snippet, filenameHint)
        buffered.reset()
        return csvResult
    }

    private fun inspectZip(inputStream: InputStream, filenameHint: String?): DetectionResult {
        val entryNames = mutableListOf<String>()
        try {
            val zipIn = ZipInputStream(inputStream)
            var entry = zipIn.nextEntry
            while (entry != null && entryNames.size < 50) {
                if (!entry.isDirectory) {
                    entryNames.add(entry.name.lowercase(Locale.ROOT))
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        } catch (e: Exception) {
            return DetectionResult(
                source = ImportSource.UNKNOWN,
                archiveType = ArchiveType.ZIP,
                isValid = false,
                validationErrors = listOf("Malformed ZIP archive: ${e.message}"),
                metadata = SourceMetadata(fileName = filenameHint, isZip = true)
            )
        }

        val source = when {
            entryNames.any { it.contains("letterboxd") || it.endsWith("diary.csv") || it.endsWith("ratings.csv") && entryNames.any { e -> e.endsWith("watched.csv") } } ->
                ImportSource.LETTERBOXD
            entryNames.any { it.contains("tracking-prod-records") || it.contains("seen_episode") || it.contains("followed_tv_show") } ->
                ImportSource.TV_TIME
            entryNames.any { it.contains("watched_movies.json") || it.contains("watched_shows.json") } ->
                ImportSource.TRAKT
            else -> ImportSource.UNKNOWN
        }

        val isValid = source != ImportSource.UNKNOWN
        val errors = if (!isValid) listOf("Unrecognized ZIP archive structure. Could not find Letterboxd, TV Time, or Trakt files.") else emptyList()

        return DetectionResult(
            source = source,
            archiveType = ArchiveType.ZIP,
            isValid = isValid,
            validationErrors = errors,
            metadata = SourceMetadata(
                fileName = filenameHint,
                isZip = true,
                headerColumns = entryNames
            )
        )
    }

    private fun inspectJson(snippet: String, filenameHint: String?): DetectionResult {
        val lower = snippet.lowercase(Locale.ROOT)
        val source = when {
            lower.contains("\"schemaversion\"") && (lower.contains("\"shows\"") || lower.contains("\"watchedmovies\"")) ->
                ImportSource.SEQUEL_BACKUP
            lower.contains("\"last_watched_at\"") || lower.contains("\"trakt\"") ->
                ImportSource.TRAKT
            else -> ImportSource.UNKNOWN
        }

        val isValid = source != ImportSource.UNKNOWN
        val errors = if (!isValid) listOf("Unknown JSON export schema") else emptyList()

        return DetectionResult(
            source = source,
            archiveType = ArchiveType.JSON,
            isValid = isValid,
            validationErrors = errors,
            metadata = SourceMetadata(fileName = filenameHint, snippet = snippet.take(100))
        )
    }

    private fun inspectCsv(snippet: String, filenameHint: String?): DetectionResult {
        val reader = BufferedReader(StringReader(snippet))
        val csvReader = CSVReader(reader)
        val header = try {
            csvReader.readNext()
        } catch (e: Exception) {
            null
        }

        if (header == null || header.isEmpty()) {
            return DetectionResult(
                source = ImportSource.UNKNOWN,
                archiveType = ArchiveType.CSV,
                isValid = false,
                validationErrors = listOf("Invalid or unreadable CSV header"),
                metadata = SourceMetadata(fileName = filenameHint)
            )
        }

        val headerCols = header.map { it.trim().lowercase(Locale.ROOT) }

        val source = when {
            headerCols.contains("letterboxd uri") -> ImportSource.LETTERBOXD
            headerCols.contains("tv_show_name") || headerCols.contains("episode_season_number") || headerCols.contains("entity_type") -> ImportSource.TV_TIME
            headerCols.contains("title") && headerCols.contains("season") && headerCols.contains("episode") && headerCols.contains("type") -> ImportSource.TRAKT
            headerCols.contains("title") || headerCols.contains("name") -> ImportSource.GENERIC_CSV
            else -> ImportSource.UNKNOWN
        }

        val isValid = source != ImportSource.UNKNOWN
        val errors = if (!isValid) listOf("CSV does not contain recognized media headers") else emptyList()

        return DetectionResult(
            source = source,
            archiveType = ArchiveType.CSV,
            isValid = isValid,
            validationErrors = errors,
            metadata = SourceMetadata(
                fileName = filenameHint,
                headerColumns = headerCols,
                snippet = snippet.take(100)
            )
        )
    }
}
