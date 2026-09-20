package dev.sequel.app.data.local.entity

import androidx.room.Embedded

data class UpNextShowTuple(
    @Embedded val show: ShowEntity,
    @Embedded(prefix = "ep_") val nextEpisode: EpisodeEntity?
)
