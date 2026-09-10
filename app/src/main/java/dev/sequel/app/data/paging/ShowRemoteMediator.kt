package dev.sequel.app.data.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.entity.RemoteKeys
import dev.sequel.app.data.local.entity.ShowEntity
import dev.sequel.app.data.local.entity.TrendingShowEntity
import dev.sequel.app.data.remote.tmdb.TmdbApiService
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEntity
import retrofit2.HttpException
import java.io.IOException

@OptIn(ExperimentalPagingApi::class)
class ShowRemoteMediator(
    private val apiService: TmdbApiService,
    private val database: SequelDatabase,
    private val mediaType: String
) : RemoteMediator<Int, ShowEntity>() {

    override suspend fun initialize(): InitializeAction {
        return if (database.trendingShowDao().getTrendingCountByMediaType(mediaType) > 0) {
            InitializeAction.SKIP_INITIAL_REFRESH
        } else {
            InitializeAction.LAUNCH_INITIAL_REFRESH
        }
    }

    private val showDao = database.showDao()
    private val remoteKeysDao = database.remoteKeysDao()
    private val trendingShowDao = database.trendingShowDao()

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, ShowEntity>
    ): MediatorResult {
        return try {
            val page = when (loadType) {
                LoadType.REFRESH -> {
                    val remoteKeys = getRemoteKeyClosestToCurrentPosition(state)
                    remoteKeys?.nextKey?.minus(1) ?: 1
                }
                LoadType.PREPEND -> {
                    val remoteKeys = getRemoteKeyForFirstItem(state)
                    val prevKey = remoteKeys?.prevKey
                        ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                    prevKey
                }
                LoadType.APPEND -> {
                    val remoteKeys = getRemoteKeyForLastItem(state)
                    val nextKey = remoteKeys?.nextKey
                        ?: return MediatorResult.Success(endOfPaginationReached = remoteKeys != null)
                    nextKey
                }
            }

            val response = apiService.getTrending(mediaType = mediaType, page = page)
            val shows = response.results
            val endOfPaginationReached = shows.isEmpty()

            database.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    remoteKeysDao.clearRemoteKeysByMediaType(mediaType)
                    trendingShowDao.clearTrendingByMediaType(mediaType)
                    // DO NOT clear shows table! This caused catastrophic data loss.
                }
                
                val prevKey = if (page == 1) null else page - 1
                val nextKey = if (endOfPaginationReached) null else page + 1
                
                val keys = shows.map {
                    RemoteKeys(showId = it.id, mediaType = mediaType, prevKey = prevKey, nextKey = nextKey)
                }
                
                val entities = shows.map { it.toEntity() }
                
                val trendingShows = shows.mapIndexed { index, show ->
                    TrendingShowEntity(
                        showId = show.id,
                        mediaType = mediaType,
                        page = page,
                        position = index
                    )
                }
                
                remoteKeysDao.insertAll(keys)
                showDao.insertShows(entities)
                
                // Update existing shows to refresh their API data without dropping user state
                entities.forEach { entity ->
                    showDao.updateShowApiData(
                        id = entity.id,
                        title = entity.title,
                        overview = entity.overview,
                        posterPath = entity.posterPath,
                        backdropPath = entity.backdropPath,
                        voteAverage = entity.voteAverage,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                
                trendingShowDao.insertAll(trendingShows)
            }
            MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (exception: Exception) {
            MediatorResult.Error(exception)
        }
    }

    private suspend fun getRemoteKeyForLastItem(state: PagingState<Int, ShowEntity>): RemoteKeys? {
        return state.pages.lastOrNull { it.data.isNotEmpty() }?.data?.lastOrNull()
            ?.let { show ->
                remoteKeysDao.remoteKeysShowId(show.id, mediaType)
            }
    }

    private suspend fun getRemoteKeyForFirstItem(state: PagingState<Int, ShowEntity>): RemoteKeys? {
        return state.pages.firstOrNull { it.data.isNotEmpty() }?.data?.firstOrNull()
            ?.let { show ->
                remoteKeysDao.remoteKeysShowId(show.id, mediaType)
            }
    }

    private suspend fun getRemoteKeyClosestToCurrentPosition(
        state: PagingState<Int, ShowEntity>
    ): RemoteKeys? {
        return state.anchorPosition?.let { position ->
            state.closestItemToPosition(position)?.id?.let { showId ->
                remoteKeysDao.remoteKeysShowId(showId, mediaType)
            }
        }
    }
}
