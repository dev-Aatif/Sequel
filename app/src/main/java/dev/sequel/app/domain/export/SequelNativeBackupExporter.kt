package dev.sequel.app.domain.export

import dev.sequel.app.domain.backup.SequelBackupService
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SequelNativeBackupExporter @Inject constructor(
    private val backupService: SequelBackupService
) : DataExporter {

    override val destination: ExportDestination = ExportDestination.SEQUEL_NATIVE

    override suspend fun export(): ExportPackage {
        val json = backupService.exportJsonBackup()
        val timestamp = System.currentTimeMillis()
        val fileName = "sequel_backup_$timestamp.json"

        return ExportPackage(
            destination = destination,
            suggestedFileName = fileName,
            mimeType = destination.mimeType,
            bytes = json.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
