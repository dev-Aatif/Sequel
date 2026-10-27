package dev.sequel.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.remote.supabase.SupabaseAuthService
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.domain.backup.SequelBackupService
import dev.sequel.app.domain.export.ExportCoordinator
import dev.sequel.app.domain.export.ExportDestination
import dev.sequel.app.domain.export.ExportPackage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authService: SupabaseAuthService,
    private val appDatabase: SequelDatabase,
    private val syncManager: SyncManager,
    private val backupService: SequelBackupService,
    private val exportCoordinator: ExportCoordinator
) : ViewModel() {

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    private val _errorMessage = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val errorMessage: kotlinx.coroutines.flow.SharedFlow<String> = _errorMessage

    private val _navigateEvent = Channel<Unit>()
    val navigateEvent = _navigateEvent.receiveAsFlow()
    
    private val _dismissDialogEvent = Channel<Unit>()
    val dismissDialogEvent = _dismissDialogEvent.receiveAsFlow()

    fun deleteAccount() {
        if (_isDeleting.value) return
        viewModelScope.launch {
            _isDeleting.value = true
            try {
                // 1. Delete user from Supabase (simulated via RPC + sign out)
                authService.deleteUser()
                
                // 2. Wipe the local Room database safely regardless of cancellation
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
                    syncManager.cancelAllSync()
                    appDatabase.clearAllTables()
                }
                
                // 3. Navigate back to Auth
                _navigateEvent.send(Unit)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _errorMessage.emit("Account deletion failed. An active internet connection is required.")
                _dismissDialogEvent.send(Unit)
            } finally {
                _isDeleting.value = false
            }
        }
    }

    fun forceSync() {
        viewModelScope.launch {
            try {
                syncManager.syncAllNow()
                _errorMessage.emit("Cloud sync triggered.")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _errorMessage.emit("Failed to trigger cloud sync.")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                authService.signOut()
            } catch (e: Exception) {
                // Ignore network errors so we can still sign out locally
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
                syncManager.cancelAllSync()
                appDatabase.clearAllTables()
            }
            _navigateEvent.send(Unit)
        }
    }

    fun exportBackup(onExportReady: (String) -> Unit) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val json = backupService.exportJsonBackup()
                onExportReady(json)
                _errorMessage.emit("Backup generated successfully.")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _errorMessage.emit("Failed to generate backup: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportCsvArchive(onArchiveReady: (ByteArray) -> Unit) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val zip = backupService.exportCsvZip()
                onArchiveReady(zip)
                _errorMessage.emit("CSV archive generated successfully.")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _errorMessage.emit("Failed to export CSVs: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun exportDestination(destination: ExportDestination, onReady: (ExportPackage) -> Unit) {
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val pkg = exportCoordinator.export(destination)
                onReady(pkg)
                _errorMessage.emit("${destination.displayName} exported successfully.")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _errorMessage.emit("Failed to export: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

}
