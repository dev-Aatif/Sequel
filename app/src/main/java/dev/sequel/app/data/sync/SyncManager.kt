package dev.sequel.app.data.sync

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
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

        workManager.enqueueUniqueWork(
            SyncWatchedEpisodesWorker.WORK_NAME + "_now",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    /** Trigger an immediate sync of reviews. */
    fun syncReviewsNow(mediaId: Int? = null) {
        val request = OneTimeWorkRequestBuilder<SyncReviewsWorker>()
            .setConstraints(networkConstraints)
            .setInitialDelay(1000, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        val uniqueWorkName = if (mediaId != null) "sync_review_${mediaId}" else SyncReviewsWorker.WORK_NAME + "_now"
        workManager.enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private var lastSyncAllTime = 0L

    fun syncAllNow() {
        val now = System.currentTimeMillis()
        // Prevent multiple ViewModels from hammering the endpoints at the exact same time
        if (now - lastSyncAllTime < 5000) return
        lastSyncAllTime = now

        syncPullNow()
        syncWatchedEpisodesNow()
        syncReviewsNow()
        syncWatchlistNow()
    }

    /** Trigger an immediate pull from Supabase to local DB. */
    fun syncPullNow() {
        val request = OneTimeWorkRequestBuilder<SyncPullWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            SyncPullWorker.WORK_NAME + "_now",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    /** Trigger an immediate sync of watchlist. */
    fun syncWatchlistNow() {
        val request = OneTimeWorkRequestBuilder<SyncWatchlistWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        workManager.enqueueUniqueWork(
            SyncWatchlistWorker.WORK_NAME + "_now",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
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

        // Watchlist — every 30 minutes
        val watchlistWork = PeriodicWorkRequestBuilder<SyncWatchlistWorker>(
            repeatInterval = 30, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWatchlistWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            watchlistWork
        )

        // Pull Sync — every 30 minutes
        val pullWork = PeriodicWorkRequestBuilder<SyncPullWorker>(
            repeatInterval = 30, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncPullWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            pullWork
        )
    }

    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(SyncWatchedEpisodesWorker.WORK_NAME)
        workManager.cancelUniqueWork(SyncReviewsWorker.WORK_NAME)
        workManager.cancelUniqueWork(SyncWatchlistWorker.WORK_NAME)
        workManager.cancelUniqueWork(SyncPullWorker.WORK_NAME)
    }

    /** Cancel all sync work (periodic + one-time). */
    fun cancelAllSync() {
        workManager.cancelAllWork()
    }
}
