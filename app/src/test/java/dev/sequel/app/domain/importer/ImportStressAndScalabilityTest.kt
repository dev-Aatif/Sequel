package dev.sequel.app.domain.importer

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.*
import dev.sequel.app.data.local.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.StringWriter

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ImportStressAndScalabilityTest {

    private lateinit var database: SequelDatabase
    private lateinit var showDao: ShowDao
    private lateinit var watchedMovieDao: WatchedMovieDao
    private lateinit var watchedEpisodeDao: WatchedEpisodeDao
    private lateinit var watchlistDao: WatchlistDao
    private lateinit var reviewDao: ReviewDao

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

    @Test
    fun parseLargeSyntheticTvTimeDataset_5000Rows() = runBlocking {
        val writer = StringWriter()
        writer.write("tv_show_name,episode_season_number,episode_number,created_at\n")

        val totalRows = 5000
        for (i in 1..totalRows) {
            val showNum = (i % 250) + 1
            val seasonNum = ((i / 250) % 5) + 1
            val episodeNum = (i % 20) + 1
            writer.write("\"Show $showNum\",$seasonNum,$episodeNum,\"2023-01-01 12:00:00\"\n")
        }

        val parser = TvTimeCsvParser()
        val inputStream = ByteArrayInputStream(writer.toString().toByteArray())
        val startTime = System.currentTimeMillis()
        val items = parser.parseStream(inputStream)
        val duration = System.currentTimeMillis() - startTime

        assertEquals(totalRows, items.size)
        assertTrue("Parsing 5,000 rows should complete quickly (took ${duration}ms)", duration < 5000)
    }

    @Test
    fun parseLargeSyntheticLetterboxdDataset_4000Rows() = runBlocking {
        val writer = StringWriter()
        writer.write("Date,Name,Year,Letterboxd URI,Rating,Review,Spoiler\n")

        val totalRows = 4000
        for (i in 1..totalRows) {
            val year = 1980 + (i % 44)
            val rating = ((i % 10) + 1) * 0.5f // 0.5 to 5.0
            val spoiler = if (i % 5 == 0) "True" else "False"
            writer.write("2023-05-01,\"Synthetic Film $i\",$year,https://boxd.it/$i,$rating,\"Great review text for movie $i\",$spoiler\n")
        }

        val parser = LetterboxdExportParser()
        val inputStream = ByteArrayInputStream(writer.toString().toByteArray())
        val startTime = System.currentTimeMillis()
        val items = parser.parseStream(inputStream)
        val duration = System.currentTimeMillis() - startTime

        assertEquals(totalRows, items.size)
        assertTrue("Parsing 4,000 rows should complete quickly (took ${duration}ms)", duration < 5000)

        // Verify rating scaling
        val first = items[0]
        assertEquals(2.0f, first.rating ?: 0f, 0.01f) // 1.0 * 2.0 = 2.0
        assertEquals(ImportRecordType.REVIEW, first.recordType)
    }

    @Test
    fun batchDatabaseUpsertStress_thousandsOfMoviesAndEpisodesWithoutLockOrLimits() = runBlocking {
        // 1. Pre-insert 100 shows into Room in chunks of 50
        val showCount = 100
        val shows = (1..showCount).map { id ->
            ShowEntity(
                id = id,
                title = "Show $id",
                overview = "Overview for show $id",
                voteAverage = 8.0,
                genreIds = "[18]",
                genresDisplay = "Drama",
                mediaType = "tv",
                numberOfSeasons = 5,
                numberOfEpisodes = 50
            )
        }
        shows.chunked(50).forEach { chunk ->
            showDao.insertShows(chunk)
        }
        assertEquals(showCount, showDao.getAllShows().size)

        // 2. Batch insert 3,000 watched episodes across shows in chunks
        val episodeCount = 3000
        val episodes = (1..episodeCount).map { i ->
            val showId = (i % showCount) + 1
            val seasonNum = ((i / 100) % 5) + 1
            val epNum = (i % 20) + 1
            WatchedEpisodeEntity(
                showId = showId,
                tmdbEpisodeId = 100000 + i,
                seasonNumber = seasonNum,
                episodeNumber = epNum,
                watchedAt = 1680000000000L + (i * 1000L),
                syncStatus = SyncStatus.PENDING
            )
        }

        database.withTransaction {
            episodes.chunked(250).forEach { chunk ->
                chunk.forEach { ep ->
                    watchedEpisodeDao.upsertWatchedEpisode(
                        showId = ep.showId,
                        tmdbEpisodeId = ep.tmdbEpisodeId,
                        seasonNumber = ep.seasonNumber,
                        episodeNumber = ep.episodeNumber,
                        watchedAt = ep.watchedAt
                    )
                }
            }
        }

        val allEpisodes = watchedEpisodeDao.getAllWatchedEpisodes()
        assertEquals(episodeCount, allEpisodes.size)

        // 3. Batch insert 2,000 watched movies in chunks
        val movieCount = 2000
        database.withTransaction {
            (1..movieCount).chunked(250).forEach { chunk ->
                chunk.forEach { mId ->
                    watchedMovieDao.upsertWatchedMovie(
                        movieId = 500000 + mId,
                        title = "Movie $mId",
                        posterPath = "/poster_$mId.jpg",
                        watchedAt = 1680000000000L + (mId * 1000L)
                    )
                }
            }
        }

        val allMovies = watchedMovieDao.getAllWatchedMovies()
        assertEquals(movieCount, allMovies.size)

        // 4. Batch insert 1,000 reviews and ratings
        val reviewCount = 1000
        database.withTransaction {
            (1..reviewCount).forEach { i ->
                val showId = (i % showCount) + 1
                reviewDao.upsertRating(
                    mediaId = showId,
                    mediaType = "tv",
                    seasonNum = (i % 5) + 1,
                    episodeNum = null,
                    rating = 8.5f
                )
            }
        }

        val allReviews = reviewDao.getAllReviews()
        assertTrue("Reviews should be inserted cleanly without conflict", allReviews.isNotEmpty())
    }
}
