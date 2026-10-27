package dev.sequel.app.data.local.entity

/**
 * POJO projection representing a user-authored review joined with show metadata.
 * Designed for offline-first reactive display on the user's Profile screen.
 */
data class ProfileReviewItem(
    val id: Long,
    val mediaId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String?,
    val firstAirDate: String? = null,
    val seasonNum: Int? = null,
    val episodeNum: Int? = null,
    val reviewText: String?,
    val rating: Float? = null,
    val isSpoiler: Boolean = false,
    val isRatingOnly: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val primaryEmotion: String? = null,
    val reactionCount: Int = 0
) {
    val year: String? get() = firstAirDate?.take(4)

    val displayEmotion: String
        get() = primaryEmotion ?: when {
            rating != null && rating >= 9f -> "Absolute Cinema"
            rating != null && rating >= 7f -> "Mindfuck"
            rating != null && rating >= 5f -> "Comfort Watch"
            rating != null && rating >= 3f -> "Mid"
            else -> "Tearjerker"
        }

    val emotionEmoji: String
        get() = when (displayEmotion) {
            "Absolute Cinema" -> "🏛️"
            "Mindfuck" -> "🌀"
            "Visual Feast" -> "🎨"
            "Tearjerker" -> "💔"
            "Comfort Watch" -> "☕"
            "Mid" -> "🥱"
            "Brain-Dead Fun" -> "🍿"
            "Heart-Pounding" -> "⚡"
            "Existential Dread" -> "🕳️"
            "Amused / Hilarious" -> "😂"
            "Disgusted / Puke" -> "🤮"
            "Euphoric / Happy" -> "✨"
            else -> "🎬"
        }
}
