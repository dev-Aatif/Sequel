package dev.sequel.app.presentation.screens.login

import dev.sequel.app.domain.repository.AuthRepository
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelGoogleTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeAuthRepository: FakeAuthRepository
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuthRepository = FakeAuthRepository()
        viewModel = LoginViewModel(fakeAuthRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setSignUpMode_updatesStateProperly() {
        assertFalse(viewModel.uiState.value.isSignUpMode)

        viewModel.setSignUpMode(true)
        assertTrue(viewModel.uiState.value.isSignUpMode)

        viewModel.setSignUpMode(false)
        assertFalse(viewModel.uiState.value.isSignUpMode)
    }

    @Test
    fun signInWithGoogle_callsRepositoryAndClearsLoadingOnSuccess() = runTest(testDispatcher) {
        viewModel.signInWithGoogle()
        advanceUntilIdle()

        assertTrue(fakeAuthRepository.googleSignInCalled)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun signInWithGoogle_setsErrorOnFailure() = runTest(testDispatcher) {
        fakeAuthRepository.shouldFail = true
        viewModel.signInWithGoogle()
        advanceUntilIdle()

        assertTrue(fakeAuthRepository.googleSignInCalled)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.error != null)
    }
}

private class FakeAuthRepository : AuthRepository {
    var googleSignInCalled = false
    var shouldFail = false

    override val isAuthenticated: Boolean = false
    override val currentUserId: String? = null
    override val currentUserEmail: String? = null
    override val authStateFlow: Flow<Boolean> = MutableStateFlow(false)

    override suspend fun signUp(email: String, password: String): Result<UserInfo> {
        throw NotImplementedError()
    }

    override suspend fun signIn(email: String, password: String): Result<UserInfo> {
        throw NotImplementedError()
    }

    override suspend fun signInWithGoogle(): Result<Unit> {
        googleSignInCalled = true
        return if (shouldFail) {
            Result.failure(RuntimeException("Google OAuth cancelled"))
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String, nonce: String?): Result<UserInfo> {
        throw NotImplementedError()
    }

    override suspend fun signOut() {}

    override suspend fun resetPasswordForEmail(email: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun updatePassword(newPassword: String): Result<Unit> {
        return Result.success(Unit)
    }
}
