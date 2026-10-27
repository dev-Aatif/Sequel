package dev.sequel.app.domain.importer.core

enum class ImportSource(val displayName: String) {
    TV_TIME("TV Time"),
    TRAKT("Trakt"),
    LETTERBOXD("Letterboxd"),
    SEQUEL_BACKUP("Sequel Backup"),
    GENERIC_CSV("Generic CSV"),
    UNKNOWN("Unknown")
}

enum class ArchiveType {
    ZIP,
    CSV,
    JSON,
    UNKNOWN
}

data class SourceMetadata(
    val fileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val isZip: Boolean = false,
    val headerColumns: List<String> = emptyList(),
    val snippet: String? = null
)
