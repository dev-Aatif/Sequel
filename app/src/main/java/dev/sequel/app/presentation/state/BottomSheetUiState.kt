package dev.sequel.app.presentation.state

data class BottomSheetShowInfo(
    val id: Int,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val mediaType: String,
    val rating: Double,
    val genreIds: String
)

data class BottomSheetEpisodeInfo(
    val title: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val overview: String,
    val stillPath: String?
)

data class BottomSheetUiState(
    val show: BottomSheetShowInfo? = null,
    val inWatchlist: Boolean = false,
    val isWatched: Boolean = false,
    val isCompleted: Boolean = false,
    val nextEpisodeString: String? = null,
    val nextEpisodeData: BottomSheetEpisodeInfo? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false
)
