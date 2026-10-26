package dev.sequel.app.data.remote.tmdb.mapper

import dev.sequel.app.data.remote.tmdb.dto.TmdbEpisodeDto
import dev.sequel.app.data.remote.tmdb.dto.TmdbSeasonSummaryDto
import dev.sequel.app.data.remote.tmdb.dto.TmdbShowDto
import dev.sequel.app.data.remote.tmdb.mapper.TmdbMapper.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbMapperTest {

    @Test
    fun tmdbShowDto_tvShow_mapsToEntityCorrectly() {
        val dto = TmdbShowDto(
            id = 1399,
            name = "Game of Thrones",
            title = null,
            overview = "Seven noble families fight for control of the mythical land of Westeros.",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            mediaType = "tv",
            firstAirDate = "2011-04-17",
            voteAverage = 8.4,
            genreIds = listOf(18, 10765)
        )

        val entity = dto.toEntity()

        assertEquals(1399, entity.id)
        assertEquals("Game of Thrones", entity.title)
        assertEquals("Seven noble families fight for control of the mythical land of Westeros.", entity.overview)
        assertEquals("/poster.jpg", entity.posterPath)
        assertEquals("/backdrop.jpg", entity.backdropPath)
        assertEquals("tv", entity.mediaType)
        assertEquals("2011-04-17", entity.firstAirDate)
        assertEquals(8.4, entity.voteAverage, 0.001)
        assertEquals("[18,10765]", entity.genreIds)
        assertNull(entity.numberOfSeasons)
        assertNull(entity.numberOfEpisodes)
    }

    @Test
    fun tmdbShowDto_movie_mapsToEntityWithMovieFallback() {
        val dto = TmdbShowDto(
            id = 157336,
            name = null,
            title = "Interstellar",
            overview = "The adventures of a group of explorers who make use of a newly discovered wormhole.",
            posterPath = "/interstellar.jpg",
            backdropPath = "/interstellar_backdrop.jpg",
            mediaType = null,
            releaseDate = "2014-11-05",
            voteAverage = 8.6,
            genreIds = listOf(12, 18, 878)
        )

        val entity = dto.toEntity(fallbackMediaType = "movie")

        assertEquals(157336, entity.id)
        assertEquals("Interstellar", entity.title)
        assertEquals("movie", entity.mediaType)
        assertEquals("2014-11-05", entity.firstAirDate)
        assertEquals(8.6, entity.voteAverage, 0.001)
        assertEquals("[12,18,878]", entity.genreIds)
    }

    @Test
    fun tmdbEpisodeDto_mapsToEpisodeEntity() {
        val dto = TmdbEpisodeDto(
            id = 63056,
            seasonNumber = 1,
            episodeNumber = 1,
            name = "Winter Is Coming",
            overview = "Jon Arryn, the Hand of the King, is dead.",
            stillPath = "/winter.jpg",
            airDate = "2011-04-17",
            runtime = 62,
            voteAverage = 8.2,
            voteCount = 500
        )

        val entity = dto.toEntity(showId = 1399)

        assertEquals(63056, entity.id)
        assertEquals(1399, entity.showId)
        assertEquals(1, entity.seasonNumber)
        assertEquals(1, entity.episodeNumber)
        assertEquals("Winter Is Coming", entity.name)
        assertEquals("Jon Arryn, the Hand of the King, is dead.", entity.overview)
        assertEquals("/winter.jpg", entity.stillPath)
        assertEquals("2011-04-17", entity.airDate)
        assertEquals(62, entity.runtime)
    }

    @Test
    fun tmdbSeasonSummaryDto_mapsToSeasonEntity() {
        val dto = TmdbSeasonSummaryDto(
            id = 3624,
            seasonNumber = 1,
            name = "Season 1",
            overview = "Trouble is brewing in Westeros.",
            posterPath = "/season1.jpg",
            episodeCount = 10,
            airDate = "2011-04-17"
        )

        val entity = dto.toEntity(showId = 1399)

        assertEquals(3624, entity.id)
        assertEquals(1399, entity.showId)
        assertEquals(1, entity.seasonNumber)
        assertEquals("Season 1", entity.name)
        assertEquals("Trouble is brewing in Westeros.", entity.overview)
        assertEquals("/season1.jpg", entity.posterPath)
        assertEquals(10, entity.episodeCount)
        assertEquals("2011-04-17", entity.airDate)
    }
}
