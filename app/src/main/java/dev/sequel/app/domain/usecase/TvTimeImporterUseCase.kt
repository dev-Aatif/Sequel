package dev.sequel.app.domain.usecase

import android.content.Context
import android.net.Uri
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.SyncStatus
import dev.sequel.app.data.local.entity.WatchedEpisodeEntity
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

data class TvTimeRow(
    val showName: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val watchedDate: String
)

sealed interface ImportProgress {
    data object Idle : ImportProgress
    data class Parsing(val message: String) : ImportProgress
    data class Importing(val current: Int, val total: Int, val showName: String) : ImportProgress
    data class Success(val importedCount: Int) : ImportProgress
    data class Error(val message: String) : ImportProgress
}

class TvTimeImporterUseCase @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val tmdbApiService: TmdbApiService,
    private val watchedEpisodeDao: WatchedEpisodeDao
) {
    fun importCsv(uri: Uri): Flow<ImportProgress> = flow {
        emit(ImportProgress.Parsing("Reading CSV file..."))
        val rows = mutableListOf<TvTimeRow>()
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    // Skip header
                    val header = reader.readLine()
                    if (header == null) {
                        emit(ImportProgress.Error("Empty file"))
                        return@flow
                    }

                    var line = reader.readLine()
                    while (line != null) {
                        // Very naive CSV parsing, assuming quotes or not.
                        // Format is typically: "Show Name","Season","Episode","Date"
                        // Or without quotes.
                        val parts = line.split("\",\"").map { it.replace("\"", "") }
                        if (parts.size >= 4) {
                            val showName = parts[0]
                            val seasonNumber = parts[1].toIntOrNull() ?: 0
                            val episodeNumber = parts[2].toIntOrNull() ?: 0
                            val date = parts[3]
                            rows.add(TvTimeRow(showName, seasonNumber, episodeNumber, date))
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: Exception) {
            emit(ImportProgress.Error("Failed to parse CSV: ${e.message}"))
            return@flow
        }

        if (rows.isEmpty()) {
            emit(ImportProgress.Error("No valid rows found in CSV"))
            return@flow
        }

        val groupedByShow = rows.groupBy { it.showName }
        val totalShows = groupedByShow.size
        var currentShowIndex = 0
        var totalImported = 0

        for ((showName, episodes) in groupedByShow) {
            currentShowIndex++
            emit(ImportProgress.Importing(currentShowIndex, totalShows, showName))
            
            try {
                // 1. Search TMDB for the show
                val searchResult = tmdbApiService.searchTv(showName)
                val showId = searchResult.results.firstOrNull()?.id
                
                if (showId != null) {
                    // 2. Insert Watched Episodes
                    val entities = episodes.map { ep ->
                        WatchedEpisodeEntity(
                            mediaType = MediaType.TV,
                            showId = showId,
                            episodeId = -1, // Placeholder
                            seasonNumber = ep.seasonNumber,
                            episodeNumber = ep.episodeNumber,
                            syncStatus = SyncStatus.PENDING
                        )
                    }
                    watchedEpisodeDao.insertWatchedEpisodes(entities)
                    totalImported += entities.size
                }
            } catch (e: Exception) {
                // Skip on error (could be rate limit, network issue, etc.)
                // Production app would need better error/retry handling here.
            }
        }

        emit(ImportProgress.Success(totalImported))
    }.flowOn(Dispatchers.IO)
}
