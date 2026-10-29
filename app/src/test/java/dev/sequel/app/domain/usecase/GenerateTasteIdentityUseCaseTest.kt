package dev.sequel.app.domain.usecase

import dev.sequel.app.data.local.dao.ReviewDao
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.domain.model.TasteWatchRecord
import dev.sequel.app.domain.repository.AuthRepository
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GenerateTasteIdentityUseCaseTest {

    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val showDao: ShowDao = mockk(relaxed = true)
    private val reviewDao: ReviewDao = mockk(relaxed = true)

    private lateinit var useCase: GenerateTasteIdentityUseCase

    @Before
    fun setUp() {
        useCase = GenerateTasteIdentityUseCase(
            authRepository = authRepository,
            showDao = showDao,
            reviewDao = reviewDao
        )
    }

    @Test
    fun calculate_determinesTopGenreByWatchTime() {
        val records = listOf(
            TasteWatchRecord(
                mediaId = 1,
                mediaType = "movie",
                title = "Blade Runner 2049",
                genres = listOf("Science Fiction", "Drama"),
                runtimeMinutes = 164, // 2.73 hrs
                watchCount = 1,
                director = "Denis Villeneuve"
            ),
            TasteWatchRecord(
                mediaId = 2,
                mediaType = "movie",
                title = "Dune",
                genres = listOf("Science Fiction", "Adventure"),
                runtimeMinutes = 155, // 2.58 hrs
                watchCount = 1,
                director = "Denis Villeneuve"
            ),
            TasteWatchRecord(
                mediaId = 3,
                mediaType = "movie",
                title = "The Godfather",
                genres = listOf("Crime", "Drama"),
                runtimeMinutes = 175, // 2.91 hrs
                watchCount = 1,
                director = "Francis Ford Coppola"
            )
        )

        // Total Sci-Fi: 164 + 155 = 319 minutes (~5.31 hours)
        // Total Drama: 164 + 175 = 339 minutes (~5.65 hours)
        val identity = useCase.calculate(
            email = "cinephile@sequel.dev",
            records = records
        )

        assertEquals("@cinephile", identity.handle)
        // Drama has 339 minutes, which is 5 hours logged
        assertEquals("Prestige Drama", identity.primaryObsession)
        assertEquals(5, identity.primaryObsessionHours)
        // Total runtime: 164 + 155 + 175 = 494 minutes = 8 hours
        assertEquals(8, identity.totalHoursLogged)
    }

    @Test
    fun calculate_determinesTopDirectorByWatchCount() {
        val records = listOf(
            TasteWatchRecord(
                mediaId = 101,
                mediaType = "movie",
                title = "Blade Runner 2049",
                genres = listOf("Science Fiction"),
                runtimeMinutes = 160,
                watchCount = 1,
                director = "Denis Villeneuve"
            ),
            TasteWatchRecord(
                mediaId = 102,
                mediaType = "movie",
                title = "Dune",
                genres = listOf("Science Fiction"),
                runtimeMinutes = 150,
                watchCount = 1,
                director = "Denis Villeneuve"
            ),
            TasteWatchRecord(
                mediaId = 103,
                mediaType = "movie",
                title = "Arrival",
                genres = listOf("Science Fiction"),
                runtimeMinutes = 116,
                watchCount = 1,
                director = "Denis Villeneuve"
            ),
            TasteWatchRecord(
                mediaId = 201,
                mediaType = "movie",
                title = "Oppenheimer",
                genres = listOf("Drama", "History"),
                runtimeMinutes = 180,
                watchCount = 1,
                director = "Christopher Nolan"
            )
        )

        val identity = useCase.calculate(
            email = "villeneuve_fan@sequel.dev",
            records = records
        )

        assertEquals("Denis Villeneuve", identity.topAuteur)
        assertEquals(3, identity.topAuteurWatchedCount)
        assertEquals(listOf("Blade Runner 2049", "Dune", "Arrival"), identity.topAuteurTitles)
        assertEquals(4, identity.totalTitlesWatched)
        assertEquals("Cyberpunk & Sci-Fi", identity.primaryObsession)
        assertEquals("Neon Auteurist", identity.archetype)
    }

    @Test
    fun calculate_identifiesControversialHotTakes() {
        val records = listOf(
            // Loved by user, mid by critics: User 9.0 vs Critic 5.8 -> Hot take!
            TasteWatchRecord(
                mediaId = 301,
                mediaType = "movie",
                title = "Tron: Legacy",
                genres = listOf("Science Fiction"),
                runtimeMinutes = 125,
                watchCount = 1,
                tmdbRating = 5.8,
                userRating = 9.0f
            ),
            // Disliked by user, acclaimed by critics: User 4.0 vs Critic 8.4 -> Slop called out!
            TasteWatchRecord(
                mediaId = 302,
                mediaType = "movie",
                title = "Overrated Consensus Darling",
                genres = listOf("Drama"),
                runtimeMinutes = 130,
                watchCount = 1,
                tmdbRating = 8.4,
                userRating = 4.0f
            ),
            // Consensus agreement: User 8.0 vs Critic 8.1 -> Not controversial
            TasteWatchRecord(
                mediaId = 303,
                mediaType = "movie",
                title = "The Dark Knight",
                genres = listOf("Action", "Crime"),
                runtimeMinutes = 152,
                watchCount = 1,
                tmdbRating = 8.5,
                userRating = 8.5f
            )
        )

        val identity = useCase.calculate(
            email = "critic_buster@sequel.dev",
            records = records
        )

        assertEquals(2, identity.controversialTakes.size)

        val firstTake = identity.controversialTakes[0]
        val secondTake = identity.controversialTakes[1]

        // Discrepancy for Tron: |9.0 - 5.8| = 3.2
        // Discrepancy for Overrated: |4.0 - 8.4| = 4.4
        // Sorted by discrepancy descending: Overrated first, Tron second
        assertEquals("Overrated Consensus Darling", firstTake.title)
        assertFalse("Expected critic loved / user disliked take", firstTake.isHotTake)
        assertEquals(4.0f, firstTake.userRating)
        assertEquals(8.4, firstTake.criticRating, 0.01)

        assertEquals("Tron: Legacy", secondTake.title)
        assertTrue("Expected user loved / critic mid hot take", secondTake.isHotTake)
        assertEquals(9.0f, secondTake.userRating)
        assertEquals(5.8, secondTake.criticRating, 0.01)

        // 2 hot takes triggers "The Cynical Visionary" archetype!
        assertEquals("The Cynical Visionary", identity.archetype)
    }

    @Test
    fun calculate_handlesEmptyRecordsCleanly() {
        val identity = useCase.calculate(
            email = null,
            records = emptyList()
        )

        assertEquals("@cinephile", identity.handle)
        assertEquals("The Aspiring Cinephile", identity.archetype)
        assertEquals("Undiscovered", identity.primaryObsession)
        assertEquals(0, identity.primaryObsessionHours)
        assertEquals("Eclectic", identity.topAuteur)
        assertEquals(0, identity.topAuteurWatchedCount)
        assertEquals(0, identity.totalHoursLogged)
        assertTrue(identity.controversialTakes.isEmpty())
        assertTrue(identity.topAuteurTitles.isEmpty())
        assertEquals(0, identity.totalTitlesWatched)
    }

    @Test
    fun calculate_resolvesExpandedKnownDirectors() {
        val records = listOf(
            TasteWatchRecord(
                mediaId = 501,
                mediaType = "movie",
                title = "The Prestige",
                genres = listOf("Drama", "Mystery"),
                runtimeMinutes = 130,
                watchCount = 1
            ),
            TasteWatchRecord(
                mediaId = 502,
                mediaType = "movie",
                title = "Whiplash",
                genres = listOf("Drama", "Music"),
                runtimeMinutes = 107,
                watchCount = 1
            ),
            TasteWatchRecord(
                mediaId = 503,
                mediaType = "movie",
                title = "No Country for Old Men",
                genres = listOf("Crime", "Drama", "Thriller"),
                runtimeMinutes = 122,
                watchCount = 1
            )
        )

        val identity = useCase.calculate(
            email = "cinephile@sequel.dev",
            records = records
        )

        assertEquals(3, identity.totalTitlesWatched)
        assertTrue(identity.topAuteur in listOf("Christopher Nolan", "Damien Chazelle", "Joel and Ethan Coen"))
        assertEquals(1, identity.topAuteurWatchedCount)
        assertTrue(identity.topAuteurTitles.isNotEmpty())
    }
}
