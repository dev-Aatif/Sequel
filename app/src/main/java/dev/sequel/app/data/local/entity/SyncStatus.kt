package dev.sequel.app.data.local.entity

/**
 * Tracks the synchronization state of user-generated records
 * (watched episodes, reviews) with Supabase.
 */
enum class SyncStatus {
    /** Successfully synced to Supabase. */
    SYNCED,

    /** Created or modified locally, awaiting sync. */
    PENDING,

    /** Sync attempted but failed. Will retry via WorkManager. */
    FAILED,

    /** Marked for deletion locally, awaiting sync to Supabase. */
    DELETED
}
