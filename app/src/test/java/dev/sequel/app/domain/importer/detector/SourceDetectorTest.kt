package dev.sequel.app.domain.importer.detector

import dev.sequel.app.domain.importer.core.ArchiveType
import dev.sequel.app.domain.importer.core.ImportSource
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SourceDetectorTest {

    private lateinit var detector: SourceDetector

    @Before
    fun setUp() {
        detector = SourceDetector()
    }

    @Test
    fun detect_identifiesLetterboxdCsvByUriHeader() {
        val csv = "Date,Name,Year,Letterboxd URI,Rating\n2023-01-01,Past Lives,2023,https://boxd.it/v5lW,4.5\n"
        val result = detector.detect(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))

        assertEquals(ImportSource.LETTERBOXD, result.source)
        assertEquals(ArchiveType.CSV, result.archiveType)
        assertTrue(result.isValid)
    }

    @Test
    fun detect_identifiesTvTimeCsvByShowAndSeasonHeaders() {
        val csv = "tv_show_name,episode_season_number,episode_number,created_at\nBreaking Bad,1,1,2021-04-12 20:30:00\n"
        val result = detector.detect(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))

        assertEquals(ImportSource.TV_TIME, result.source)
        assertEquals(ArchiveType.CSV, result.archiveType)
        assertTrue(result.isValid)
    }

    @Test
    fun detect_identifiesTraktCsvHeaders() {
        val csv = "Title,Year,Season,Episode,Type,WatchedAt\nSeverance,2022,1,1,episode,2024-01-15T20:30:00.000Z\n"
        val result = detector.detect(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))

        assertEquals(ImportSource.TRAKT, result.source)
        assertEquals(ArchiveType.CSV, result.archiveType)
        assertTrue(result.isValid)
    }

    @Test
    fun detect_identifiesSequelBackupJson() {
        val json = """{"schemaVersion": 1, "shows": [], "watchedMovies": []}"""
        val result = detector.detect(ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)))

        assertEquals(ImportSource.SEQUEL_BACKUP, result.source)
        assertEquals(ArchiveType.JSON, result.archiveType)
        assertTrue(result.isValid)
    }

    @Test
    fun detect_identifiesTraktRawJson() {
        val json = """[{"movie": {"title": "Dune", "ids": {"trakt": 1}}, "last_watched_at": "2024-01-01T00:00:00.000Z"}]"""
        val result = detector.detect(ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)))

        assertEquals(ImportSource.TRAKT, result.source)
        assertEquals(ArchiveType.JSON, result.archiveType)
        assertTrue(result.isValid)
    }

    @Test
    fun detect_identifiesLetterboxdZipArchive() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("watched.csv"))
            zip.write("Date,Name,Year,Letterboxd URI\n".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("diary.csv"))
            zip.write("Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date\n".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        val result = detector.detect(ByteArrayInputStream(out.toByteArray()))
        assertEquals(ImportSource.LETTERBOXD, result.source)
        assertEquals(ArchiveType.ZIP, result.archiveType)
        assertTrue(result.isValid)
    }

    @Test
    fun detect_identifiesTvTimeGdprZipArchive() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("tracking-prod-records.csv"))
            zip.write("entity_type,movie_name,type,created_at\n".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        val result = detector.detect(ByteArrayInputStream(out.toByteArray()))
        assertEquals(ImportSource.TV_TIME, result.source)
        assertEquals(ArchiveType.ZIP, result.archiveType)
        assertTrue(result.isValid)
    }

    @Test
    fun detect_handlesEmptyAndMalformedStreams() {
        val emptyResult = detector.detect(ByteArrayInputStream(ByteArray(0)))
        assertEquals(ImportSource.UNKNOWN, emptyResult.source)
        assertFalse(emptyResult.isValid)

        val malformedCsv = detector.detect(ByteArrayInputStream("foo,bar\n1,2\n".toByteArray(StandardCharsets.UTF_8)))
        assertEquals(ImportSource.UNKNOWN, malformedCsv.source)
        assertFalse(malformedCsv.isValid)
    }
}
