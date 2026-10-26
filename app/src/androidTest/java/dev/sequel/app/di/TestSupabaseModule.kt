package dev.sequel.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import dev.sequel.app.data.remote.supabase.FakeSupabaseClient
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [SupabaseModule::class]
)
object TestSupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        return FakeSupabaseClient.create()
    }
}
