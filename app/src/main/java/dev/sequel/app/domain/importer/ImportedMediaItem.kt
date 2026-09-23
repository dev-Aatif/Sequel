package dev.sequel.app.domain.importer

data class ImportedMediaItem(
    val title: String,
    val year: Int?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val watchedAt: Long?,
    val mediaType: String
)
