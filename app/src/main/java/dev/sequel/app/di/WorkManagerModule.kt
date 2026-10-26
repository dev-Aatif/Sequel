package dev.sequel.app.di

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {

    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager {
        return try {
            WorkManager.getInstance(context)
        } catch (_: IllegalStateException) {
            val config = Configuration.Builder().build()
            WorkManager.initialize(context, config)
            WorkManager.getInstance(context)
        }
    }
}
