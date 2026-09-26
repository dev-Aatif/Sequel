package dev.sequel.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Application entry point for Hilt dependency injection
 * and WorkManager initialization.
 */
@HiltAndroidApp
class SequelApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncManager: dev.sequel.app.data.sync.SyncManager

    @Inject
    lateinit var authService: dev.sequel.app.data.remote.supabase.SupabaseAuthService

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        if (dev.sequel.app.BuildConfig.TMDB_API_KEY == "YOUR_TMDB_API_KEY" || dev.sequel.app.BuildConfig.TMDB_API_KEY.isBlank()) {
            throw IllegalStateException("TMDB API Key is missing. Please set TMDB_API_KEY in local.properties.")
        }
        
        val airDateWorkRequest = androidx.work.PeriodicWorkRequestBuilder<dev.sequel.app.data.sync.AirDateWorker>(
            24, java.util.concurrent.TimeUnit.HOURS
        ).build()
        
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            dev.sequel.app.data.sync.AirDateWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            airDateWorkRequest
        )

        // Ensure background sync starts if logged in, cancels if logged out
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            authService.authStateFlow.collect { isLoggedIn ->
                if (isLoggedIn) {
                    syncManager.schedulePeriodicSync()
                } else {
                    syncManager.cancelPeriodicSync()
                }
            }
        }
    }
}
