package dev.sequel.app.data.repository

import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEntity
import dev.sequel.app.domain.repository.SearchRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

class SearchRepositoryImpl @Inject constructor(
    private val tmdbApiService: TmdbApiService
) : SearchRepository {

    override suspend fun discover(genreId: Int, filter: String): List<ShowEntity> = coroutineScope {
        if (filter == "TV Shows") {
            val tvDef1 = async { tmdbApiService.discoverTv(withGenres = genreId.toString(), page = 1) }
            val tvDef2 = async { tmdbApiService.discoverTv(withGenres = genreId.toString(), page = 2) }
            (tvDef1.await().results + tvDef2.await().results).map { it.copy(mediaType = "tv").toEntity("tv") }
        } else if (filter == "Movies") {
            val movDef1 = async { tmdbApiService.discoverMovies(withGenres = genreId.toString(), page = 1) }
            val movDef2 = async { tmdbApiService.discoverMovies(withGenres = genreId.toString(), page = 2) }
            (movDef1.await().results + movDef2.await().results).map { it.copy(mediaType = "movie").toEntity("movie") }
        } else {
            val tvDef1 = async { tmdbApiService.discoverTv(withGenres = genreId.toString(), page = 1) }
            val tvDef2 = async { tmdbApiService.discoverTv(withGenres = genreId.toString(), page = 2) }
            val movDef1 = async { tmdbApiService.discoverMovies(withGenres = genreId.toString(), page = 1) }
            val movDef2 = async { tmdbApiService.discoverMovies(withGenres = genreId.toString(), page = 2) }
            val tvResults = (tvDef1.await().results + tvDef2.await().results).map { it.copy(mediaType = "tv").toEntity("tv") }
            val movResults = (movDef1.await().results + movDef2.await().results).map { it.copy(mediaType = "movie").toEntity("movie") }
            tvResults.zip(movResults) { tv, movie -> listOf(tv, movie) }.flatten() + tvResults.drop(movResults.size) + movResults.drop(tvResults.size)
        }
    }

    override suspend fun search(query: String, filter: String): List<ShowEntity> = coroutineScope {
        if (filter == "TV Shows") {
            tmdbApiService.searchTv(query.trim()).results.map { it.copy(mediaType = "tv").toEntity("tv") }
        } else if (filter == "Movies") {
            tmdbApiService.searchMovie(query.trim()).results.map { it.copy(mediaType = "movie").toEntity("movie") }
        } else {
            val tvDef = async { tmdbApiService.searchTv(query.trim()) }
            val movDef = async { tmdbApiService.searchMovie(query.trim()) }
            val tvResults = tvDef.await().results.map { it.copy(mediaType = "tv").toEntity("tv") }
            val movResults = movDef.await().results.map { it.copy(mediaType = "movie").toEntity("movie") }
            tvResults.zip(movResults) { tv, movie -> listOf(tv, movie) }.flatten() + tvResults.drop(movResults.size) + movResults.drop(tvResults.size)
        }
    }
}
