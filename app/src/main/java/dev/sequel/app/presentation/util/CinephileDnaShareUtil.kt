package dev.sequel.app.presentation.util

import android.content.Context
import android.content.Intent
import dev.sequel.app.domain.model.TasteIdentity

/**
 * Utility for formatting and launching viral Cinephile DNA share intents.
 * Includes a text call-to-action prompting recipients to try out and download Sequel on Google Play.
 */
object CinephileDnaShareUtil {

    const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=dev.sequel.app"

    /**
     * Builds viral, high-engagement share text highlighting the user's cinematic identity,
     * top hot takes, and a call-to-action asking recipients to download Sequel on the Play Store.
     */
    fun createShareText(identity: TasteIdentity): String = buildString {
        appendLine("🧬 CINEPHILE DNA • ${identity.handle}")
        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine("🎭 Archetype: ${identity.archetype}")
        appendLine("🔥 Primary Obsession: ${identity.primaryObsession} (${identity.primaryObsessionHours}h logged)")

        val auteurCountSuffix = if (identity.topAuteurWatchedCount > 0) " (${identity.topAuteurWatchedCount} logged)" else ""
        appendLine("🎬 Top Auteur: ${identity.topAuteur}$auteurCountSuffix")
        appendLine("⏱ Total Screen Time: ${identity.totalHoursLogged} hrs logged")

        val hotTake = identity.controversialTakes.firstOrNull()
        if (hotTake != null) {
            val label = if (hotTake.isHotTake) "Defended vs Critics" else "Called Out Slop"
            appendLine("💊 Hot Take: ${hotTake.title} — $label (User: ${hotTake.userRating.toInt()}/10 vs TMDB: ${"%.1f".format(hotTake.criticRating)})")
        }

        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━")
        appendLine("✨ What does your film taste say about you?")
        appendLine("Try out Sequel on the Google Play Store to reveal your Cinephile DNA, rate on the Red Pill scale & own your watch history offline!")
        appendLine()
        appendLine("📲 Download Sequel on Google Play:")
        appendLine(PLAY_STORE_URL)
    }

    /**
     * Launches the system share sheet with the Cinephile DNA payload.
     */
    fun shareCinephileDna(context: Context, identity: TasteIdentity) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, createShareText(identity))
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, "Share Your Cinephile DNA")
        context.startActivity(chooser)
    }
}
