package dev.sequel.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sequel.app.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "sequel_user_preferences")

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UserPreferencesRepository {

    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")

    override val isOnboardingCompleted: Flow<Boolean> = context.userPreferencesDataStore.data
        .map { preferences ->
            preferences[onboardingCompletedKey] ?: false
        }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.userPreferencesDataStore.edit { preferences ->
            preferences[onboardingCompletedKey] = completed
        }
    }
}
