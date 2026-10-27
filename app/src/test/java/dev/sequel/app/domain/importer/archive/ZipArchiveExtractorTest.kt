package dev.sequel.app.domain.importer.archive

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipArchiveExtractorTest {

    @Test
    fun isZipStream_identifiesValidZipHeader() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("test.txt"))
            zip.write("hello".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }
        val bytes = out.toByteArray()
        assertTrue(ZipArchiveExtractor.isZipStream(ByteArrayInputStream(bytes)))

        val nonZip = "not a zip stream".toByteArray(StandardCharsets.UTF_8)
        assertFalse(ZipArchiveExtractor.isZipStream(ByteArrayInputStream(nonZip)))
    }

    @Test
    fun forEachEntry_sanitizesPathTraversalAndMacArtifacts() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            // Path traversal attempt (Zip Slip)
            zip.putNextEntry(ZipEntry("../../etc/passwd"))
            zip.write("malicious".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            // Mac OS artifact
            zip.putNextEntry(ZipEntry("__MACOSX/._watched.csv"))
            zip.write("mac metadata".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            // Legitimate entry
            zip.putNextEntry(ZipEntry("data/watched.csv"))
            zip.write("legitimate data".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
        }

        val extracted = mutableMapOf<String, String>()
        ZipArchiveExtractor.forEachEntry(ByteArrayInputStream(out.toByteArray())) { name, data ->
            extracted[name] = String(data, StandardCharsets.UTF_8)
        }

        assertEquals(1, extracted.size)
        assertTrue(extracted.containsKey("watched.csv"))
        assertEquals("legitimate data", extracted["watched.csv"])
        assertFalse(extracted.containsKey("passwd"))
        assertFalse(extracted.containsKey("._watched.csv"))
    }
}
