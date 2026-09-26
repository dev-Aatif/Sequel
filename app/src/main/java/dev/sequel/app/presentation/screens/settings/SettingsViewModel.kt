package dev.sequel.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.remote.supabase.SupabaseAuthService
import dev.sequel.app.data.sync.SyncManager
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
    private val syncManager: SyncManager
) : ViewModel() {

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting

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
}
