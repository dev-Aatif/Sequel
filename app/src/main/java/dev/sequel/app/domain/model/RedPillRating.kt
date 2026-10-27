package dev.sequel.app.domain.model

import androidx.compose.ui.graphics.Color
import kotlin.math.round

/**
 * Metadata for a given Red Pill rating tier.
 */
data class RedPillRatingTier(
    val icon: String,
    val scoreText: String,
    val statusPhrase: String,
    val vibeTranslation: String,
    val glowColor: Color,
    val verdictLabel: String
)

object RedPillRatingUtil {

    /**
     * Clamps and snaps any float value to the nearest 0.5 step within [0.5f, 10.0f].
     */
    fun clampAndSnapRating(value: Float): Float {
        val clamped = value.coerceIn(0.5f, 10.0f)
        val snapped = round(clamped * 2f) / 2f
        return snapped.coerceIn(0.5f, 10.0f)
    }

    /**
     * Formats rating as a clean 1-decimal string (e.g. "7.5", "10.0").
     */
    fun formatScore(score: Float): String = String.format(java.util.Locale.US, "%.1f", score)

    /**
     * Resolves the signature Red Pill tier based on the score.
     */
    fun getRatingTier(score: Float): RedPillRatingTier {
        val clamped = clampAndSnapRating(score)
        val scoreStr = formatScore(clamped)

        return when {
            clamped >= 10.0f -> RedPillRatingTier(
                icon = "👑",
                scoreText = "10.0 / 10.0",
                statusPhrase = "Generational Peak",
                vibeTranslation = "Flawless; once-a-decade cultural reset. Zero notes.",
                glowColor = Color(0xFFF43F5E), // Crimson Glow
                verdictLabel = "Absolute Cinema"
            )
            clamped >= 9.0f -> RedPillRatingTier(
                icon = "🔥",
                scoreText = "$scoreStr / 10.0",
                statusPhrase = "Certified Masterclass",
                vibeTranslation = "Elite craft and execution; near perfection with minor nitpicks.",
                glowColor = Color(0xFFE11D48), // Crimson Glow
                verdictLabel = "Absolute Cinema"
            )
            clamped >= 8.0f -> RedPillRatingTier(
                icon = "⚡",
                scoreText = "$scoreStr / 10.0",
                statusPhrase = "Valid Banger",
                vibeTranslation = "Extremely good; no skips, high replay value, instantly recommended.",
                glowColor = Color(0xFF14B8A6), // Teal Glow
                verdictLabel = "Certified Banger"
            )
            clamped >= 7.0f -> RedPillRatingTier(
                icon = "🥪",
                scoreText = "$scoreStr / 10.0",
                statusPhrase = "Respectably Solid",
                vibeTranslation = "Fun, competent, and respects your time, even if formulaic.",
                glowColor = if (clamped >= 7.5f) Color(0xFF14B8A6) else Color(0xFF71717A),
                verdictLabel = if (clamped >= 7.5f) "Certified Banger" else "Decent / Mid"
            )
            clamped >= 6.0f -> RedPillRatingTier(
                icon = "📺",
                scoreText = "$scoreStr / 10.0",
                statusPhrase = "Second-Screen Core",
                vibeTranslation = "Inoffensive NPC entertainment; perfect for watching while scrolling your phone.",
                glowColor = Color(0xFF71717A), // Neutral Dim
                verdictLabel = "Decent / Mid"
            )
            clamped >= 5.0f -> RedPillRatingTier(
                icon = "📉",
                scoreText = "$scoreStr / 10.0",
                statusPhrase = "Aggressively Mid",
                vibeTranslation = "Neither good nor terrible enough to be memorable; pure unseasoned oatmeal.",
                glowColor = Color(0xFF71717A), // Neutral Dim
                verdictLabel = "Decent / Mid"
            )
            clamped >= 3.5f -> RedPillRatingTier(
                icon = "💀",
                scoreText = "$scoreStr / 10.0",
                statusPhrase = "Dopamine Sinkhole",
                vibeTranslation = "Actively boring or poorly paced; you feel your lifespan leaving your body.",
                glowColor = Color(0xFF475569), // Muted Slate
                verdictLabel = "Blue Pill"
            )
            else -> RedPillRatingTier(
                icon = "☣️",
                scoreText = "$scoreStr / 10.0",
                statusPhrase = "Terminal Biohazard",
                vibeTranslation = "Complete catastrophe; offensive to the senses. Avoid at all costs.",
                glowColor = Color(0xFF334155), // Muted Slate
                verdictLabel = "Blue Pill"
            )
        }
    }
}
