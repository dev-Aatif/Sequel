package dev.sequel.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_keys", primaryKeys = ["showId", "mediaType"])
data class RemoteKeys(
    val showId: Int,
    val mediaType: String,
    val prevKey: Int?,
    val nextKey: Int?
)
