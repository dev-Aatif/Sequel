package dev.sequel.app.presentation.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.sequel.app.data.local.entity.MountRushmoreEntity
import dev.sequel.app.presentation.screens.profile.MountRushmoreSection
import dev.sequel.app.presentation.screens.profile.MountRushmoreSlotUi
import dev.sequel.app.presentation.util.TestTags
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MountRushmoreComposeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mountRushmoreSection_rendersFourEmptySlotsAndHandlesClick() {
        var clickedSlot: Int? = null
        val emptySlots = (0..3).map { MountRushmoreSlotUi(slotIndex = it, item = null) }

        composeTestRule.setContent {
            MountRushmoreSection(
                slots = emptySlots,
                onEmptySlotClick = { clickedSlot = it },
                onFilledSlotClick = { _, _ -> }
            )
        }

        // Verify all 4 empty slots are displayed with their semantic test tags
        for (i in 0..3) {
            composeTestRule.onNodeWithTag(TestTags.rushSlotEmpty(i)).assertIsDisplayed()
        }

        // Click slot 0 and verify callback
        composeTestRule.onNodeWithTag(TestTags.rushSlotEmpty(0)).performClick()
        assertEquals(0, clickedSlot)

        // Click slot 2 and verify callback
        composeTestRule.onNodeWithTag(TestTags.rushSlotEmpty(2)).performClick()
        assertEquals(2, clickedSlot)
    }

    @Test
    fun mountRushmoreSection_rendersFilledSlotsAndHandlesClick() {
        var clickedFilledSlot: Pair<Int, MountRushmoreEntity>? = null

        val sampleEntity = MountRushmoreEntity(
            slotIndex = 0,
            mediaId = 550,
            mediaType = "movie",
            title = "Fight Club",
            posterPath = "/sample.jpg",
            year = "1999",
            rating = 8.4f
        )

        val slots = listOf(
            MountRushmoreSlotUi(slotIndex = 0, item = sampleEntity),
            MountRushmoreSlotUi(slotIndex = 1, item = null),
            MountRushmoreSlotUi(slotIndex = 2, item = null),
            MountRushmoreSlotUi(slotIndex = 3, item = null)
        )

        composeTestRule.setContent {
            MountRushmoreSection(
                slots = slots,
                onEmptySlotClick = { },
                onFilledSlotClick = { slot, item -> clickedFilledSlot = slot to item }
            )
        }

        // Verify slot 0 is rendered as filled
        composeTestRule.onNodeWithTag(TestTags.rushSlotFilled(0)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Fight Club").assertIsDisplayed()

        // Slots 1..3 remain empty
        composeTestRule.onNodeWithTag(TestTags.rushSlotEmpty(1)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.rushSlotEmpty(2)).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TestTags.rushSlotEmpty(3)).assertIsDisplayed()

        // Click filled slot 0 and verify callback receives slot and entity
        composeTestRule.onNodeWithTag(TestTags.rushSlotFilled(0)).performClick()
        assertEquals(0, clickedFilledSlot?.first)
        assertEquals("Fight Club", clickedFilledSlot?.second?.title)
    }
}
