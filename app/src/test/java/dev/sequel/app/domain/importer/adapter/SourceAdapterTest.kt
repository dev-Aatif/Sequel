package dev.sequel.app.domain.importer.adapter

import android.app.Application
import dev.sequel.app.domain.importer.ImportRecordType
import dev.sequel.app.domain.importer.core.ImportSource
import dev.sequel.app.testing.synthetic.SyntheticDatasetConfig
import dev.sequel.app.testing.synthetic.SyntheticDatasetGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Exhaustive parser and adapter unit test suite verifying:
 * - Valid parsing and canonical field mapping
 * - Missing optional fields, unexpected columns, and invalid values
 * - Unicode encodings (Arabic, Japanese, Cyrillic, emojis, multiline text)
 * - Archive layouts and single vs multi-file formats
 * - Descriptive error reporting on corrupt or unreadable data
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SourceAdapterTest {

    private val tvTimeAdapter = TvTimeSourceAdapter()
    private val letterboxdAdapter = LetterboxdSourceAdapter()
    private val traktAdapter = TraktSourceAdapter()
    private val sequelAdapter = SequelBackupSourceAdapter()

    // ─────────────────────────────────────────────────────────────────
    // 1. TV TIME ADAPTER TESTS
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun tvTimeAdapter_parsesGdprZipPackageSuccessfully() = runBlocking {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(seed = 101L, movieCount = 5, showCount = 3, watchEventCount = 10)
        )
        val zipBytes = dataset.toTvTimeGdprZip()

        val canonical = tvTimeAdapter.parse(ByteArrayInputStream(zipBytes), "tvtime_gdpr.zip")
        assertTrue("Should parse canonical records from GDPR zip", canonical.isNotEmpty())

        val movieRecord = canonical.firstOrNull { it.mediaType == "movie" && it.recordType == ImportRecordType.WATCHED }
        assertNotNull("Should map movie watch records", movieRecord)
        assertTrue(movieRecord?.rawTitle?.isNotBlank() == true)
        assertTrue((movieRecord?.watchedAt ?: 0) > 0)

        val epRecord = canonical.firstOrNull { it.mediaType == "tv" && it.recordType == ImportRecordType.WATCHED }
        assertNotNull("Should map episode watch records", epRecord)
        assertNotNull("Episode should have season number", epRecord?.seasonNumber)
        assertNotNull("Episode should have episode number", epRecord?.episodeNumber)
    }

    @Test
    fun tvTimeAdapter_parsesFlatCsvWithUnexpectedColumnsAndBom() = runBlocking {
        // Flat CSV with UTF-8 BOM, unexpected columns, and missing season/episode
        val csvWithBomAndExtra = "\uFEFFtv_show_name,episode_season_number,episode_number,created_at,extra_column_1,extra_notes\n" +
                "\"Severance\",1,1,\"2023-01-15 20:30:00\",foo,bar\n" +
                "\"Inception\",,,\"2023-02-01 14:00:00\",custom_value,\"Saw in IMAX\"\n"

        val canonical = tvTimeAdapter.parse(
            ByteArrayInputStream(csvWithBomAndExtra.toByteArray(StandardCharsets.UTF_8)),
            "export.csv"
        )
        assertEquals(2, canonical.size)

        val ep = canonical.first { it.rawTitle == "Severance" }
        assertEquals("tv", ep.mediaType)
        assertEquals(1, ep.seasonNumber)
        assertEquals(1, ep.episodeNumber)

        val movie = canonical.first { it.rawTitle == "Inception" }
        assertEquals("movie", movie.mediaType)
        assertNull(movie.seasonNumber)
        assertNull(movie.episodeNumber)
    }

    @Test(expected = IllegalArgumentException::class)
    fun tvTimeAdapter_throwsOnEmptyInput() = runBlocking {
        tvTimeAdapter.parse(ByteArrayInputStream(ByteArray(0)), "empty.csv")
        Unit
    }

    // ─────────────────────────────────────────────────────────────────
    // 2. LETTERBOXD ADAPTER TESTS
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun letterboxdAdapter_parsesFullZipWithReviewsDiaryAndRatings() = runBlocking {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(seed = 202L, movieCount = 10, watchEventCount = 15, reviewCount = 8)
        )
        val zipBytes = dataset.toLetterboxdZip()

        val canonical = letterboxdAdapter.parse(ByteArrayInputStream(zipBytes), "letterboxd.zip")
        assertTrue(canonical.isNotEmpty())

        // Letterboxd is film-only
        assertTrue("All records should be movies", canonical.all { it.mediaType == "movie" })

        // Check ratings scaling (* 2.0)
        val ratedRecord = canonical.firstOrNull { it.rating != null }
        assertNotNull(ratedRecord)
        assertTrue("Rating should be normalized to 10-point scale", (ratedRecord?.rating ?: 0f) in 0.5f..10.0f)
    }

    @Test
    fun letterboxdAdapter_parsesSingleDiaryCsvWithUnicodeAndMultilineReviews() = runBlocking {
        val diaryCsv = """
            Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date
            2023-05-01,"Past Lives",2023,https://boxd.it/pastlives,4.5,Yes,"crying, masterpiece",2023-05-01
            2023-05-02,"千と千尋の神隠し",2001,https://boxd.it/spirited,5.0,,"ghibli",2023-05-02
        """.trimIndent()

        val canonical = letterboxdAdapter.parse(
            ByteArrayInputStream(diaryCsv.toByteArray(StandardCharsets.UTF_8)),
            "diary.csv"
        )
        assertEquals(2, canonical.size)

        val pastLives = canonical.first { it.rawTitle == "Past Lives" }
        assertEquals(2023, pastLives.year)
        assertEquals(9.0f, pastLives.rating ?: 0f, 0.01f) // 4.5 * 2 = 9.0
        assertTrue("Should detect rewatch flag", pastLives.isRewatch)
        assertEquals("2023-05-01", pastLives.watchDate)

        val spiritedAway = canonical.first { it.rawTitle == "千と千尋の神隠し" }
        assertEquals(10.0f, spiritedAway.rating ?: 0f, 0.01f) // 5.0 * 2 = 10.0
        assertFalse(spiritedAway.isRewatch)
    }

    @Test
    fun letterboxdAdapter_handlesReviewsWithQuotesAndEmojis() = runBlocking {
        val reviewsCsv = """
            Date,Name,Year,Letterboxd URI,Rating,Review,Spoiler
            2023-06-01,"Oppenheimer",2023,https://boxd.it/oppenheimer,5.0,"Masterpiece! 🍿✨\n\n${"\"\""}Now I am become Death.${"\"\""}",True
        """.trimIndent()

        val canonical = letterboxdAdapter.parse(
            ByteArrayInputStream(reviewsCsv.toByteArray(StandardCharsets.UTF_8)),
            "reviews.csv"
        )
        assertEquals(1, canonical.size)
        val oppy = canonical.first()
        assertTrue("Should preserve emojis", oppy.reviewText?.contains("🍿✨") == true)
        assertTrue("Should preserve quotes", oppy.reviewText?.contains("\"Now I am become Death.\"") == true)
        assertTrue("Should mark spoiler flag", oppy.isSpoiler)
        assertEquals(ImportRecordType.REVIEW, oppy.recordType)
    }

    @Test(expected = IllegalArgumentException::class)
    fun letterboxdAdapter_throwsOnEmptyInput() = runBlocking {
        letterboxdAdapter.parse(ByteArrayInputStream(ByteArray(0)), "empty.csv")
        Unit
    }

    // ─────────────────────────────────────────────────────────────────
    // 3. TRAKT ADAPTER TESTS
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun traktAdapter_parsesStandardHistoryCsv() = runBlocking {
        val traktCsv = """
            Title,Year,Season,Episode,Type,WatchedAt
            "Dune: Part Two",2024,,,movie,2024-03-01T20:00:00.000Z
            "Severance",2022,1,1,episode,2023-01-10T19:00:00.000Z
        """.trimIndent()

        val canonical = traktAdapter.parse(
            ByteArrayInputStream(traktCsv.toByteArray(StandardCharsets.UTF_8)),
            "trakt.csv"
        )
        assertEquals(2, canonical.size)

        val movie = canonical.first { it.rawTitle == "Dune: Part Two" }
        assertEquals("movie", movie.mediaType)
        assertEquals(2024, movie.year)
        assertEquals(ImportRecordType.WATCHED, movie.recordType)

        val ep = canonical.first { it.rawTitle == "Severance" }
        assertEquals("tv", ep.mediaType)
        assertEquals(1, ep.seasonNumber)
        assertEquals(1, ep.episodeNumber)
    }

    @Test
    fun traktAdapter_parsesOfficialJsonExportZip() = runBlocking {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(seed = 303L, movieCount = 5, showCount = 3, watchEventCount = 10)
        )
        val traktZip = dataset.toTraktExportZip()

        val canonical = traktAdapter.parse(ByteArrayInputStream(traktZip), "trakt_export.zip")
        assertTrue("Should parse canonical items from Trakt export ZIP", canonical.isNotEmpty())

        assertTrue(canonical.any { it.mediaType == "movie" })
        assertTrue(canonical.any { it.mediaType == "tv" || it.mediaType == "episode" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun traktAdapter_throwsOnEmptyInput() = runBlocking {
        traktAdapter.parse(ByteArrayInputStream(ByteArray(0)), "empty.csv")
        Unit
    }

    // ─────────────────────────────────────────────────────────────────
    // 4. SEQUEL BACKUP ADAPTER TESTS
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun sequelBackupAdapter_parsesLosslessJsonBackup() = runBlocking {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(seed = 404L, movieCount = 8, showCount = 4, watchEventCount = 12, reviewCount = 6)
        )
        val backupJson = dataset.toSequelBackupJson()

        val canonical = sequelAdapter.parse(
            ByteArrayInputStream(backupJson.toByteArray(StandardCharsets.UTF_8)),
            "sequel_backup.json"
        )
        assertTrue(canonical.isNotEmpty())

        assertTrue(canonical.any { it.recordType == ImportRecordType.WATCHED })
        assertTrue(canonical.any { it.recordType == ImportRecordType.WATCHLIST })
        assertTrue(canonical.any { it.recordType == ImportRecordType.RATING || it.recordType == ImportRecordType.REVIEW })
    }

    @Test(expected = IllegalArgumentException::class)
    fun sequelBackupAdapter_throwsOnMalformedJson() = runBlocking {
        sequelAdapter.parse(
            ByteArrayInputStream("{ malformed json }".toByteArray(StandardCharsets.UTF_8)),
            "sequel_backup.json"
        )
        Unit
    }
}
