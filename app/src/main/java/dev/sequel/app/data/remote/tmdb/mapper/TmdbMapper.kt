package dev.sequel.app.data.remote.tmdb.mapper

import dev.sequel.app.data.local.entity.EpisodeEntity
import dev.sequel.app.data.local.entity.SeasonEntity
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.remote.tmdb.dto.TmdbEpisodeDto
import dev.sequel.app.data.remote.tmdb.dto.TmdbMovieDetailDto
import dev.sequel.app.data.remote.tmdb.dto.TmdbSeasonDetailDto
import dev.sequel.app.data.remote.tmdb.dto.TmdbSeasonSummaryDto
import dev.sequel.app.data.remote.tmdb.dto.TmdbShowDetailDto
import dev.sequel.app.data.remote.tmdb.dto.TmdbShowDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Maps TMDB DTOs → Room entities.
 * This is the boundary between the network layer and the local database.
 */
object TmdbMapper {

    // ── Show (from list endpoints) → ShowEntity ───────────────────

    fun TmdbShowDto.toEntity(fallbackMediaType: String = "tv"): ShowEntity {
        return ShowEntity(
            id = id,
            title = displayTitle,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            mediaType = mediaType ?: fallbackMediaType,
            firstAirDate = displayDate,
            voteAverage = voteAverage,
            genreIds = Json.encodeToString(genreIds),
            numberOfSeasons = null,
            numberOfEpisodes = null,
            status = null
        )
    }

    // ── Show Detail (TV) → ShowEntity ─────────────────────────────

    fun TmdbShowDetailDto.toEntity(): ShowEntity {
        return ShowEntity(
            id = id,
            title = name,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            mediaType = "tv",
            firstAirDate = firstAirDate,
            voteAverage = voteAverage,
            genreIds = Json.encodeToString(genres.map { it.id }),
            numberOfSeasons = numberOfSeasons,
            numberOfEpisodes = numberOfEpisodes,
            status = status
        )
    }

    // ── Movie Detail → ShowEntity ─────────────────────────────────

    fun TmdbMovieDetailDto.toEntity(): ShowEntity {
        return ShowEntity(
            id = id,
            title = title,
            overview = overview,
            posterPath = posterPath,
            backdropPath = backdropPath,
            mediaType = "movie",
            firstAirDate = releaseDate,
            voteAverage = voteAverage,
            genreIds = Json.encodeToString(genres.map { it.id }),
            numberOfSeasons = null,
            numberOfEpisodes = null,
            status = status
        )
    }

    // ── Season Summary → SeasonEntity ─────────────────────────────

    fun TmdbSeasonSummaryDto.toEntity(showId: Int): SeasonEntity {
        return SeasonEntity(
            id = id,
            showId = showId,
            seasonNumber = seasonNumber,
            name = name,
            overview = overview,
            posterPath = posterPath,
            episodeCount = episodeCount,
            airDate = airDate
        )
    }

    // ── Season Detail → SeasonEntity + List<EpisodeEntity> ────────

    fun TmdbSeasonDetailDto.toSeasonEntity(showId: Int): SeasonEntity {
        return SeasonEntity(
            id = id,
            showId = showId,
            seasonNumber = seasonNumber,
            name = name,
            overview = overview,
            posterPath = posterPath,
            episodeCount = episodes.size,
            airDate = airDate
        )
    }

    fun TmdbSeasonDetailDto.toEpisodeEntities(showId: Int): List<EpisodeEntity> {
        return episodes.map { it.toEntity(showId) }
    }

    // ── Episode → EpisodeEntity ───────────────────────────────────

    fun TmdbEpisodeDto.toEntity(showId: Int): EpisodeEntity {
        return EpisodeEntity(
            id = id,
            showId = showId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            name = name,
            overview = overview,
            stillPath = stillPath,
            airDate = airDate,
            runtime = runtime
        )
    }
}
