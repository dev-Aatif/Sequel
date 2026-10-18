# ADR-002: Supabase as Cloud Backend for Auth and Delta Sync

## Status
Accepted

## Date
2026-08-18

## Context
Sequel requires multi-device synchronization so users can switch between phones and tablets without losing progress. Key requirements:
- Reliable User Authentication (email/password, session restoration, password reset).
- Relational database schema with Row-Level Security (RLS) to isolate user libraries securely.
- Generous free tier suitable for indie Android apps without recurring maintenance overhead.
- Official or first-class Kotlin Multiplatform / Android SDK support.

## Decision
Adopt **Supabase** (PostgreSQL + PostgREST + GoTrue Auth + Realtime) using the official `supabase-kt` Kotlin client SDK:

1. **Authentication**: Supabase Auth handles user registration, JWT generation, secure session storage, and email confirmation/recovery deep links (`sequel://login-callback`).
2. **PostgREST Storage**: Normalized tables in PostgreSQL (`user_watchlist`, `watched_movies`, `watched_episodes`, `reviews`) mirroring client entity definitions.
3. **Row-Level Security (RLS)**: Enforce strict data isolation using PostgreSQL policies (`auth.uid() = user_id`) on all CRUD operations.
4. **Delta Sync via Timestamps**: Cloud tables record `updated_at` / `watched_at` timestamps to allow the client to request only modified records since the last sync cursor.

## Alternatives Considered

### Firebase (Authentication + Cloud Firestore)
- **Pros**: Established Android SDK, offline caching out of the box.
- **Cons**: NoSQL document model makes complex multi-table joins, relational integrity, and episode/season aggregations clumsy. Expensive and unpredictable read/write billing spikes.
- **Rejected**: Relational tracking data (shows -> seasons -> episodes -> watches) is inherently relational. PostgreSQL is a vastly superior fit.

### Custom Backend (Node.js / Go + PostgreSQL)
- **Pros**: 100% bespoke control over API endpoints and logic.
- **Cons**: Significant infrastructure overhead, server maintenance, Docker deployments, hosting costs, and security compliance burdens.
- **Rejected**: Distracts from client application quality. Supabase provides the exact PostgreSQL primitives without server management overhead.

## Consequences

### Positive
- Standard PostgreSQL primitives: foreign keys, cascading deletes, partial unique indexes, and RLS policies.
- Low operational cost within Supabase free-tier limits.
- Kotlin-first SDK (`io.github.jan-tennert.supabase:postgrest-kt`) with seamless `kotlinx.serialization` support.

### Negative / Trade-offs
- Deep linking required for password recovery must be handled via Android intent filters and activity lifecycle callbacks.
- Batch operations must be chunked (e.g., 1,000 items per range query) to avoid HTTP payload limits.
