package dev.sequel.app.domain.export

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.*
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.local.entity.WatchlistEntity
import dev.sequel.app.domain.backup.SequelBackupService
import dev.sequel.app.domain.importer.archive.ZipArchiveExtractor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExportCoordinatorTest {

    private lateinit var database: SequelDatabase
    private lateinit var showDao: ShowDao
    private lateinit var watchedMovieDao: WatchedMovieDao
    private lateinit var watchedEpisodeDao: WatchedEpisodeDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var reviewDao: ReviewDao
    private lateinit var mountRushmoreDao: MountRushmoreDao

    private lateinit var exportCoordinator: ExportCoordinator

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SequelDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        showDao = database.showDao()
        watchedMovieDao = database.watchedMovieDao()
        watchedEpisodeDao = database.watchedEpisodeDao()
        watchlistDao = database.watchlistDao()
        reviewDao = database.reviewDao()
        mountRushmoreDao = database.mountRushmoreDao()

        val backupService = SequelBackupService(
            database = database,
            showDao = showDao,
            watchedMovieDao = watchedMovieDao,
            watchedEpisodeDao = watchedEpisodeDao,
            watchlistDao = watchlistDao,
            reviewDao = reviewDao,
            mountRushmoreDao = mountRushmoreDao
        )

        val nativeExporter = SequelNativeBackupExporter(backupService)
        val letterboxdExporter = LetterboxdDataExporter(showDao, watchedMovieDao, watchlistDao, reviewDao)
        val traktExporter = TraktDataExporter(showDao, watchedMovieDao, watchedEpisodeDao)

        exportCoordinator = ExportCoordinator(
            nativeExporter = nativeExporter,
            letterboxdExporter = letterboxdExporter,
            traktExporter = traktExporter
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportAllDestinations_generatesValidPackages() = runBlocking {
        // Seed 1 movie and 1 show
        val movie = ShowEntity(
            id = 10,
            title = "Dune: Part Two",
            overview = "Paul Atreides unites with Chani...",
            firstAirDate = "2024-03-01",
            voteAverage = 8.5,
            genreIds = "[878]",
            genresDisplay = "Sci-Fi",
            mediaType = "movie"
        )
        showDao.insertShow(movie)
        watchedMovieDao.upsertWatchedMovie(10, "Dune: Part Two", "/dune2.jpg", 1709300000000L)
        reviewDao.upsertRating(10, "movie", null, null, 9.0f)

        val show = ShowEntity(
            id = 20,
            title = "Severance",
            overview = "Lumon Industries employees...",
            firstAirDate = "2022-02-18",
            voteAverage = 8.6,
            genreIds = "[878, 9648]",
            genresDisplay = "Sci-Fi, Mystery",
            mediaType = "tv",
            numberOfSeasons = 1,
            numberOfEpisodes = 9
        )
        showDao.insertShow(show)
        watchedEpisodeDao.upsertWatchedEpisode(20, 201, 1, 1, 1680000000000L)

        watchlistDao.insertToWatchlist(
            WatchlistEntity(
                tmdbId = 30,
                mediaType = MediaType.MOVIE,
                title = "Gladiator II",
                posterPath = "/gladiator.jpg",
                addedAt = 1700000000000L
            )
        )

        // 1. Export Native Sequel Backup
        val nativePkg = exportCoordinator.export(ExportDestination.SEQUEL_NATIVE)
        assertEquals(ExportDestination.SEQUEL_NATIVE, nativePkg.destination)
        assertTrue(nativePkg.suggestedFileName.endsWith(".json"))
        val jsonStr = String(nativePkg.bytes, StandardCharsets.UTF_8)
        assertTrue(jsonStr.contains("\"schemaVersion\": 1"))
        assertTrue(jsonStr.contains("\"title\": \"Dune: Part Two\""))

        // 2. Export Letterboxd ZIP
        val lboxdPkg = exportCoordinator.export(ExportDestination.LETTERBOXD_ZIP)
        assertEquals(ExportDestination.LETTERBOXD_ZIP, lboxdPkg.destination)
        assertTrue(lboxdPkg.suggestedFileName.endsWith(".zip"))
        assertTrue(ZipArchiveExtractor.isZipStream(ByteArrayInputStream(lboxdPkg.bytes)))

        // 3. Export Trakt CSV
        val traktPkg = exportCoordinator.export(ExportDestination.TRAKT_CSV)
        assertEquals(ExportDestination.TRAKT_CSV, traktPkg.destination)
        assertTrue(traktPkg.suggestedFileName.endsWith(".csv"))
        val csvStr = String(traktPkg.bytes, StandardCharsets.UTF_8)
        assertTrue(csvStr.startsWith("Title,Year,Season,Episode,Type,WatchedAt"))
        assertTrue(csvStr.contains("Dune: Part Two"))
        assertTrue(csvStr.contains("Severance"))
    }
}
