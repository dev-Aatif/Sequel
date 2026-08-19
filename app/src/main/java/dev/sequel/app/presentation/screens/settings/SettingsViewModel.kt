package dev.sequel.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.remote.supabase.SupabaseAuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authService: SupabaseAuthService,
    private val appDatabase: SequelDatabase
) : ViewModel() {

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting

    fun deleteAccount(onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isDeleting.value = true
            try {
                // 1. Delete user from Supabase (simulated via RPC + sign out)
                authService.deleteUser()
                
                // 2. Wipe the local Room database
                appDatabase.clearAllTables()
                
                // 3. Navigate back to Auth
                onSuccess()
            } catch (e: Exception) {
                // Ignore error, force sign out locally
                authService.signOut()
                appDatabase.clearAllTables()
                onSuccess()
            } finally {
                _isDeleting.value = false
            }
        }
    }
}
