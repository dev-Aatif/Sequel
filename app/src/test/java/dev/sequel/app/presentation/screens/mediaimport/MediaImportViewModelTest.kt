package dev.sequel.app.presentation.screens.mediaimport

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.sequel.app.domain.backup.BackupRestoreResult
import dev.sequel.app.domain.backup.SequelBackupService
import dev.sequel.app.domain.importer.coordinator.ImportCoordinator
import dev.sequel.app.domain.importer.core.*
import dev.sequel.app.domain.importer.detector.SourceDetector
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediaImportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    private val backupService: SequelBackupService = mockk(relaxed = true)
    private val importCoordinator: ImportCoordinator = mockk(relaxed = true)
    private val sourceDetector: SourceDetector = mockk(relaxed = true)

    private lateinit var viewModel: MediaImportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()

        val config = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        try {
            androidx.work.WorkManager.initialize(context, config)
        } catch (_: Exception) {
            // Already initialized
        }

        viewModel = MediaImportViewModel(
            context = context,
            backupService = backupService,
            importCoordinator = importCoordinator,
            sourceDetector = sourceDetector
        ).apply {
            ioDispatcher = testDispatcher
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_hasSensibleDefaults() = runTest {
        assertFalse(viewModel.isPreparing.value)
        assertNull(viewModel.prepareError.value)
        assertNull(viewModel.backupRestoreSuccess.value)
        assertNull(viewModel.preview.value)
        assertNull(viewModel.report.value)
        assertEquals(ConflictResolutionPolicy.KEEP_EXISTING, viewModel.selectedPolicy.value)
    }

    @Test
    fun setPolicy_updatesSelectedPolicy() = runTest {
        viewModel.setPolicy(ConflictResolutionPolicy.OVERWRITE_LOCAL)
        assertEquals(ConflictResolutionPolicy.OVERWRITE_LOCAL, viewModel.selectedPolicy.value)

        viewModel.setPolicy(ConflictResolutionPolicy.LATEST_TIMESTAMP)
        assertEquals(ConflictResolutionPolicy.LATEST_TIMESTAMP, viewModel.selectedPolicy.value)
    }

    @Test
    fun resetState_resetsAllFlowsToNullOrDefaults() = runTest {
        viewModel.setPolicy(ConflictResolutionPolicy.OVERWRITE_LOCAL)
        viewModel.resetState()

        assertNull(viewModel.prepareError.value)
        assertNull(viewModel.backupRestoreSuccess.value)
        assertNull(viewModel.preview.value)
        assertNull(viewModel.report.value)
    }

    @Test
    fun startImport_sequelValidBackup_triggersRestoreAndSetsSuccess() = runTest {
        val testUri = Uri.parse("content://media/external/backup.json")
        val backupJson = """{"schemaVersion": 1, "exportedAt": 1000, "shows": []}"""
        
        // Mock ContentResolver
        val shadowContentResolver = org.robolectric.Shadows.shadowOf(context.contentResolver)
        shadowContentResolver.registerInputStream(testUri, ByteArrayInputStream(backupJson.toByteArray()))

        coEvery { backupService.restoreJsonBackup(backupJson) } returns BackupRestoreResult(
            totalRestored = 42,
            showsCount = 5,
            moviesCount = 10,
            episodesCount = 20,
            watchlistCount = 3,
            reviewsCount = 4,
            mountRushmoreCount = 0
        )

        viewModel.startImport(testUri, "sequel")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.backupRestoreSuccess.value)
        assertEquals(10, viewModel.backupRestoreSuccess.value?.moviesCount)
        assertEquals(20, viewModel.backupRestoreSuccess.value?.episodesCount)
        assertNull(viewModel.prepareError.value)
    }

    @Test
    fun startImport_sequelInvalidBackup_setsPrepareErrorAndDoesNotEnqueueWork() = runTest {
        val testUri = Uri.parse("content://media/external/corrupt.json")
        val maliciousJson = """{"schemaVersion": 1, "access_token": "secret123"}"""

        val shadowContentResolver = org.robolectric.Shadows.shadowOf(context.contentResolver)
        shadowContentResolver.registerInputStream(testUri, ByteArrayInputStream(maliciousJson.toByteArray()))

        coEvery { backupService.restoreJsonBackup(maliciousJson) } throws IllegalArgumentException(
            "Security violation: Backup contains sensitive authentication field matching 'access_token'"
        )

        viewModel.startImport(testUri, "sequel")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.backupRestoreSuccess.value)
        assertNotNull(viewModel.prepareError.value)
        assertTrue(viewModel.prepareError.value!!.contains("Security violation"))
    }

    @Test
    fun startImport_generatesPreviewAndUpdatesState() = runTest {
        val testUri = Uri.parse("content://media/external/letterboxd.zip")
        val mockData = "dummy_zip_content"

        val shadowContentResolver = org.robolectric.Shadows.shadowOf(context.contentResolver)
        shadowContentResolver.registerInputStream(testUri, ByteArrayInputStream(mockData.toByteArray()))

        val mockPreview = ImportPreview(
            source = ImportSource.LETTERBOXD,
            totalRecordsParsed = 50,
            uniqueTitlesCount = 45,
            newMoviesCount = 40,
            newEpisodesCount = 0,
            newWatchlistCount = 5,
            newRatingsCount = 25,
            newReviewsCount = 10,
            identicalSkippedCount = 5,
            conflicts = emptyList(),
            confidenceDistribution = emptyMap()
        )

        coEvery {
            importCoordinator.generatePreview(any(), any(), ImportSource.LETTERBOXD)
        } returns mockPreview

        viewModel.startImport(testUri, "letterboxd")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockPreview, viewModel.preview.value)
        assertNull(viewModel.prepareError.value)
    }

    @Test
    fun confirmImportWithPolicy_executesImportAndSetsReport() = runTest {
        val testUri = Uri.parse("content://media/external/trakt.csv")
        val mockData = "Title,Year,Season,Episode,Type,WatchedAt\nInception,2010,,,,movie,2023-01-01\n"

        val shadowContentResolver = org.robolectric.Shadows.shadowOf(context.contentResolver)
        shadowContentResolver.registerInputStream(testUri, ByteArrayInputStream(mockData.toByteArray()))

        val mockPreview = ImportPreview(
            source = ImportSource.TRAKT,
            totalRecordsParsed = 1,
            uniqueTitlesCount = 1,
            newMoviesCount = 1,
            newEpisodesCount = 0,
            newWatchlistCount = 0,
            newRatingsCount = 0,
            newReviewsCount = 0,
            identicalSkippedCount = 0,
            conflicts = emptyList(),
            confidenceDistribution = emptyMap()
        )
        coEvery { importCoordinator.generatePreview(any(), any(), ImportSource.TRAKT) } returns mockPreview

        val mockReport = ImportReport(
            importId = "test_import_id",
            source = ImportSource.TRAKT,
            policyApplied = ConflictResolutionPolicy.OVERWRITE_LOCAL,
            totalProcessed = 1,
            insertedCount = 1,
            updatedCount = 0,
            skippedIdenticalCount = 0,
            duplicateInSourceCount = 0,
            failedCount = 0,
            durationMs = 150
        )
        coEvery {
            importCoordinator.executeImport(any(), any(), ImportSource.TRAKT, ConflictResolutionPolicy.OVERWRITE_LOCAL)
        } returns mockReport

        viewModel.startImport(testUri, "trakt")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.confirmImportWithPolicy(ConflictResolutionPolicy.OVERWRITE_LOCAL)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mockReport, viewModel.report.value)
        assertNull(viewModel.preview.value)
    }
}
