package dev.sequel.app.data.remote.supabase

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/**
 * Provides an in-memory/stubbed [SupabaseClient] for Android instrumentation tests.
 * Avoids any live network operations or real device persistent session writes.
 */
object FakeSupabaseClient {
    fun create(): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://mock-test.supabase.co",
            supabaseKey = "mock-anon-key-testing-only"
        ) {
            install(Auth) {
                alwaysAutoRefresh = false
                autoLoadFromStorage = false
                autoSaveToStorage = false
                codeVerifierCache = MemoryCodeVerifierCache()
            }
            install(Postgrest)
            install(Realtime)
        }
    }
}
