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

    // ── MediaType ↔ String ───────────────────────────────────────

    @TypeConverter
    fun fromMediaType(type: dev.sequel.app.data.local.entity.MediaType): String = type.name

    @TypeConverter
    fun toMediaType(value: String): dev.sequel.app.data.local.entity.MediaType = dev.sequel.app.data.local.entity.MediaType.valueOf(value)
}
