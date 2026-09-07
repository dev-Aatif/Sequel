package dev.sequel.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trending_shows")
data class TrendingShowEntity(
    @PrimaryKey val showId: Int,
    val mediaType: String,
    val page: Int,
    val position: Int
)
