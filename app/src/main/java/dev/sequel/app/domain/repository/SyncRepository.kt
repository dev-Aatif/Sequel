package dev.sequel.app.domain.repository

interface SyncRepository {
    suspend fun syncWatchlistNow()
    suspend fun syncWatchedEpisodesNow()
}
