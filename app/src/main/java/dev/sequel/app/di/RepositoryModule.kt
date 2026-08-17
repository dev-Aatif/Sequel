package dev.sequel.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.sequel.app.data.repository.AuthRepositoryImpl
import dev.sequel.app.data.repository.ReviewRepositoryImpl
import dev.sequel.app.data.repository.SeasonRepositoryImpl
import dev.sequel.app.data.repository.ShowRepositoryImpl
import dev.sequel.app.data.repository.WatchedRepositoryImpl
import dev.sequel.app.domain.repository.AuthRepository
import dev.sequel.app.domain.repository.ReviewRepository
import dev.sequel.app.domain.repository.SeasonRepository
import dev.sequel.app.domain.repository.ShowRepository
import dev.sequel.app.domain.repository.WatchedRepository
import javax.inject.Singleton

/**
 * Binds repository interfaces to their implementations.
 * This is the key module for Clean Architecture — the domain layer
 * depends on interfaces, and Hilt provides the concrete implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindShowRepository(impl: ShowRepositoryImpl): ShowRepository

    @Binds
    @Singleton
    abstract fun bindSeasonRepository(impl: SeasonRepositoryImpl): SeasonRepository

    @Binds
    @Singleton
    abstract fun bindWatchedRepository(impl: WatchedRepositoryImpl): WatchedRepository

    @Binds
    @Singleton
    abstract fun bindReviewRepository(impl: ReviewRepositoryImpl): ReviewRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
