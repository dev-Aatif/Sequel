package dev.sequel.app.domain.export

import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.data.local.dao.WatchedMovieDao
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktDataExporter @Inject constructor(
    private val showDao: ShowDao,
    private val watchedMovieDao: WatchedMovieDao,
    private val watchedEpisodeDao: WatchedEpisodeDao
) : DataExporter {

    override val destination: ExportDestination = ExportDestination.TRAKT_CSV

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override suspend fun export(): ExportPackage {
        val out = ByteArrayOutputStream()
        val writer = OutputStreamWriter(out, StandardCharsets.UTF_8)

        writer.write("Title,Year,Season,Episode,Type,WatchedAt\n")

        // 1. Movies
        val movies = watchedMovieDao.getAllWatchedMovies()
        for (m in movies) {
            val show = showDao.getShowById(m.tmdbMovieId, "movie")
            val year = show?.firstAirDate?.take(4) ?: ""
            val dateStr = isoFormat.format(m.watchedAt)
            val title = escapeCsv(m.title)
            writer.write("$title,$year,,,movie,$dateStr\n")
        }

        // 2. Episodes
        val episodes = watchedEpisodeDao.getAllWatchedEpisodes()
        for (e in episodes) {
            val show = showDao.getShowById(e.showId, "tv")
            val year = show?.firstAirDate?.take(4) ?: ""
            val dateStr = isoFormat.format(e.watchedAt)
            val title = escapeCsv(show?.title ?: "Show ${e.showId}")
            writer.write("$title,$year,${e.seasonNumber},${e.episodeNumber},episode,$dateStr\n")
        }

        writer.flush()
        val timestamp = System.currentTimeMillis()
        return ExportPackage(
            destination = destination,
            suggestedFileName = "trakt_history_$timestamp.csv",
            mimeType = destination.mimeType,
            bytes = out.toByteArray()
        )
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
