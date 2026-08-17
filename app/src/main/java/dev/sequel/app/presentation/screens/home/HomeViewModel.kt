package dev.sequel.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.domain.repository.ShowRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Success(val shows: List<ShowEntity>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val showRepository: ShowRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        fetchTrendingShows()
    }

    fun fetchTrendingShows() {
        _uiState.value = HomeUiState.Loading
        viewModelScope.launch {
            showRepository.fetchTrending(mediaType = "tv", timeWindow = "week", page = 1)
                .onSuccess { shows ->
                    _uiState.value = HomeUiState.Success(shows)
                }
                .onFailure { exception ->
                    _uiState.value = HomeUiState.Error(
                        exception.localizedMessage ?: "Failed to fetch trending shows."
                    )
                }
        }
    }
}
