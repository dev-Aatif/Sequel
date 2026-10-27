package dev.sequel.app.presentation.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sequel.app.domain.model.ControversialTake
import dev.sequel.app.domain.model.TasteIdentity
import dev.sequel.app.presentation.screens.profile.components.TasteIdentityCard
import dev.sequel.app.presentation.util.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TasteIdentityCardComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tasteIdentityCard_rendersDnaMetricsAndFiresShareCallback() {
        var sharedIdentity: TasteIdentity? = null

        val testIdentity = TasteIdentity(
            handle = "@neo",
            archetype = "Neon Auteurist",
            primaryObsession = "Cyberpunk & Sci-Fi",
            primaryObsessionHours = 48,
            topAuteur = "Denis Villeneuve",
            topAuteurWatchedCount = 6,
            totalHoursLogged = 125,
            controversialTakes = listOf(
                ControversialTake(
                    mediaId = 1,
                    mediaType = "movie",
                    title = "Blade Runner 2049",
                    posterPath = null,
                    userRating = 10.0f,
                    criticRating = 8.0,
                    isHotTake = true
                )
            )
        )

        composeTestRule.setContent {
            TasteIdentityCard(
                identity = testIdentity,
                onShareClick = { sharedIdentity = it }
            )
        }

        // Verify the card container is displayed
        composeTestRule.onNodeWithTag(TestTags.CARD_TASTE_IDENTITY).assertIsDisplayed()

        // Verify archetype pill
        composeTestRule.onNodeWithTag(TestTags.TEXT_TASTE_ARCHETYPE, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Neon Auteurist", substring = true)

        // Verify primary obsession
        composeTestRule.onNodeWithTag(TestTags.TEXT_TASTE_PRIMARY_OBSESSION, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Cyberpunk & Sci-Fi", substring = true)

        // Verify top auteur
        composeTestRule.onNodeWithTag(TestTags.TEXT_TASTE_TOP_AUTEUR, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Denis Villeneuve", substring = true)

        // Verify total hours
        composeTestRule.onNodeWithTag(TestTags.TEXT_TASTE_HOURS, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("125 hrs logged", substring = true)

        // Verify share button click callback
        composeTestRule.onNodeWithTag(TestTags.BTN_SHARE_TASTE_CARD, useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        assertNotNull(sharedIdentity)
        assertEquals("@neo", sharedIdentity?.handle)
        assertEquals("Neon Auteurist", sharedIdentity?.archetype)
    }
}
