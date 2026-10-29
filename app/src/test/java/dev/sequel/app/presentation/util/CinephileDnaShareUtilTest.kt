package dev.sequel.app.presentation.util

import dev.sequel.app.domain.model.ControversialTake
import dev.sequel.app.domain.model.TasteIdentity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinephileDnaShareUtilTest {

    @Test
    fun createShareText_includesAllKeyDnaMetricsAndPlayStoreCta() {
        val identity = TasteIdentity(
            handle = "@tarantino_fan",
            archetype = "The Noir Realist",
            primaryObsession = "Noir & Crime Thrillers",
            primaryObsessionHours = 45,
            topAuteur = "Quentin Tarantino",
            topAuteurWatchedCount = 7,
            totalHoursLogged = 130,
            controversialTakes = listOf(
                ControversialTake(
                    mediaId = 101,
                    mediaType = "movie",
                    title = "The Hateful Eight",
                    posterPath = null,
                    userRating = 9.5f,
                    criticRating = 7.1,
                    isHotTake = true
                )
            ),
            topAuteurTitles = listOf("Pulp Fiction", "Django Unchained", "The Hateful Eight"),
            totalTitlesWatched = 24
        )

        val shareText = CinephileDnaShareUtil.createShareText(identity)

        // Metrics presence
        assertTrue(shareText.contains("🧬 CINEPHILE DNA • @tarantino_fan"))
        assertTrue(shareText.contains("Archetype: The Noir Realist"))
        assertTrue(shareText.contains("Primary Obsession: Noir & Crime Thrillers (45h logged)"))
        assertTrue(shareText.contains("Top Auteur: Quentin Tarantino (7 logged)"))
        assertTrue(shareText.contains("Total Screen Time: 130 hrs logged"))

        // Hot take presence
        assertTrue(shareText.contains("Hot Take: The Hateful Eight — Defended vs Critics"))
        assertTrue(shareText.contains("User: 9/10 vs TMDB: 7.1"))

        // Text CTA hook & Play Store presence
        assertTrue(shareText.contains("What does your film taste say about you?"))
        assertTrue(shareText.contains("Try out Sequel on the Google Play Store to reveal your Cinephile DNA"))
        assertTrue(shareText.contains("Download Sequel on Google Play:"))
        assertTrue(shareText.contains("https://play.google.com/store/apps/details?id=dev.sequel.app"))

        // No fictional website URLs
        assertFalse(shareText.contains("https://sequel.app"))
    }
}
