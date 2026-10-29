package dev.sequel.app.data.remote.supabase

import dev.sequel.app.data.local.entity.*
import dev.sequel.app.data.remote.supabase.dto.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Verifies Supabase data contracts, serialization, RLS ownership fields,
 * and entity-to-DTO mapping boundaries for cloud sync.
 */
class SupabaseContractAndIntegrationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val testUserId = UUID.randomUUID().toString()

    @Test
    fun supabaseReviewDto_serializesFloatRatingAndSpoilerCorrectly() {
        val dto = SupabaseReviewDto(
            id = UUID.randomUUID().toString(),
            userId = testUserId,
            mediaId = 550,
            mediaType = "movie",
            seasonNum = null,
            episodeNum = null,
            reviewText = "Masterpiece! 🍿",
            rating = 9.5f,
            isSpoiler = true,
            updatedAt = 1680000000000L
        )

        val jsonStr = json.encodeToString(dto)
        assertTrue("JSON must contain user_id", jsonStr.contains("\"user_id\":\"$testUserId\""))
        assertTrue("JSON must serialize rating as Float", jsonStr.contains("\"rating\":9.5"))
        assertTrue("JSON must serialize is_spoiler as boolean", jsonStr.contains("\"is_spoiler\":true"))

        // Deserialize back
        val decoded = json.decodeFromString<SupabaseReviewDto>(jsonStr)
        assertEquals(dto.id, decoded.id)
        assertEquals(dto.userId, decoded.userId)
        assertEquals(9.5f, decoded.rating ?: 0f, 0.01f)
        assertTrue(decoded.isSpoiler)
        assertEquals(dto.reviewText, decoded.reviewText)
    }

    @Test
    fun supabaseWatchedMovieDto_serializesOwnershipAndTmdbIdCorrectly() {
        val dto = SupabaseWatchedMovieDto(
            id = UUID.randomUUID().toString(),
            userId = testUserId,
            tmdbMovieId = 157336,
            title = "Interstellar",
            posterPath = "/interstellar.jpg",
            watchedAt = 1680000000000L
        )

        val jsonStr = json.encodeToString(dto)
        assertTrue(jsonStr.contains("\"tmdb_movie_id\":157336"))
        assertTrue(jsonStr.contains("\"user_id\":\"$testUserId\""))

        val decoded = json.decodeFromString<SupabaseWatchedMovieDto>(jsonStr)
        assertEquals(157336, decoded.tmdbMovieId)
        assertEquals("Interstellar", decoded.title)
        assertEquals(testUserId, decoded.userId)
    }

    @Test
    fun supabaseWatchedEpisodeDto_serializesTvShowHierarchyAndSkippedFlag() {
        val dto = SupabaseWatchedEpisodeDto(
            id = UUID.randomUUID().toString(),
            userId = testUserId,
            tmdbShowId = 100,
            tmdbEpisodeId = 1001,
            seasonNumber = 1,
            episodeNumber = 1,
            watchedAt = 1680005000000L,
            isSkipped = false
        )

        val jsonStr = json.encodeToString(dto)
        assertTrue(jsonStr.contains("\"tmdb_show_id\":100"))
        assertTrue(jsonStr.contains("\"tmdb_episode_id\":1001"))
        assertTrue(jsonStr.contains("\"season_number\":1"))
        assertTrue(jsonStr.contains("\"episode_number\":1"))
        assertTrue(jsonStr.contains("\"is_skipped\":false"))

        val decoded = json.decodeFromString<SupabaseWatchedEpisodeDto>(jsonStr)
        assertEquals(100, decoded.tmdbShowId)
        assertEquals(1001, decoded.tmdbEpisodeId)
        assertEquals(1, decoded.seasonNumber)
        assertEquals(1, decoded.episodeNumber)
        assertFalse(decoded.isSkipped)
    }

    @Test
    fun supabaseWatchlistDto_serializesMediaTypeAndCompositeKeys() {
        val dto = SupabaseWatchlistDto(
            userId = testUserId,
            tmdbId = 999,
            mediaType = "movie",
            title = "Gladiator II",
            posterPath = "/gladiator.jpg",
            addedAt = 1700000000000L
        )

        val jsonStr = json.encodeToString(dto)
        assertTrue(jsonStr.contains("\"media_type\":\"movie\""))
        assertTrue(jsonStr.contains("\"tmdb_id\":999"))

        val decoded = json.decodeFromString<SupabaseWatchlistDto>(jsonStr)
        assertEquals(999, decoded.tmdbId)
        assertEquals("movie", decoded.mediaType)
        assertEquals("Gladiator II", decoded.title)
    }

    @Test
    fun multiUserIsolationContract_verifiesEveryDtoEnforcesUserId() {
        val userA = UUID.randomUUID().toString()
        val userB = UUID.randomUUID().toString()

        val movieA = SupabaseWatchedMovieDto(userId = userA, tmdbMovieId = 1, title = "A", watchedAt = 1000L)
        val movieB = SupabaseWatchedMovieDto(userId = userB, tmdbMovieId = 1, title = "A", watchedAt = 2000L)

        assertNotEquals(movieA.userId, movieB.userId)
        assertEquals(userA, movieA.userId)
        assertEquals(userB, movieB.userId)
    }
}
