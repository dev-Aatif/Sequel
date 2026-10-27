package dev.sequel.app.domain.importer.normalizer

import dev.sequel.app.domain.importer.ImportRecordType
import dev.sequel.app.domain.importer.core.CanonicalImportRecord
import dev.sequel.app.domain.importer.core.ImportSource
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ImportNormalizerTest {

    private lateinit var normalizer: ImportNormalizer

    @Before
    fun setUp() {
        normalizer = ImportNormalizer()
    }

    @Test
    fun normalize_extractsEmbeddedYearAndCleansTitle() {
        val input = listOf(
            CanonicalImportRecord(
                rawTitle = " \"Inception (2010)\" ",
                normalizedTitle = "Inception (2010)",
                year = null,
                mediaType = "movie"
            ),
            CanonicalImportRecord(
                rawTitle = "Severance [2022]",
                normalizedTitle = "Severance [2022]",
                year = null,
                mediaType = "tv",
                seasonNumber = 1,
                episodeNumber = 1
            )
        )

        val normalized = normalizer.normalize(input)
        assertEquals(2, normalized.size)

        assertEquals("Inception", normalized[0].normalizedTitle)
        assertEquals(2010, normalized[0].year)

        assertEquals("Severance", normalized[1].normalizedTitle)
        assertEquals(2022, normalized[1].year)
        assertEquals("tv", normalized[1].mediaType)
    }

    @Test
    fun normalize_roundsRatingToHalfPillSteps() {
        val input = listOf(
            CanonicalImportRecord(
                rawTitle = "Past Lives",
                normalizedTitle = "Past Lives",
                mediaType = "movie",
                rating = 8.74f // should round to 8.5
            ),
            CanonicalImportRecord(
                rawTitle = "Dune: Part Two",
                normalizedTitle = "Dune: Part Two",
                mediaType = "movie",
                rating = 9.8f // should round to 10.0
            ),
            CanonicalImportRecord(
                rawTitle = "Bad Movie",
                normalizedTitle = "Bad Movie",
                mediaType = "movie",
                rating = -1.0f // should become null
            )
        )

        val normalized = normalizer.normalize(input)
        assertEquals(3, normalized.size)
        assertEquals(8.5f, normalized[0].rating ?: 0f, 0.01f)
        assertEquals(10.0f, normalized[1].rating ?: 0f, 0.01f)
        assertNull(normalized[2].rating)
    }

    @Test
    fun normalize_deduplicatesIdenticalRecordsInSingleBatch() {
        val record = CanonicalImportRecord(
            rawTitle = "The Bear",
            normalizedTitle = "The Bear",
            year = 2022,
            mediaType = "tv",
            seasonNumber = 1,
            episodeNumber = 1,
            watchedAt = 1680000000000L,
            source = ImportSource.TV_TIME
        )

        val input = listOf(record, record.copy(rawTitle = " The Bear "))
        val normalized = normalizer.normalize(input)

        assertEquals(1, normalized.size)
    }
}
