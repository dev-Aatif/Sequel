package dev.sequel.app.domain.importer

import android.content.Context
import android.net.Uri
import com.opencsv.CSVReader
import dev.sequel.app.domain.error.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class TvTimeCsvParser : MediaDataParser {
    override suspend fun parse(context: Context, uri: Uri): List<ImportedMediaItem> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<ImportedMediaItem>()
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    CSVReader(reader).use { csvReader ->
                        // Skip header
                        csvReader.readNext() ?: throw AppError.Validation("Empty file")

                        var line: Array<String>? = csvReader.readNext()
                        while (line != null) {
                            if (line.size >= 4) {
                                val showName = line[0]
                                val seasonNumber = line[1].toIntOrNull() ?: 0
                                val episodeNumber = line[2].toIntOrNull() ?: 0
                                // TvTime export doesn't provide year, we pass null
                                // For TV Time, everything is TV
                                rows.add(
                                    ImportedMediaItem(
                                        title = showName,
                                        year = null,
                                        seasonNumber = seasonNumber,
                                        episodeNumber = episodeNumber,
                                        watchedAt = System.currentTimeMillis(), // We could parse the date if needed, or just use current time
                                        mediaType = "tv"
                                    )
                                )
                            }
                            line = csvReader.readNext()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            throw AppError.Validation("Failed to parse TV Time CSV: ${e.message}")
        }
        
        if (rows.isEmpty()) {
            throw AppError.Validation("No valid rows found in CSV")
        }
        
        rows
    }
}
