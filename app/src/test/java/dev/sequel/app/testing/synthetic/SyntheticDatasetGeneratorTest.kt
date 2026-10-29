package dev.sequel.app.testing.synthetic

import android.app.Application
import dev.sequel.app.domain.importer.ImportRecordType
import dev.sequel.app.domain.importer.adapter.*
import dev.sequel.app.domain.importer.archive.ZipArchiveExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * Test suite for the determinism, referential integrity, edge-case coverage,
 * source-adapter conformity, and fault injection of the Synthetic Dataset Generator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SyntheticDatasetGeneratorTest {

    private val tvTimeAdapter = TvTimeSourceAdapter()
    private val letterboxdAdapter = LetterboxdSourceAdapter()
    private val traktAdapter = TraktSourceAdapter()
    private val sequelAdapter = SequelBackupSourceAdapter()

    @Test
    fun deterministicGeneration_sameSeedProducesIdenticalOutput() {
        val config1 = SyntheticDatasetConfig(seed = 12345L, movieCount = 10, showCount = 5)
        val config2 = SyntheticDatasetConfig(seed = 12345L, movieCount = 10, showCount = 5)

        val dataset1 = SyntheticDatasetGenerator.generate(config1)
        val dataset2 = SyntheticDatasetGenerator.generate(config2)

        assertEquals(dataset1.movies.size, dataset2.movies.size)
        assertEquals(dataset1.shows.size, dataset2.shows.size)
        assertEquals(dataset1.watchEvents.size, dataset2.watchEvents.size)
        assertEquals(dataset1.reviews.size, dataset2.reviews.size)

        for (i in dataset1.movies.indices) {
            assertEquals(dataset1.movies[i].id, dataset2.movies[i].id)
            assertEquals(dataset1.movies[i].title, dataset2.movies[i].title)
            assertEquals(dataset1.movies[i].year, dataset2.movies[i].year)
            assertEquals(dataset1.movies[i].tmdbId, dataset2.movies[i].tmdbId)
        }

        for (i in dataset1.watchEvents.indices) {
            assertEquals(dataset1.watchEvents[i].eventId, dataset2.watchEvents[i].eventId)
            assertEquals(dataset1.watchEvents[i].watchedAt, dataset2.watchEvents[i].watchedAt)
            assertEquals(dataset1.watchEvents[i].watchDate, dataset2.watchEvents[i].watchDate)
        }
    }

    @Test
    fun seedDivergence_differentSeedsProduceDifferentDatasets() {
        val datasetA = SyntheticDatasetGenerator.generate(SyntheticDatasetConfig(seed = 111L, movieCount = 15, reviewCount = 10))
        val datasetB = SyntheticDatasetGenerator.generate(SyntheticDatasetConfig(seed = 999L, movieCount = 15, reviewCount = 10))

        assertNotEquals(datasetA.movies.map { it.year }, datasetB.movies.map { it.year })
        assertNotEquals(datasetA.reviews.map { it.rating }, datasetB.reviews.map { it.rating })
    }

    @Test
    fun defaultGeneration_isReferentiallyValid() {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(seed = 42L, movieCount = 20, showCount = 10)
        )

        // 1. Shows & Episodes referential integrity
        val showIds = dataset.shows.map { it.id }.toSet()
        dataset.shows.forEach { show ->
            assertTrue(show.id > 0)
            assertTrue(show.title.isNotBlank())
            show.seasons.forEach { season ->
                assertEquals(show.id, season.showId)
                season.episodes.forEach { episode ->
                    assertEquals(show.id, episode.showId)
                    assertEquals(season.seasonNumber, episode.seasonNumber)
                    assertTrue(episode.episodeId > 0)
                }
            }
        }

        // 2. Watch Events referential integrity
        dataset.watchEvents.forEach { ev ->
            assertTrue(ev.watchedAt > 0)
            assertTrue(ev.title.isNotBlank())
            if (ev.mediaType == "tv") {
                assertTrue("TV watch event must reference an existing show", showIds.contains(ev.showOrMovieId))
            }
        }

        // 3. Reviews bounds
        dataset.reviews.forEach { r ->
            assertTrue(r.mediaId > 0)
            if (r.rating != null) {
                assertTrue("Rating should be within valid bounds (0.5..10.0)", r.rating in 0.5f..10.0f)
            }
        }

        // 4. Default dataset should have zero injected faults
        assertTrue(dataset.injectedFaults.isEmpty())
    }

    @Test
    fun realisticEdgeCases_animeSpecialsAndIrregularNumbering() {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(
                seed = 777L,
                showCount = 10,
                edgeCases = EdgeCaseConfig(
                    includeAnimeAndSpecials = true,
                    includeIrregularNumbering = true
                )
            )
        )

        // Verify Season 0 specials exist
        val hasSpecials = dataset.shows.any { s -> s.seasons.any { it.isSpecial && it.seasonNumber == 0 } }
        assertTrue("Dataset should contain Season 0 special seasons", hasSpecials)

        // Verify high episode numbers for anime (e.g. 1054)
        val hasHighEpisodeNumber = dataset.shows.any { s ->
            s.seasons.any { it.episodes.any { ep -> ep.episodeNumber > 1000 } }
        }
        assertTrue("Anime shows should have high episode numbers (> 1000)", hasHighEpisodeNumber)

        // Verify fractional episode numbers (e.g. 5.5 recap)
        val hasFractionalEp = dataset.shows.any { s ->
            s.seasons.any { it.episodes.any { ep -> ep.fractionalEpisodeNumber != null } }
        }
        assertTrue("Should contain fractional episode numbers", hasFractionalEp)
    }

    @Test
    fun realisticEdgeCases_leapDaysMidnightAndRewatches() {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(
                seed = 888L,
                movieCount = 20,
                edgeCases = EdgeCaseConfig(
                    includeLeapDaysAndMidnightTimestamps = true,
                    includeLegitimateRewatches = true
                )
            )
        )

        // Verify Leap Day (2024-02-29)
        val hasLeapDay = dataset.watchEvents.any { it.watchDate == "2024-02-29" }
        assertTrue("Should contain Leap Day watch event (2024-02-29)", hasLeapDay)

        // Verify Legitimate Rewatch flag
        val hasRewatch = dataset.watchEvents.any { it.isRewatch }
        assertTrue("Should contain legitimate rewatch event", hasRewatch)
    }

    @Test
    fun realisticEdgeCases_multilingualReviewsAndUnicode() {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(
                seed = 999L,
                reviewCount = 30,
                edgeCases = EdgeCaseConfig(
                    includeMultilingualAndEmojiReviews = true,
                    includeRatingScaleVariance = true
                )
            )
        )

        val reviews = dataset.reviews
        assertTrue("Should include emoji reviews", reviews.any { it.reviewText?.contains("🍿") == true })
        assertTrue("Should include Japanese Unicode", reviews.any { it.reviewText?.contains("圧巻") == true })
        assertTrue("Should include Arabic Unicode", reviews.any { it.reviewText?.contains("تحفة") == true })
        assertTrue("Should include empty reviews", reviews.any { it.reviewText == "" })
        assertTrue("Should include multiline reviews", reviews.any { it.reviewText?.contains("\n") == true })

        // Rating scale variance (min 0.5, max 10.0, fractional 7.5)
        assertTrue("Should contain minimum rating (0.5)", reviews.any { it.rating == 0.5f })
        assertTrue("Should contain maximum rating (10.0)", reviews.any { it.rating == 10.0f })
        assertTrue("Should contain fractional rating (7.5)", reviews.any { it.rating == 7.5f })
    }

    @Test
    fun tvTimeAdapter_successfullyParsesSyntheticGdprAndFlatCsv() = runBlocking {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(seed = 101L, movieCount = 10, showCount = 5, watchEventCount = 20)
        )

        // 1. GDPR ZIP
        val zipBytes = dataset.toTvTimeGdprZip()
        assertTrue(ZipArchiveExtractor.isZipStream(ByteArrayInputStream(zipBytes)))
        val gdprCanonical = tvTimeAdapter.parse(ByteArrayInputStream(zipBytes), "tvtime_gdpr.zip")
        assertTrue("TV Time GDPR adapter should parse records", gdprCanonical.isNotEmpty())
        assertTrue(gdprCanonical.any { it.recordType == ImportRecordType.WATCHED })

        // 2. Flat CSV
        val csvString = dataset.toTvTimeFlatCsv()
        assertTrue(csvString.contains("tv_show_name"))
        val flatCanonical = tvTimeAdapter.parse(ByteArrayInputStream(csvString.toByteArray()), "tvtime.csv")
        assertTrue("TV Time flat CSV adapter should parse records", flatCanonical.isNotEmpty())
    }

    @Test
    fun letterboxdAdapter_successfullyParsesSyntheticLetterboxdZip() = runBlocking {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(seed = 202L, movieCount = 15, watchEventCount = 25, reviewCount = 10)
        )

        // 1. Full Letterboxd ZIP
        val zipBytes = dataset.toLetterboxdZip()
        assertTrue(ZipArchiveExtractor.isZipStream(ByteArrayInputStream(zipBytes)))
        val canonicalItems = letterboxdAdapter.parse(ByteArrayInputStream(zipBytes), "letterboxd.zip")
        assertTrue("Letterboxd adapter should parse records", canonicalItems.isNotEmpty())
        assertTrue(canonicalItems.any { it.recordType == ImportRecordType.WATCHED })
        assertTrue(canonicalItems.any { it.recordType == ImportRecordType.RATING || it.recordType == ImportRecordType.REVIEW })

        // 2. Single CSV (watched.csv)
        val singleCsv = dataset.toLetterboxdSingleCsv("watched.csv")
        val singleCanonical = letterboxdAdapter.parse(ByteArrayInputStream(singleCsv.toByteArray()), "watched.csv")
        assertTrue("Letterboxd single CSV adapter should parse records", singleCanonical.isNotEmpty())
    }

    @Test
    fun traktAdapter_successfullyParsesSyntheticTraktCsvAndExportZip() = runBlocking {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(seed = 303L, movieCount = 10, showCount = 5, watchEventCount = 20)
        )

        // 1. Trakt CSV
        val traktCsv = dataset.toTraktCsv()
        assertTrue(traktCsv.startsWith("Title,Year,Season,Episode,Type,WatchedAt"))
        val csvCanonical = traktAdapter.parse(ByteArrayInputStream(traktCsv.toByteArray()), "trakt.csv")
        assertTrue("Trakt CSV adapter should parse records", csvCanonical.isNotEmpty())
        assertTrue(csvCanonical.any { it.mediaType == "movie" })
        assertTrue(csvCanonical.any { it.mediaType == "tv" || it.mediaType == "episode" })

        // 2. Trakt Export ZIP
        val traktZip = dataset.toTraktExportZip()
        assertTrue(ZipArchiveExtractor.isZipStream(ByteArrayInputStream(traktZip)))
        val zipCanonical = traktAdapter.parse(ByteArrayInputStream(traktZip), "trakt_export.zip")
        assertTrue("Trakt ZIP adapter should parse records", zipCanonical.isNotEmpty())
    }

    @Test
    fun sequelBackupAdapter_successfullyParsesSyntheticSequelBackupJson() = runBlocking {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(seed = 404L, movieCount = 10, showCount = 5, watchEventCount = 15, reviewCount = 8)
        )

        val backupJson = dataset.toSequelBackupJson()
        assertTrue(backupJson.contains("\"schemaVersion\": 1"))

        val canonical = sequelAdapter.parse(ByteArrayInputStream(backupJson.toByteArray()), "sequel_backup.json")
        assertTrue("Sequel backup adapter should parse records", canonical.isNotEmpty())
        assertTrue(canonical.any { it.recordType == ImportRecordType.WATCHED })
        assertTrue(canonical.any { it.recordType == ImportRecordType.WATCHLIST })
    }

    @Test
    fun faultInjection_recordsInjectedFaultsAndAppliesExpectedCorruptions() {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(
                seed = 505L,
                movieCount = 30,
                faults = FaultInjectionConfig(
                    missingIdRate = 0.5,
                    missingYearRate = 0.5,
                    conflictingIdRate = 0.3,
                    invalidNumericRate = 0.4
                )
            )
        )

        assertTrue("Should have recorded injected faults in audit log", dataset.injectedFaults.isNotEmpty())
        assertTrue(dataset.injectedFaults.any { it.faultType == "MISSING_ID" })
        assertTrue(dataset.injectedFaults.any { it.faultType == "MISSING_YEAR" })

        // Test corrupted ZIP fixtures
        val truncatedZip = dataset.toCorruptedZip(CorruptedZipType.TRUNCATED_BYTES)
        assertTrue(truncatedZip.isNotEmpty())

        val invalidHeaderZip = dataset.toCorruptedZip(CorruptedZipType.INVALID_HEADER)
        assertFalse(ZipArchiveExtractor.isZipStream(ByteArrayInputStream(invalidHeaderZip)))

        val emptyZip = dataset.toCorruptedZip(CorruptedZipType.EMPTY_STREAM)
        assertEquals(0, emptyZip.size)
    }

    @Test
    fun generationReport_producesHumanReadableSummaryAndReproducibleSnippet() {
        val dataset = SyntheticDatasetGenerator.generate(
            SyntheticDatasetConfig(seed = 606L, movieCount = 10, showCount = 5)
        )

        val report = dataset.report
        assertEquals(606L, report.seed)
        assertEquals(10, report.totalMovies)
        assertEquals(5, report.totalShows)

        val reportString = report.toString()
        assertTrue(reportString.contains("SEQUEL SYNTHETIC DATASET GENERATION & FAILURE REPORT"))
        assertTrue(reportString.contains("Seed: 606"))
        assertTrue(reportString.contains("To Reproduce This Dataset in a Test:"))
        assertTrue(reportString.contains("seed = 606L"))
    }
}
