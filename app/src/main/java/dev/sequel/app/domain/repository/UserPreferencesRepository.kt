package dev.sequel.app.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Contract for managing user preferences and first-time setup state.
 */
interface UserPreferencesRepository {

    /**
     * Whether the user has completed or skipped the onboarding flow.
     */
    val isOnboardingCompleted: Flow<Boolean>

    /**
     * Mark the onboarding flow as completed.
     */
    suspend fun setOnboardingCompleted(completed: Boolean)
}
