package dev.sequel.app.presentation.screens.mediaimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sequel.app.data.worker.MediaImportWorker
import dev.sequel.app.domain.backup.BackupRestoreResult
import dev.sequel.app.domain.backup.SequelBackupService
import dev.sequel.app.domain.importer.coordinator.ImportCoordinator
import dev.sequel.app.domain.importer.core.ConflictResolutionPolicy
import dev.sequel.app.domain.importer.core.ImportPreview
import dev.sequel.app.domain.importer.core.ImportReport
import dev.sequel.app.domain.importer.core.ImportSource
import dev.sequel.app.domain.importer.detector.SourceDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupService: SequelBackupService,
    private val importCoordinator: ImportCoordinator,
    private val sourceDetector: SourceDetector
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    val importWorkInfo: Flow<WorkInfo?> = workManager
        .getWorkInfosForUniqueWorkFlow(MediaImportWorker.WORK_NAME)
        .map { it.firstOrNull() }

    private val _isPreparing = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> = _isPreparing.asStateFlow()

    private val _prepareError = MutableStateFlow<String?>(null)
    val prepareError: StateFlow<String?> = _prepareError.asStateFlow()

    private val _backupRestoreSuccess = MutableStateFlow<BackupRestoreResult?>(null)
    val backupRestoreSuccess: StateFlow<BackupRestoreResult?> = _backupRestoreSuccess.asStateFlow()

    private val _preview = MutableStateFlow<ImportPreview?>(null)
    val preview: StateFlow<ImportPreview?> = _preview.asStateFlow()

    private val _selectedPolicy = MutableStateFlow(ConflictResolutionPolicy.KEEP_EXISTING)
    val selectedPolicy: StateFlow<ConflictResolutionPolicy> = _selectedPolicy.asStateFlow()

    private val _report = MutableStateFlow<ImportReport?>(null)
    val report: StateFlow<ImportReport?> = _report.asStateFlow()

    private var pendingUri: Uri? = null
    private var pendingSource: String = "tvtime"

    fun setPolicy(policy: ConflictResolutionPolicy) {
        _selectedPolicy.value = policy
    }

    fun startImport(uri: Uri, source: String) {
        pendingUri = uri
        pendingSource = source

        viewModelScope.launch(Dispatchers.IO) {
            _isPreparing.value = true
            _prepareError.value = null
            _backupRestoreSuccess.value = null
            _preview.value = null
            _report.value = null

            try {
                // If restoring Sequel's own backup format, restore directly using SequelBackupService
                if (source == "sequel") {
                    val content = context.contentResolver.openInputStream(uri)?.use {
                        it.bufferedReader(Charsets.UTF_8).readText()
                    } ?: throw IllegalArgumentException("Could not read backup file")
                    val result = backupService.restoreJsonBackup(content)
                    _backupRestoreSuccess.value = result
                    return@launch
                }

                // Generate preview first to inspect conflicts
                val sourceEnum = when (source) {
                    "trakt" -> ImportSource.TRAKT
                    "letterboxd" -> ImportSource.LETTERBOXD
                    "tvtime" -> ImportSource.TV_TIME
                    else -> null
                }

                val previewResult = context.contentResolver.openInputStream(uri)?.use { stream ->
                    importCoordinator.generatePreview(
                        inputStream = stream,
                        filenameHint = uri.lastPathSegment,
                        sourceOverride = sourceEnum
                    )
                }

                if (previewResult != null) {
                    _preview.value = previewResult
                } else {
                    // Fallback directly to WorkManager import
                    enqueueWorkManagerImport(uri, source)
                }
            } catch (e: Exception) {
                // If preview fails, fallback to standard WorkManager pipeline
                enqueueWorkManagerImport(uri, source)
            } finally {
                _isPreparing.value = false
            }
        }
    }

    fun confirmImportWithPolicy(policy: ConflictResolutionPolicy) {
        val uri = pendingUri ?: return
        val source = pendingSource

        viewModelScope.launch(Dispatchers.IO) {
            _isPreparing.value = true
            try {
                val sourceEnum = when (source) {
                    "trakt" -> ImportSource.TRAKT
                    "letterboxd" -> ImportSource.LETTERBOXD
                    "tvtime" -> ImportSource.TV_TIME
                    else -> ImportSource.UNKNOWN
                }

                val importReport = context.contentResolver.openInputStream(uri)?.use { stream ->
                    importCoordinator.executeImport(
                        inputStream = stream,
                        filenameHint = uri.lastPathSegment,
                        sourceOverride = sourceEnum,
                        policy = policy
                    )
                }

                _report.value = importReport
                _preview.value = null
            } catch (e: Exception) {
                _prepareError.value = e.message ?: "Failed to execute import"
            } finally {
                _isPreparing.value = false
            }
        }
    }

    private fun enqueueWorkManagerImport(uri: Uri, source: String) {
        try {
            val ext = when (source) {
                "letterboxd", "tvtime", "trakt" -> ".tmp"
                else -> ".csv"
            }
            val cacheFile = java.io.File(context.cacheDir, "import_file_${System.currentTimeMillis()}$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<MediaImportWorker>()
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        MediaImportWorker.KEY_URI to android.net.Uri.fromFile(cacheFile).toString(),
                        MediaImportWorker.KEY_SOURCE to source
                    )
                )
                .build()

            workManager.enqueueUniqueWork(
                MediaImportWorker.WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        } catch (e: Exception) {
            _prepareError.value = e.message ?: "Failed to enqueue background import."
        }
    }

    fun resetState() {
        _prepareError.value = null
        _backupRestoreSuccess.value = null
        _preview.value = null
        _report.value = null
        pendingUri = null
        workManager.cancelUniqueWork(MediaImportWorker.WORK_NAME)
        workManager.pruneWork()
    }
}
