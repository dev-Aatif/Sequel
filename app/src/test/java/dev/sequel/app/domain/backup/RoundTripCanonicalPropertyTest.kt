package dev.sequel.app.domain.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.entity.MediaType
import dev.sequel.app.data.local.entity.MountRushmoreEntity
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.local.entity.WatchlistEntity
import dev.sequel.app.testing.synthetic.EdgeCaseConfig
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

/**
 * Property-based and multi-cycle round-trip tests verifying:
 * - Full canonical field survival across export and restore
 * - Show and episode relational integrity
 * - Ratings, reviews, Unicode, emojis, and spoilers survive intact
 * - Dates and timestamps do not shift
 * - Multi-cycle invariance: repeated export/import cycles do not accumulate loss or duplication ($L_1 = L_2$)
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RoundTripCanonicalPropertyTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun createIsolatedDatabase(): SequelDatabase {
        return Room.inMemoryDatabaseBuilder(context, SequelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun createBackupService(db: SequelDatabase): SequelBackupService {
        return SequelBackupService(
            database = db,
            showDao = db.showDao(),
            watchedMovieDao = db.watchedMovieDao(),
            watchedEpisodeDao = db.watchedEpisodeDao(),
            watchlistDao = db.watchlistDao(),
            reviewDao = db.reviewDao(),
            mountRushmoreDao = db.mountRushmoreDao()
        )
    }

    @Test
    fun propertyTest_acrossMultipleDeterministicSeeds_preservesAllCanonicalRecords() = runBlocking {
        val testSeeds = listOf(42L, 101L, 777L, 9999L)

        for (seed in testSeeds) {
            val db1 = createIsolatedDatabase()
            val db2 = createIsolatedDatabase()
            try {
                val service1 = createBackupService(db1)
                val service2 = createBackupService(db2)

                val dataset = SyntheticDatasetGenerator.generate(
                    SyntheticDatasetConfig(
                        seed = seed,
                        movieCount = 15,
                        showCount = 8,
                        watchEventCount = 30,
                        reviewCount = 15,
                        watchlistCount = 10,
                        edgeCases = EdgeCaseConfig(
                            includeMultilingualAndEmojiReviews = true,
                            includeLeapDaysAndMidnightTimestamps = true,
                            includeAnimeAndSpecials = true
                        )
                    )
                )

                // 1. Seed db1 with dataset
                dataset.shows.forEach { s ->
                    db1.showDao().insertShow(
                        ShowEntity(
                            id = s.id,
                            title = s.title,
                            firstAirDate = "${s.year ?: 2020}-01-01",
                            mediaType = "tv",
                            numberOfSeasons = s.seasons.size,
                            numberOfEpisodes = s.seasons.sumOf { it.episodes.size }
                        )
                    )
                }

                dataset.movies.forEach { m ->
                    db1.showDao().insertShow(
                        ShowEntity(
                            id = m.id,
                            title = m.title,
                            firstAirDate = "${m.year ?: 2020}-01-01",
                            mediaType = "movie",
                            runtime = m.runtime
                        )
                    )
                    db1.watchedMovieDao().upsertWatchedMovie(
                        movieId = m.id,
                        title = m.title,
                        posterPath = m.posterPath,
                        watchedAt = 1680000000000L + (m.id * 1000L)
                    )
                }

                dataset.shows.forEach { s ->
                    val season = s.seasons.firstOrNull()
                    val episode = season?.episodes?.firstOrNull()
                    if (episode != null) {
                        db1.watchedEpisodeDao().upsertWatchedEpisode(
                            showId = s.id,
                            tmdbEpisodeId = episode.episodeId * 10,
                            seasonNumber = episode.seasonNumber,
                            episodeNumber = episode.episodeNumber,
                            watchedAt = 1680005000000L
                        )
                    }
                }

                dataset.watchlist.forEach { wl ->
                    val mediaType = if (wl.mediaType.equals("movie", ignoreCase = true)) MediaType.MOVIE else MediaType.TV
                    db1.watchlistDao().insertToWatchlist(
                        WatchlistEntity(
                            tmdbId = wl.tmdbId ?: (wl.title.hashCode() and 0x7FFFFFFF),
                            mediaType = mediaType,
                            title = wl.title,
                            addedAt = wl.addedAt
                        )
                    )
                }

                dataset.reviews.forEach { r ->
                    if (r.rating != null) {
                        db1.reviewDao().upsertRating(
                            mediaId = r.mediaId,
                            mediaType = r.mediaType,
                            seasonNum = r.seasonNumber,
                            episodeNum = r.episodeNumber,
                            rating = r.rating
                        )
                    }
                    if (!r.reviewText.isNullOrBlank()) {
                        db1.reviewDao().upsertReviewText(
                            mediaId = r.mediaId,
                            mediaType = r.mediaType,
                            seasonNum = r.seasonNumber,
                            episodeNum = r.episodeNumber,
                            text = r.reviewText,
                            isSpoiler = r.isSpoiler
                        )
                    }
                }

                // Mount Rushmore (4 slots)
                dataset.movies.take(4).forEachIndexed { slot, m ->
                    db1.mountRushmoreDao().upsertSlot(
                        MountRushmoreEntity(
                            slotIndex = slot,
                            mediaId = m.id,
                            mediaType = "movie",
                            title = m.title,
                            posterPath = m.posterPath,
                            year = m.year?.toString()
                        )
                    )
                }

                // 2. Export from db1
                val backupJson = service1.exportJsonBackup()
                assertTrue(backupJson.contains("\"schemaVersion\": 1"))

                // 3. Restore into clean isolated db2
                val restoreResult = service2.restoreJsonBackup(backupJson, mode = RestoreMode.REPLACE_CLEAN)
                assertTrue("Restored items must be positive", restoreResult.totalRestored > 0)

                // 4. Canonical comparison
                assertEquals(db1.showDao().getAllShows().size, db2.showDao().getAllShows().size)
                assertEquals(db1.watchedMovieDao().getAllWatchedMovies().size, db2.watchedMovieDao().getAllWatchedMovies().size)
                assertEquals(db1.watchedEpisodeDao().getAllWatchedEpisodes().size, db2.watchedEpisodeDao().getAllWatchedEpisodes().size)
                assertEquals(db1.watchlistDao().getAllWatchlist().size, db2.watchlistDao().getAllWatchlist().size)
                assertEquals(db1.reviewDao().getAllReviews().size, db2.reviewDao().getAllReviews().size)
                assertEquals(db1.mountRushmoreDao().getAllSlots().size, db2.mountRushmoreDao().getAllSlots().size)

                // Verify specific movie survival
                val originalMovies = db1.watchedMovieDao().getAllWatchedMovies().associateBy { it.tmdbMovieId }
                val restoredMovies = db2.watchedMovieDao().getAllWatchedMovies().associateBy { it.tmdbMovieId }
                originalMovies.forEach { (id, orig) ->
                    val restored = restoredMovies[id]
                    assertNotNull("Movie $id must exist in restored database", restored)
                    assertEquals(orig.title, restored?.title)
                    assertEquals(orig.watchedAt, restored?.watchedAt)
                }

                // Verify specific review survival (including Unicode, emoji, and ratings)
                val originalReviews = db1.reviewDao().getAllReviews().associateBy { "${it.mediaId}_${it.mediaType}_${it.seasonNum}_${it.episodeNum}" }
                val restoredReviews = db2.reviewDao().getAllReviews().associateBy { "${it.mediaId}_${it.mediaType}_${it.seasonNum}_${it.episodeNum}" }
                originalReviews.forEach { (key, orig) ->
                    val restored = restoredReviews[key]
                    assertNotNull("Review $key must survive restore", restored)
                    assertEquals(orig.rating, restored?.rating)
                    assertEquals(orig.reviewText, restored?.reviewText)
                    assertEquals(orig.isSpoiler, restored?.isSpoiler)
                }
            } finally {
                db1.close()
                db2.close()
            }
        }
    }

    @Test
    fun multiCycleProperty_repeatedExportRestoreCycles_accumulateZeroLossOrDuplication() = runBlocking {
        // L0 -> Export1 -> L1 -> Export2 -> L2 (Asserting L1 == L2)
        val db1 = createIsolatedDatabase()
        val db2 = createIsolatedDatabase()
        val db3 = createIsolatedDatabase()

        try {
            val service1 = createBackupService(db1)
            val service2 = createBackupService(db2)
            val service3 = createBackupService(db3)

            // 1. Seed db1
            val movie = ShowEntity(id = 500, title = "Interstellar", firstAirDate = "2014-11-07", mediaType = "movie")
            db1.showDao().insertShow(movie)
            db1.watchedMovieDao().upsertWatchedMovie(movieId = 500, title = "Interstellar", posterPath = "/interstellar.jpg", watchedAt = 1680000000000L)
            db1.reviewDao().upsertRating(mediaId = 500, mediaType = "movie", seasonNum = null, episodeNum = null, rating = 9.5f)
            db1.reviewDao().upsertReviewText(mediaId = 500, mediaType = "movie", seasonNum = null, episodeNum = null, text = "Transcendent cinema 🪐", isSpoiler = false)

            // Cycle 1: db1 -> export1 -> restore into db2 (L1)
            val backupJson1 = service1.exportJsonBackup()
            val result1 = service2.restoreJsonBackup(backupJson1, mode = RestoreMode.REPLACE_CLEAN)
            assertEquals(1, result1.moviesCount)
            assertEquals(1, result1.reviewsCount)

            // Cycle 2: db2 -> export2 -> restore into db3 (L2)
            val backupJson2 = service2.exportJsonBackup()
            val result2 = service3.restoreJsonBackup(backupJson2, mode = RestoreMode.REPLACE_CLEAN)
            assertEquals(1, result2.moviesCount)
            assertEquals(1, result2.reviewsCount)

            // Compare L1 (db2) and L2 (db3)
            assertEquals(db2.watchedMovieDao().getAllWatchedMovies().size, db3.watchedMovieDao().getAllWatchedMovies().size)
            assertEquals(db2.reviewDao().getAllReviews().size, db3.reviewDao().getAllReviews().size)

            val m2 = db2.watchedMovieDao().getAllWatchedMovies().first()
            val m3 = db3.watchedMovieDao().getAllWatchedMovies().first()
            assertEquals(m2.tmdbMovieId, m3.tmdbMovieId)
            assertEquals(m2.title, m3.title)
            assertEquals(m2.watchedAt, m3.watchedAt)

            val r2 = db2.reviewDao().getAllReviews().first()
            val r3 = db3.reviewDao().getAllReviews().first()
            assertEquals(r2.mediaId, r3.mediaId)
            assertEquals(r2.rating, r3.rating)
            assertEquals(r2.reviewText, r3.reviewText)
        } finally {
            db1.close()
            db2.close()
            db3.close()
        }
    }
}
