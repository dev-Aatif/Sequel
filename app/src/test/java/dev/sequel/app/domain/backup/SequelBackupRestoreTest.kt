package dev.sequel.app.domain.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.*
import dev.sequel.app.data.local.entity.*
import dev.sequel.app.domain.importer.ImportRecordType
import dev.sequel.app.domain.importer.SequelBackupParser
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

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SequelBackupRestoreTest {

    private lateinit var database: SequelDatabase
    private lateinit var showDao: ShowDao
    private lateinit var watchedMovieDao: WatchedMovieDao
    private lateinit var watchedEpisodeDao: WatchedEpisodeDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var reviewDao: ReviewDao
    private lateinit var mountRushmoreDao: MountRushmoreDao

    private lateinit var backupService: SequelBackupService
    private lateinit var backupParser: SequelBackupParser

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

        backupService = SequelBackupService(
            database = database,
            showDao = showDao,
            watchedMovieDao = watchedMovieDao,
            watchedEpisodeDao = watchedEpisodeDao,
            watchlistDao = watchlistDao,
            reviewDao = reviewDao,
            mountRushmoreDao = mountRushmoreDao
        )

        backupParser = SequelBackupParser()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun roundTripBackupAndRestore_preservesAllEntitiesAndIntegrity() = runBlocking {
        // 1. Seed Show entity (needed for FKs)
        val show = ShowEntity(
            id = 100,
            title = "Severance",
            overview = "Mark leads a team of office workers...",
            posterPath = "/severance.jpg",
            backdropPath = "/severance_bg.jpg",
            firstAirDate = "2022-02-18",
            voteAverage = 8.5,
            genreIds = "[18, 9648]",
            genresDisplay = "Drama, Mystery",
            mediaType = "tv",
            numberOfSeasons = 1,
            numberOfEpisodes = 9
        )
        showDao.insertShow(show)

        val movieShow = ShowEntity(
            id = 200,
            title = "Inception",
            overview = "A thief who steals corporate secrets...",
            posterPath = "/inception.jpg",
            backdropPath = "/inception_bg.jpg",
            firstAirDate = "2010-07-16",
            voteAverage = 8.8,
            genreIds = "[28, 878]",
            genresDisplay = "Action, Sci-Fi",
            mediaType = "movie"
        )
        showDao.insertShow(movieShow)

        // 2. Seed Watched Movie
        watchedMovieDao.upsertWatchedMovie(
            movieId = 200,
            title = "Inception",
            posterPath = "/inception.jpg",
            watchedAt = 1680000000000L
        )

        // 3. Seed Watched Episode
        watchedEpisodeDao.upsertWatchedEpisode(
            showId = 100,
            tmdbEpisodeId = 1001,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedAt = 1680005000000L
        )

        // 4. Seed Watchlist
        watchlistDao.insertToWatchlist(
            WatchlistEntity(
                tmdbId = 300,
                mediaType = MediaType.MOVIE,
                title = "Dune: Part Two",
                posterPath = "/dune2.jpg",
                addedAt = 1680010000000L
            )
        )

        // 5. Seed Reviews / Ratings
        reviewDao.upsertRating(
            mediaId = 200,
            mediaType = "movie",
            seasonNum = null,
            episodeNum = null,
            rating = 9.5f
        )
        reviewDao.upsertReviewText(
            mediaId = 100,
            mediaType = "tv",
            seasonNum = 1,
            episodeNum = null,
            text = "Phenomenal season finale.",
            isSpoiler = false
        )

        // 6. Seed Mount Rushmore
        mountRushmoreDao.upsertSlot(
            MountRushmoreEntity(
                slotIndex = 0,
                mediaId = 200,
                mediaType = "movie",
                title = "Inception",
                posterPath = "/inception.jpg",
                year = "2010",
                rating = 9.5f
            )
        )

        // 7. Export Backup JSON
        val backupJson = backupService.exportJsonBackup()
        assertTrue(backupJson.contains("\"schemaVersion\": 1"))
        assertTrue(backupJson.contains("\"title\": \"Severance\""))
        assertTrue(backupJson.contains("\"title\": \"Inception\""))

        // 8. Test CSV Export ZIP
        val zipBytes = backupService.exportCsvZip()
        assertTrue(ZipArchiveExtractor.isZipStream(ByteArrayInputStream(zipBytes)))

        // 9. Wipe database tables
        database.clearAllTables()
        assertEquals(0, showDao.getAllShows().size)
        assertEquals(0, watchedMovieDao.getAllWatchedMovies().size)
        assertEquals(0, watchedEpisodeDao.getAllWatchedEpisodes().size)

        // 10. Restore from Backup JSON
        val restoreResult = backupService.restoreJsonBackup(backupJson)
        assertEquals(2, restoreResult.showsCount)
        assertEquals(1, restoreResult.moviesCount)
        assertEquals(1, restoreResult.episodesCount)
        assertEquals(1, restoreResult.watchlistCount)
        assertEquals(2, restoreResult.reviewsCount)
        assertEquals(1, restoreResult.mountRushmoreCount)

        // 11. Verify Restored Data Integrity
        val restoredMovie = watchedMovieDao.getAllWatchedMovies().firstOrNull()
        assertNotNull(restoredMovie)
        assertEquals(200, restoredMovie?.tmdbMovieId)
        assertEquals("Inception", restoredMovie?.title)

        val restoredEpisode = watchedEpisodeDao.getAllWatchedEpisodes().firstOrNull()
        assertNotNull(restoredEpisode)
        assertEquals(100, restoredEpisode?.showId)
        assertEquals(1001, restoredEpisode?.tmdbEpisodeId)

        val restoredWatchlist = watchlistDao.getAllWatchlist().firstOrNull()
        assertNotNull(restoredWatchlist)
        assertEquals(300, restoredWatchlist?.tmdbId)

        val restoredReviews = reviewDao.getAllReviews()
        assertEquals(2, restoredReviews.size)

        val restoredSlot = mountRushmoreDao.getSlot(0)
        assertNotNull(restoredSlot)
        assertEquals("Inception", restoredSlot?.title)

        // 12. Verify SequelBackupParser stream parsing
        val parsedItems = backupParser.parseStream(ByteArrayInputStream(backupJson.toByteArray()))
        assertTrue(parsedItems.isNotEmpty())
        assertTrue(parsedItems.any { it.title == "Inception" && it.recordType == ImportRecordType.WATCHED })
        assertTrue(parsedItems.any { it.title == "Severance" && it.recordType == ImportRecordType.WATCHED })
    }

    @Test(expected = IllegalArgumentException::class)
    fun restoreInvalidJson_throwsIllegalArgumentException() = runBlocking {
        backupService.restoreJsonBackup("{ not valid json }")
        Unit
    }

    @Test(expected = IllegalArgumentException::class)
    fun restoreFutureSchemaVersion_throwsIllegalArgumentException() = runBlocking {
        val futureJson = """{"schemaVersion": 999, "exportedAt": 12345}"""
        backupService.restoreJsonBackup(futureJson)
        Unit
    }
}
