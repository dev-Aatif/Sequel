package dev.sequel.app.data.local

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.local.entity.MountRushmoreEntity
import dev.sequel.app.data.local.entity.ShowEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * JVM unit test verifying that Room database and DAOs instantiate,
 * initialize schemas, perform CRUD operations, and close cleanly.
 *
 * Configured with a clean [Application] to prevent Robolectric from booting the full
 * [dev.sequel.app.SequelApplication] graph (which triggers WorkManager, Supabase, and Crashlytics).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RoomDatabaseSmokeTest {

    private lateinit var database: SequelDatabase
    private lateinit var showDao: ShowDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            SequelDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        showDao = database.showDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        database.close()
    }

    @Test
    fun database_createsAndInstantiatesAllDaosCleanly() {
        assertNotNull(database)
        assertNotNull(showDao)
        assertNotNull(database.seasonDao())
        assertNotNull(database.episodeDao())
        assertNotNull(database.watchedEpisodeDao())
        assertNotNull(database.watchedMovieDao())
        assertNotNull(database.reviewDao())
        assertNotNull(database.remoteKeysDao())
        assertNotNull(database.watchlistDao())
        assertNotNull(database.trendingShowDao())
        assertNotNull(database.mountRushmoreDao())
    }

    @Test
    fun mountRushmoreDao_persistsAndObservesSlotsCleanly() = runBlocking {
        val rushDao = database.mountRushmoreDao()
        val testSlot = MountRushmoreEntity(
            slotIndex = 0,
            mediaId = 550,
            mediaType = "movie",
            title = "Fight Club",
            posterPath = "/pB8BM7pdSp6B6Ih7QZ4DrQ3PmJK.jpg",
            year = "1999",
            rating = 8.4f
        )

        rushDao.upsertSlot(testSlot)

        val retrieved = rushDao.getSlot(0)
        assertNotNull(retrieved)
        assertEquals(testSlot.slotIndex, retrieved?.slotIndex)
        assertEquals(testSlot.mediaId, retrieved?.mediaId)
        assertEquals(testSlot.title, retrieved?.title)
        assertEquals(testSlot.year, retrieved?.year)
        assertEquals(testSlot.rating ?: 0f, retrieved?.rating ?: 0f, 0.01f)

        rushDao.deleteSlot(0)
        val afterDelete = rushDao.getSlot(0)
        org.junit.Assert.assertNull(afterDelete)
    }

    @Test
    fun insertAndQueryShowEntity_returnsExpectedEntity() = runBlocking {
        val dummyShow = ShowEntity(
            id = 999999,
            title = "Testing Infrastructure Show",
            overview = "Automated smoke test verifying Room SQLite persistence.",
            posterPath = "/smoke_test_poster.jpg",
            backdropPath = "/smoke_test_backdrop.jpg",
            mediaType = "tv",
            firstAirDate = "2026-01-01",
            voteAverage = 9.2,
            genreIds = "[18, 10765]",
            numberOfSeasons = 2,
            numberOfEpisodes = 20,
            status = "Returning Series",
            isFavorite = true,
            isInWatchlist = false,
            runtime = 50,
            genresDisplay = "Drama, Sci-Fi",
            episodeRuntime = 50,
            contentRating = "TV-MA",
            lastUpdated = System.currentTimeMillis()
        )

        showDao.insertShow(dummyShow)

        val retrieved = showDao.getShowById(999999, "tv")
        assertNotNull("Retrieved show should not be null", retrieved)
        assertEquals(dummyShow.id, retrieved?.id)
        assertEquals(dummyShow.title, retrieved?.title)
        assertEquals(dummyShow.overview, retrieved?.overview)
        assertEquals(dummyShow.mediaType, retrieved?.mediaType)
        assertEquals(dummyShow.voteAverage, retrieved?.voteAverage ?: 0.0, 0.001)
        assertEquals(dummyShow.isFavorite, retrieved?.isFavorite)
    }
}
