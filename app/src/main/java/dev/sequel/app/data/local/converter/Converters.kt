package dev.sequel.app.data.local.converter

import androidx.room.TypeConverter
import dev.sequel.app.data.local.entity.SyncStatus

/**
 * Room type converters for non-primitive types used in entities.
 */
class Converters {

    // ── SyncStatus ↔ String ──────────────────────────────────────

    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}
