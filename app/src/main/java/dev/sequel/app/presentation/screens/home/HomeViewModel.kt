package dev.sequel.app.presentation.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.domain.repository.ShowRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val showRepository: ShowRepository
) : ViewModel() {

    val pagedShows: Flow<PagingData<ShowEntity>> =
        showRepository.getPagedTrendingShows().cachedIn(viewModelScope)
}
