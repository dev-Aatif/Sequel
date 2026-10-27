package dev.sequel.app.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sequel.app.presentation.util.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RedPillRatingSliderComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun redPillRatingSlider_rendersScoreAndStatusPhraseAccurately() {
        var currentRating by mutableFloatStateOf(7.5f)

        composeTestRule.setContent {
            RedPillRatingSlider(
                rating = currentRating,
                onRatingChange = { currentRating = it }
            )
        }

        // Verify container
        composeTestRule.onNodeWithTag(TestTags.SLIDER_RED_PILL).assertIsDisplayed()

        // Verify score text
        composeTestRule.onNodeWithTag(TestTags.TEXT_RATING_SCORE, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("7.5 / 10.0", substring = true)

        // Verify status phrase & vibe translation
        composeTestRule.onNodeWithTag(TestTags.TEXT_RATING_STATUS, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Respectably Solid", substring = true)

        composeTestRule.onNodeWithTag(TestTags.TEXT_RATING_VIBE, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Fun, competent", substring = true)
    }

    @Test
    fun redPillRatingSlider_swipingRightIncreasesRatingAndUpdatesPhrase() {
        var currentRating by mutableFloatStateOf(7.5f)

        composeTestRule.setContent {
            RedPillRatingSlider(
                rating = currentRating,
                onRatingChange = { currentRating = it }
            )
        }

        // Swipe right on the slider to increase score
        composeTestRule.onNodeWithTag(TestTags.SLIDER_RED_PILL).performTouchInput {
            swipeRight(startX = width * 0.5f, endX = width * 0.95f)
        }

        // Verify that rating increased past 8.0
        assertTrue("Rating should increase after swiping right, got $currentRating", currentRating >= 8.0f)

        // Verify UI reactive update
        composeTestRule.onNodeWithTag(TestTags.TEXT_RATING_SCORE, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun ratingModalBottomSheet_invokesSaveAndClearCallbacks() {
        var savedRating: Float? = null
        var cleared = false

        composeTestRule.setContent {
            RatingModalBottomSheet(
                initialRating = 8.5f,
                title = "Blade Runner 2049",
                seasonNumber = null,
                onDismissRequest = {},
                onSaveRating = { savedRating = it },
                onClearRating = { cleared = true }
            )
        }

        // Verify Sheet is displayed
        composeTestRule.onNodeWithTag(TestTags.SHEET_RATING).assertIsDisplayed()

        // Verify initial score
        composeTestRule.onNodeWithTag(TestTags.TEXT_RATING_SCORE, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("8.5 / 10.0", substring = true)

        // Click Save Button
        composeTestRule.onNodeWithTag(TestTags.BTN_SAVE_RATING, useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        assertNotNull("Save callback should have been invoked", savedRating)
        assertEquals(8.5f, savedRating!!, 0.01f)

        // Click Clear Button
        composeTestRule.onNodeWithTag(TestTags.BTN_CLEAR_RATING, useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        assertTrue("Clear callback should have been invoked", cleared)
    }
}
