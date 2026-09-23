package dev.sequel.app.domain.importer

import android.content.Context
import android.net.Uri

interface MediaDataParser {
    suspend fun parse(context: Context, uri: Uri): List<ImportedMediaItem>
}
