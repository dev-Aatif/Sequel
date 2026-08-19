package dev.sequel.app.data.remote.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.filter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wrapper around Supabase Auth for email/password authentication.
 */
@Singleton
class SupabaseAuthService @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    private val auth: Auth get() = supabaseClient.auth

    /** Current user ID or null if not authenticated. */
    val currentUserId: String?
        get() = auth.currentUserOrNull()?.id

    /** Current user email or null if not authenticated. */
    val currentUserEmail: String?
        get() = auth.currentUserOrNull()?.email

    /** Whether the user is currently authenticated. */
    val isAuthenticated: Boolean
        get() = auth.currentUserOrNull() != null

    /** Observe auth state changes. Emits true when logged in, false when logged out. Waits for storage load. */
    val authStateFlow: Flow<Boolean>
        get() = auth.sessionStatus
            .filter { status ->
                status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated ||
                status is io.github.jan.supabase.auth.status.SessionStatus.NotAuthenticated
            }
            .map { status ->
                status is io.github.jan.supabase.auth.status.SessionStatus.Authenticated
            }

    /**
     * Sign up with email and password.
     * @return [Result] wrapping the user info on success.
     */
    suspend fun signUp(email: String, password: String): Result<UserInfo> {
        return runCatching {
            auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val user = auth.currentUserOrNull() 
            if (user == null) {
                throw IllegalStateException("Sign up succeeded, but you are not logged in. This usually means 'Confirm Email' is enabled in your Supabase dashboard. Please check your email to verify your account, or disable email confirmations in Supabase.")
            }
            user
        }
    }

    /**
     * Sign in with email and password.
     * @return [Result] wrapping the user info on success.
     */
    suspend fun signIn(email: String, password: String): Result<UserInfo> {
        return runCatching {
            auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            auth.currentUserOrNull() ?: throw IllegalStateException("Sign in succeeded but user is null")
        }
    }

    /** Sign out the current user. */
    suspend fun signOut() {
        auth.signOut()
    }

    /** 
     * Delete the current user's account. 
     * Note: In Supabase, self-deletion typically requires an RPC or Edge Function.
     * We simulate it here by calling an RPC (which the user must create in their DB) and signing out.
     */
    suspend fun deleteUser() {
        try {
            // Attempt to call a custom RPC for self-deletion if it exists
            supabaseClient.postgrest.rpc("delete_user")
        } catch (e: Exception) {
            // Ignore if RPC does not exist
        }
        auth.signOut()
    }

    /** Retrieve the current session's access token (for API calls if needed). */
    suspend fun getAccessToken(): String? {
        return auth.currentAccessTokenOrNull()
    }
}
