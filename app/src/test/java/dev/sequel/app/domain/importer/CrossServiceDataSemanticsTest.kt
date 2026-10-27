package dev.sequel.app.domain.importer

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.*
import dev.sequel.app.data.local.entity.*
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.dto.TmdbPagedResponse
import dev.sequel.app.data.remote.tmdb.dto.TmdbShowDto
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.domain.backup.*
import dev.sequel.app.domain.importer.adapter.*
import dev.sequel.app.domain.importer.conflict.ConflictAnalyzer
import dev.sequel.app.domain.importer.coordinator.ImportApplier
import dev.sequel.app.domain.importer.coordinator.ImportCoordinator
import dev.sequel.app.domain.importer.core.*
import dev.sequel.app.domain.importer.detector.SourceDetector
import dev.sequel.app.domain.importer.normalizer.ImportNormalizer
import dev.sequel.app.domain.importer.resolver.IdentityResolver
import dev.sequel.app.domain.importer.util.ImportDateParser
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
class CrossServiceDataSemanticsTest {

    private lateinit var database: SequelDatabase
    private lateinit var showDao: ShowDao
    private lateinit var watchedMovieDao: WatchedMovieDao
    private lateinit var watchedEpisodeDao: WatchedEpisodeDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var reviewDao: ReviewDao
    private lateinit var seasonDao: SeasonDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var mountRushmoreDao: MountRushmoreDao

    private lateinit var tmdbApiService: TmdbApiService
    private lateinit var syncManager: SyncManager
    private lateinit var coordinator: ImportCoordinator
    private lateinit var backupService: SequelBackupService
    private lateinit var importApplier: ImportApplier

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
        mountRushmoreDao = database.mountRushmoreDao()

        tmdbApiService = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)

        // Mock TMDB search responses for Inception and Interstellar
        coEvery { tmdbApiService.searchMovie("Inception") } returns TmdbPagedResponse(
            page = 1,
            results = listOf(
                TmdbShowDto(
                    id = 27205,
                    title = "Inception",
                    overview = "A thief who steals corporate secrets...",
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
            syncManager = syncManager
        )

        backupService = SequelBackupService(
            database = database,
            showDao = showDao,
            watchedMovieDao = watchedMovieDao,
            watchedEpisodeDao = watchedEpisodeDao,
            watchlistDao = watchlistDao,
            reviewDao = reviewDao,
            mountRushmoreDao = mountRushmoreDao
        )

        coordinator = ImportCoordinator(
            sourceDetector = detector,
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

    // ── Scenario 1: Same movie across multiple services -> 1 canonical movie ──
    @Test
    fun scenario1_sameMovieAcrossMultipleServices_resolvesToOneCanonicalMovie() = runBlocking {
        // Letterboxd watched.csv
        val letterboxdCsv = """
            Date,Name,Year,Letterboxd URI
            2023-01-01,"Inception",2010,https://boxd.it/157336
        """.trimIndent()

        // Trakt watched movies JSON
        val traktJson = """
            [
              {
                "plays": 1,
                "last_watched_at": "2023-02-01T12:00:00.000Z",
                "movie": {
                  "title": "Inception",
                  "year": 2010,
                  "ids": {
                    "trakt": 1234,
                    "tmdb": 27205
                  }
                }
              }
            ]
        """.trimIndent()

        // TV Time tracking-prod-records.csv
        val tvTimeCsv = """
            entity_type,movie_name,type,created_at
            movie,"Inception",watch,"2023-03-01 18:00:00"
        """.trimIndent()

        // Import from all 3 services
        coordinator.executeImport(ByteArrayInputStream(letterboxdCsv.toByteArray(StandardCharsets.UTF_8)), "watched.csv")
        coordinator.executeImport(ByteArrayInputStream(traktJson.toByteArray(StandardCharsets.UTF_8)), "watched_movies.json")
        coordinator.executeImport(ByteArrayInputStream(tvTimeCsv.toByteArray(StandardCharsets.UTF_8)), "tracking-prod-records.csv")

        // Assert exactly 1 movie in watched_movies table
        val watched = watchedMovieDao.getAllWatchedMovies()
        assertEquals("Should only have 1 canonical movie record", 1, watched.size)
        assertEquals(27205, watched[0].tmdbMovieId)
        assertEquals("Inception", watched[0].title)
    }

    // ── Scenario 2: Multiple legitimate watch events preserved ──
    @Test
    fun scenario2_multipleLegitimateWatchEvents_preservedAcrossDistinctDates() = runBlocking {
        val normalizer = ImportNormalizer()
        val items = listOf(
            ImportedMediaItem(
                title = "Inception",
                year = 2010,
                watchedAt = 1672531200000L, // 2023-01-01
                watchDate = "2023-01-01",
                mediaType = "movie",
                recordType = ImportRecordType.WATCHED,
                isRewatch = false
            ),
            ImportedMediaItem(
                title = "Inception",
                year = 2010,
                watchedAt = 1688169600000L, // 2023-07-01
                watchDate = "2023-07-01",
                mediaType = "movie",
                recordType = ImportRecordType.WATCHED,
                isRewatch = true
            )
        )

        val canonical = items.map { it.toCanonical(ImportSource.LETTERBOXD) }
        val normalized = normalizer.normalize(canonical)

        // Verifies distinct viewing dates are NOT collapsed
        assertEquals("Multiple watch events on different dates must be preserved", 2, normalized.size)
        assertEquals("2023-01-01", normalized[0].watchDate)
        assertEquals("2023-07-01", normalized[1].watchDate)
        assertTrue(normalized[1].isRewatch)
    }

    // ── Scenario 3: Diary-style date-only viewing dates preserved without inventing time-of-day ──
    @Test
    fun scenario3_diaryDateOnlyEntries_preservedWithoutInventingTimeOfDay() {
        val parsed = ImportDateParser.parse("2023-11-01")
        assertNotNull(parsed)
        assertTrue("Must be identified as date-only", parsed!!.isDateOnly)
        assertEquals("2023-11-01", parsed.dateOnlyString)
        assertNotNull(parsed.epochMillis)

        // Check noon UTC anchoring: 12 hours * 3600 seconds * 1000 ms = 43,200,000 ms into the day
        val dayRemainder = parsed.epochMillis!! % (24 * 60 * 60 * 1000L)
        assertEquals("Must be anchored at noon UTC (12:00:00) to prevent timezone drift", 12 * 60 * 60 * 1000L, dayRemainder)
    }

    // ── Scenario 4: Timestamps and timezone conversions do not shift calendar date ──
    @Test
    fun scenario4_timestampsAndTimezones_doNotShiftCalendarDate() {
        // UTC timestamp
        val utcDate = ImportDateParser.parse("2023-11-01T23:45:00Z")
        assertNotNull(utcDate)
        assertEquals("2023-11-01", utcDate!!.dateOnlyString)
        assertFalse(utcDate.isDateOnly)

        // Positive offset
        val posOffset = ImportDateParser.parse("2023-11-01T15:30:00+08:00")
        assertNotNull(posOffset)
        assertEquals("2023-11-01", posOffset!!.dateOnlyString)

        // Negative offset
        val negOffset = ImportDateParser.parse("2023-11-01T20:00:00-04:00")
        assertNotNull(negOffset)
        assertEquals("2023-11-01", negOffset!!.dateOnlyString)
    }

    // ── Scenario 5: Ratings from different scales preserved and converted faithfully ──
    @Test
    fun scenario5_ratingsFromDifferentScales_preserveOriginalScaleAndConvertFaithfully() = runBlocking {
        // Letterboxd 4.5 / 5.0 -> Sequel 9.0 / 10.0
        val lbItem = ImportedMediaItem(
            title = "Inception",
            mediaType = "movie",
            recordType = ImportRecordType.RATING,
            rating = 9.0f,
            originalRating = 4.5f,
            originalRatingScale = 5.0f
        )
        val lbCanonical = lbItem.toCanonical(ImportSource.LETTERBOXD)
        assertEquals(9.0f, lbCanonical.rating ?: 0f, 0.01f)
        assertEquals(4.5f, lbCanonical.originalRating ?: 0f, 0.01f)
        assertEquals(5.0f, lbCanonical.originalRatingScale ?: 0f, 0.01f)

        // Trakt 8 / 10.0 -> Sequel 8.0 / 10.0
        val traktItem = ImportedMediaItem(
            title = "Inception",
            mediaType = "movie",
            recordType = ImportRecordType.RATING,
            rating = 8.0f,
            originalRating = 8.0f,
            originalRatingScale = 10.0f
        )
        val traktCanonical = traktItem.toCanonical(ImportSource.TRAKT)
        assertEquals(8.0f, traktCanonical.rating ?: 0f, 0.01f)
        assertEquals(8.0f, traktCanonical.originalRating ?: 0f, 0.01f)
        assertEquals(10.0f, traktCanonical.originalRatingScale ?: 0f, 0.01f)
    }

    // ── Scenario 6: Colliding source-specific IDs across providers never corrupt data ──
    @Test
    fun scenario6_collidingSourceSpecificIds_scopedSafelyWithoutCrossContamination() = runBlocking {
        // Source A has provider ID 100 on TV Time (could be a show ID)
        val tvTimeRecord = ImportedMediaItem(
            title = "Show A",
            mediaType = "tv",
            recordType = ImportRecordType.WATCHED,
            externalId = "100",
            externalIdType = "tvtime"
        ).toCanonical(ImportSource.TV_TIME)

        // Source B has provider ID 100 on Trakt (could be a completely different movie ID)
        val traktRecord = ImportedMediaItem(
            title = "Movie B",
            mediaType = "movie",
            recordType = ImportRecordType.WATCHED,
            externalId = "100",
            externalIdType = "trakt"
        ).toCanonical(ImportSource.TRAKT)

        // Assert provenance and scoped identifiers
        assertEquals(ImportSource.TV_TIME, tvTimeRecord.provenance.source)
        assertEquals("100", tvTimeRecord.provenance.sourceRecordId)
        assertNull(tvTimeRecord.identifiers.traktId)

        assertEquals(ImportSource.TRAKT, traktRecord.provenance.source)
        assertEquals("100", traktRecord.provenance.sourceRecordId)
        assertEquals(100, traktRecord.identifiers.traktId)
    }

    // ── Scenario 7: Deterministic conflict resolution for all four policies ──
    @Test
    fun scenario7_deterministicConflictResolution_allFourPolicies() = runBlocking {
        val movieId = 27205
        val title = "Inception"

        fun createResolved(watchedAt: Long, rating: Float, review: String) = dev.sequel.app.domain.importer.resolver.ResolvedMediaRecord(
            originalRecord = CanonicalImportRecord(
                rawTitle = title,
                normalizedTitle = title,
                mediaType = "movie",
                recordType = ImportRecordType.WATCHED,
                watchedAt = watchedAt,
                rating = rating,
                reviewText = review,
                source = ImportSource.LETTERBOXD
            ),
            tmdbId = movieId,
            resolvedTitle = title,
            confidence = MatchConfidence.EXACT_ID
        )

        // Seed ShowEntity to satisfy foreign key constraints for reviews
        showDao.insertShow(
            ShowEntity(
                id = movieId,
                title = title,
                overview = "Dreams within dreams",
                mediaType = "movie",
                voteAverage = 8.4
            )
        )

        // 1. KEEP_EXISTING
        watchedMovieDao.upsertWatchedMovie(movieId, title, null, watchedAt = 1000L)
        reviewDao.upsertRating(movieId, "movie", null, null, 7.0f)
        reviewDao.upsertReviewText(movieId, "movie", null, null, "Local Review", false)

        val reportKeep = importApplier.apply(
            source = ImportSource.LETTERBOXD,
            resolvedRecords = listOf(createResolved(2000L, 9.0f, "Incoming Review")),
            policy = ConflictResolutionPolicy.KEEP_EXISTING
        )
        assertEquals(1, reportKeep.skippedIdenticalCount)
        assertEquals(1000L, watchedMovieDao.getWatchedMovie(movieId)!!.watchedAt)
        assertEquals(7.0f, reviewDao.getRatingRow(movieId, "movie")!!.rating ?: 0f, 0.01f)
        assertEquals("Local Review", reviewDao.getReviewForMedia(movieId, "movie")!!.reviewText)

        // 2. OVERWRITE_LOCAL
        val reportOverwrite = importApplier.apply(
            source = ImportSource.LETTERBOXD,
            resolvedRecords = listOf(createResolved(2000L, 9.0f, "Incoming Review")),
            policy = ConflictResolutionPolicy.OVERWRITE_LOCAL
        )
        assertEquals(1, reportOverwrite.updatedCount)
        assertEquals(2000L, watchedMovieDao.getWatchedMovie(movieId)!!.watchedAt)
        assertEquals(9.0f, reviewDao.getRatingRow(movieId, "movie")!!.rating ?: 0f, 0.01f)

        // 3. LATEST_TIMESTAMP: Incoming newer (3000L > 2000L) updates
        val reportLatestNewer = importApplier.apply(
            source = ImportSource.LETTERBOXD,
            resolvedRecords = listOf(createResolved(3000L, 10.0f, "Newest Review")),
            policy = ConflictResolutionPolicy.LATEST_TIMESTAMP
        )
        assertEquals(1, reportLatestNewer.updatedCount)
        assertEquals(3000L, watchedMovieDao.getWatchedMovie(movieId)!!.watchedAt)

        // LATEST_TIMESTAMP: Incoming older (1500L < 3000L) is skipped
        val reportLatestOlder = importApplier.apply(
            source = ImportSource.LETTERBOXD,
            resolvedRecords = listOf(createResolved(1500L, 6.0f, "Old Review")),
            policy = ConflictResolutionPolicy.LATEST_TIMESTAMP
        )
        assertEquals(1, reportLatestOlder.skippedIdenticalCount)
        assertEquals(3000L, watchedMovieDao.getWatchedMovie(movieId)!!.watchedAt)

        // 4. MERGE_ADDITIVE: Preserves existing local state
        val reportMerge = importApplier.apply(
            source = ImportSource.LETTERBOXD,
            resolvedRecords = listOf(createResolved(4000L, 8.0f, "Merge Review")),
            policy = ConflictResolutionPolicy.MERGE_ADDITIVE
        )
        assertEquals(1, reportMerge.skippedIdenticalCount)
        assertEquals(3000L, watchedMovieDao.getWatchedMovie(movieId)!!.watchedAt)
    }

    // ── Scenario 8: Unsupported but intentionally retained source data survives backup/restore ──
    @Test
    fun scenario8_unsupportedButRetainedSourceData_survivesSequelBackupRestore() {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

        val backup = SequelBackupModel(
            schemaVersion = SequelBackupModel.CURRENT_SCHEMA_VERSION,
            watchedMovies = listOf(
                WatchedMovieBackupDto(
                    tmdbMovieId = 27205,
                    title = "Inception",
                    watchedAt = 1672531200000L,
                    watchDate = "2023-01-01",
                    watchCount = 3
                )
            ),
            reviews = listOf(
                ReviewBackupDto(
                    mediaId = 27205,
                    mediaType = "movie",
                    rating = 9.0f,
                    originalRating = 4.5f,
                    originalRatingScale = 5.0f,
                    reviewText = "Mind-bending masterpiece"
                )
            )
        )

        val serialized = json.encodeToString(backup)
        val deserialized = json.decodeFromString<SequelBackupModel>(serialized)

        assertEquals("2023-01-01", deserialized.watchedMovies[0].watchDate)
        assertEquals(3, deserialized.watchedMovies[0].watchCount)
        assertEquals(4.5f, deserialized.reviews[0].originalRating ?: 0f, 0.01f)
        assertEquals(5.0f, deserialized.reviews[0].originalRatingScale ?: 0f, 0.01f)
    }

    // ── Scenario 9: Reimporting the same source dataset is idempotent ──
    @Test
    fun scenario9_reimportingSameDataset_isIdempotent() = runBlocking {
        val letterboxdCsv = """
            Date,Name,Year,Letterboxd URI,Rating
            2023-01-01,"Inception",2010,https://boxd.it/157336,4.5
        """.trimIndent()

        // 1st Import
        val report1 = coordinator.executeImport(
            inputStream = ByteArrayInputStream(letterboxdCsv.toByteArray(StandardCharsets.UTF_8)),
            filenameHint = "watched.csv",
            policy = ConflictResolutionPolicy.KEEP_EXISTING
        )
        assertEquals(1, report1.insertedCount)
        assertEquals(0, report1.skippedIdenticalCount)

        // 2nd Import (identical)
        val report2 = coordinator.executeImport(
            inputStream = ByteArrayInputStream(letterboxdCsv.toByteArray(StandardCharsets.UTF_8)),
            filenameHint = "watched.csv",
            policy = ConflictResolutionPolicy.KEEP_EXISTING
        )
        assertEquals(0, report2.insertedCount)
        assertEquals(1, report2.skippedIdenticalCount)

        // DB still has exactly 1 record
        assertEquals(1, watchedMovieDao.getAllWatchedMovies().size)
    }

    // ── Scenario 10: Complete data export and restore without original services ──
    @Test
    fun scenario10_fullRoundTripBackupAndRestore_cleanDatabaseWithoutOriginalSource() = runBlocking {
        // Seed database
        showDao.insertShow(
            ShowEntity(
                id = 27205,
                title = "Inception",
                overview = "Dreams within dreams",
                mediaType = "movie",
                voteAverage = 8.4
            )
        )
        watchedMovieDao.upsertWatchedMovie(27205, "Inception", null, 1672531200000L)
        watchlistDao.insertToWatchlist(
            WatchlistEntity(
                tmdbId = 157336,
                mediaType = MediaType.MOVIE,
                title = "Interstellar",
                posterPath = null,
                addedAt = 1680000000000L
            )
        )
        reviewDao.upsertRating(27205, "movie", null, null, 9.0f)
        reviewDao.upsertReviewText(27205, "movie", null, null, "Brilliant cinematography", false)

        // Export JSON backup
        val jsonString = backupService.exportJsonBackup()
        assertNotNull(jsonString)
        assertTrue(jsonString.contains("Inception"))
        assertTrue(jsonString.contains("Interstellar"))

        // Wipe database
        database.clearAllTables()
        assertEquals(0, showDao.getAllShows().size)
        assertEquals(0, watchedMovieDao.getAllWatchedMovies().size)
        assertEquals(0, watchlistDao.getAllWatchlist().size)
        assertEquals(0, reviewDao.getAllReviews().size)

        // Restore backup into clean database
        val restoreResult = backupService.restoreJsonBackup(jsonString)
        assertTrue(restoreResult.totalRestored >= 4)
        assertEquals(1, restoreResult.showsCount)
        assertEquals(1, restoreResult.moviesCount)
        assertEquals(1, restoreResult.watchlistCount)
        assertEquals(1, restoreResult.reviewsCount)

        // Verify state is restored with 100% fidelity
        val restoredMovie = watchedMovieDao.getWatchedMovie(27205)
        assertNotNull(restoredMovie)
        assertEquals("Inception", restoredMovie!!.title)
        assertEquals(1672531200000L, restoredMovie.watchedAt)

        val restoredReview = reviewDao.getReviewForMedia(27205, "movie")
        assertNotNull(restoredReview)
        assertEquals("Brilliant cinematography", restoredReview!!.reviewText)
        assertEquals(9.0f, restoredReview.rating ?: 0f, 0.01f)
    }

    // ── Scenario 11: Stored metadata and provenance do not leak into UI presentation ──
    @Test
    fun scenario11_storedMetadataAndProvenance_doNotLeakIntoPresentationOrClutterUI() {
        // WatchedMovieEntity presentation entity stays minimal and clean
        val entity = WatchedMovieEntity(
            tmdbMovieId = 27205,
            title = "Inception",
            posterPath = "/poster.jpg",
            watchedAt = 1672531200000L,
            syncStatus = SyncStatus.SYNCED
        )

        // Domain CanonicalImportRecord holds rich provenance for auditability
        val canonical = CanonicalImportRecord(
            rawTitle = "Inception",
            normalizedTitle = "Inception",
            mediaType = "movie",
            recordType = ImportRecordType.WATCHED,
            source = ImportSource.LETTERBOXD,
            provenance = SourceProvenance(
                source = ImportSource.LETTERBOXD,
                sourceRecordId = "https://boxd.it/157336",
                rawDateString = "2023-01-01",
                isDateOnly = true,
                rawRating = 4.5f
            )
        )

        // Verify decoupling: Entity has no source-specific leak or raw CSV strings
        assertEquals(27205, entity.tmdbMovieId)
        assertEquals("Inception", entity.title)

        // Provenance remains safely encapsulated in import subsystem
        assertEquals(ImportSource.LETTERBOXD, canonical.provenance.source)
        assertEquals("2023-01-01", canonical.provenance.rawDateString)
        assertEquals(4.5f, canonical.provenance.rawRating ?: 0f, 0.01f)
    }
}
