package dev.sequel.app.domain.importer.checkpoint

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class CheckpointStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Serializable
data class ImportCheckpoint(
    val importId: String,
    val source: String,
    val fingerprint: String,
    val totalRecords: Int,
    val processedIndex: Int,
    val insertedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val failedCount: Int,
    val policy: String,
    val status: CheckpointStatus,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Singleton
class ImportCheckpointManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    var customDirectory: File? = null

    private val checkpointDir: File
        get() {
            val dir = customDirectory ?: File(context.filesDir, "import_checkpoints")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun saveCheckpoint(checkpoint: ImportCheckpoint) {
        val file = File(checkpointDir, "checkpoint_${checkpoint.fingerprint}.json")
        val content = json.encodeToString(checkpoint)
        file.writeText(content, Charsets.UTF_8)
    }

    fun getCheckpoint(fingerprint: String): ImportCheckpoint? {
        val file = File(checkpointDir, "checkpoint_${fingerprint}.json")
        if (!file.exists()) return null
        return try {
            json.decodeFromString<ImportCheckpoint>(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    fun clearCheckpoint(fingerprint: String) {
        val file = File(checkpointDir, "checkpoint_${fingerprint}.json")
        if (file.exists()) {
            file.delete()
        }
    }

    fun listActiveCheckpoints(): List<ImportCheckpoint> {
        val files = checkpointDir.listFiles { _, name -> name.startsWith("checkpoint_") && name.endsWith(".json") }
            ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                json.decodeFromString<ImportCheckpoint>(file.readText(Charsets.UTF_8))
            } catch (_: Exception) {
                null
            }
        }.filter { it.status == CheckpointStatus.IN_PROGRESS }
    }

    companion object {
        fun computeFingerprint(source: Any, records: List<Any>): String {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(source.toString().toByteArray(Charsets.UTF_8))
            for (rec in records) {
                md.update(rec.toString().toByteArray(Charsets.UTF_8))
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        fun computeFingerprint(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            return md.digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }
}
