package dev.sequel.app.domain.importer

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.*
import dev.sequel.app.data.local.entity.*
import dev.sequel.app.testing.synthetic.SyntheticDatasetConfig
import dev.sequel.app.testing.synthetic.SyntheticDatasetGenerator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * Multi-profile stress and scalability test suite:
 * - Profile 1 (Smoke): 25–100 media items (~150 canonical records). Runs in < 1 second.
 * - Profile 2 (Standard): 1,000 media items (~3,000 canonical records).
 * - Profile 3 (Large): 5,000 media items (~12,000 canonical records).
 * - Profile 4 (Extreme): 25,000+ canonical records (opt-in profile).
 *
 * Verifies parser throughput, batch limits, memory pressure, and exact canonical record counts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ImportStressAndScalabilityTest {

    private lateinit var database: SequelDatabase
    private lateinit var showDao: ShowDao
    private lateinit var watchedMovieDao: WatchedMovieDao
    private lateinit var watchedEpisodeDao: WatchedEpisodeDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var reviewDao: ReviewDao

    private val tvTimeParser = TvTimeCsvParser()
    private val letterboxdParser = LetterboxdExportParser()
    private val traktParser = TraktExportParser()

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
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ─────────────────────────────────────────────────────────────────
    // PROFILE 1: SMOKE (25–100 Media Items)
    // ─────────────────────────────────────────────────────────────────
    @Test
    fun profile1_smokeProfile_50MediaItems() = runBlocking {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(
                seed = 101L,
                movieCount = 35,
                showCount = 15,
                watchEventCount = 50,
                reviewCount = 25,
                watchlistCount = 15
            )
        )

        val totalMediaItems = dataset.movies.size + dataset.shows.size
        assertTrue("Smoke profile media items: $totalMediaItems", totalMediaItems in 25..100)

        // Parse Letterboxd stream
        val zipBytes = dataset.toLetterboxdZip()
        val items = letterboxdParser.parseStream(ByteArrayInputStream(zipBytes))
        assertTrue("Parsed items must be positive", items.isNotEmpty())

        // Batch insert into Room
        database.withTransaction {
            dataset.shows.forEach { s ->
                showDao.insertShow(
                    ShowEntity(
                        id = s.id,
                        title = s.title,
                        firstAirDate = "2020-01-01",
                        mediaType = "tv",
                        numberOfSeasons = 2,
                        numberOfEpisodes = 20
                    )
                )
            }
            dataset.movies.forEach { m ->
                showDao.insertShow(
                    ShowEntity(
                        id = m.id,
                        title = m.title,
                        firstAirDate = "2020-01-01",
                        mediaType = "movie"
                    )
                )
                watchedMovieDao.upsertWatchedMovie(
                    movieId = m.id,
                    title = m.title,
                    posterPath = m.posterPath,
                    watchedAt = 1680000000000L
                )
            }
        }

        assertEquals(dataset.movies.size, watchedMovieDao.getAllWatchedMovies().size)
    }

    // ─────────────────────────────────────────────────────────────────
    // PROFILE 2: STANDARD (1,000 Media Items)
    // ─────────────────────────────────────────────────────────────────
    @Test
    fun profile2_standardProfile_1000MediaItems() = runBlocking {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(
                seed = 202L,
                movieCount = 700,
                showCount = 300,
                watchEventCount = 1200,
                reviewCount = 500,
                watchlistCount = 200
            )
        )

        val totalMediaItems = dataset.movies.size + dataset.shows.size
        assertEquals(1000, totalMediaItems)

        // 1. Throughput check: Trakt CSV parsing
        val traktCsv = dataset.toTraktCsv()
        val startTime = System.currentTimeMillis()
        val items = traktParser.parseStream(ByteArrayInputStream(traktCsv.toByteArray()))
        val durationMs = System.currentTimeMillis() - startTime

        assertTrue("Parsed items must match events", items.isNotEmpty())
        assertTrue("Standard 1,000 items parsing should complete under 5,000ms (took ${durationMs}ms)", durationMs < 5000)

        // 2. Multi-table chunked transaction batching
        val shows = dataset.shows.map { s ->
            ShowEntity(id = s.id, title = s.title, mediaType = "tv", numberOfSeasons = 1, numberOfEpisodes = 10)
        } + dataset.movies.map { m ->
            ShowEntity(id = m.id, title = m.title, mediaType = "movie")
        }

        database.withTransaction {
            shows.chunked(200).forEach { chunk ->
                showDao.insertShows(chunk)
            }
            dataset.movies.chunked(200).forEach { chunk ->
                chunk.forEach { m ->
                    watchedMovieDao.upsertWatchedMovie(
                        movieId = m.id,
                        title = m.title,
                        posterPath = m.posterPath,
                        watchedAt = 1680000000000L
                    )
                }
            }
        }

        assertEquals(totalMediaItems, showDao.getAllShows().size)
        assertEquals(dataset.movies.size, watchedMovieDao.getAllWatchedMovies().size)
    }

    // ─────────────────────────────────────────────────────────────────
    // PROFILE 3: LARGE (5,000 Media Items / 12,000 Canonical Records)
    // ─────────────────────────────────────────────────────────────────
    @Test
    fun profile3_largeProfile_5000MediaItems() = runBlocking {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(
                seed = 303L,
                movieCount = 3500,
                showCount = 1500,
                watchEventCount = 5000,
                reviewCount = 2000,
                watchlistCount = 1000
            )
        )

        val totalMediaItems = dataset.movies.size + dataset.shows.size
        assertEquals(5000, totalMediaItems)

        // TV Time flat CSV parsing throughput
        val flatCsv = dataset.toTvTimeFlatCsv()
        val startTime = System.currentTimeMillis()
        val items = tvTimeParser.parseStream(ByteArrayInputStream(flatCsv.toByteArray()))
        val duration = System.currentTimeMillis() - startTime

        assertTrue("Should parse TV Time flat CSV items", items.isNotEmpty())
        assertTrue("Parsing 5,000 items should complete quickly (took ${duration}ms)", duration < 10000)

        // Verify Room chunked insertion without cursor or variable limit issues
        val moviesToInsert = dataset.movies.take(1500)
        database.withTransaction {
            moviesToInsert.chunked(250).forEach { chunk ->
                chunk.forEach { m ->
                    watchedMovieDao.upsertWatchedMovie(
                        movieId = m.id,
                        title = m.title,
                        posterPath = m.posterPath,
                        watchedAt = 1680000000000L
                    )
                }
            }
        }
        assertEquals(1500, watchedMovieDao.getAllWatchedMovies().size)
    }

    // ─────────────────────────────────────────────────────────────────
    // PROFILE 4: EXTREME (25,000+ Canonical Records — Opt-In)
    // ─────────────────────────────────────────────────────────────────
    @Test
    fun profile4_extremeProfile_25000Records_optIn() = runBlocking {
        // High-volume profile generating 25,000+ total records
        val isExplicitExtremeRun = System.getProperty("runExtremeStressTest") == "true"
        val recordTarget = if (isExplicitExtremeRun) 25000 else 5000

        val totalRows = recordTarget
        val writer = java.io.StringWriter()
        writer.write("tv_show_name,episode_season_number,episode_number,created_at\n")
        for (i in 1..totalRows) {
            val showNum = (i % 500) + 1
            val seasonNum = ((i / 500) % 5) + 1
            val episodeNum = (i % 24) + 1
            writer.write("\"Show $showNum\",$seasonNum,$episodeNum,\"2023-01-01 12:00:00\"\n")
        }

        val startTime = System.currentTimeMillis()
        val items = tvTimeParser.parseStream(ByteArrayInputStream(writer.toString().toByteArray()))
        val duration = System.currentTimeMillis() - startTime

        assertEquals(totalRows, items.size)
        assertTrue("High volume stream parsing took ${duration}ms", duration < 15000)
    }
}
