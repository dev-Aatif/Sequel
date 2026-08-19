package dev.sequel.app.presentation.screens.tvtimeimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import dev.sequel.app.domain.usecase.ImportProgress

/**
 * TV Time GDPR CSV import screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvTimeImportScreen(
    onBackClick: () -> Unit,
    viewModel: TvTimeImportViewModel = hiltViewModel()
) {
    val importState by viewModel.importState.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.startImport(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from TV Time") },
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
                text = "Import your TV Time watch history via GDPR CSV export",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(32.dp))

            when (val state = importState) {
                is ImportProgress.Idle -> {
                    Button(
                        onClick = { launcher.launch(arrayOf("text/comma-separated-values", "text/csv", "*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Select CSV File")
                    }
                }
                is ImportProgress.Parsing -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(state.message)
                }
                is ImportProgress.Importing -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Importing ${state.current} of ${state.total} shows...")
                    Text("Current: ${state.showName}", style = MaterialTheme.typography.bodySmall)
                }
                is ImportProgress.Success -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Imported ${state.importedCount} episodes successfully!")
                    Button(onClick = viewModel::resetState, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Import Another")
                    }
                }
                is ImportProgress.Error -> {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::resetState, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Try Again")
                    }
                }
            }
        }
    }
}
