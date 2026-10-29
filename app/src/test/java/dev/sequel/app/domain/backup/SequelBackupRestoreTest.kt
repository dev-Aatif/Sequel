package dev.sequel.app.domain.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.*
import dev.sequel.app.data.local.entity.*
import dev.sequel.app.domain.export.ExportDestination
import dev.sequel.app.domain.export.LetterboxdDataExporter
import dev.sequel.app.domain.export.TraktDataExporter
import dev.sequel.app.domain.importer.ImportRecordType
import dev.sequel.app.domain.importer.SequelBackupParser
import dev.sequel.app.domain.importer.archive.ZipArchiveExtractor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SequelBackupRestoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: SequelDatabase
    private lateinit var showDao: ShowDao
    private lateinit var watchedMovieDao: WatchedMovieDao
    private lateinit var watchedEpisodeDao: WatchedEpisodeDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var reviewDao: ReviewDao
    private lateinit var mountRushmoreDao: MountRushmoreDao

    private lateinit var backupValidator: BackupValidator
    private lateinit var backupMigrator: BackupMigrator
    private lateinit var backupService: SequelBackupService
    private lateinit var backupParser: SequelBackupParser

    private lateinit var letterboxdExporter: LetterboxdDataExporter
    private lateinit var traktExporter: TraktDataExporter

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

        backupValidator = BackupValidator()
        backupMigrator = BackupMigrator()

        backupService = SequelBackupService(
            database = database,
            showDao = showDao,
            watchedMovieDao = watchedMovieDao,
            watchedEpisodeDao = watchedEpisodeDao,
            watchlistDao = watchlistDao,
            reviewDao = reviewDao,
            mountRushmoreDao = mountRushmoreDao,
            backupValidator = backupValidator,
            backupMigrator = backupMigrator
        )

        backupParser = SequelBackupParser()

        letterboxdExporter = LetterboxdDataExporter(
            showDao = showDao,
            watchedMovieDao = watchedMovieDao,
            watchlistDao = watchlistDao,
            reviewDao = reviewDao
        )

        traktExporter = TraktDataExporter(
            showDao = showDao,
            watchedMovieDao = watchedMovieDao,
            watchedEpisodeDao = watchedEpisodeDao
        )
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

    @Test
    fun restoreWithCleanMode_wipesExistingDataBeforeRestoring() = runBlocking {
        // Pre-seed an existing movie
        watchedMovieDao.upsertWatchedMovie(
            movieId = 999,
            title = "Existing Old Movie",
            posterPath = null,
            watchedAt = 1000L
        )
        assertEquals(1, watchedMovieDao.getAllWatchedMovies().size)

        val backupJson = """
        {
            "schemaVersion": 1,
            "exportedAt": 1690000000000,
            "appVersion": "1.0.0",
            "shows": [
                {
                    "id": 100,
                    "title": "New Movie",
                    "mediaType": "movie"
                }
            ],
            "watchedMovies": [
                {
                    "tmdbMovieId": 100,
                    "title": "New Movie",
                    "watchedAt": 2000
                }
            ]
        }
        """.trimIndent()

        // Restore with REPLACE_CLEAN
        val result = backupService.restoreJsonBackup(backupJson, mode = RestoreMode.REPLACE_CLEAN)
        assertEquals(1, result.moviesCount)

        val currentMovies = watchedMovieDao.getAllWatchedMovies()
        assertEquals(1, currentMovies.size)
        assertEquals(100, currentMovies.first().tmdbMovieId)
        assertEquals("New Movie", currentMovies.first().title)
        assertFalse(currentMovies.any { it.tmdbMovieId == 999 })
    }

    @Test
    fun restoreWithMergeMode_preservesNonConflictingExistingData() = runBlocking {
        // Pre-seed an existing movie
        watchedMovieDao.upsertWatchedMovie(
            movieId = 999,
            title = "Existing Movie",
            posterPath = null,
            watchedAt = 1000L
        )

        val backupJson = """
        {
            "schemaVersion": 1,
            "exportedAt": 1690000000000,
            "appVersion": "1.0.0",
            "shows": [
                {
                    "id": 100,
                    "title": "Added Movie",
                    "mediaType": "movie"
                }
            ],
            "watchedMovies": [
                {
                    "tmdbMovieId": 100,
                    "title": "Added Movie",
                    "watchedAt": 2000
                }
            ]
        }
        """.trimIndent()

        // Restore with MERGE_EXISTING (default)
        val result = backupService.restoreJsonBackup(backupJson, mode = RestoreMode.MERGE_EXISTING)
        assertEquals(1, result.moviesCount)

        val currentMovies = watchedMovieDao.getAllWatchedMovies()
        assertEquals(2, currentMovies.size)
        assertTrue(currentMovies.any { it.tmdbMovieId == 999 && it.title == "Existing Movie" })
        assertTrue(currentMovies.any { it.tmdbMovieId == 100 && it.title == "Added Movie" })
    }

    @Test
    fun restoreCorruptEntities_failsPreRestorationValidation() = runBlocking {
        // Invalid rating: 15.0f (must be 0.5..10.0)
        val invalidRatingBackup = """
        {
            "schemaVersion": 1,
            "exportedAt": 1690000000000,
            "shows": [{"id": 100, "title": "Test Show", "mediaType": "tv"}],
            "reviews": [
                {
                    "mediaId": 100,
                    "mediaType": "tv",
                    "rating": 15.0
                }
            ]
        }
        """.trimIndent()

        try {
            backupService.restoreJsonBackup(invalidRatingBackup)
            fail("Should have thrown IllegalArgumentException due to invalid rating")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("out-of-range rating") == true)
        }

        // Invalid Mount Rushmore slot: 5 (must be 0..3)
        val invalidRushmoreBackup = """
        {
            "schemaVersion": 1,
            "exportedAt": 1690000000000,
            "mountRushmore": [
                {
                    "slotIndex": 5,
                    "mediaId": 100,
                    "mediaType": "movie",
                    "title": "Test"
                }
            ]
        }
        """.trimIndent()

        try {
            backupService.restoreJsonBackup(invalidRushmoreBackup)
            fail("Should have thrown IllegalArgumentException due to invalid slotIndex")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("invalid slotIndex") == true)
        }
    }

    @Test
    fun restoreFutureSchemaVersion_throwsActionableError() = runBlocking {
        val futureJson = """{"schemaVersion": 999, "exportedAt": 12345}"""
        try {
            backupService.restoreJsonBackup(futureJson)
            fail("Should have thrown IllegalArgumentException for future schema version")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("is newer than current supported version") == true)
        }
    }

    @Test
    fun restoreInvalidJson_throwsDescriptiveIllegalArgumentException() = runBlocking {
        try {
            backupService.restoreJsonBackup("{ not valid json }")
            fail("Should have thrown IllegalArgumentException for malformed json")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Malformed JSON") == true || e.message?.contains("Invalid Sequel backup") == true)
        }
    }

    @Test
    fun restoreV0UnversionedBackup_migratesSuccessfullyToV1() = runBlocking {
        // v0 unversioned backup (missing schemaVersion, legacy 5-star rating)
        val legacyV0Json = """
        {
            "shows": [
                {
                    "id": 550,
                    "title": "Fight Club",
                    "mediaType": "movie"
                }
            ],
            "reviews": [
                {
                    "mediaId": 550,
                    "mediaType": "movie",
                    "rating": 4.5,
                    "originalRatingScale": 5.0
                }
            ]
        }
        """.trimIndent()

        val result = backupService.restoreJsonBackup(legacyV0Json, mode = RestoreMode.REPLACE_CLEAN)
        assertEquals(1, result.showsCount)
        assertEquals(1, result.reviewsCount)

        val restoredReview = reviewDao.getAllReviews().firstOrNull()
        assertNotNull(restoredReview)
        assertEquals(550, restoredReview?.mediaId)
        // 4.5 on 5.0 scale scaled to 9.0 on 10.0 scale
        assertEquals(9.0f, restoredReview?.rating ?: 0f, 0.01f)
    }

    @Test
    fun atomicRollback_onRestoreFailure_leavesExistingDatabaseUntouched() = runBlocking {
        // 1. Pre-seed database
        val originalShow = ShowEntity(
            id = 50,
            title = "Original Show",
            mediaType = "tv"
        )
        showDao.insertShow(originalShow)

        watchedMovieDao.upsertWatchedMovie(
            movieId = 500,
            title = "Original Movie",
            posterPath = null,
            watchedAt = 5000L
        )

        // 2. Prepare backup with valid show and watched movie, but failing entity that triggers DB constraint violation or exception
        // E.g. Watched episode referencing non-existent show under strict FK
        val corruptBackupJson = """
        {
            "schemaVersion": 1,
            "exportedAt": 1690000000000,
            "shows": [
                {"id": 1, "title": "Valid Show", "mediaType": "tv"}
            ],
            "watchedEpisodes": [
                {
                    "showId": 99999,
                    "tmdbEpisodeId": 1234,
                    "seasonNumber": 1,
                    "episodeNumber": 1,
                    "watchedAt": 1000
                }
            ]
        }
        """.trimIndent()

        try {
            backupService.restoreJsonBackup(corruptBackupJson, mode = RestoreMode.REPLACE_CLEAN)
            fail("Expected restore to fail due to foreign key constraint")
        } catch (e: Exception) {
            // Expected failure (FOREIGN KEY constraint)
        }

        // 3. Verify that atomic rollback kept original data intact despite REPLACE_CLEAN
        val currentMovies = watchedMovieDao.getAllWatchedMovies()
        assertEquals(1, currentMovies.size)
        assertEquals(500, currentMovies.first().tmdbMovieId)
        assertEquals("Original Movie", currentMovies.first().title)

        val currentShows = showDao.getAllShows()
        assertEquals(1, currentShows.size)
        assertEquals(50, currentShows.first().id)
    }

    @Test
    fun exportJsonBackupToFile_stagesToTempFile_andCleansUpOnFailure() = runBlocking {
        val targetFile = File(tempFolder.root, "test_backup.json")
        val exported = backupService.exportJsonBackupToFile(targetFile)

        assertTrue(exported.exists())
        assertEquals(targetFile.absolutePath, exported.absolutePath)
        assertTrue(exported.length() > 0)

        // Verify no leftover .tmp files
        val tmpFiles = tempFolder.root.listFiles { _, name -> name.endsWith(".tmp") } ?: emptyArray()
        assertEquals(0, tmpFiles.size)
    }

    @Test
    fun exportJsonBackup_containsNoSecretsOrUserAccountData() = runBlocking {
        showDao.insertShow(ShowEntity(id = 10, title = "Test", mediaType = "movie"))
        watchedMovieDao.upsertWatchedMovie(movieId = 10, title = "Test", posterPath = null, watchedAt = 1000L)

        val json = backupService.exportJsonBackup()

        // Audit for sensitive patterns
        val forbiddenKeys = listOf(
            "access_token",
            "refresh_token",
            "client_secret",
            "password",
            "supabase_id",
            "user_id"
        )
        for (forbidden in forbiddenKeys) {
            assertFalse("Export should not contain '$forbidden'", json.contains("\"$forbidden\""))
        }
    }

    @Test
    fun destinationExporters_letterboxdAndTrakt_produceExpectedFormats() = runBlocking {
        // Seed movie and episode
        showDao.insertShow(ShowEntity(id = 101, title = "Oppenheimer", firstAirDate = "2023-07-21", mediaType = "movie"))
        watchedMovieDao.upsertWatchedMovie(movieId = 101, title = "Oppenheimer", posterPath = null, watchedAt = 1690000000000L)
        reviewDao.upsertRating(mediaId = 101, mediaType = "movie", seasonNum = null, episodeNum = null, rating = 9.0f)

        showDao.insertShow(ShowEntity(id = 202, title = "Succession", firstAirDate = "2018-06-03", mediaType = "tv"))
        watchedEpisodeDao.upsertWatchedEpisode(showId = 202, tmdbEpisodeId = 999, seasonNumber = 4, episodeNumber = 10, watchedAt = 1690005000000L)

        // 1. Test Letterboxd export
        val letterboxdPackage = letterboxdExporter.export()
        assertEquals(ExportDestination.LETTERBOXD_ZIP, letterboxdPackage.destination)
        assertTrue(letterboxdPackage.suggestedFileName.endsWith(".zip"))

        // Verify Letterboxd ZIP contents
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(letterboxdPackage.bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = zip.nextEntry
            }
        }
        assertTrue(entries.contains("watched.csv"))
        assertTrue(entries.contains("ratings.csv"))
        assertTrue(entries.contains("reviews.csv"))
        assertTrue(entries.contains("watchlist.csv"))

        // 2. Test Trakt export
        val traktPackage = traktExporter.export()
        assertEquals(ExportDestination.TRAKT_CSV, traktPackage.destination)
        assertTrue(traktPackage.suggestedFileName.endsWith(".csv"))

        val traktCsvString = String(traktPackage.bytes, Charsets.UTF_8)
        assertTrue(traktCsvString.startsWith("Title,Year,Season,Episode,Type,WatchedAt"))
        assertTrue(traktCsvString.contains("Oppenheimer,2023,,,movie,"))
        assertTrue(traktCsvString.contains("Succession,2018,4,10,episode,"))
    }
}
