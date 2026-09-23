package dev.sequel.app.presentation.screens.mediaimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sequel.app.data.worker.MediaImportWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class MediaImportViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    val importWorkInfo: Flow<WorkInfo?> = workManager
        .getWorkInfosForUniqueWorkFlow(MediaImportWorker.WORK_NAME)
        .map { it.firstOrNull() }

    fun startImport(uri: Uri, source: String) {
        val request = OneTimeWorkRequestBuilder<MediaImportWorker>()
            .setInputData(
                workDataOf(
                    MediaImportWorker.KEY_URI to uri.toString(),
                    MediaImportWorker.KEY_SOURCE to source
                )
            )
            .build()

        workManager.enqueueUniqueWork(
            MediaImportWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun resetState() {
        workManager.cancelUniqueWork(MediaImportWorker.WORK_NAME)
        workManager.pruneWork()
    }
}
