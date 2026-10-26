package dev.sequel.app.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * End-to-end Compose UI instrumentation test.
 * Verifies that MainActivity launches with Hilt dependency injection
 * and renders the root composable tree without crashing.
 */
@HiltAndroidTest
class HomeScreenSmokeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun mainActivity_bootsWithHiltAndRendersRootScaffold() {
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().assertIsDisplayed()
    }
}
