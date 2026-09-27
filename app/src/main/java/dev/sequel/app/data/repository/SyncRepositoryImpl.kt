package dev.sequel.app.data.repository

import dev.sequel.app.data.sync.SyncManager
import dev.sequel.app.domain.repository.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val syncManager: SyncManager
) : SyncRepository {
    override suspend fun syncWatchlistNow() {
        syncManager.syncWatchlistNow()
    }

    override suspend fun syncWatchedEpisodesNow() {
        syncManager.syncWatchedEpisodesNow()
    }
}
