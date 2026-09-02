package dev.sequel.app.data.remote.tmdb

import dev.sequel.app.data.remote.tmdb.dto.TmdbMovieDetailDto
import dev.sequel.app.data.remote.tmdb.dto.TmdbPagedResponse
import dev.sequel.app.data.remote.tmdb.dto.TmdbSeasonDetailDto
import dev.sequel.app.data.remote.tmdb.dto.TmdbShowDetailDto
import dev.sequel.app.data.remote.tmdb.dto.TmdbShowDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit service interface for The Movie Database (TMDB) API v3.
 *
 * All endpoints require an API key, which is injected via an OkHttp interceptor
 * (see [TmdbApiKeyInterceptor]).
 */
interface TmdbApiService {

    // ── Trending ──────────────────────────────────────────────────

    /**
     * Get trending TV shows and movies.
     * @param mediaType "all", "tv", or "movie"
     * @param timeWindow "day" or "week"
     */
    @GET("trending/{media_type}/{time_window}")
    suspend fun getTrending(
        @Path("media_type") mediaType: String = "tv",
        @Path("time_window") timeWindow: String = "week",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbShowDto>

    // ── Search ────────────────────────────────────────────────────

    /** Multi-search across TV, movies, and people. */
    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbShowDto>

    /** Search TV shows only. */
    @GET("search/tv")
    suspend fun searchTv(
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbShowDto>

    /** Search movies only. */
    @GET("search/movie")
    suspend fun searchMovie(
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbShowDto>

    // ── TV Show Details ───────────────────────────────────────────

    /** Get full details for a TV show, including season summaries. */
    @GET("tv/{tv_id}")
    suspend fun getTvShowDetail(
        @Path("tv_id") tvId: Int
    ): TmdbShowDetailDto

    /** Get full season detail including all episodes. */
    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetail(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int
    ): TmdbSeasonDetailDto

    // ── Movie Details ─────────────────────────────────────────────

    /** Get full details for a movie. */
    @GET("movie/{movie_id}")
    suspend fun getMovieDetail(
        @Path("movie_id") movieId: Int
    ): TmdbMovieDetailDto

    // ── Discover ──────────────────────────────────────────────────

    /** Discover popular TV shows. */
    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbShowDto>

    /** Discover popular movies. */
    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbShowDto>

    // ── Recommendations ──────────────────────────────────────────

    /** Get TV show recommendations based on a show. */
    @GET("tv/{tv_id}/recommendations")
    suspend fun getTvRecommendations(
        @Path("tv_id") tvId: Int,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbShowDto>

    /** Get movie recommendations based on a movie. */
    @GET("movie/{movie_id}/recommendations")
    suspend fun getMovieRecommendations(
        @Path("movie_id") movieId: Int,
        @Query("page") page: Int = 1
    ): TmdbPagedResponse<TmdbShowDto>
}
