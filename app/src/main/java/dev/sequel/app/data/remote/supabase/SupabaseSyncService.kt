package dev.sequel.app.data.remote.supabase

import dev.sequel.app.data.remote.supabase.dto.SupabaseReviewDto
import dev.sequel.app.data.remote.supabase.dto.SupabaseWatchedEpisodeDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles CRUD operations against Supabase Postgrest tables
 * for syncing local data to the cloud.
 */
@Singleton
class SupabaseSyncService @Inject constructor(
    private val supabaseClient: SupabaseClient
) {

    // ── Watched Episodes ──────────────────────────────────────────

    /**
     * Upsert a watched episode to Supabase.
     * @return The Supabase-generated UUID for the record.
     */
    suspend fun upsertWatchedEpisode(dto: SupabaseWatchedEpisodeDto): String {
        val result = supabaseClient.postgrest[TABLE_WATCHED_EPISODES]
            .upsert(dto) {
                select(Columns.list("id"))
            }
            .decodeSingle<SupabaseWatchedEpisodeDto>()
        return result.id ?: throw IllegalStateException("Supabase did not return an ID")
    }

    /**
     * Batch upsert watched episodes.
     * @return List of Supabase UUIDs.
     */
    suspend fun upsertWatchedEpisodes(dtos: List<SupabaseWatchedEpisodeDto>): List<String> {
        if (dtos.isEmpty()) return emptyList()
        val results = supabaseClient.postgrest[TABLE_WATCHED_EPISODES]
            .upsert(dtos) {
                select(Columns.list("id"))
            }
            .decodeList<SupabaseWatchedEpisodeDto>()
        return results.mapNotNull { it.id }
    }

    /**
     * Delete a watched episode from Supabase by its UUID.
     */
    suspend fun deleteWatchedEpisode(supabaseId: String) {
        supabaseClient.postgrest[TABLE_WATCHED_EPISODES]
            .delete {
                filter {
                    eq("id", supabaseId)
                }
            }
    }

    /**
     * Fetch all watched episodes for the current user from Supabase.
     */
    suspend fun fetchAllWatchedEpisodes(userId: String): List<SupabaseWatchedEpisodeDto> {
        return supabaseClient.postgrest[TABLE_WATCHED_EPISODES]
            .select {
                filter {
                    eq("user_id", userId)
                }
            }
            .decodeList()
    }

    // ── Reviews ───────────────────────────────────────────────────

    /**
     * Upsert a review to Supabase.
     * @return The Supabase-generated UUID.
     */
    suspend fun upsertReview(dto: SupabaseReviewDto): String {
        val result = supabaseClient.postgrest[TABLE_REVIEWS]
            .upsert(dto) {
                select(Columns.list("id"))
            }
            .decodeSingle<SupabaseReviewDto>()
        return result.id ?: throw IllegalStateException("Supabase did not return an ID")
    }

    /**
     * Delete a review from Supabase by its UUID.
     */
    suspend fun deleteReview(supabaseId: String) {
        supabaseClient.postgrest[TABLE_REVIEWS]
            .delete {
                filter {
                    eq("id", supabaseId)
                }
            }
    }

    /**
     * Fetch all reviews for the current user from Supabase.
     */
    suspend fun fetchAllReviews(userId: String): List<SupabaseReviewDto> {
        return supabaseClient.postgrest[TABLE_REVIEWS]
            .select {
                filter {
                    eq("user_id", userId)
                }
            }
    }

    // ── Watchlist ─────────────────────────────────────────────────

    companion object {
        const val TABLE_WATCHED_EPISODES = "watched_episodes"
        const val TABLE_REVIEWS = "reviews"
        const val TABLE_WATCHLIST = "user_watchlist"
    }

    suspend fun upsertWatchlist(dtos: List<dev.sequel.app.data.remote.supabase.dto.SupabaseWatchlistDto>) {
        if (dtos.isEmpty()) return
        supabaseClient.postgrest[TABLE_WATCHLIST].upsert(dtos)
    }

    suspend fun deleteFromWatchlist(userId: String, tmdbId: Int) {
        supabaseClient.postgrest[TABLE_WATCHLIST].delete {
            filter {
                eq("user_id", userId)
                eq("tmdb_id", tmdbId)
            }
        }
    }
}
