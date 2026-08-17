package dev.sequel.app.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.sequel.app.data.local.SequelDatabase
import dev.sequel.app.data.local.dao.EpisodeDao
import dev.sequel.app.data.local.dao.ReviewDao
import dev.sequel.app.data.local.dao.SeasonDao
import dev.sequel.app.data.local.dao.ShowDao
import dev.sequel.app.data.local.dao.WatchedEpisodeDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): SequelDatabase {
        return Room.databaseBuilder(
            context,
            SequelDatabase::class.java,
            SequelDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideShowDao(database: SequelDatabase): ShowDao = database.showDao()

    @Provides
    fun provideSeasonDao(database: SequelDatabase): SeasonDao = database.seasonDao()

    @Provides
    fun provideEpisodeDao(database: SequelDatabase): EpisodeDao = database.episodeDao()

    @Provides
    fun provideWatchedEpisodeDao(database: SequelDatabase): WatchedEpisodeDao = database.watchedEpisodeDao()

    @Provides
    fun provideReviewDao(database: SequelDatabase): ReviewDao = database.reviewDao()
}
