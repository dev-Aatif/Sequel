# ADR-004: WorkManager with APPEND_OR_REPLACE for Background Synchronization

## Status
Accepted

## Date
2026-09-05

## Context
When users mark multiple episodes as watched in rapid succession (e.g., binge-watching a season or checking off 10 episodes after an import), the application must queue these changes and synchronize them to Supabase reliably.

Early prototypes using standard coroutines launched directly in ViewModels suffered from:
- Synchronization being cancelled when the user navigates away or closes the app.
- Lack of execution guarantees when the device is offline or entering Doze mode.

Initial WorkManager configurations using `ExistingWorkPolicy.REPLACE` suffered from a P1 defect: rapid consecutive taps repeatedly aborted the currently running worker, causing repeated network reconnections, redundant Supabase upserts, and battery drain.

## Decision
Use **Android Jetpack WorkManager** with **Hilt Worker Injection (`@HiltWorker`)** and `ExistingWorkPolicy.APPEND_OR_REPLACE` (or debounced scheduling) for all background sync operations:

1. **Guaranteed Execution**: WorkManager survives app process death, reboots, and network transitions.
2. **Network Constraints**: All sync workers require `NetworkType.CONNECTED` constraints to prevent wasted battery cycles when offline.
3. **Exponential Backoff**: Configured with `BackoffPolicy.EXPONENTIAL` (initial delay 30s) to gracefully handle transient network timeouts and Supabase rate limits.
4. **Append or Replace Policy**: `workManager.enqueueUniqueWork(..., ExistingWorkPolicy.APPEND_OR_REPLACE, request)` ensures inflight upload tasks are allowed to complete their batch upserts while subsequent mutations are appended to the queue rather than violently terminated.
5. **Debounced Sync for Interactive Inputs**: Review and rating sync incorporates an initial delay (e.g., 1000ms debounce) to allow users to finish typing or editing before scheduling the worker.

## Alternatives Considered

### Direct Coroutine in ViewModel (`viewModelScope.launch`)
- **Pros**: Simplest implementation.
- **Cons**: Destroyed as soon as the ViewModel clears or the activity finishes. Unacceptable for data synchronization where losing a write results in data inconsistency.
- **Rejected**: Fails persistence and reliability requirements.

### JobScheduler / AlarmManager
- **Pros**: Lower-level Android system APIs.
- **Cons**: High boilerplate, manual lifecycle management, difficult testing, and lack of dependency injection support.
- **Rejected**: WorkManager is Google's recommended abstraction over JobScheduler and AlarmManager.

## Consequences

### Positive
- Guaranteed eventual consistency between Room and Supabase.
- Smooth user experience even during bursts of rapid interactions.
- Full dependency injection via `@HiltWorker` allowing DAOs and Supabase clients to be injected cleanly.

### Negative / Trade-offs
- Slight delay between local write and remote reflection (typically 1–3 seconds when online).
- WorkManager database overhead on disk (minimal SQLite table managed by AndroidX).
