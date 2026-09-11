package dev.sequel.app.presentation.state

import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.remote.tmdb.dto.TmdbNextEpisodeDto

data class BottomSheetUiState(
    val show: ShowEntity? = null,
    val inWatchlist: Boolean = false,
    val isWatched: Boolean = false,
    val isCompleted: Boolean = false,
    val nextEpisodeString: String? = null,
    val nextEpisodeData: TmdbNextEpisodeDto? = null,
    val isLoading: Boolean = false
)
