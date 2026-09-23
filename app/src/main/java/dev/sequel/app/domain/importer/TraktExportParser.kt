package dev.sequel.app.domain.importer

import android.content.Context
import android.net.Uri
import com.opencsv.CSVReader
import dev.sequel.app.domain.error.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class TraktExportParser : MediaDataParser {
    override suspend fun parse(context: Context, uri: Uri): List<ImportedMediaItem> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<ImportedMediaItem>()

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    CSVReader(reader).use { csvReader ->
                        // Read header
                        val header = csvReader.readNext() ?: throw IllegalArgumentException("Empty file")
                        
                        val titleIdx = header.indexOfFirst { it.equals("Title", ignoreCase = true) }
                        val yearIdx = header.indexOfFirst { it.equals("Year", ignoreCase = true) }
                        val seasonIdx = header.indexOfFirst { it.equals("Season", ignoreCase = true) }
                        val episodeIdx = header.indexOfFirst { it.equals("Episode", ignoreCase = true) }
                        val typeIdx = header.indexOfFirst { it.equals("Type", ignoreCase = true) }

                        if (titleIdx == -1) {
                            throw IllegalArgumentException("Invalid Trakt CSV: Missing 'Title' column")
                        }

                        var line: Array<String>? = csvReader.readNext()
                        while (line != null) {
                            val title = line.getOrNull(titleIdx) ?: ""
                            val year = if (yearIdx != -1) line.getOrNull(yearIdx)?.toIntOrNull() else null
                            val seasonNumber = if (seasonIdx != -1) line.getOrNull(seasonIdx)?.toIntOrNull() else null
                            val episodeNumber = if (episodeIdx != -1) line.getOrNull(episodeIdx)?.toIntOrNull() else null
                            var mediaType = if (typeIdx != -1) line.getOrNull(typeIdx)?.lowercase() else null
                            
                            if (mediaType == null) {
                                // Infer from presence of season/episode
                                mediaType = if (seasonNumber != null && episodeNumber != null) "tv" else "movie"
                            } else {
                                if (mediaType == "show" || mediaType == "episode") mediaType = "tv"
                            }

                            if (title.isNotBlank()) {
                                rows.add(
                                    ImportedMediaItem(
                                        title = title,
                                        year = year,
                                        seasonNumber = seasonNumber,
                                        episodeNumber = episodeNumber,
                                        watchedAt = System.currentTimeMillis(),
                                        mediaType = mediaType
                                    )
                                )
                            }
                            line = csvReader.readNext()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse Trakt CSV: ${e.message}")
        }

        if (rows.isEmpty()) {
            throw IllegalArgumentException("No valid rows found in CSV")
        }

        rows
    }
}
