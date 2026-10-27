package dev.sequel.app.domain.importer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TvTimeCsvParserTest {

    private lateinit var parser: TvTimeCsvParser

    @Before
    fun setUp() {
        parser = TvTimeCsvParser()
    }

    @Test
    fun parseLegacyCsv_validWatchedEpisodesAndMovies() = runBlocking {
        val csv = """
            tv_show_name,episode_season_number,episode_number,created_at
            "Breaking Bad",1,1,"2021-04-12 20:30:00"
            "Breaking Bad",1,2,"2021-04-12 21:25:00"
            "Inception",,,"2022-01-15 18:00:00"
        """.trimIndent()

        val items = parser.parseStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(3, items.size)

        val ep1 = items[0]
        assertEquals("Breaking Bad", ep1.title)
        assertEquals(1, ep1.seasonNumber)
        assertEquals(1, ep1.episodeNumber)
        assertEquals("tv", ep1.mediaType)
        assertEquals(ImportRecordType.WATCHED, ep1.recordType)

        val movie = items[2]
        assertEquals("Inception", movie.title)
        assertNull(movie.seasonNumber)
        assertNull(movie.episodeNumber)
        assertEquals("movie", movie.mediaType)
        assertEquals(ImportRecordType.WATCHED, movie.recordType)
    }

    @Test
    fun parseGdprTrackingProdRecords_handlesWatchedAndWatchlist() = runBlocking {
        val csv = """
            entity_type,movie_name,episode_season_number,episode_number,type,created_at,rating
            movie,"Interstellar",,,watch,"2023-05-10 14:00:00",4.5
            series,"Severance",1,1,watch,"2023-05-11 19:30:00",5.0
            show,"Succession",,,follow,"2023-05-12 10:00:00",
            movie,"Oppenheimer",,,towatch,"2023-05-13 11:00:00",
        """.trimIndent()

        val items = parser.parseStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(4, items.size)

        // Movie watch with rating (4.5 / 5 -> 9.0 / 10)
        val m1 = items[0]
        assertEquals("Interstellar", m1.title)
        assertEquals("movie", m1.mediaType)
        assertEquals(ImportRecordType.WATCHED, m1.recordType)
        assertEquals(9.0f, m1.rating ?: 0f, 0.01f)

        // TV watch with rating (5.0 / 5 -> 10.0 / 10)
        val tv1 = items[1]
        assertEquals("Severance", tv1.title)
        assertEquals(1, tv1.seasonNumber)
        assertEquals(1, tv1.episodeNumber)
        assertEquals("tv", tv1.mediaType)
        assertEquals(ImportRecordType.WATCHED, tv1.recordType)
        assertEquals(10.0f, tv1.rating ?: 0f, 0.01f)

        // Followed show -> Watchlist
        val tvFollow = items[2]
        assertEquals("Succession", tvFollow.title)
        assertEquals("tv", tvFollow.mediaType)
        assertEquals(ImportRecordType.WATCHLIST, tvFollow.recordType)

        // Towatch movie -> Watchlist
        val mFollow = items[3]
        assertEquals("Oppenheimer", mFollow.title)
        assertEquals("movie", mFollow.mediaType)
        assertEquals(ImportRecordType.WATCHLIST, mFollow.recordType)
    }

    @Test
    fun parseGdprZipArchive_extractsMultipleFilesCleanly() = runBlocking {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("seen_episode.csv"))
            val seenData = """
                tv_show_name,season_number,episode_number,updated_at
                "The Bear",1,1,"2023-06-20 22:00:00"
                "The Bear",1,2,"2023-06-20 22:35:00"
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            zip.write(seenData)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("followed_tv_show.csv"))
            val followedData = """
                tv_show_name,created_at
                "The Wire","2023-01-01 00:00:00"
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
            zip.write(followedData)
            zip.closeEntry()
        }

        val items = parser.parseStream(ByteArrayInputStream(out.toByteArray()))
        assertEquals(3, items.size)

        val watchedBear = items.filter { it.title == "The Bear" }
        assertEquals(2, watchedBear.size)
        assertTrue(watchedBear.all { it.recordType == ImportRecordType.WATCHED && it.mediaType == "tv" })

        val followedWire = items.find { it.title == "The Wire" }
        assertNotNull(followedWire)
        assertEquals(ImportRecordType.WATCHLIST, followedWire?.recordType)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseEmptyCsv_throwsIllegalArgumentException() = runBlocking {
        parser.parseStream(ByteArrayInputStream("".toByteArray(StandardCharsets.UTF_8)))
        Unit
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseInvalidHeaderCsv_throwsIllegalArgumentException() = runBlocking {
        val csv = "some_random_column,another_col\n1,2\n"
        parser.parseStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        Unit
    }
}
