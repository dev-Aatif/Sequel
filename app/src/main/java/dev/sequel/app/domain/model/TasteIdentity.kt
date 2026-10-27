package dev.sequel.app.domain.model

/**
 * Represents a title where the user's rating diverges significantly from the TMDB critic / community consensus.
 *
 * - When [isHotTake] is true (User Loved): userRating >= 8.5 (or >= 8.0) while criticRating <= 6.5.
 * - When [isHotTake] is false (Slop Called Out): userRating <= 5.0 while criticRating >= 7.5.
 */
data class ControversialTake(
    val mediaId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String? = null,
    val userRating: Float,
    val criticRating: Double,
    val isHotTake: Boolean
) {
    val discrepancy: Double get() = kotlin.math.abs(userRating.toDouble() - criticRating)
}

/**
 * Algorithmic Cinephile Identity synthesized from local watch history and user ratings.
 */
data class TasteIdentity(
    val handle: String,
    val archetype: String,
    val primaryObsession: String,
    val primaryObsessionHours: Int,
    val topAuteur: String,
    val topAuteurWatchedCount: Int,
    val totalHoursLogged: Int,
    val controversialTakes: List<ControversialTake> = emptyList()
)

/**
 * Normalized record representing a watched title for algorithmic taste analysis.
 */
data class TasteWatchRecord(
    val mediaId: Int,
    val mediaType: String,
    val title: String,
    val genres: List<String> = emptyList(),
    val runtimeMinutes: Int = 0,
    val watchCount: Int = 1,
    val director: String? = null,
    val tmdbRating: Double = 0.0,
    val userRating: Float? = null,
    val posterPath: String? = null
)
