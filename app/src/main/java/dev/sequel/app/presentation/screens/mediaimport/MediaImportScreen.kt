package dev.sequel.app.presentation.screens.mediaimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.WorkInfo
import dev.sequel.app.data.worker.MediaImportWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaImportScreen(
    onBackClick: () -> Unit,
    viewModel: MediaImportViewModel = hiltViewModel()
) {
    val workInfo by viewModel.importWorkInfo.collectAsState(initial = null)
    val isPreparing by viewModel.isPreparing.collectAsState(initial = false)
    val prepareError by viewModel.prepareError.collectAsState(initial = null)
    
    var selectedSource by remember { mutableStateOf("tvtime") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.startImport(uri, selectedSource)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Data") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "Import your watch history from other tracking apps. You can safely leave this screen while importing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(32.dp))

            val currentError = prepareError
            if (currentError != null) {
                dev.sequel.app.presentation.components.BeautifulErrorState(
                    error = dev.sequel.app.domain.error.AppError.Validation(currentError),
                    onRetry = viewModel::resetState,
                    isCard = true
                )
            } else if (isPreparing) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Preparing file...")
            } else {
                when (workInfo?.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> {
                        val progress = workInfo?.progress
                        val state = progress?.getString(MediaImportWorker.KEY_PROGRESS_STATE)
                        val message = progress?.getString(MediaImportWorker.KEY_MESSAGE)
                        val current = progress?.getInt(MediaImportWorker.KEY_CURRENT, 0) ?: 0
                        val total = progress?.getInt(MediaImportWorker.KEY_TOTAL, 0) ?: 0
                        val showName = progress?.getString(MediaImportWorker.KEY_SHOW_NAME) ?: ""

                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))

                        if (state == MediaImportWorker.STATE_PARSING) {
                            Text(message ?: "Parsing file...")
                        } else if (state == MediaImportWorker.STATE_IMPORTING) {
                            Text("Importing $current of $total shows...")
                            Text("Current: $showName", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text("Processing...")
                        }
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val output = workInfo?.outputData
                        val importedCount = output?.getInt(MediaImportWorker.KEY_IMPORTED_COUNT, 0) ?: 0
                        val skippedCount = output?.getInt("skipped_count", 0) ?: 0

                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Import complete!")
                        Text("Successfully imported episodes for $importedCount shows.", style = MaterialTheme.typography.bodySmall)
                        if (skippedCount > 0) {
                            Text("Skipped $skippedCount shows (unmatched or errors).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }

                        Button(onClick = viewModel::resetState, modifier = Modifier.padding(top = 16.dp)) {
                            Text("Import Another")
                        }
                    }
                    WorkInfo.State.FAILED -> {
                        val output = workInfo?.outputData
                        val message = output?.getString(MediaImportWorker.KEY_MESSAGE) ?: "An unknown error occurred"
                        dev.sequel.app.presentation.components.BeautifulErrorState(
                            error = dev.sequel.app.domain.error.AppError.Validation(message),
                            onRetry = viewModel::resetState,
                            isCard = true
                        )
                    }
                    else -> {
                        // Idle state
                        Text("Select Import Source", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedSource == "tvtime",
                                onClick = { selectedSource = "tvtime" }
                            )
                            Text("TV Time (GDPR CSV)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selectedSource == "trakt",
                                onClick = { selectedSource = "trakt" }
                            )
                            Text("Trakt (CSV Export)")
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { launcher.launch(arrayOf("text/comma-separated-values", "text/csv", "*/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select CSV File")
                        }
                    }
                }
            }
        }
    }
}
