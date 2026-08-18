package dev.sequel.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.domain.repository.ShowRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val showRepository: ShowRepository
) : ViewModel() {

    private val _mediaType = MutableStateFlow("tv")
    val mediaType: StateFlow<String> = _mediaType.asStateFlow()

    val pagedShows: Flow<PagingData<ShowEntity>> = _mediaType
        .flatMapLatest { type ->
            showRepository.getPagedTrendingShows(type)
        }
        .cachedIn(viewModelScope)

    fun setMediaType(type: String) {
        _mediaType.value = type
    }
}
