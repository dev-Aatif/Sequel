# ADR-005: Schema Separation of Movies from TV Episodes into Dedicated Tables

## Status
Accepted

## Date
2026-10-02

## Context
In early schema versions (v1 through v15), watched movies were stored inside the `watched_episodes` table using synthetic values (`season_number = 0`, `episode_number = 0`, `media_type = 'MOVIE'`).

While this avoided creating a second table initially, it introduced severe design flaws as the application matured:
- **Nullability and Leaky Semantics**: TV episodes require `show_id`, `season_number`, `episode_number`, `is_skipped`, and air date tracking. Movies require `tmdb_movie_id`, `runtime`, `title`, and `poster_path`.
- **Query Inefficiencies**: Fetching watched TV progress required complex `WHERE media_type != 'MOVIE'` filters, bloating index sizes and confusing aggregation logic (e.g., calculating completed seasons vs. total watched runtime).
- **Unique Constraint Clashes**: Unique indices on `(show_id, season_number, episode_number)` caused unintended collision edge cases when movie IDs overlapped with TV show IDs in TMDB's independent numbering spaces.
- **Ghost Reviews for Movies**: Movies have no season/episode numbers (`NULL`). Standard SQL UNIQUE constraints do not treat `NULL = NULL`, permitting duplicate review rows for the same movie.

## Decision
Execute Room Database **Migration 15→16** and **Migration 16→17**:

1. **Dedicated Table**: Create `watched_movies` with a dedicated primary key `tmdb_movie_id: Int`, `title: String`, `poster_path: String?`, `watched_at: Long`, `sync_status: String`, and `supabase_id: String?`.
2. **Data Migration**: Automatically migrate existing movie rows from `watched_episodes` into `watched_movies` via SQL `INSERT ... SELECT` joining with `shows`, then `DELETE FROM watched_episodes WHERE media_type = 'MOVIE'`.
3. **Partial Unique Index for Movie Reviews**: Add a SQLite partial unique index:
   ```sql
   CREATE UNIQUE INDEX IF NOT EXISTS idx_reviews_movie_unique 
   ON reviews (media_id, media_type) 
   WHERE season_num IS NULL AND episode_num IS NULL;
   ```
   This strictly enforces one review per movie while allowing episode-level reviews for TV shows.

## Alternatives Considered

### Keep Unified Table with Single Table Inheritance
- **Pros**: Fewer Room DAOs and entities.
- **Cons**: Columns are permanently polluted with nullable fields (`season_number`, `episode_number`, `is_skipped`), queries are slower, and cross-type foreign key constraints are impossible to enforce cleanly.
- **Rejected**: Schema purity and query performance heavily favored separation.

## Consequences

### Positive
- Strict type safety and domain clarity: `WatchedMovieEntity` and `WatchedEpisodeEntity` now model their respective domains accurately.
- Prevention of duplicate ghost movie review records.
- Elimination of ID collision risk between TMDB movie IDs and TV show IDs.
- Significantly simpler and faster DAO queries for both movies and TV shows.

### Negative / Trade-offs
- Required an irreversible SQLite table migration (Room version 16 and 17).
- Required adding dedicated DAOs (`WatchedMovieDao`) and separate sync workers (`SyncWatchedWorker` handles both tables in segregated batches).
