package dev.sequel.app.domain.importer.archive

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Safe utility to extract and inspect ZIP archives containing media export data.
 * Protects against Zip Slip path traversal and excessive decompression bombs.
 */
object ZipArchiveExtractor {

    private const val MAX_ENTRY_BYTES = 50 * 1024 * 1024L // 50 MB max per entry
    private const val MAX_TOTAL_BYTES = 150 * 1024 * 1024L // 150 MB max total uncompressed
    private const val MAX_ENTRY_COUNT = 150

    /**
     * Checks if the given input stream is a ZIP archive using the standard ZIP magic bytes.
     * Note: Marks and resets the stream, or uses buffered stream.
     */
    fun isZipStream(inputStream: InputStream): Boolean {
        if (!inputStream.markSupported()) {
            return false
        }
        inputStream.mark(4)
        val header = ByteArray(4)
        val read = inputStream.read(header, 0, 4)
        inputStream.reset()
        if (read < 4) return false
        // Standard PK zip header: 0x50, 0x4B, 0x03, 0x04
        return header[0] == 0x50.toByte() &&
               header[1] == 0x4B.toByte() &&
               header[2] == 0x03.toByte() &&
               header[3] == 0x04.toByte()
    }

    /**
     * Iterates over safe entries in a ZIP stream, ignoring macOS metadata and directory entries.
     */
    fun forEachEntry(
        inputStream: InputStream,
        block: (entryName: String, entryData: ByteArray) -> Unit
    ) {
        val zipIn = ZipInputStream(BufferedInputStream(inputStream))
        var totalBytesRead = 0L
        var entryCount = 0

        var entry: ZipEntry? = zipIn.nextEntry
        while (entry != null) {
            entryCount++
            if (entryCount > MAX_ENTRY_COUNT) {
                throw IllegalStateException("ZIP archive contains too many entries (exceeds $MAX_ENTRY_COUNT)")
            }

            val rawName = entry.name
            // Skip directory markers, hidden mac files, or path traversal attempts
            if (!entry.isDirectory &&
                !rawName.contains("__MACOSX") &&
                !rawName.contains(".DS_Store") &&
                !rawName.contains("..")
            ) {
                val cleanName = rawName.substringAfterLast('/')
                val buffer = ByteArray(8192)
                val out = ByteArrayOutputStream()
                var entryBytesRead = 0L
                var len: Int

                while (zipIn.read(buffer).also { len = it } > 0) {
                    entryBytesRead += len
                    totalBytesRead += len

                    if (entryBytesRead > MAX_ENTRY_BYTES) {
                        throw IllegalStateException("ZIP entry $cleanName exceeds size limit ($MAX_ENTRY_BYTES bytes)")
                    }
                    if (totalBytesRead > MAX_TOTAL_BYTES) {
                        throw IllegalStateException("ZIP uncompressed size exceeds limit ($MAX_TOTAL_BYTES bytes)")
                    }
                    out.write(buffer, 0, len)
                }

                zipIn.closeEntry()
                block(cleanName, out.toByteArray())
            } else {
                zipIn.closeEntry()
            }
            entry = zipIn.nextEntry
        }
    }
}
