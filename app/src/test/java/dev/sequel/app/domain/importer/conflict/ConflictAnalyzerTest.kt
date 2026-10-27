package dev.sequel.app.domain.importer.conflict

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.*
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.local.entity.WatchlistEntity
import dev.sequel.app.domain.importer.ImportRecordType
import dev.sequel.app.domain.importer.core.*
import dev.sequel.app.domain.importer.resolver.ResolvedMediaRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ConflictAnalyzerTest {

    private lateinit var database: SequelDatabase
    private lateinit var showDao: ShowDao
    private lateinit var watchedMovieDao: WatchedMovieDao
    private lateinit var watchedEpisodeDao: WatchedEpisodeDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var reviewDao: ReviewDao

    private lateinit var analyzer: ConflictAnalyzer

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

        analyzer = ConflictAnalyzer(
            watchedMovieDao = watchedMovieDao,
            watchedEpisodeDao = watchedEpisodeDao,
            watchlistDao = watchlistDao,
            reviewDao = reviewDao
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun analyze_detectsIdenticalDuplicateMovieAndRatingDivergence() = runBlocking {
        // 1. Seed existing movie in Room
        val show = ShowEntity(
            id = 550,
            title = "Fight Club",
            overview = "An insomniac office worker...",
            voteAverage = 8.4,
            genreIds = "[18]",
            genresDisplay = "Drama",
            mediaType = "movie"
        )
        showDao.insertShow(show)

        watchedMovieDao.upsertWatchedMovie(
            movieId = 550,
            title = "Fight Club",
            posterPath = "/fightclub.jpg",
            watchedAt = 1600000000000L
        )

        reviewDao.upsertRating(
            mediaId = 550,
            mediaType = "movie",
            seasonNum = null,
            episodeNum = null,
            rating = 8.5f
        )

        // 2. Prepare test resolved records
        val identicalMovieRecord = ResolvedMediaRecord(
            originalRecord = CanonicalImportRecord(
                rawTitle = "Fight Club",
                normalizedTitle = "Fight Club",
                year = 1999,
                mediaType = "movie",
                recordType = ImportRecordType.WATCHED,
                watchedAt = 1600000000000L
            ),
            tmdbId = 550,
            resolvedTitle = "Fight Club",
            confidence = MatchConfidence.EXACT_ID
        )

        val conflictingRatingRecord = ResolvedMediaRecord(
            originalRecord = CanonicalImportRecord(
                rawTitle = "Fight Club",
                normalizedTitle = "Fight Club",
                year = 1999,
                mediaType = "movie",
                recordType = ImportRecordType.RATING,
                rating = 10.0f
            ),
            tmdbId = 550,
            resolvedTitle = "Fight Club",
            confidence = MatchConfidence.EXACT_ID
        )

        val newWatchlistRecord = ResolvedMediaRecord(
            originalRecord = CanonicalImportRecord(
                rawTitle = "Oppenheimer",
                normalizedTitle = "Oppenheimer",
                year = 2023,
                mediaType = "movie",
                recordType = ImportRecordType.WATCHLIST
            ),
            tmdbId = 872585,
            resolvedTitle = "Oppenheimer",
            confidence = MatchConfidence.EXACT_TITLE_YEAR
        )

        val preview = analyzer.analyze(
            source = ImportSource.LETTERBOXD,
            resolvedRecords = listOf(identicalMovieRecord, conflictingRatingRecord, newWatchlistRecord)
        )

        assertEquals(3, preview.totalRecordsParsed)
        assertEquals(2, preview.uniqueTitlesCount)
        assertEquals(1, preview.newWatchlistCount)
        assertEquals(0, preview.newMoviesCount) // 550 already watched
        assertEquals(1, preview.identicalSkippedCount) // 550 duplicate
        assertEquals(2, preview.conflicts.size) // 1 identical duplicate + 1 rating divergence

        val ratingConflict = preview.conflicts.find { it.conflictType == ConflictType.RATING_DIVERGENCE }
        assertNotNull(ratingConflict)
        assertTrue(ratingConflict?.resolutionMessage?.contains("Rating differs") == true)
    }
}
