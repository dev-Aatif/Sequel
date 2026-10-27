package dev.sequel.app.domain.backup

import kotlinx.serialization.Serializable

@Serializable
data class SequelBackupModel(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val shows: List<ShowBackupDto> = emptyList(),
    val watchedMovies: List<WatchedMovieBackupDto> = emptyList(),
    val watchedEpisodes: List<WatchedEpisodeBackupDto> = emptyList(),
    val watchlist: List<WatchlistBackupDto> = emptyList(),
    val reviews: List<ReviewBackupDto> = emptyList(),
    val mountRushmore: List<MountRushmoreBackupDto> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class ShowBackupDto(
    val id: Int,
    val title: String,
    val overview: String = "",
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val firstAirDate: String? = null,
    val voteAverage: Double = 0.0,
    val genreIds: String = "[]",
    val status: String? = null,
    val mediaType: String,
    val numberOfSeasons: Int? = null,
    val numberOfEpisodes: Int? = null,
    val runtime: Int? = null,
    val genresDisplay: String? = null,
    val episodeRuntime: Int? = null,
    val contentRating: String? = null
)

@Serializable
data class WatchedMovieBackupDto(
    val tmdbMovieId: Int,
    val title: String,
    val posterPath: String? = null,
    val watchedAt: Long,
    val syncStatus: String = "PENDING",
    val watchDate: String? = null,
    val watchCount: Int = 1
)

@Serializable
data class WatchedEpisodeBackupDto(
    val showId: Int,
    val mediaType: String = "tv",
    val tmdbEpisodeId: Int,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val watchedAt: Long,
    val isSkipped: Boolean = false,
    val syncStatus: String = "PENDING",
    val watchDate: String? = null,
    val watchCount: Int = 1
)

@Serializable
data class WatchlistBackupDto(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String? = null,
    val addedAt: Long
)

@Serializable
data class ReviewBackupDto(
    val mediaId: Int,
    val mediaType: String,
    val seasonNum: Int? = null,
    val episodeNum: Int? = null,
    val reviewText: String? = null,
    val rating: Float? = null,
    val isSpoiler: Boolean = false,
    val isRatingOnly: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val originalRating: Float? = null,
    val originalRatingScale: Float? = null
)

@Serializable
data class MountRushmoreBackupDto(
    val slotIndex: Int,
    val mediaId: Int,
    val mediaType: String,
    val title: String,
    val posterPath: String? = null,
    val year: String? = null,
    val rating: Float? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class BackupRestoreResult(
    val totalRestored: Int,
    val showsCount: Int,
    val moviesCount: Int,
    val episodesCount: Int,
    val watchlistCount: Int,
    val reviewsCount: Int,
    val mountRushmoreCount: Int
)
