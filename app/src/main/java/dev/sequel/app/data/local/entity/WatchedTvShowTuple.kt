package dev.sequel.app.data.local.entity

import androidx.room.Embedded

data class WatchedTvShowTuple(
    @Embedded val show: ShowEntity,
    val watchedCount: Int,
    val hasUnwatchedEpisodes: Boolean
)
