package dev.sequel.app.domain.export

import dev.sequel.app.data.local.dao.ReviewDao
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.local.dao.WatchedMovieDao
import dev.sequel.app.data.local.dao.WatchlistDao
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LetterboxdDataExporter @Inject constructor(
    private val showDao: ShowDao,
    private val watchedMovieDao: WatchedMovieDao,
    private val watchlistDao: WatchlistDao,
    private val reviewDao: ReviewDao
) : DataExporter {

    override val destination: ExportDestination = ExportDestination.LETTERBOXD_ZIP

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override suspend fun export(): ExportPackage {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // 1. watched.csv
            zip.putNextEntry(ZipEntry("watched.csv"))
            val wWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            wWriter.write("Date,Name,Year,Letterboxd URI\n")
            val watchedMovies = watchedMovieDao.getAllWatchedMovies()
            for (m in watchedMovies) {
                val show = showDao.getShowById(m.tmdbMovieId, "movie")
                val year = show?.firstAirDate?.take(4) ?: ""
                val dateStr = dateFormat.format(m.watchedAt)
                val title = escapeCsv(m.title)
                wWriter.write("$dateStr,$title,$year,\n")
            }
            wWriter.flush()
            zip.closeEntry()

            // 2. ratings.csv
            zip.putNextEntry(ZipEntry("ratings.csv"))
            val rWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            rWriter.write("Date,Name,Year,Letterboxd URI,Rating\n")
            val movieRatings = reviewDao.getAllReviews().filter { it.mediaType == "movie" && it.rating != null }
            for (r in movieRatings) {
                val show = showDao.getShowById(r.mediaId, "movie")
                val year = show?.firstAirDate?.take(4) ?: ""
                val dateStr = dateFormat.format(r.updatedAt)
                val title = escapeCsv(show?.title ?: "Movie ${r.mediaId}")
                val letterboxdRating = (r.rating!! / 2.0f).coerceIn(0.5f, 5.0f)
                rWriter.write("$dateStr,$title,$year,,$letterboxdRating\n")
            }
            rWriter.flush()
            zip.closeEntry()

            // 3. reviews.csv
            zip.putNextEntry(ZipEntry("reviews.csv"))
            val revWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            revWriter.write("Date,Name,Year,Letterboxd URI,Rating,Review,Spoiler\n")
            val movieReviews = reviewDao.getAllReviews().filter { it.mediaType == "movie" && !it.reviewText.isNullOrBlank() }
            for (rev in movieReviews) {
                val show = showDao.getShowById(rev.mediaId, "movie")
                val year = show?.firstAirDate?.take(4) ?: ""
                val dateStr = dateFormat.format(rev.updatedAt)
                val title = escapeCsv(show?.title ?: "Movie ${rev.mediaId}")
                val letterboxdRating = rev.rating?.let { (it / 2.0f).coerceIn(0.5f, 5.0f) } ?: ""
                val reviewText = escapeCsv(rev.reviewText ?: "")
                val spoilerStr = if (rev.isSpoiler) "True" else "False"
                revWriter.write("$dateStr,$title,$year,,$letterboxdRating,$reviewText,$spoilerStr\n")
            }
            revWriter.flush()
            zip.closeEntry()

            // 4. watchlist.csv
            zip.putNextEntry(ZipEntry("watchlist.csv"))
            val wlWriter = OutputStreamWriter(zip, StandardCharsets.UTF_8)
            wlWriter.write("Date,Name,Year,Letterboxd URI\n")
            val movieWatchlist = watchlistDao.getAllWatchlist().filter { it.mediaType.name.equals("movie", true) }
            for (w in movieWatchlist) {
                val show = showDao.getShowById(w.tmdbId, "movie")
                val year = show?.firstAirDate?.take(4) ?: ""
                val dateStr = dateFormat.format(w.addedAt)
                val title = escapeCsv(w.title)
                wlWriter.write("$dateStr,$title,$year,\n")
            }
            wlWriter.flush()
            zip.closeEntry()
        }

        val timestamp = System.currentTimeMillis()
        return ExportPackage(
            destination = destination,
            suggestedFileName = "letterboxd_export_$timestamp.zip",
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
