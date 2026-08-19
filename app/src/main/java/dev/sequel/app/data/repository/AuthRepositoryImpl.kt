package dev.sequel.app.data.repository

import dev.sequel.app.data.remote.supabase.SupabaseAuthService
import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.domain.repository.AuthRepository
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabaseAuthService: SupabaseAuthService,
    private val syncManager: SyncManager
) : AuthRepository {

    override val isAuthenticated: Boolean
        get() = supabaseAuthService.isAuthenticated

    override val currentUserId: String?
        get() = supabaseAuthService.currentUserId

    override val currentUserEmail: String?
        get() = supabaseAuthService.currentUserEmail

    override val authStateFlow: Flow<Boolean>
        get() = supabaseAuthService.authStateFlow

    override suspend fun signUp(email: String, password: String): Result<UserInfo> {
        val result = supabaseAuthService.signUp(email, password)
        if (result.isSuccess) {
            // Start periodic sync on successful auth
            syncManager.schedulePeriodicSync()
        }
        return result
    }

    override suspend fun signIn(email: String, password: String): Result<UserInfo> {
        val result = supabaseAuthService.signIn(email, password)
        if (result.isSuccess) {
            syncManager.schedulePeriodicSync()
            // Sync any data that was created while offline
            syncManager.syncAllNow()
        }
        return result
    }

    override suspend fun signOut() {
        syncManager.cancelPeriodicSync()
        supabaseAuthService.signOut()
    }
}
