package dev.sequel.app.domain.importer

import android.content.Context
import android.net.Uri
import com.opencsv.CSVReader
import dev.sequel.app.domain.error.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

class TvTimeCsvParser : MediaDataParser {
    override suspend fun parse(context: Context, uri: Uri): List<ImportedMediaItem> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<ImportedMediaItem>()
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    CSVReader(reader).use { csvReader ->
                        // Read header
                        val header = csvReader.readNext() ?: throw IllegalArgumentException("Empty file")

                        val titleIdx = header.indexOfFirst { c -> c.equals("tv_show_name", true) || c.equals("movie_name", true) || c.equals("title", true) || c.contains("name", true) }
                        val seasonIdx = header.indexOfFirst { c -> c.equals("episode_season_number", true) || c.equals("season", true) || c.contains("season", true) }
                        val episodeIdx = header.indexOfFirst { c -> c.equals("episode_number", true) || c.equals("episode", true) }
                        val dateIdx = header.indexOfFirst { c -> c.equals("created_at", true) || c.equals("updated_at", true) || c.equals("date", true) }

                        if (titleIdx == -1) {
                            throw IllegalArgumentException("Invalid TV Time CSV: Missing Title/Name column")
                        }

                        var line: Array<String>? = csvReader.readNext()
                        while (line != null) {
                            val title = line.getOrNull(titleIdx)?.trim() ?: ""
                            val seasonStr = if (seasonIdx != -1) line.getOrNull(seasonIdx) else null
                            val episodeStr = if (episodeIdx != -1) line.getOrNull(episodeIdx) else null
                            val dateStr = if (dateIdx != -1) line.getOrNull(dateIdx) else null
                            
                            val seasonNumber = seasonStr?.toIntOrNull()
                            val episodeNumber = episodeStr?.toIntOrNull()

                            val mediaType = if (seasonNumber != null || episodeNumber != null) "tv" else "movie"

                            var watchedAt = System.currentTimeMillis()
                            if (!dateStr.isNullOrBlank()) {
                                try {
                                    val parsedDate = dateFormatter.parse(dateStr.replace("T", " ").replace("Z", ""))
                                    if (parsedDate != null) {
                                        watchedAt = parsedDate.time
                                    }
                                } catch (e: Exception) {
                                    // Fallback to current time if parsing fails
                                }
                            }

                            if (title.isNotBlank()) {
                                rows.add(
                                    ImportedMediaItem(
                                        title = title,
                                        year = null,
                                        seasonNumber = seasonNumber ?: 0,
                                        episodeNumber = episodeNumber ?: 0,
                                        watchedAt = watchedAt,
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
            throw IllegalArgumentException("Failed to parse TV Time CSV: ${e.message}")
        }
        
        if (rows.isEmpty()) {
            throw IllegalArgumentException("No valid rows found in CSV")
        }
        
        rows
    }
}
