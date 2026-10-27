package dev.sequel.app.domain.export

enum class ExportDestination(val displayName: String, val extension: String, val mimeType: String) {
    SEQUEL_NATIVE("Sequel Complete Backup (JSON)", "json", "application/json"),
    LETTERBOXD_ZIP("Letterboxd Compatible (ZIP/CSV)", "zip", "application/zip"),
    TRAKT_CSV("Trakt Compatible (CSV)", "csv", "text/csv"),
    STANDARD_CSV_ZIP("Spreadsheet Compatible (ZIP/CSV)", "zip", "application/zip")
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
