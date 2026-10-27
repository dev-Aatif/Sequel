package dev.sequel.app.domain.importer

import android.content.Context
import android.net.Uri
import java.io.InputStream

interface MediaDataParser {
    suspend fun parse(context: Context, uri: Uri): List<ImportedMediaItem>
    suspend fun parseStream(inputStream: InputStream): List<ImportedMediaItem> = emptyList()
}
