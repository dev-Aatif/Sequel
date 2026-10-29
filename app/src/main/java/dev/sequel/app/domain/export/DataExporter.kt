package dev.sequel.app.domain.export

/**
 * Supported destination export formats with metadata and format-specific loss warnings.
 */
enum class ExportDestination(
    val displayName: String,
    val extension: String,
    val mimeType: String,
    val description: String = "",
    val lossWarning: String? = null
) {
    SEQUEL_NATIVE(
        displayName = "Sequel Complete Backup (JSON)",
        extension = "json",
        mimeType = "application/json",
        description = "Lossless full-fidelity backup of all shows, movies, episodes, ratings, reviews, watchlist, and Mount Rushmore.",
        lossWarning = null
    ),
    LETTERBOXD_ZIP(
        displayName = "Letterboxd Compatible (ZIP/CSV)",
        extension = "zip",
        mimeType = "application/zip",
        description = "Standard Letterboxd CSV files (watched, ratings, reviews, watchlist).",
        lossWarning = "Letterboxd only supports movies. TV shows, seasons, episodes, and Mount Rushmore favorites will be excluded. 10-point ratings are mapped to Letterboxd 5-star scale."
    ),
    TRAKT_CSV(
        displayName = "Trakt Compatible (CSV)",
        extension = "csv",
        mimeType = "text/csv",
        description = "Standard Trakt CSV watch history file.",
        lossWarning = "Trakt CSV history format only preserves watch timestamps. Written reviews, 10-point ratings, watchlist, and Mount Rushmore favorites will be excluded."
    ),
    STANDARD_CSV_ZIP(
        displayName = "Spreadsheet Compatible (ZIP/CSV)",
        extension = "zip",
        mimeType = "application/zip",
        description = "Multi-table CSV archive suitable for Excel, Google Sheets, and custom data processing.",
        lossWarning = null
    )
}

data class ExportPackage(
    val destination: ExportDestination,
    val suggestedFileName: String,
    val mimeType: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ExportPackage
        return destination == other.destination && suggestedFileName == other.suggestedFileName && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = destination.hashCode()
        result = 31 * result + suggestedFileName.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

interface DataExporter {
    val destination: ExportDestination
    suspend fun export(): ExportPackage
}
