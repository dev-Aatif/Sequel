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

class LetterboxdExportParserTest {

    private lateinit var parser: LetterboxdExportParser

    @Before
    fun setUp() {
        parser = LetterboxdExportParser()
    }

    @Test
    fun parseWatchedCsv_moviesParsedAsWatched() = runBlocking {
        val csv = """
            Date,Name,Year,Letterboxd URI
            2023-10-15,"Past Lives",2023,https://boxd.it/v5lW
            2023-10-20,"Anatomy of a Fall",2023,https://boxd.it/xRty
        """.trimIndent()

        val items = parser.parseStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(2, items.size)

        val m1 = items[0]
        assertEquals("Past Lives", m1.title)
        assertEquals(2023, m1.year)
        assertEquals("movie", m1.mediaType)
        assertEquals(ImportRecordType.WATCHED, m1.recordType)
        assertEquals("https://boxd.it/v5lW", m1.externalId)
        assertEquals("letterboxd", m1.externalIdType)
    }

    @Test
    fun parseDiaryCsv_handlesWatchedDateRatingAndRewatch() = runBlocking {
        val csv = """
            Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date
            2023-11-01,"The Zone of Interest",2023,https://boxd.it/v6wG,4.5,No,"cinema",2023-10-31
            2023-11-05,"Blade Runner 2049",2017,https://boxd.it/97C0,5.0,Yes,"imax,favorite",2023-11-04
        """.trimIndent()

        val items = parser.parseStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(2, items.size)

        val m1 = items[0]
        assertEquals("The Zone of Interest", m1.title)
        assertEquals(2023, m1.year)
        // 4.5 * 2.0 = 9.0
        assertEquals(9.0f, m1.rating ?: 0f, 0.01f)
        assertFalse(m1.isRewatch)

        val m2 = items[1]
        assertEquals("Blade Runner 2049", m2.title)
        assertEquals(2017, m2.year)
        // 5.0 * 2.0 = 10.0
        assertEquals(10.0f, m2.rating ?: 0f, 0.01f)
        assertTrue(m2.isRewatch)
    }

    @Test
    fun parseRatingsCsv_correctlyScalesToSequelScale() = runBlocking {
        val csv = """
            Date,Name,Year,Letterboxd URI,Rating
            2023-08-01,"Barbie",2023,https://boxd.it/j2eE,4.0
            2023-08-02,"Poor Things",2023,https://boxd.it/uOfe,4.5
        """.trimIndent()

        val items = parser.parseStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(2, items.size)

        assertEquals("Barbie", items[0].title)
        assertEquals(8.0f, items[0].rating ?: 0f, 0.01f)
        assertEquals(ImportRecordType.RATING, items[0].recordType)

        assertEquals("Poor Things", items[1].title)
        assertEquals(9.0f, items[1].rating ?: 0f, 0.01f)
    }

    @Test
    fun parseReviewsCsv_capturesReviewTextAndSpoilerFlag() = runBlocking {
        val csv = """
            Date,Name,Year,Letterboxd URI,Rating,Review,Spoiler
            2023-07-22,"Oppenheimer",2023,https://boxd.it/t2S4,5.0,"A harrowing cinematic masterpiece.",False
            2023-07-23,"Spider-Man: Across the Spider-Verse",2023,https://boxd.it/pAae,4.5,"The canon event reveal blew my mind.",True
        """.trimIndent()

        val items = parser.parseStream(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(2, items.size)

        val r1 = items[0]
        assertEquals("Oppenheimer", r1.title)
        assertEquals("A harrowing cinematic masterpiece.", r1.reviewText)
        assertEquals(10.0f, r1.rating ?: 0f, 0.01f)
        assertFalse(r1.isSpoiler)
        assertEquals(ImportRecordType.REVIEW, r1.recordType)

        val r2 = items[1]
        assertEquals("Spider-Man: Across the Spider-Verse", r2.title)
        assertEquals("The canon event reveal blew my mind.", r2.reviewText)
        assertEquals(9.0f, r2.rating ?: 0f, 0.01f)
        assertTrue(r2.isSpoiler)
        assertEquals(ImportRecordType.REVIEW, r2.recordType)
    }

    @Test
    fun parseLetterboxdZipArchive_handlesMultiFileExport() = runBlocking {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("watched.csv"))
            val wData = "Date,Name,Year,Letterboxd URI\n2023-01-01,\"The Batman\",2022,https://boxd.it/j89k\n"
                .toByteArray(StandardCharsets.UTF_8)
            zip.write(wData)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("watchlist.csv"))
            val wlData = "Date,Name,Year,Letterboxd URI\n,\"Dune: Part Two\",2024,https://boxd.it/vmCg\n"
                .toByteArray(StandardCharsets.UTF_8)
            zip.write(wlData)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("reviews.csv"))
            val rData = "Date,Name,Year,Letterboxd URI,Rating,Review,Spoiler\n2023-01-02,\"The Batman\",2022,https://boxd.it/j89k,4.5,\"Gripping noir.\",False\n"
                .toByteArray(StandardCharsets.UTF_8)
            zip.write(rData)
            zip.closeEntry()
        }

        val items = parser.parseStream(ByteArrayInputStream(out.toByteArray()))
        assertTrue(items.isNotEmpty())

        val dune = items.find { it.title == "Dune: Part Two" }
        assertNotNull(dune)
        assertEquals(ImportRecordType.WATCHLIST, dune?.recordType)

        val batmanReview = items.find { it.title == "The Batman" && it.recordType == ImportRecordType.REVIEW }
        assertNotNull(batmanReview)
        assertEquals(9.0f, batmanReview?.rating ?: 0f, 0.01f)
        assertEquals("Gripping noir.", batmanReview?.reviewText)
    }
}
