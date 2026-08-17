package dev.sequel.app.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates background sync between Room and Supabase via WorkManager.
 *
 * Supports two modes:
 * - **Immediate**: One-time sync triggered after a write (e.g. marking an episode watched).
 * - **Periodic**: Recurring sync every 30 minutes to catch any missed records.
 */
@Singleton
class SyncManager @Inject constructor(
    private val workManager: WorkManager
) {

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    // ── Immediate (one-time) sync ─────────────────────────────────

    /** Trigger an immediate sync of watched episodes. */
    fun syncWatchedEpisodesNow() {
        val request = OneTimeWorkRequestBuilder<SyncWatchedEpisodesWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        workManager.enqueue(request)
    }

    /** Trigger an immediate sync of reviews. */
    fun syncReviewsNow() {
        val request = OneTimeWorkRequestBuilder<SyncReviewsWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        workManager.enqueue(request)
    }

    /** Trigger immediate sync of all data types. */
    fun syncAllNow() {
        syncWatchedEpisodesNow()
        syncReviewsNow()
    }

    // ── Periodic sync ─────────────────────────────────────────────

    /**
     * Schedule periodic background sync.
     * Call this once at app startup (e.g. from Application or MainActivity).
     * Uses KEEP policy — won't replace existing periodic work.
     */
    fun schedulePeriodicSync() {
        // Watched episodes — every 30 minutes
        val watchedWork = PeriodicWorkRequestBuilder<SyncWatchedEpisodesWorker>(
            repeatInterval = 30, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWatchedEpisodesWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            watchedWork
        )

        // Reviews — every 30 minutes
        val reviewsWork = PeriodicWorkRequestBuilder<SyncReviewsWorker>(
            repeatInterval = 30, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncReviewsWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            reviewsWork
        )
    }

    /** Cancel all periodic sync work. Useful on sign-out. */
    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(SyncWatchedEpisodesWorker.WORK_NAME)
        workManager.cancelUniqueWork(SyncReviewsWorker.WORK_NAME)
    }

    /** Cancel all sync work (periodic + one-time). */
    fun cancelAllSync() {
        workManager.cancelAllWork()
    }
}
