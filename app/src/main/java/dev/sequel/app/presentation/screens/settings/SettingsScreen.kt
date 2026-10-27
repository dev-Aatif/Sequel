package dev.sequel.app.presentation.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.saveable.rememberSaveable

import dev.sequel.app.domain.export.ExportDestination
import dev.sequel.app.domain.export.ExportPackage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onImportClick: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit
) {
    val isDeleting by viewModel.isDeleting.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var pendingExportPackage by remember { mutableStateOf<ExportPackage?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            val pkg = pendingExportPackage
            if (pkg != null) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(pkg.bytes)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.errorMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    
    LaunchedEffect(viewModel) {
        viewModel.navigateEvent.collectLatest {
            showDeleteDialog = false
            showSignOutDialog = false
            onNavigateToAuth()
        }
    }
    
    LaunchedEffect(viewModel) {
        viewModel.dismissDialogEvent.collectLatest {
            showDeleteDialog = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.statusBars
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // Section 1: Data & Migration
            SettingsSection(title = "Data & Migration") {
                SettingsItem(
                    icon = { Icon(Icons.Outlined.FileDownload, contentDescription = null) },
                    title = "Import Data",
                    subtitle = "Migrate history from TV Time, Trakt, Letterboxd, or Backup",
                    trailingIcon = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                    onClick = onImportClick
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = { Icon(Icons.Outlined.FileUpload, contentDescription = null) },
                    title = "Export Library",
                    subtitle = "Export to Sequel Backup, Letterboxd, or Trakt formats",
                    trailingIcon = { Icon(Icons.Outlined.ChevronRight, contentDescription = null) },
                    onClick = { showExportDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) },
                    title = "Sync & Cloud Backup",
                    subtitle = "Manage cloud synchronization",
                    onClick = {
                        viewModel.forceSync()
                    }
                )
            }

            // Section 2: Legal & About
            SettingsSection(title = "Legal & About") {
                SettingsItem(
                    icon = { Icon(Icons.Outlined.Security, contentDescription = null) },
                    title = "Privacy Policy",
                    trailingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                    onClick = onNavigateToPrivacyPolicy
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = { Icon(Icons.Outlined.Movie, contentDescription = null, tint = Color(0xFF01D277)) },
                    title = "The Movie Database (TMDB)",
                    subtitle = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                    trailingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.themoviedb.org"))
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                    title = "App Version",
                    subtitle = "v1.0.0 (Build 1)"
                )
            }


            // Section 3: Account (Destructive Actions)
            SettingsSection(title = "Account") {
                SettingsItem(
                    icon = { Icon(Icons.Outlined.Logout, contentDescription = null) },
                    title = "Sign Out",
                    onClick = { showSignOutDialog = true }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = "Delete Account",
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showDeleteDialog = true }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Export Format Selection Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            icon = { Icon(Icons.Outlined.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Export Library") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select a destination export format:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    ExportDestination.values().forEach { destination ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showExportDialog = false
                                    viewModel.exportDestination(destination) { pkg ->
                                        pendingExportPackage = pkg
                                        exportFileLauncher.launch(pkg.suggestedFileName)
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = destination.displayName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "File extension: .${destination.extension}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sign Out Dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut()
                    }
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Account Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Account") },
            text = { Text("Are you sure you want to delete your account? This action permanently deletes your cloud data and watch history. This cannot be undone.", fontWeight = FontWeight.Medium) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onError,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Delete", color = MaterialTheme.colorScheme.onError)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, end = 16.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailingIcon()
        }
    }
}
