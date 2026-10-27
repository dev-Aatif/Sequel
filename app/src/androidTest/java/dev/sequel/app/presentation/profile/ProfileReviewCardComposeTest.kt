package dev.sequel.app.presentation.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sequel.app.data.local.entity.ProfileReviewItem
import dev.sequel.app.presentation.screens.profile.ProfileReviewCard
import dev.sequel.app.presentation.screens.profile.ProfileTab
import dev.sequel.app.presentation.screens.profile.ProfileTabSwitcher
import dev.sequel.app.presentation.util.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileReviewCardComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun profileReviewCard_rendersReviewTextEmotionChipAndRatingBadge() {
        var clicked = false

        val sampleReview = ProfileReviewItem(
            id = 42L,
            mediaId = 550,
            mediaType = "movie",
            title = "Fight Club",
            posterPath = "/sample.jpg",
            firstAirDate = "1999-10-15",
            seasonNum = null,
            episodeNum = null,
            reviewText = "First rule of Fight Club is you do not talk about Fight Club.",
            rating = 9f,
            isSpoiler = false,
            primaryEmotion = "Absolute Cinema",
            reactionCount = 15
        )

        composeTestRule.setContent {
            ProfileReviewCard(
                review = sampleReview,
                onClick = { clicked = true }
            )
        }

        // Verify title & poster card container
        composeTestRule.onNodeWithTag(TestTags.CARD_PROFILE_REVIEW).assertIsDisplayed()
        composeTestRule.onNodeWithText("Fight Club", useUnmergedTree = true).assertIsDisplayed()

        // Verify Red Pill rating badge
        composeTestRule.onNodeWithTag(TestTags.BADGE_RED_PILL, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.BADGE_RED_PILL, useUnmergedTree = true).assertTextContains("9/10", substring = true)

        // Verify Primary Emotion chip
        composeTestRule.onNodeWithTag(TestTags.CHIP_EMOTION, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.CHIP_EMOTION, useUnmergedTree = true).assertTextContains("Absolute Cinema", substring = true)

        // Verify review text snippet
        composeTestRule.onNodeWithTag(TestTags.TEXT_REVIEW_SNIPPET, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.TEXT_REVIEW_SNIPPET, useUnmergedTree = true).assertTextContains("First rule of Fight Club", substring = true)

        // Verify card click triggers callback
        composeTestRule.onNodeWithTag(TestTags.CARD_PROFILE_REVIEW).performClick()
        assertTrue(clicked)
    }

    @Test
    fun profileReviewCard_rendersSpoilerTagWhenSpoilerIsTrue() {
        val sampleSpoilerReview = ProfileReviewItem(
            id = 43L,
            mediaId = 1396,
            mediaType = "tv",
            title = "Breaking Bad",
            posterPath = "/sample_bb.jpg",
            firstAirDate = "2008-01-20",
            seasonNum = 5,
            episodeNum = 14,
            reviewText = "Major climax: Hank discovers Walt's identity.",
            rating = 10f,
            isSpoiler = true,
            primaryEmotion = "Mindfuck",
            reactionCount = 88
        )

        composeTestRule.setContent {
            ProfileReviewCard(
                review = sampleSpoilerReview,
                onClick = { }
            )
        }

        // Verify spoiler badge is displayed
        composeTestRule.onNodeWithTag(TestTags.TAG_SPOILER, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.TAG_SPOILER, useUnmergedTree = true).assertTextContains("SPOILER", substring = true)

        // Verify emotion chip displays Mindfuck
        composeTestRule.onNodeWithTag(TestTags.CHIP_EMOTION, useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.CHIP_EMOTION, useUnmergedTree = true).assertTextContains("Mindfuck", substring = true)
    }

    @Test
    fun profileTabSwitcher_switchesBetweenWatchHistoryAndReviews() {
        var selectedTab: ProfileTab? = null

        composeTestRule.setContent {
            ProfileTabSwitcher(
                selectedTab = ProfileTab.WATCH_HISTORY,
                onTabSelected = { selectedTab = it }
            )
        }

        // Verify both tabs are displayed
        composeTestRule.onNodeWithTag(TestTags.TAB_WATCH_HISTORY).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.TAB_MY_REVIEWS).assertIsDisplayed()

        // Click My Reviews tab and assert callback receives ProfileTab.REVIEWS
        composeTestRule.onNodeWithTag(TestTags.TAB_MY_REVIEWS).performClick()
        assertEquals(ProfileTab.REVIEWS, selectedTab)
    }
}
