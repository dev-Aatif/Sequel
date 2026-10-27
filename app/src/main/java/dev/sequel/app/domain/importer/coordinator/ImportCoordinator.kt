package dev.sequel.app.domain.importer.coordinator

import dev.sequel.app.domain.importer.adapter.*
import dev.sequel.app.domain.importer.checkpoint.ImportCheckpointManager
import dev.sequel.app.domain.importer.conflict.ConflictAnalyzer
import dev.sequel.app.domain.importer.core.*
import dev.sequel.app.domain.importer.detector.SourceDetector
import dev.sequel.app.domain.importer.normalizer.ImportNormalizer
import dev.sequel.app.domain.importer.resolver.IdentityResolver
import dev.sequel.app.domain.importer.resolver.ResolvedMediaRecord
import java.io.BufferedInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportCoordinator @Inject constructor(
    private val sourceDetector: SourceDetector,
    private val normalizer: ImportNormalizer,
    private val identityResolver: IdentityResolver,
    private val conflictAnalyzer: ConflictAnalyzer,
    private val importApplier: ImportApplier,
    private val tvTimeAdapter: TvTimeSourceAdapter,
    private val traktAdapter: TraktSourceAdapter,
    private val letterboxdAdapter: LetterboxdSourceAdapter,
    private val sequelBackupAdapter: SequelBackupSourceAdapter
) {

    /**
     * Inspects input data, executes source adapter parsing, normalizes fields,
     * resolves identities, and analyzes conflicts to produce an interactive preview.
     */
    suspend fun generatePreview(
        inputStream: InputStream,
        filenameHint: String? = null,
        sourceOverride: ImportSource? = null
    ): ImportPreview {
        val buffered = if (inputStream.markSupported()) inputStream else BufferedInputStream(inputStream)
        val source = sourceOverride ?: run {
            val detection = sourceDetector.detect(buffered, filenameHint)
            detection.source
        }

        val adapter = getAdapterForSource(source)
        val rawRecords = adapter.parse(buffered, filenameHint)
        val normResult = normalizer.normalizeWithStats(rawRecords)

        val resolved = normResult.records.map { identityResolver.resolve(it) }
        return conflictAnalyzer.analyze(source, resolved)
    }

    /**
     * Executes the end-to-end import pipeline with atomic chunking, conflict resolution,
     * idempotent deduplication, and crash recovery.
     */
    suspend fun executeImport(
        inputStream: InputStream,
        filenameHint: String? = null,
        sourceOverride: ImportSource? = null,
        policy: ConflictResolutionPolicy = ConflictResolutionPolicy.KEEP_EXISTING,
        fingerprint: String? = null,
        resumeFromCheckpoint: Boolean = true,
        batchSize: Int = 200,
        onProgress: (current: Int, total: Int, currentTitle: String) -> Unit = { _, _, _ -> }
    ): ImportReport {
        val buffered = if (inputStream.markSupported()) inputStream else BufferedInputStream(inputStream)
        val source = sourceOverride ?: run {
            val detection = sourceDetector.detect(buffered, filenameHint)
            detection.source
        }

        val adapter = getAdapterForSource(source)
        val rawRecords = adapter.parse(buffered, filenameHint)
        val normResult = normalizer.normalizeWithStats(rawRecords)

        val resolved = normResult.records.map { identityResolver.resolve(it) }
        val effectiveFingerprint = fingerprint ?: ImportCheckpointManager.computeFingerprint(source.name, normResult.records)

        return importApplier.apply(
            source = source,
            resolvedRecords = resolved,
            policy = policy,
            duplicateInSourceCount = normResult.duplicateInSourceCount,
            totalParsedCount = rawRecords.size,
            fingerprint = effectiveFingerprint,
            resumeFromCheckpoint = resumeFromCheckpoint,
            batchSize = batchSize,
            onProgress = onProgress
        )
    }

    private fun getAdapterForSource(source: ImportSource): SourceAdapter {
        return when (source) {
            ImportSource.TV_TIME -> tvTimeAdapter
            ImportSource.TRAKT -> traktAdapter
            ImportSource.LETTERBOXD -> letterboxdAdapter
            ImportSource.SEQUEL_BACKUP -> sequelBackupAdapter
            else -> tvTimeAdapter // fallback generic parser
        }
    }
}
