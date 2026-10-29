package dev.sequel.app.domain.export

import dev.sequel.app.domain.backup.SequelBackupService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportCoordinator @Inject constructor(
    private val nativeExporter: SequelNativeBackupExporter,
    private val letterboxdExporter: LetterboxdDataExporter,
    private val traktExporter: TraktDataExporter,
    private val backupService: SequelBackupService
) {

    /**
     * Dispatches export generation to the appropriate destination-specific exporter.
     */
    suspend fun export(destination: ExportDestination): ExportPackage {
        return when (destination) {
            ExportDestination.SEQUEL_NATIVE -> nativeExporter.export()
            ExportDestination.LETTERBOXD_ZIP -> letterboxdExporter.export()
            ExportDestination.TRAKT_CSV -> traktExporter.export()
            ExportDestination.STANDARD_CSV_ZIP -> {
                val bytes = backupService.exportCsvZip()
                val timestamp = System.currentTimeMillis()
                ExportPackage(
                    destination = destination,
                    suggestedFileName = "sequel_tables_$timestamp.zip",
                    mimeType = destination.mimeType,
                    bytes = bytes
                )
            }
        }
    }
}
