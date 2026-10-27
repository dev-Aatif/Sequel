package dev.sequel.app.domain.importer.coordinator

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.*
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.dto.TmdbPagedResponse
import dev.sequel.app.data.remote.tmdb.dto.TmdbShowDto
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.domain.backup.SequelBackupService
import dev.sequel.app.domain.importer.adapter.*
import dev.sequel.app.domain.importer.conflict.ConflictAnalyzer
import dev.sequel.app.domain.importer.core.ConflictResolutionPolicy
import dev.sequel.app.domain.importer.core.ImportSource
import dev.sequel.app.domain.importer.detector.SourceDetector
import dev.sequel.app.domain.importer.normalizer.ImportNormalizer
import dev.sequel.app.domain.importer.resolver.IdentityResolver
import io.mockk.coEvery
import io.mockk.mockk
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
class ImportCoordinatorTest {

    private lateinit var database: SequelDatabase
    private lateinit var showDao: ShowDao
    private lateinit var watchedMovieDao: WatchedMovieDao
    private lateinit var watchedEpisodeDao: WatchedEpisodeDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var reviewDao: ReviewDao
    private lateinit var seasonDao: SeasonDao
    private lateinit var episodeDao: EpisodeDao

    private lateinit var tmdbApiService: TmdbApiService
    private lateinit var syncManager: SyncManager
    private lateinit var coordinator: ImportCoordinator

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
        seasonDao = database.seasonDao()
        episodeDao = database.episodeDao()

        tmdbApiService = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)

        coEvery { tmdbApiService.searchMovie(any()) } returns TmdbPagedResponse(
            page = 1,
            results = listOf(
                TmdbShowDto(
                    id = 157336,
                    title = "Interstellar",
                    overview = "Mankind was born on Earth...",
                    firstAirDate = "2014-11-05",
                    voteAverage = 8.6,
                    voteCount = 30000,
                    popularity = 95.0,
                    genreIds = emptyList(),
                    mediaType = "movie"
                )
            ),
            totalPages = 1,
            totalResults = 1
        )

        val detector = SourceDetector()
        val normalizer = ImportNormalizer()
        val resolver = IdentityResolver(showDao, tmdbApiService)
        val conflictAnalyzer = ConflictAnalyzer(watchedMovieDao, watchedEpisodeDao, watchlistDao, reviewDao)
        val applier = ImportApplier(
            database = database,
            showDao = showDao,
            seasonDao = seasonDao,
            episodeDao = episodeDao,
            watchedMovieDao = watchedMovieDao,
            watchedEpisodeDao = watchedEpisodeDao,
            watchlistDao = watchlistDao,
            reviewDao = reviewDao,
            tmdbApiService = tmdbApiService,
            syncManager = syncManager
        )

        val backupService = SequelBackupService(
            database = database,
            showDao = showDao,
            watchedMovieDao = watchedMovieDao,
            watchedEpisodeDao = watchedEpisodeDao,
            watchlistDao = watchlistDao,
            reviewDao = reviewDao,
            mountRushmoreDao = database.mountRushmoreDao()
        )

        coordinator = ImportCoordinator(
            sourceDetector = detector,
            normalizer = normalizer,
            identityResolver = resolver,
            conflictAnalyzer = conflictAnalyzer,
            importApplier = applier,
            tvTimeAdapter = TvTimeSourceAdapter(),
            traktAdapter = TraktSourceAdapter(),
            letterboxdAdapter = LetterboxdSourceAdapter(),
            sequelBackupAdapter = SequelBackupSourceAdapter()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun endToEnd_letterboxdPreviewAndImportExecution() = runBlocking {
        val letterboxdCsv = """
            Date,Name,Year,Letterboxd URI,Rating,Review,Spoiler
            2023-12-01,"Interstellar",2014,https://boxd.it/157336,4.5,"Masterpiece in sound and scope",False
        """.trimIndent()

        val previewStream = ByteArrayInputStream(letterboxdCsv.toByteArray(StandardCharsets.UTF_8))
        val preview = coordinator.generatePreview(
            inputStream = previewStream,
            filenameHint = "reviews.csv"
        )

        assertEquals(ImportSource.LETTERBOXD, preview.source)
        assertEquals(1, preview.totalRecordsParsed)
        assertEquals(1, preview.newReviewsCount)
        assertEquals(0, preview.identicalSkippedCount)

        // Execute import with KEEP_EXISTING
        val importStream = ByteArrayInputStream(letterboxdCsv.toByteArray(StandardCharsets.UTF_8))
        val report = coordinator.executeImport(
            inputStream = importStream,
            filenameHint = "reviews.csv",
            policy = ConflictResolutionPolicy.KEEP_EXISTING
        )

        assertEquals(1, report.totalProcessed)
        assertTrue(report.insertedCount >= 1)
        assertEquals(0, report.failedCount)

        // Verify write landed in reviewDao
        val reviews = reviewDao.getAllReviews()
        assertEquals(1, reviews.size)
        assertEquals(157336, reviews[0].mediaId)
        assertEquals("Masterpiece in sound and scope", reviews[0].reviewText)
        assertEquals(9.0f, reviews[0].rating ?: 0f, 0.01f) // 4.5 * 2 = 9.0
    }
}
