package dev.sequel.app.domain.repository

import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for authentication.
 */
interface AuthRepository {

    /** Whether the user is currently authenticated. */
    val isAuthenticated: Boolean

    /** Current user ID or null. */
    val currentUserId: String?

    /** Current user Email or null. */
    val currentUserEmail: String?

    /** Observe auth state (true = logged in, false = logged out). */
    val authStateFlow: Flow<Boolean>

    /** Sign up with email and password. */
    suspend fun signUp(email: String, password: String): Result<UserInfo>

    /** Sign in with email and password. */
    suspend fun signIn(email: String, password: String): Result<UserInfo>

    /** Sign out. */
    suspend fun signOut()
}
