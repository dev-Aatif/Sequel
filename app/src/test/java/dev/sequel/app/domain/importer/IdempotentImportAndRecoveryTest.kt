package dev.sequel.app.domain.importer

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.*
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.dto.TmdbPagedResponse
import dev.sequel.app.data.remote.tmdb.dto.TmdbShowDto
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.domain.importer.adapter.*
import dev.sequel.app.domain.importer.checkpoint.CheckpointStatus
import dev.sequel.app.domain.importer.checkpoint.ImportCheckpointManager
import dev.sequel.app.domain.importer.conflict.ConflictAnalyzer
import dev.sequel.app.domain.importer.coordinator.ImportApplier
import dev.sequel.app.domain.importer.coordinator.ImportCoordinator
import dev.sequel.app.domain.importer.core.*
import dev.sequel.app.domain.importer.detector.SourceDetector
import dev.sequel.app.domain.importer.normalizer.ImportNormalizer
import dev.sequel.app.domain.importer.resolver.IdentityResolver
import dev.sequel.app.domain.importer.resolver.ResolvedMediaRecord
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class IdempotentImportAndRecoveryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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
    private lateinit var checkpointManager: ImportCheckpointManager
    private lateinit var normalizer: ImportNormalizer
    private lateinit var resolver: IdentityResolver
    private lateinit var conflictAnalyzer: ConflictAnalyzer
    private lateinit var importApplier: ImportApplier
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

        checkpointManager = ImportCheckpointManager(context).apply {
            customDirectory = tempFolder.newFolder("checkpoints")
        }

        // Mock TMDB responses
        coEvery { tmdbApiService.searchMovie("Inception") } returns TmdbPagedResponse(
            page = 1,
            results = listOf(
                TmdbShowDto(
                    id = 27205,
                    title = "Inception",
                    overview = "Dream within a dream",
                    firstAirDate = "2010-07-15",
                    voteAverage = 8.4,
                    voteCount = 35000,
                    popularity = 90.0,
                    genreIds = emptyList(),
                    mediaType = "movie"
                )
            ),
            totalPages = 1,
            totalResults = 1
        )

        coEvery { tmdbApiService.searchMovie("Interstellar") } returns TmdbPagedResponse(
            page = 1,
            results = listOf(
                TmdbShowDto(
                    id = 157336,
                    title = "Interstellar",
                    overview = "Space exploration",
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

        coEvery { tmdbApiService.searchTv("Breaking Bad") } returns TmdbPagedResponse(
            page = 1,
            results = listOf(
                TmdbShowDto(
                    id = 1396,
                    name = "Breaking Bad",
                    overview = "Chemistry teacher turns kingpin",
                    firstAirDate = "2008-01-20",
                    voteAverage = 9.2,
                    voteCount = 12000,
                    popularity = 120.0,
                    genreIds = emptyList(),
                    mediaType = "tv"
                )
            ),
            totalPages = 1,
            totalResults = 1
        )

        normalizer = ImportNormalizer()
        resolver = IdentityResolver(showDao, tmdbApiService)
        conflictAnalyzer = ConflictAnalyzer(watchedMovieDao, watchedEpisodeDao, watchlistDao, reviewDao)

        importApplier = ImportApplier(
            database = database,
            showDao = showDao,
            seasonDao = seasonDao,
            episodeDao = episodeDao,
            watchedMovieDao = watchedMovieDao,
            watchedEpisodeDao = watchedEpisodeDao,
            watchlistDao = watchlistDao,
            reviewDao = reviewDao,
            tmdbApiService = tmdbApiService,
            syncManager = syncManager,
            checkpointManager = checkpointManager
        )

        coordinator = ImportCoordinator(
            sourceDetector = SourceDetector(),
            normalizer = normalizer,
            identityResolver = resolver,
            conflictAnalyzer = conflictAnalyzer,
            importApplier = importApplier,
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

    // ── 1. Full Idempotency Across All Conflict Policies ─────────────────────
    @Test
    fun test01_idempotentImport_reimportingMovieDataset_leavesDatabaseAndCountsConsistent() = runBlocking {
        val letterboxdCsv = """
            Date,Name,Year,Letterboxd URI,Rating
            2023-01-01,"Inception",2010,https://boxd.it/157336,4.5
            2023-02-01,"Interstellar",2014,https://boxd.it/27205,5.0
        """.trimIndent()

        // 1st Import: KEEP_EXISTING
        val report1 = coordinator.executeImport(
            inputStream = ByteArrayInputStream(letterboxdCsv.toByteArray(StandardCharsets.UTF_8)),
            filenameHint = "watched.csv",
            policy = ConflictResolutionPolicy.KEEP_EXISTING
        )
        assertEquals(2, report1.totalProcessed)
        assertEquals(2, report1.insertedCount)
        assertEquals(0, report1.skippedIdenticalCount)
        assertEquals(2, watchedMovieDao.getAllWatchedMovies().size)

        // 2nd Import: KEEP_EXISTING (Re-importing exact same dataset)
        val report2 = coordinator.executeImport(
            inputStream = ByteArrayInputStream(letterboxdCsv.toByteArray(StandardCharsets.UTF_8)),
            filenameHint = "watched.csv",
            policy = ConflictResolutionPolicy.KEEP_EXISTING
        )
        assertEquals(2, report2.totalProcessed)
        assertEquals(0, report2.insertedCount)
        assertEquals(2, report2.skippedIdenticalCount)
        assertEquals(2, watchedMovieDao.getAllWatchedMovies().size)

        // 3rd Import: OVERWRITE_LOCAL (Re-importing under overwrite)
        val report3 = coordinator.executeImport(
            inputStream = ByteArrayInputStream(letterboxdCsv.toByteArray(StandardCharsets.UTF_8)),
            filenameHint = "watched.csv",
            policy = ConflictResolutionPolicy.OVERWRITE_LOCAL
        )
        assertEquals(2, report3.totalProcessed)
        assertEquals(0, report3.insertedCount)
        assertEquals(2, report3.updatedCount)
        assertEquals(2, watchedMovieDao.getAllWatchedMovies().size)

        // 4th Import: MERGE_ADDITIVE
        val report4 = coordinator.executeImport(
            inputStream = ByteArrayInputStream(letterboxdCsv.toByteArray(StandardCharsets.UTF_8)),
            filenameHint = "watched.csv",
            policy = ConflictResolutionPolicy.MERGE_ADDITIVE
        )
        assertEquals(2, report4.totalProcessed)
        assertEquals(0, report4.insertedCount)
        assertEquals(2, report4.skippedIdenticalCount)
        assertEquals(2, watchedMovieDao.getAllWatchedMovies().size)
    }

    // ── 2. TV Episode Watched Idempotency ────────────────────────────────────
    @Test
    fun test02_idempotentImport_tvEpisodeWatches_neverCreateDuplicateEpisodes() = runBlocking {
        val tvTimeCsv = """
            episode_id,show_name,season_number,episode_number,updated_at
            101,"Breaking Bad",1,1,2023-01-10 12:00:00
            102,"Breaking Bad",1,2,2023-01-11 12:00:00
        """.trimIndent()

        // 1st Import
        val report1 = coordinator.executeImport(
            inputStream = ByteArrayInputStream(tvTimeCsv.toByteArray(StandardCharsets.UTF_8)),
            filenameHint = "tracking-prod-records.csv",
            policy = ConflictResolutionPolicy.KEEP_EXISTING
        )
        assertEquals(2, report1.insertedCount)
        assertEquals(2, watchedEpisodeDao.getAllWatchedEpisodes().size)

        // 2nd Import
        val report2 = coordinator.executeImport(
            inputStream = ByteArrayInputStream(tvTimeCsv.toByteArray(StandardCharsets.UTF_8)),
            filenameHint = "tracking-prod-records.csv",
            policy = ConflictResolutionPolicy.KEEP_EXISTING
        )
        assertEquals(0, report2.insertedCount)
        assertEquals(2, report2.skippedIdenticalCount)
        assertEquals(2, watchedEpisodeDao.getAllWatchedEpisodes().size)

        // Assert distinct deterministic episode identifiers
        val episodes = watchedEpisodeDao.getAllWatchedEpisodes()
        assertEquals(2, episodes.map { it.tmdbEpisodeId }.distinct().size)
    }

    // ── 3. Checkpoint & Interrupted Import Resumption ─────────────────────────
    @Test
    fun test03_checkpointAndRecovery_simulatedProcessInterruption_resumesFromExactIndex() = runBlocking {
        // Create 4 distinct records
        val records = listOf(
            ResolvedMediaRecord(
                originalRecord = CanonicalImportRecord(
                    source = ImportSource.LETTERBOXD,
                    recordType = ImportRecordType.WATCHED,
                    rawTitle = "Inception",
                    normalizedTitle = "Inception",
                    year = 2010,
                    mediaType = "movie",
                    watchedAt = 1000L
                ),
                tmdbId = 27205,
                resolvedTitle = "Inception",
                confidence = MatchConfidence.EXACT_ID
            ),
            ResolvedMediaRecord(
                originalRecord = CanonicalImportRecord(
                    source = ImportSource.LETTERBOXD,
                    recordType = ImportRecordType.WATCHED,
                    rawTitle = "Interstellar",
                    normalizedTitle = "Interstellar",
                    year = 2014,
                    mediaType = "movie",
                    watchedAt = 2000L
                ),
                tmdbId = 157336,
                resolvedTitle = "Interstellar",
                confidence = MatchConfidence.EXACT_ID
            ),
            ResolvedMediaRecord(
                originalRecord = CanonicalImportRecord(
                    source = ImportSource.LETTERBOXD,
                    recordType = ImportRecordType.WATCHED,
                    rawTitle = "Movie 3",
                    normalizedTitle = "Movie 3",
                    year = 2020,
                    mediaType = "movie",
                    watchedAt = 3000L
                ),
                tmdbId = 30001,
                resolvedTitle = "Movie 3",
                confidence = MatchConfidence.EXACT_ID
            ),
            ResolvedMediaRecord(
                originalRecord = CanonicalImportRecord(
                    source = ImportSource.LETTERBOXD,
                    recordType = ImportRecordType.WATCHED,
                    rawTitle = "Movie 4",
                    normalizedTitle = "Movie 4",
                    year = 2021,
                    mediaType = "movie",
                    watchedAt = 4000L
                ),
                tmdbId = 30002,
                resolvedTitle = "Movie 4",
                confidence = MatchConfidence.EXACT_ID
            )
        )

        val fingerprint = "test_interrupted_fingerprint"

        // Step A: Process only first batch of 2 records and save checkpoint as if interrupted
        val firstBatch = records.take(2)
        importApplier.apply(
            source = ImportSource.LETTERBOXD,
            resolvedRecords = firstBatch,
            policy = ConflictResolutionPolicy.KEEP_EXISTING,
            fingerprint = fingerprint,
            batchSize = 2
        )

        // Manually configure checkpoint to represent process termination at index 2 out of 4
        val simulatedInterruptedCheckpoint = dev.sequel.app.domain.importer.checkpoint.ImportCheckpoint(
            importId = "interrupted_import_id",
            source = ImportSource.LETTERBOXD.name,
            fingerprint = fingerprint,
            totalRecords = 4,
            processedIndex = 2,
            insertedCount = 2,
            updatedCount = 0,
            skippedCount = 0,
            failedCount = 0,
            policy = ConflictResolutionPolicy.KEEP_EXISTING.name,
            status = CheckpointStatus.IN_PROGRESS
        )
        checkpointManager.saveCheckpoint(simulatedInterruptedCheckpoint)

        val savedCheckpoint = checkpointManager.getCheckpoint(fingerprint)
        assertNotNull(savedCheckpoint)
        assertEquals(CheckpointStatus.IN_PROGRESS, savedCheckpoint!!.status)
        assertEquals(2, savedCheckpoint.processedIndex)

        // Step B: Resume the import with the full list of 4 records
        val resumedReport = importApplier.apply(
            source = ImportSource.LETTERBOXD,
            resolvedRecords = records,
            policy = ConflictResolutionPolicy.KEEP_EXISTING,
            fingerprint = fingerprint,
            resumeFromCheckpoint = true,
            batchSize = 2
        )

        assertEquals(4, resumedReport.totalProcessed)
        assertEquals(4, resumedReport.insertedCount)
        assertEquals(0, resumedReport.failedCount)

        // Final checkpoint status should now be COMPLETED
        val completedCheckpoint = checkpointManager.getCheckpoint(fingerprint)
        assertNotNull(completedCheckpoint)
        assertEquals(CheckpointStatus.COMPLETED, completedCheckpoint!!.status)
        assertEquals(4, completedCheckpoint.processedIndex)

        // Database must have all 4 movies and zero duplicates
        assertEquals(4, watchedMovieDao.getAllWatchedMovies().size)
    }

    // ── 4. Partial Import Re-import Without Checkpoint Is Safe ────────────────
    @Test
    fun test04_partialImportRecovery_reimportWithoutCheckpoint_isIdempotent() = runBlocking {
        // Pre-seed 1st movie in DB (simulating an earlier partial batch success)
        watchedMovieDao.upsertWatchedMovie(
            movieId = 27205,
            title = "Inception",
            posterPath = null,
            watchedAt = 1672531200000L
        )
        assertEquals(1, watchedMovieDao.getAllWatchedMovies().size)

        val letterboxdCsv = """
            Date,Name,Year,Letterboxd URI
            2023-01-01,"Inception",2010,https://boxd.it/157336
            2023-02-01,"Interstellar",2014,https://boxd.it/27205
        """.trimIndent()

        // Reimport the full 2-item dataset
        val report = coordinator.executeImport(
            inputStream = ByteArrayInputStream(letterboxdCsv.toByteArray(StandardCharsets.UTF_8)),
            filenameHint = "watched.csv",
            policy = ConflictResolutionPolicy.KEEP_EXISTING,
            resumeFromCheckpoint = false
        )

        // Inception was existing -> skipped; Interstellar was new -> inserted
        assertEquals(2, report.totalProcessed)
        assertEquals(1, report.insertedCount)
        assertEquals(1, report.skippedIdenticalCount)
        assertEquals(2, watchedMovieDao.getAllWatchedMovies().size)
    }

    // ── 5. Network Timeout & Transient 429 Recovery with Retry ────────────────
    @Test
    fun test05_networkTimeoutRecovery_transientHttp429AndIoException_retriesAndSucceeds() = runBlocking {
        val duneRecord = CanonicalImportRecord(
            source = ImportSource.LETTERBOXD,
            recordType = ImportRecordType.WATCHED,
            rawTitle = "Dune",
            normalizedTitle = "Dune",
            year = 2021,
            mediaType = "movie"
        )

        var callCount = 0
        coEvery { tmdbApiService.searchMovie("Dune") } answers {
            callCount++
            when (callCount) {
                1 -> {
                    val errorBody = "Rate limited".toResponseBody(null)
                    val response = Response.error<TmdbPagedResponse<TmdbShowDto>>(429, errorBody)
                    throw HttpException(response)
                }
                2 -> {
                    throw IOException("Socket timeout connecting to api.themoviedb.org")
                }
                else -> {
                    TmdbPagedResponse(
                        page = 1,
                        results = listOf(
                            TmdbShowDto(
                                id = 438631,
                                title = "Dune",
                                overview = "Paul Atreides leads nomadic tribes...",
                                firstAirDate = "2021-09-15",
                                voteAverage = 7.9,
                                voteCount = 10000,
                                popularity = 80.0,
                                genreIds = emptyList(),
                                mediaType = "movie"
                            )
                        ),
                        totalPages = 1,
                        totalResults = 1
                    )
                }
            }
        }

        val resolved = resolver.resolve(duneRecord)

        // Verified that withRetry retried across HTTP 429 and IOException and resolved successfully
        assertEquals(438631, resolved.tmdbId)
        assertEquals("Dune", resolved.resolvedTitle)
        assertTrue(resolved.confidence != MatchConfidence.UNRESOLVED)
        assertTrue(callCount >= 3)
    }

    // ── 6. Foreign Key Safety For Ratings, Reviews, and Episodes ──────────────
    @Test
    fun test06_foreignKeySafety_reviewsAndEpisodesWithoutPreseededShows_neverFailConstraint() = runBlocking {
        // Ensure shows table is completely empty
        assertEquals(0, showDao.getAllShows().size)

        // 1. Apply a movie rating & review
        val movieRecord = ResolvedMediaRecord(
            originalRecord = CanonicalImportRecord(
                source = ImportSource.LETTERBOXD,
                recordType = ImportRecordType.REVIEW,
                rawTitle = "Inception",
                normalizedTitle = "Inception",
                year = 2010,
                mediaType = "movie",
                rating = 9.0f,
                reviewText = "Mind-bending masterwork"
            ),
            tmdbId = 27205,
            resolvedTitle = "Inception",
            confidence = MatchConfidence.EXACT_ID
        )

        // 2. Apply a TV episode watch
        val tvRecord = ResolvedMediaRecord(
            originalRecord = CanonicalImportRecord(
                source = ImportSource.TV_TIME,
                recordType = ImportRecordType.WATCHED,
                rawTitle = "Breaking Bad",
                normalizedTitle = "Breaking Bad",
                year = 2008,
                mediaType = "tv",
                seasonNumber = 1,
                episodeNumber = 1,
                watchedAt = 1672531200000L
            ),
            tmdbId = 1396,
            resolvedTitle = "Breaking Bad",
            confidence = MatchConfidence.EXACT_ID
        )

        val report = importApplier.apply(
            source = ImportSource.LETTERBOXD,
            resolvedRecords = listOf(movieRecord, tvRecord),
            policy = ConflictResolutionPolicy.KEEP_EXISTING
        )

        // Zero database constraint failures
        assertEquals(0, report.failedCount)
        assertEquals(2, report.insertedCount)

        // Verify parent ShowEntity was hydrated into shows table for both
        assertNotNull(showDao.getShowById(27205, "movie"))
        assertNotNull(showDao.getShowById(1396, "tv"))

        // Verify child records safely committed
        assertEquals(1, reviewDao.getAllReviews().size)
        assertEquals(1, watchedEpisodeDao.getAllWatchedEpisodes().size)
    }

    // ── 7. Mathematical Consistency of ImportReport Metrics ───────────────────
    @Test
    fun test07_mathematicalConsistency_reportCountsDistinguishAllCategories() = runBlocking {
        // Pre-seed Inception so it becomes "skipped" under KEEP_EXISTING
        watchedMovieDao.upsertWatchedMovie(
            movieId = 27205,
            title = "Inception",
            posterPath = null,
            watchedAt = 1000L
        )

        // Mock unresolvable title
        coEvery { tmdbApiService.searchMovie("NonExistentMovie999") } returns TmdbPagedResponse(
            page = 1,
            results = emptyList(),
            totalPages = 0,
            totalResults = 0
        )

        // CSV containing:
        // - 1 existing movie (Inception -> skipped)
        // - 1 new movie (Interstellar -> inserted)
        // - 1 duplicate row inside the source file (duplicate Inception -> duplicateInSource)
        // - 1 unresolvable title (NonExistentMovie999 -> unresolvable / failed)
        val csv = """
            Date,Name,Year,Letterboxd URI
            2023-01-01,"Inception",2010,https://boxd.it/27205
            2023-01-01,"Inception",2010,https://boxd.it/27205
            2023-02-01,"Interstellar",2014,https://boxd.it/157336
            2023-03-01,"NonExistentMovie999",2025,https://boxd.it/99999
        """.trimIndent()

        val report = coordinator.executeImport(
            inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)),
            filenameHint = "watched.csv",
            policy = ConflictResolutionPolicy.KEEP_EXISTING
        )

        // 4 total parsed lines in CSV
        assertEquals(4, report.totalProcessed)
        assertEquals(1, report.insertedCount)          // Interstellar
        assertEquals(1, report.skippedIdenticalCount)  // Inception
        assertEquals(1, report.duplicateInSourceCount) // duplicate Inception row
        assertEquals(1, report.failedCount)            // NonExistentMovie999 unresolvable
        assertEquals(1, report.unresolvableCount)

        // Strict mathematical identity across outcomes
        val sumOfComponents = report.insertedCount +
                report.updatedCount +
                report.skippedIdenticalCount +
                report.duplicateInSourceCount +
                report.failedCount

        assertEquals(report.totalProcessed, sumOfComponents)
    }
}
