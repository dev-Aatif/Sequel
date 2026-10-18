# ADR-001: Offline-First Architecture with Room as Single Source of Truth

## Status
Accepted

## Date
2026-08-10

## Context
Sequel is an Android media tracker designed for users tracking thousands of shows, movies, and episodes across daily commutes, flights, and variable connectivity environments. Relying directly on remote network requests for screen rendering would cause:
- High latency and loading spinners on screen navigation.
- Inability to mark episodes watched or read notes when offline.
- Excessive TMDB API usage and battery consumption.
- Vulnerability to remote cloud outages.

We required an architectural pattern where the application guarantees 100% read and write availability offline, while synchronizing with the cloud opportunistically.

## Decision
Adopt an **Offline-First Architecture** with Android **Room Database (SQLite)** serving as the application's Single Source of Truth (SSOT):

1. **Unidirectional Read Flow**: UI components observe Room queries via `Flow` or `PagingData`. ViewModels never wait for remote network responses before populating UI state.
2. **Optimistic Local Writes**: User actions (e.g., toggling an episode watched, adding to watchlist, writing a review) write to Room immediately with a `SyncStatus.PENDING_*` state.
3. **Background Asynchronous Sync**: Once committed locally, a background worker is triggered to push changes to the Supabase cloud backend.
4. **Cache Invalidation & TTL**: Media metadata pulled from TMDB is cached indefinitely in Room and updated lazily or on user pull-to-refresh.

## Alternatives Considered

### Remote-First with HTTP Caching (OkHttp Cache)
- **Pros**: Minimal local database code; relies on standard HTTP `Cache-Control` headers.
- **Cons**: Write operations cannot function offline without a custom outbox queue. Cache eviction is nondeterministic and controlled by LRU size rather than user entity relationships.
- **Rejected**: Fails offline write requirements and complex relational queries.

### Realm / ObjectBox
- **Pros**: Fast object database; built-in change listeners.
- **Cons**: Proprietary native binaries, heavier APK size, non-standard query semantics, and friction with Jetpack Paging 3 and standard SQL migrations.
- **Rejected**: Room is first-class in modern Android, fully supported by Google, and uses rock-solid SQLite with explicit compile-time SQL verification via KSP.

## Consequences

### Positive
- Instant UI responsiveness (zero latency on cached reads).
- Complete offline capability for tracking, logging, and history browsing.
- Strict compile-time SQL verification and seamless integration with Jetpack Paging 3.

### Negative / Trade-offs
- Requires maintaining explicit local database schemas and versioned migrations.
- Requires conflict resolution logic between local Room records and remote Supabase state.
