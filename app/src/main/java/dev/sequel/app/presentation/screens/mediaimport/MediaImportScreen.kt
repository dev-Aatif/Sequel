package dev.sequel.app.presentation.screens.mediaimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.work.WorkInfo
import dev.sequel.app.data.worker.MediaImportWorker
import dev.sequel.app.domain.backup.BackupRestoreResult
import dev.sequel.app.domain.importer.core.ConflictResolutionPolicy
import dev.sequel.app.domain.importer.core.ImportPreview
import dev.sequel.app.domain.importer.core.ImportReport
import dev.sequel.app.domain.importer.core.ImportSource
import dev.sequel.app.presentation.theme.SequelTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaImportScreen(
    onBackClick: () -> Unit,
    viewModel: MediaImportViewModel = hiltViewModel()
) {
    val workInfo by viewModel.importWorkInfo.collectAsState(initial = null)
    val isPreparing by viewModel.isPreparing.collectAsState(initial = false)
    val prepareError by viewModel.prepareError.collectAsState(initial = null)
    val backupRestoreResult by viewModel.backupRestoreSuccess.collectAsState(initial = null)
    val preview by viewModel.preview.collectAsState(initial = null)
    val report by viewModel.report.collectAsState(initial = null)
    val selectedPolicy by viewModel.selectedPolicy.collectAsState()

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
        MediaImportContent(
            modifier = Modifier.padding(innerPadding),
            isPreparing = isPreparing,
            prepareError = prepareError,
            backupRestoreResult = backupRestoreResult,
            preview = preview,
            report = report,
            selectedPolicy = selectedPolicy,
            workInfo = workInfo,
            selectedSource = selectedSource,
            onSelectSource = { selectedSource = it },
            onSetPolicy = viewModel::setPolicy,
            onConfirmImport = viewModel::confirmImportWithPolicy,
            onResetState = viewModel::resetState,
            onOpenFilePicker = {
                launcher.launch(
                    arrayOf(
                        "text/comma-separated-values",
                        "text/csv",
                        "application/zip",
                        "application/json",
                        "application/octet-stream",
                        "*/*"
                    )
                )
            }
        )
    }
}

/**
 * Hoisted, testable content component for [MediaImportScreen].
 */
@Composable
fun MediaImportContent(
    modifier: Modifier = Modifier,
    isPreparing: Boolean = false,
    prepareError: String? = null,
    backupRestoreResult: BackupRestoreResult? = null,
    preview: ImportPreview? = null,
    report: ImportReport? = null,
    selectedPolicy: ConflictResolutionPolicy = ConflictResolutionPolicy.KEEP_EXISTING,
    workInfo: WorkInfo? = null,
    selectedSource: String = "tvtime",
    onSelectSource: (String) -> Unit = {},
    onSetPolicy: (ConflictResolutionPolicy) -> Unit = {},
    onConfirmImport: (ConflictResolutionPolicy) -> Unit = {},
    onResetState: () -> Unit = {},
    onOpenFilePicker: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Import your watch history, ratings, and reviews from TV Time, Trakt, Letterboxd, or restore a Sequel backup. You can inspect preview conflicts before applying.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (prepareError != null) {
            dev.sequel.app.presentation.components.BeautifulErrorState(
                error = dev.sequel.app.domain.error.AppError.Validation(prepareError),
                onRetry = onResetState,
                isCard = true
            )
        } else if (isPreparing) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Analyzing and preparing records...")
        } else if (preview != null) {
            // Interactive Preview & Conflict Policy Selection
            val p = preview
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Import Preview: ${p.source.displayName}", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Total Records: ${p.totalRecordsParsed} (${p.uniqueTitlesCount} unique titles)")
                    Text("• New Movies: ${p.newMoviesCount}")
                    Text("• New TV Episodes: ${p.newEpisodesCount}")
                    Text("• Watchlist Items: ${p.newWatchlistCount}")
                    Text("• Ratings & Reviews: ${p.newRatingsCount + p.newReviewsCount}")
                    if (p.identicalSkippedCount > 0) {
                        Text("• Identical Entries: ${p.identicalSkippedCount} (already tracked)")
                    }

                    if (p.conflicts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${p.conflicts.size} Conflicts Detected",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Select Conflict Resolution Policy:", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))

                    ConflictResolutionPolicy.entries.forEach { policy ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = selectedPolicy == policy,
                                onClick = { onSetPolicy(policy) }
                            )
                            Column(modifier = Modifier.padding(start = 4.dp)) {
                                Text(policy.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(policy.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onResetState,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { onConfirmImport(selectedPolicy) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Apply Import")
                        }
                    }
                }
            }
        } else if (report != null) {
            // Structured Report
            val rep = report
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Import Completed Successfully!", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("• Source: ${rep.source.displayName}")
            Text("• Policy Applied: ${rep.policyApplied.displayName}")
            Text("• Total Processed: ${rep.totalProcessed}")
            Text("• New Inserted: ${rep.insertedCount}")
            Text("• Updated: ${rep.updatedCount}")
            Text("• Identical Skipped: ${rep.skippedIdenticalCount}")
            if (rep.failedCount > 0) {
                Text("• Failed / Unresolved: ${rep.failedCount}", color = MaterialTheme.colorScheme.error)
            }
            Text("• Elapsed Time: ${rep.durationMs}ms", style = MaterialTheme.typography.bodySmall)

            Button(onClick = onResetState, modifier = Modifier.padding(top = 16.dp)) {
                Text("Import Another")
            }
        } else if (backupRestoreResult != null) {
            val res = backupRestoreResult
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("Backup Restored Successfully!", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Restored ${res.moviesCount} movies, ${res.episodesCount} episodes across ${res.showsCount} shows, ${res.watchlistCount} watchlist items, and ${res.reviewsCount} reviews.", style = MaterialTheme.typography.bodySmall)

            Button(onClick = onResetState, modifier = Modifier.padding(top = 16.dp)) {
                Text("Import Another")
            }
        } else {
            when (workInfo?.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING -> {
                    val progress = workInfo.progress
                    val state = progress.getString(MediaImportWorker.KEY_PROGRESS_STATE)
                    val message = progress.getString(MediaImportWorker.KEY_MESSAGE)
                    val current = progress.getInt(MediaImportWorker.KEY_CURRENT, 0)
                    val total = progress.getInt(MediaImportWorker.KEY_TOTAL, 0)
                    val showName = progress.getString(MediaImportWorker.KEY_SHOW_NAME) ?: ""

                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))

                    if (state == MediaImportWorker.STATE_PARSING) {
                        Text(message ?: "Parsing file...")
                    } else if (state == MediaImportWorker.STATE_IMPORTING) {
                        Text("Importing $current of $total titles...")
                        Text("Current: $showName", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Processing...")
                    }
                }
                WorkInfo.State.SUCCEEDED -> {
                    val output = workInfo.outputData
                    val importedCount = output.getInt(MediaImportWorker.KEY_IMPORTED_COUNT, 0)
                    val skippedCount = output.getInt("skipped_count", 0)

                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Import complete!")
                    Text("Successfully imported $importedCount records.", style = MaterialTheme.typography.bodySmall)
                    if (skippedCount > 0) {
                        Text("Skipped $skippedCount titles (unmatched or rate limited).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }

                    Button(onClick = onResetState, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Import Another")
                    }
                }
                WorkInfo.State.FAILED -> {
                    val output = workInfo.outputData
                    val message = output.getString(MediaImportWorker.KEY_MESSAGE) ?: "An unknown error occurred"
                    dev.sequel.app.presentation.components.BeautifulErrorState(
                        error = dev.sequel.app.domain.error.AppError.Validation(message),
                        onRetry = onResetState,
                        isCard = true
                    )
                }
                else -> {
                    // Idle state
                    Text("Select Import Source", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedSource == "tvtime",
                            onClick = { onSelectSource("tvtime") }
                        )
                        Text("TV Time (GDPR ZIP or CSV)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedSource == "trakt",
                            onClick = { onSelectSource("trakt") }
                        )
                        Text("Trakt (CSV or Raw JSON / ZIP)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedSource == "letterboxd",
                            onClick = { onSelectSource("letterboxd") }
                        )
                        Text("Letterboxd (Full Export ZIP or CSV)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedSource == "sequel",
                            onClick = { onSelectSource("sequel") }
                        )
                        Text("Sequel Backup (JSON)")
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onOpenFilePicker,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select Export File (ZIP, CSV, or JSON)")
                    }
                }
            }
        }
    }
}

@Preview(name = "Media Import Screen - Idle", showBackground = true)
@Composable
fun MediaImportContentPreview() {
    SequelTheme {
        Surface {
            MediaImportContent(
                selectedSource = "tvtime",
                onSelectSource = {},
                onOpenFilePicker = {}
            )
        }
    }
}

@Preview(name = "Media Import Screen - Preview State", showBackground = true)
@Composable
fun MediaImportContentWithDataPreview() {
    SequelTheme {
        Surface {
            MediaImportContent(
                preview = ImportPreview(
                    source = ImportSource.TV_TIME,
                    totalRecordsParsed = 42,
                    uniqueTitlesCount = 42,
                    newMoviesCount = 10,
                    newEpisodesCount = 32,
                    newWatchlistCount = 5,
                    newRatingsCount = 12,
                    newReviewsCount = 2,
                    identicalSkippedCount = 0,
                    conflicts = emptyList(),
                    confidenceDistribution = emptyMap()
                ),
                selectedSource = "tvtime"
            )
        }
    }
}
