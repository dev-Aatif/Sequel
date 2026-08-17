package dev.sequel.app.data.repository

import dev.sequel.app.data.local.dao.SeasonDao
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEntity
import dev.sequel.app.domain.repository.ShowRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShowRepositoryImpl @Inject constructor(
    private val tmdbApiService: TmdbApiService,
    private val showDao: ShowDao,
    private val seasonDao: SeasonDao
) : ShowRepository {

    override suspend fun fetchTrending(
        mediaType: String,
        timeWindow: String,
        page: Int
    ): Result<List<ShowEntity>> = runCatching {
        val response = tmdbApiService.getTrending(mediaType, timeWindow, page)
        val entities = response.results.map { it.toEntity(fallbackMediaType = mediaType) }
        showDao.insertShows(entities)
        entities
    }

    override suspend fun search(query: String, page: Int): Result<List<ShowEntity>> = runCatching {
        val response = tmdbApiService.searchMulti(query, page)
        val entities = response.results
            .filter { it.mediaType == "tv" || it.mediaType == "movie" } // exclude people
            .map { it.toEntity() }
        showDao.insertShows(entities)
        entities
    }

    override suspend fun fetchShowDetail(showId: Int): Result<ShowEntity> = runCatching {
        val detail = tmdbApiService.getTvShowDetail(showId)
        val showEntity = detail.toEntity()

        // Cache show
        showDao.insertShow(showEntity)

        // Cache season summaries
        val seasonEntities = detail.seasons.map { it.toEntity(showId) }
        seasonDao.insertSeasons(seasonEntities)

        showEntity
    }

    override suspend fun fetchMovieDetail(movieId: Int): Result<ShowEntity> = runCatching {
        val detail = tmdbApiService.getMovieDetail(movieId)
        val entity = detail.toEntity()
        showDao.insertShow(entity)
        entity
    }

    // ── Local queries ─────────────────────────────────────────────

    override fun observeShow(showId: Int): Flow<ShowEntity?> =
        showDao.observeShowById(showId)

    override fun observeShowsByType(mediaType: String): Flow<List<ShowEntity>> =
        showDao.observeShowsByType(mediaType)

    override fun observeFavorites(): Flow<List<ShowEntity>> =
        showDao.observeFavorites()

    override fun observeWatchlist(): Flow<List<ShowEntity>> =
        showDao.observeWatchlist()

    override fun searchLocal(query: String): Flow<List<ShowEntity>> =
        showDao.searchShows(query)

    // ── Local mutations ───────────────────────────────────────────

    override suspend fun toggleFavorite(showId: Int, isFavorite: Boolean) {
        showDao.updateFavoriteStatus(showId, isFavorite)
    }

    override suspend fun toggleWatchlist(showId: Int, isInWatchlist: Boolean) {
        showDao.updateWatchlistStatus(showId, isInWatchlist)
    }
}
