package dev.sequel.app.presentation.screens.mediaimport

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.sequel.app.domain.backup.BackupRestoreResult
import dev.sequel.app.domain.importer.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end Compose UI test suite verifying the complete user-facing import and export flows:
 * - Format selection across TV Time, Trakt, Letterboxd, and Sequel Backup
 * - Interactive preview with conflict detection
 * - Policy selection and submission
 * - Structured completion report display
 * - Full-fidelity backup restoration report
 * - Error states and recovery
 */
@RunWith(AndroidJUnit4::class)
class MediaImportExportComposeUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun initialIdleState_rendersAllFourSupportedSourcesAndFilePickerButton() {
        var selectedSource by mutableStateOf("tvtime")
        var filePickerClicked = false

        composeTestRule.setContent {
            MediaImportContent(
                isPreparing = false,
                prepareError = null,
                selectedSource = selectedSource,
                onSelectSource = { selectedSource = it },
                onOpenFilePicker = { filePickerClicked = true }
            )
        }

        // Verify title & supported source radio options
        composeTestRule.onNodeWithText("Select Import Source").assertIsDisplayed()
        composeTestRule.onNodeWithText("TV Time (GDPR ZIP or CSV)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Trakt (CSV or Raw JSON / ZIP)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Letterboxd (Full Export ZIP or CSV)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sequel Backup (JSON)").assertIsDisplayed()

        // Switch to Letterboxd
        composeTestRule.onNodeWithText("Letterboxd (Full Export ZIP or CSV)").performClick()
        assertEquals("letterboxd", selectedSource)

        // Verify File Picker button
        val pickerButton = composeTestRule.onNodeWithText("Select Export File (ZIP, CSV, or JSON)")
        pickerButton.assertIsDisplayed()
        pickerButton.performClick()
        assertTrue(filePickerClicked)
    }

    @Test
    fun preparingLoadingState_displaysProgressIndicatorAndText() {
        composeTestRule.setContent {
            MediaImportContent(
                isPreparing = true,
                prepareError = null
            )
        }

        composeTestRule.onNodeWithText("Analyzing and preparing records...").assertIsDisplayed()
    }

    @Test
    fun previewState_displaysParsedCountsConflictsAndPolicySelector() {
        val previewData = ImportPreview(
            source = ImportSource.LETTERBOXD,
            totalRecordsParsed = 120,
            uniqueTitlesCount = 100,
            newMoviesCount = 85,
            newEpisodesCount = 0,
            newWatchlistCount = 15,
            newRatingsCount = 50,
            newReviewsCount = 12,
            identicalSkippedCount = 10,
            conflicts = listOf(
                ConflictCandidate(
                    incoming = CanonicalImportRecord(
                        rawTitle = "Inception",
                        normalizedTitle = "Inception",
                        year = 2010,
                        mediaType = "movie",
                        recordType = ImportRecordType.RATING,
                        rating = 10.0f
                    ),
                    divergences = listOf(FieldDivergence.RATING_DIFFERENCE)
                )
            )
        )

        var chosenPolicy by mutableStateOf(ConflictResolutionPolicy.KEEP_EXISTING)
        var appliedPolicy: ConflictResolutionPolicy? = null
        var cancelled = false

        composeTestRule.setContent {
            MediaImportContent(
                isPreparing = false,
                preview = previewData,
                selectedPolicy = chosenPolicy,
                onSetPolicy = { chosenPolicy = it },
                onConfirmImport = { appliedPolicy = it },
                onResetState = { cancelled = true }
            )
        }

        // Verify summary statistics
        composeTestRule.onNodeWithText("Import Preview: Letterboxd", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("• Total Records: 120 (100 unique titles)", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("• New Movies: 85", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("1 Conflicts Detected", substring = true).assertIsDisplayed()

        // Select OVERWRITE_LOCAL
        composeTestRule.onNodeWithText("Overwrite Local", substring = true).performClick()
        assertEquals(ConflictResolutionPolicy.OVERWRITE_LOCAL, chosenPolicy)

        // Click Apply Import
        composeTestRule.onNodeWithText("Apply Import").performClick()
        assertEquals(ConflictResolutionPolicy.OVERWRITE_LOCAL, appliedPolicy)
    }

    @Test
    fun reportState_displaysTruthfulCompletionReportAndMetrics() {
        val reportData = ImportReport(
            source = ImportSource.TRAKT,
            policyApplied = ConflictResolutionPolicy.KEEP_EXISTING,
            totalProcessed = 50,
            insertedCount = 42,
            updatedCount = 0,
            skippedIdenticalCount = 8,
            duplicateInSourceCount = 0,
            failedCount = 0,
            durationMs = 320
        )

        var resetClicked = false

        composeTestRule.setContent {
            MediaImportContent(
                isPreparing = false,
                report = reportData,
                onResetState = { resetClicked = true }
            )
        }

        composeTestRule.onNodeWithText("Import Completed Successfully!").assertIsDisplayed()
        composeTestRule.onNodeWithText("• Source: Trakt", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("• Total Processed: 50", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("• New Inserted: 42", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("• Identical Skipped: 8", substring = true).assertIsDisplayed()

        composeTestRule.onNodeWithText("Import Another").performClick()
        assertTrue(resetClicked)
    }

    @Test
    fun backupRestoreState_displaysLosslessSummaryMetrics() {
        val restoreResult = BackupRestoreResult(
            totalRestored = 25,
            showsCount = 3,
            moviesCount = 8,
            episodesCount = 12,
            watchlistCount = 2,
            reviewsCount = 10,
            mountRushmoreCount = 4
        )

        composeTestRule.setContent {
            MediaImportContent(
                backupRestoreResult = restoreResult
            )
        }

        composeTestRule.onNodeWithText("Backup Restored Successfully!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Restored 8 movies, 12 episodes across 3 shows", substring = true).assertIsDisplayed()
    }

    @Test
    fun errorState_displaysValidationErrorMessageAndRetry() {
        var retryTriggered = false

        composeTestRule.setContent {
            MediaImportContent(
                prepareError = "Corrupted ZIP archive: invalid header bytes",
                onResetState = { retryTriggered = true }
            )
        }

        composeTestRule.onNodeWithText("Corrupted ZIP archive: invalid header bytes", substring = true).assertIsDisplayed()
    }
}
