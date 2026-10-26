package dev.sequel.app.data.remote.tmdb

import dev.sequel.app.data.remote.tmdb.dto.TmdbMovieDetailDto
import dev.sequel.app.data.remote.tmdb.dto.TmdbPagedResponse
import dev.sequel.app.data.remote.tmdb.dto.TmdbSeasonDetailDto
import dev.sequel.app.data.remote.tmdb.dto.TmdbShowDetailDto
import dev.sequel.app.data.remote.tmdb.dto.TmdbShowDto

/**
 * In-memory fake implementation of [TmdbApiService] for instrumented tests.
 * Allows tests to supply custom responses or fall back to sensible stub data.
 */
class FakeTmdbApiService : TmdbApiService {

    var trendingResponse: TmdbPagedResponse<TmdbShowDto> = TmdbPagedResponse(
        page = 1,
        results = emptyList(),
        totalPages = 1,
        totalResults = 0
    )

    var searchResponse: TmdbPagedResponse<TmdbShowDto> = TmdbPagedResponse(
        page = 1,
        results = emptyList(),
        totalPages = 1,
        totalResults = 0
    )

    var showDetailResponse: TmdbShowDetailDto? = null
    var seasonDetailResponse: TmdbSeasonDetailDto? = null
    var movieDetailResponse: TmdbMovieDetailDto? = null

    override suspend fun getTrending(
        mediaType: String,
        timeWindow: String,
        page: Int
    ): TmdbPagedResponse<TmdbShowDto> = trendingResponse

    override suspend fun searchMulti(
        query: String,
        page: Int
    ): TmdbPagedResponse<TmdbShowDto> = searchResponse

    override suspend fun searchTv(
        query: String,
        page: Int
    ): TmdbPagedResponse<TmdbShowDto> = searchResponse

    override suspend fun searchMovie(
        query: String,
        page: Int
    ): TmdbPagedResponse<TmdbShowDto> = searchResponse

    override suspend fun getTvShowDetail(
        tvId: Int,
        appendToResponse: String
    ): TmdbShowDetailDto {
        return showDetailResponse ?: TmdbShowDetailDto(
            id = tvId,
            name = "Fake TV Show $tvId",
            overview = "Fake Overview for TV show $tvId",
            firstAirDate = "2026-01-01",
            voteAverage = 8.5,
            status = "Ended"
        )
    }

    override suspend fun getSeasonDetail(
        tvId: Int,
        seasonNumber: Int
    ): TmdbSeasonDetailDto {
        return seasonDetailResponse ?: TmdbSeasonDetailDto(
            id = 1000 + seasonNumber,
            seasonNumber = seasonNumber,
            name = "Season $seasonNumber",
            overview = "Overview for season $seasonNumber"
        )
    }

    override suspend fun getMovieDetail(
        movieId: Int
    ): TmdbMovieDetailDto {
        return movieDetailResponse ?: TmdbMovieDetailDto(
            id = movieId,
            title = "Fake Movie $movieId",
            overview = "Fake Overview for movie $movieId",
            releaseDate = "2026-01-01",
            voteAverage = 8.0,
            status = "Released"
        )
    }

    override suspend fun discoverTv(
        withGenres: String?,
        sortBy: String,
        page: Int
    ): TmdbPagedResponse<TmdbShowDto> = trendingResponse

    override suspend fun discoverMovies(
        withGenres: String?,
        sortBy: String,
        page: Int
    ): TmdbPagedResponse<TmdbShowDto> = trendingResponse

    override suspend fun getTvRecommendations(
        tvId: Int,
        page: Int
    ): TmdbPagedResponse<TmdbShowDto> = trendingResponse

    override suspend fun getMovieRecommendations(
        movieId: Int,
        page: Int
    ): TmdbPagedResponse<TmdbShowDto> = trendingResponse

    override suspend fun getAiringToday(
        page: Int
    ): TmdbPagedResponse<TmdbShowDto> = trendingResponse
}
