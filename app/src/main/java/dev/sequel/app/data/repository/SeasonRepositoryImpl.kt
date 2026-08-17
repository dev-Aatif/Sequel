package dev.sequel.app.data.repository

import dev.sequel.app.data.local.dao.EpisodeDao
import dev.sequel.app.data.local.dao.SeasonDao
import dev.sequel.app.data.local.entity.EpisodeEntity
import dev.sequel.app.data.local.entity.SeasonEntity
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEpisodeEntities
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toSeasonEntity
import dev.sequel.app.domain.repository.SeasonRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeasonRepositoryImpl @Inject constructor(
    private val tmdbApiService: TmdbApiService,
    private val seasonDao: SeasonDao,
    private val episodeDao: EpisodeDao
) : SeasonRepository {

    override suspend fun fetchSeasonDetail(
        showId: Int,
        seasonNumber: Int
    ): Result<List<EpisodeEntity>> = runCatching {
        val detail = tmdbApiService.getSeasonDetail(showId, seasonNumber)

        // Cache season
        val seasonEntity = detail.toSeasonEntity(showId)
        seasonDao.insertSeasons(listOf(seasonEntity))

        // Cache episodes
        val episodeEntities = detail.toEpisodeEntities(showId)
        episodeDao.insertEpisodes(episodeEntities)

        episodeEntities
    }

    override fun observeSeasons(showId: Int): Flow<List<SeasonEntity>> =
        seasonDao.observeSeasonsByShow(showId)

    override fun observeEpisodes(showId: Int, seasonNumber: Int): Flow<List<EpisodeEntity>> =
        episodeDao.observeEpisodesBySeason(showId, seasonNumber)

    override suspend fun getEpisodeCount(showId: Int): Int =
        episodeDao.getEpisodeCountForShow(showId)
}
