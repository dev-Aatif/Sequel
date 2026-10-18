# Architecture Decision Records (ADRs)

This directory documents the significant architectural and design decisions made in **Sequel**.

## Format

Each record uses the ADR template capturing:
- **Context**: Problem statement, requirements, constraints, and forces.
- **Decision**: The selected architecture, technology, or pattern.
- **Alternatives Considered**: Other candidate solutions and why they were rejected.
- **Consequences**: Positive, negative, and operational impacts of the decision.

## Record Index

| ADR | Title | Status | Date |
| --- | ----- | ------ | ---- |
| [ADR-001](ADR-001-offline-first-room-truth.md) | Offline-First Architecture with Room as Single Source of Truth | Accepted | 2026-08-10 |
| [ADR-002](ADR-002-supabase-auth-and-delta-sync.md) | Supabase as Cloud Backend for Auth and Delta Sync | Accepted | 2026-08-18 |
| [ADR-003](ADR-003-tmdb-metadata-integration.md) | TMDB API v3 as Authoritative Media Metadata Provider | Accepted | 2026-08-25 |
| [ADR-004](ADR-004-workmanager-background-sync.md) | WorkManager with APPEND_OR_REPLACE for Background Synchronization | Accepted | 2026-09-05 |
| [ADR-005](ADR-005-separation-of-movies-and-tv-shows.md) | Schema Separation of Movies from TV Episodes into Dedicated Tables | Accepted | 2026-10-02 |
