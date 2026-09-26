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
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    val importWorkInfo: Flow<WorkInfo?> = workManager
        .getWorkInfosForUniqueWorkFlow(MediaImportWorker.WORK_NAME)
        .map { it.firstOrNull() }

    private val _isPreparing = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> = _isPreparing.asStateFlow()

    private val _prepareError = MutableStateFlow<String?>(null)
    val prepareError: StateFlow<String?> = _prepareError.asStateFlow()

    fun startImport(uri: Uri, source: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isPreparing.value = true
            _prepareError.value = null
            try {
                val cacheFile = java.io.File(context.cacheDir, "import_file_${System.currentTimeMillis()}.csv")
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
                _prepareError.value = e.message ?: "Failed to read file."
            } finally {
                _isPreparing.value = false
            }
        }
    }

    fun resetState() {
        _prepareError.value = null
        workManager.cancelUniqueWork(MediaImportWorker.WORK_NAME)
        workManager.pruneWork()
    }
}
