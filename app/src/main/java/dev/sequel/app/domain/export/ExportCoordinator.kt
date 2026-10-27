package dev.sequel.app.domain.export

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportCoordinator @Inject constructor(
    private val nativeExporter: SequelNativeBackupExporter,
    private val letterboxdExporter: LetterboxdDataExporter,
    private val traktExporter: TraktDataExporter
) {

    /**
     * Dispatches export generation to the appropriate destination-specific exporter.
     */
    suspend fun export(destination: ExportDestination): ExportPackage {
        return when (destination) {
            ExportDestination.SEQUEL_NATIVE -> nativeExporter.export()
            ExportDestination.LETTERBOXD_ZIP -> letterboxdExporter.export()
            ExportDestination.TRAKT_CSV -> traktExporter.export()
            ExportDestination.STANDARD_CSV_ZIP -> letterboxdExporter.export()
        }
    }
}
