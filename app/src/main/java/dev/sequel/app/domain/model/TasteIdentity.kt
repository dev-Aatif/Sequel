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
    val controversialTakes: List<ControversialTake> = emptyList(),
    val topAuteurTitles: List<String> = emptyList(),
    val totalTitlesWatched: Int = 0
)

/**
 * Returns a human-readable narrative explanation of the cinephile archetype.
 */
fun getArchetypeDescription(archetype: String): String = when (archetype) {
    "Neon Auteurist" -> "You gravitate towards atmospheric, visionary worldbuilding with high aesthetic discipline, neon melancholia, and existential depth."
    "The Noir Realist" -> "Drawn to shadowy morality, urban grit, high-stakes cynicism, and razor-sharp crime thrillers."
    "The Prestige Purist" -> "You revere meticulous craft, Oscar-tier performances, historical scale, and deeply human character studies."
    "The Cynical Visionary" -> "Unapologetically contrarian. You call out critical consensus slop and elevate misunderstood masterpieces."
    "The Midnight Hedonist" -> "Chasing macabre atmospheres, psychological tension, primal fears, and boundary-pushing genre cinema."
    "The Worldbuilder" -> "Immersed in sprawling mythologies, hand-crafted animation, and visionary speculative universes."
    "The Adrenaline Purist" -> "Kinetic spectacle, breathless choreography, and relentless momentum are your cinematic fuel."
    "The Aspiring Cinephile" -> "Your journey into cinema has just begun. Log more titles to unveil your deeper cinematic archetype."
    else -> "An omnivorous, curious palate exploring across borders, decades, and genres without limits."
}

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
