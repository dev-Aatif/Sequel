package dev.sequel.app.presentation.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import dev.sequel.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val email: String? = null,
    val totalRuntimeMinutes: Int = 0,
    val totalEpisodesWatched: Int = 0,
    val totalMoviesWatched: Int = 0
) {
    val watchTimeFormatted: String
        get() {
            if (totalRuntimeMinutes == 0) return "0h"
            val totalHours = totalRuntimeMinutes / 60
            val months = totalHours / (24 * 30)
            val days = (totalHours % (24 * 30)) / 24
            val hours = totalHours % 24

            return buildString {
                if (months > 0) append("${months}mo ")
                if (days > 0) append("${days}d ")
                if (hours > 0 || (months == 0 && days == 0)) append("${hours}h")
            }.trim()
        }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val watchedEpisodeDao: WatchedEpisodeDao
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        watchedEpisodeDao.observeTotalEpisodesWatched(),
        watchedEpisodeDao.observeTotalMoviesWatched(),
        watchedEpisodeDao.observeTotalRuntimeMinutes()
    ) { episodes, movies, runtime ->
        ProfileUiState(
            email = authRepository.currentUserEmail,
            totalEpisodesWatched = episodes,
            totalMoviesWatched = movies,
            totalRuntimeMinutes = runtime
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ProfileUiState(email = authRepository.currentUserEmail)
    )

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
        }
    }
}
