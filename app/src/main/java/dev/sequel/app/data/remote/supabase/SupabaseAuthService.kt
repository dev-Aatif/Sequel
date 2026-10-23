package dev.sequel.app.data.remote.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

class EmailConfirmationRequiredException(message: String) : Exception(message)

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

    /** Observe auth state changes. Emits true when logged in, false when logged out. Handles offline mode gracefully. */
    val authStateFlow: Flow<Boolean>
        get() = auth.sessionStatus
            .filter { status ->
                status !is io.github.jan.supabase.auth.status.SessionStatus.Initializing
            }
            .map { status ->
                when (status) {
                    is io.github.jan.supabase.auth.status.SessionStatus.Authenticated -> true
                    is io.github.jan.supabase.auth.status.SessionStatus.NotAuthenticated -> false
                    is io.github.jan.supabase.auth.status.SessionStatus.NetworkError -> {
                        // When offline, if a local user/session exists, treat as authenticated
                        auth.currentUserOrNull() != null || auth.currentSessionOrNull() != null
                    }
                    else -> auth.currentUserOrNull() != null
                }
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
                throw EmailConfirmationRequiredException("Sign up succeeded, but email confirmation is required. Please check your inbox to verify your account.")
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

    /**
     * Sign in or sign up with Google OAuth.
     * Launches the OAuth provider flow which redirects to sequel://login-callback.
     */
    suspend fun signInWithGoogle(): Result<Unit> {
        return runCatching {
            auth.signInWith(Google)
        }
    }

    /**
     * Sign in or sign up with a Google ID token directly.
     */
    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String? = null): Result<UserInfo> {
        return runCatching {
            auth.signInWith(IDToken) {
                this.idToken = idToken
                this.provider = Google
                this.nonce = nonce
            }
            auth.currentUserOrNull() ?: throw IllegalStateException("Google sign in succeeded but user is null")
        }
    }

    /**
     * Send a password reset email.
     */
    suspend fun resetPasswordForEmail(email: String, redirectUrl: String? = null) {
        auth.resetPasswordForEmail(email = email, redirectUrl = redirectUrl)
    }


    /**
     * Update the user's password.
     */
    suspend fun updatePassword(newPassword: String) {
        auth.updateUser {
            this.password = newPassword
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
        // Attempt to call a custom RPC for self-deletion if it exists
        supabaseClient.postgrest.rpc("delete_user")
        try {
            auth.signOut()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Ignore network/401 errors since the user is already deleted on the server
        }
    }

    /** Retrieve the current session's access token (for API calls if needed). */
    suspend fun getAccessToken(): String? {
        return auth.currentAccessTokenOrNull()
    }

    /**
     * Wait for auth session to be initialized from storage on app start,
     * and return the current user ID (or null if not logged in).
     */
    suspend fun awaitUserId(): String? {
        val current = auth.currentUserOrNull()?.id ?: auth.currentSessionOrNull()?.user?.id
        if (current != null) return current

        return kotlinx.coroutines.withTimeoutOrNull(3000L) {
            val status = auth.sessionStatus.filter { 
                it !is io.github.jan.supabase.auth.status.SessionStatus.Initializing
            }.first()
            
            when (status) {
                is io.github.jan.supabase.auth.status.SessionStatus.Authenticated -> status.session.user?.id
                is io.github.jan.supabase.auth.status.SessionStatus.NetworkError -> {
                    auth.currentUserOrNull()?.id ?: auth.currentSessionOrNull()?.user?.id
                }
                else -> null
            }
        } ?: auth.currentUserOrNull()?.id
    }
}

