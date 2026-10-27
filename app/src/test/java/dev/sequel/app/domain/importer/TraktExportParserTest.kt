package dev.sequel.app.domain.importer

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TraktExportParserTest {

    private lateinit var parser: TraktExportParser

    @Before
    fun setUp() {
        parser = TraktExportParser()
    }

    @Test
    fun parseTraktCsv_moviesAndEpisodes() = runBlocking {
        val csv = """
            Title,Year,Season,Episode,Type,WatchedAt
            "Severance",2022,1,1,"episode","2024-01-15T20:30:00.000Z"
            "Severance",2022,1,2,"episode","2024-01-15T21:30:00.000Z"
            "Dune: Part Two",2024,,,"movie","2024-03-01T18:00:00.000Z"
        """.trimIndent()

        val items = parser.parseStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(3, items.size)

        val ep1 = items[0]
        assertEquals("Severance", ep1.title)
        assertEquals(2022, ep1.year)
        assertEquals(1, ep1.seasonNumber)
        assertEquals(1, ep1.episodeNumber)
        assertEquals("tv", ep1.mediaType)
        assertEquals(ImportRecordType.WATCHED, ep1.recordType)

        val movie = items[2]
        assertEquals("Dune: Part Two", movie.title)
        assertEquals(2024, movie.year)
        assertNull(movie.seasonNumber)
        assertNull(movie.episodeNumber)
        assertEquals("movie", movie.mediaType)
        assertEquals(ImportRecordType.WATCHED, movie.recordType)
    }

    @Test
    fun parseTraktWatchedMoviesJson_extractsMovieAndTmdbId() = runBlocking {
        val json = """
            [
              {
                "plays": 1,
                "last_watched_at": "2024-03-01T20:00:00.000Z",
                "movie": {
                  "title": "Dune: Part Two",
                  "year": 2024,
                  "ids": {
                    "trakt": 1,
                    "slug": "dune-part-two-2024",
                    "imdb": "tt15239678",
                    "tmdb": 693134
                  }
                }
              }
            ]
        """.trimIndent()

        val items = parser.parseStream(ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(1, items.size)

        val m = items[0]
        assertEquals("Dune: Part Two", m.title)
        assertEquals(2024, m.year)
        assertEquals("movie", m.mediaType)
        assertEquals(ImportRecordType.WATCHED, m.recordType)
        assertEquals("693134", m.externalId)
        assertEquals("tmdb", m.externalIdType)
    }

    @Test
    fun parseTraktWatchedShowsJson_unpacksSeasonsAndEpisodes() = runBlocking {
        val json = """
            [
              {
                "plays": 2,
                "last_watched_at": "2024-02-15T18:00:00.000Z",
                "show": {
                  "title": "Severance",
                  "year": 2022,
                  "ids": {
                    "tmdb": 95557
                  }
                },
                "seasons": [
                  {
                    "number": 1,
                    "episodes": [
                      { "number": 1, "plays": 1, "last_watched_at": "2024-02-15T18:00:00.000Z" },
                      { "number": 2, "plays": 1, "last_watched_at": "2024-02-15T19:00:00.000Z" }
                    ]
                  }
                ]
              }
            ]
        """.trimIndent()

        val items = parser.parseStream(ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(2, items.size)

        assertEquals("Severance", items[0].title)
        assertEquals(1, items[0].seasonNumber)
        assertEquals(1, items[0].episodeNumber)
        assertEquals("95557", items[0].externalId)

        assertEquals("Severance", items[1].title)
        assertEquals(1, items[1].seasonNumber)
        assertEquals(2, items[1].episodeNumber)
    }

    @Test
    fun parseTraktRatingsAndWatchlistJson_handlesRatingsAndWatchlist() = runBlocking {
        val ratingsJson = """
            [
              {
                "rating": 9,
                "rated_at": "2024-03-02T12:00:00.000Z",
                "type": "movie",
                "movie": { "title": "Oppenheimer", "year": 2023, "ids": { "tmdb": 872585 } }
              }
            ]
        """.trimIndent()

        val watchlistJson = """
            [
              {
                "listed_at": "2024-01-10T12:00:00.000Z",
                "type": "movie",
                "movie": { "title": "Gladiator II", "year": 2024, "ids": { "tmdb": 558449 } }
              }
            ]
        """.trimIndent()

        val ratedItems = parser.parseStream(ByteArrayInputStream(ratingsJson.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(1, ratedItems.size)
        assertEquals("Oppenheimer", ratedItems[0].title)
        assertEquals(ImportRecordType.RATING, ratedItems[0].recordType)
        assertEquals(9.0f, ratedItems[0].rating ?: 0f, 0.01f)

        val watchItems = parser.parseStream(ByteArrayInputStream(watchlistJson.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(1, watchItems.size)
        assertEquals("Gladiator II", watchItems[0].title)
        assertEquals(ImportRecordType.WATCHLIST, watchItems[0].recordType)
    }

    @Test
    fun parseTraktZipArchive_extractsMultipleFiles() = runBlocking {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("watched_movies.json"))
            val mData = """[{"movie": {"title": "Arrival", "year": 2016, "ids": {"tmdb": 329865}}}]"""
                .toByteArray(StandardCharsets.UTF_8)
            zip.write(mData)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("watchlist.json"))
            val wData = """[{"listed_at": "2024-01-01T00:00:00.000Z", "movie": {"title": "Challengers", "year": 2024}}]"""
                .toByteArray(StandardCharsets.UTF_8)
            zip.write(wData)
            zip.closeEntry()
        }

        val items = parser.parseStream(ByteArrayInputStream(out.toByteArray()))
        assertEquals(2, items.size)

        val arrival = items.find { it.title == "Arrival" }
        assertNotNull(arrival)
        assertEquals(ImportRecordType.WATCHED, arrival?.recordType)

        val challengers = items.find { it.title == "Challengers" }
        assertNotNull(challengers)
        assertEquals(ImportRecordType.WATCHLIST, challengers?.recordType)
    }
}
