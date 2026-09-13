package dev.sequel.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point for Hilt dependency injection
 * and WorkManager initialization.
 */
@HiltAndroidApp
class SequelApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

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
    }
}
