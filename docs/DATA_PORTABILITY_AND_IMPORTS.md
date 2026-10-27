# Sequel — Data Portability, Import & Export Subsystem

## 1. Overview

Users transitioning from other tracking services (such as **TV Time**, **Trakt**, or **Letterboxd**) can seamlessly migrate their complete libraries into Sequel without losing watch dates, star ratings, written reviews, or watchlist entries. Sequel also provides a full-fidelity, versioned backup and restore system.

The data portability subsystem consists of:
- **`MediaDataParser` Interface**: Contract for streaming CSV/JSON/ZIP export files into normalized `ImportedMediaItem` objects.
- **`TvTimeCsvParser`**: Multi-format parser for TV Time account exports, supporting both official GDPR ZIP archives (`tracking-prod-records.csv`, `seen_episode.csv`, `followed_tv_show.csv`) and community/legacy CSV extracts.
- **`TraktExportParser`**: Dedicated parser for Trakt history CSV exports and official raw JSON backup packages (`watched_movies.json`, `watched_shows.json`, `ratings.json`, `watchlist.json`).
- **`LetterboxdExportParser`**: Dedicated parser for Letterboxd ZIP archives and individual CSV files (`watched.csv`, `diary.csv`, `ratings.csv`, `reviews.csv`, `watchlist.csv`), with automatic star rating normalization (0.5–5.0 to 1.0–10.0 scale) and spoiler tagging.
- **`SequelBackupService` & `SequelBackupParser`**: Complete, schema-versioned JSON backup and restore service that serializes/restores shows, watched movies, watched episodes, watchlist entries, ratings, reviews, and Mount Rushmore favorites within atomic Room transactions.
- **`ZipArchiveExtractor`**: Safe archive streaming utility equipped with Zip Slip path traversal defenses and decompression limit controls.
- **`MediaImportWorker`**: Jetpack WorkManager background process handling title matching, local Room cache-first resolution, TMDB API hydration with exponential backoff, batch SQLite transactions, and cloud sync dispatch.

```mermaid
flowchart TD
    UserFile[User Selects Export ZIP / CSV / JSON]
    FilePicker[Android System File Picker SAF]
    ZipExtract[ZipArchiveExtractor (Security Sandbox)]
    Parsers[TvTimeCsvParser / TraktExportParser / LetterboxdExportParser / SequelBackupParser]
    Normalized[Unified ImportedMediaItem Stream]
    ImportWorker[MediaImportWorker]
    LocalCache{Local Room Cache?}
    TMDB_Search[TMDB Multi-Search / Hydration]
    RoomStore[(Room SQLite Database)]
    SyncQ[SyncManager Queue]

    UserFile --> FilePicker
    FilePicker --> ZipExtract
    ZipExtract --> Parsers
    Parsers --> Normalized
    Normalized --> ImportWorker
    ImportWorker --> LocalCache
    LocalCache -->|Miss| TMDB_Search
    LocalCache -->|Hit| RoomStore
    TMDB_Search --> RoomStore
    RoomStore --> SyncQ
```

---

## 2. Supported Export Formats & Grammar

### 2.1 TV Time (`TvTimeCsvParser`)
Supports both flat CSV files and official GDPR `.zip` archives.

#### Recognized Formats & Files:
1. **GDPR `tracking-prod-records.csv` / `tracking-prod-records-v2.csv`**:
   - `entity_type`: `movie`, `show`, `series`, `episode`
   - `movie_name` / `tv_show_name` / `alpha_range_key` / `name` / `title`
   - `episode_season_number` / `season_number` / `season`
   - `episode_number` / `episode`
   - `type` / `action`: `watch` (watched), `follow` / `towatch` (watchlist)
   - `created_at` / `updated_at` / `watched_at` (ISO 8601 or `yyyy-MM-dd HH:mm:ss`)
   - `rating`: Normalized to Sequel's 0.5–10.0 scale
2. **GDPR `seen_episode.csv` & `followed_tv_show.csv`**:
   - `seen_episode.csv`: `tv_show_name`, `season_number`, `episode_number`, `updated_at`
   - `followed_tv_show.csv`: `tv_show_name`, `created_at` (mapped to watchlist)
3. **Legacy / Community Flat CSV**:
   - Recognized headers: `tv_show_name`, `episode_season_number`, `episode_number`, `created_at`. If season/episode are missing, row is classified as `movie`; otherwise `tv`.

---

### 2.2 Trakt (`TraktExportParser`)
Supports both standard CSV and raw JSON exports (single files or `.zip` archives).

#### Recognized Formats:
1. **Trakt CSV**:
   - Headers: `Title`, `Year`, `Season`, `Episode`, `Type` (`movie`, `show`, `episode`), `WatchedAt` / `Date`, `Rating`, `TMDB_ID`.
2. **Trakt Raw JSON**:
   - `watched_movies.json`: Array of `{ "movie": { "title", "year", "ids": { "tmdb" } }, "last_watched_at" }`
   - `watched_shows.json`: Array of `{ "show": { "title", "year", "ids": { "tmdb" } }, "seasons": [ { "number", "episodes": [ { "number", "last_watched_at" } ] } ] }`
   - `ratings.json`: Array of `{ "rating", "rated_at", "movie" / "show", "ids" }`
   - `watchlist.json`: Array of `{ "listed_at", "movie" / "show", "ids" }`

---

### 2.3 Letterboxd (`LetterboxdExportParser`)
Supports full Letterboxd `.zip` account exports or individual CSV files.

#### Recognized Files & Columns:
- **`watched.csv`**: `Date,Name,Year,Letterboxd URI` → Movie watch record.
- **`diary.csv`**: `Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date` → Movie watch record with watch date, rewatch indicator, and rating.
- **`ratings.csv`**: `Date,Name,Year,Letterboxd URI,Rating` → Movie rating record.
- **`reviews.csv`**: `Date,Name,Year,Letterboxd URI,Rating,Review,Spoiler` → Movie review record with text, rating, and spoiler flag.
- **`watchlist.csv`**: `Date,Name,Year,Letterboxd URI` → Watchlist bookmark.

#### Rating Conversion:
Letterboxd uses a 0.5 to 5.0 star rating. Sequel normalizes this using:
$$\text{Sequel Rating} = (\text{Letterboxd Rating} \times 2.0).coerceIn(0.5, 10.0)$$

---

### 2.4 Sequel Backup & Restore (`SequelBackupService`)
Sequel provides its own lossless, versioned JSON backup format (`schemaVersion: 1`).

#### Contents of `sequel_backup_*.json`:
- `schemaVersion`: Integer format version.
- `exportedAt`: Epoch millisecond timestamp.
- `shows`: Cached TMDB metadata.
- `watchedMovies`: Dedicated `watched_movies` rows with TMDB ID, title, poster path, watched timestamp.
- `watchedEpisodes`: TV episode records with show ID, TMDB episode ID, season, episode, watched timestamp, skipped status.
- `watchlist`: Bookmarked movies and shows with added timestamp.
- `reviews`: Red Pill ratings (0.5–10.0 scale) and written reviews, with spoiler and rating-only flags.
- `mountRushmore`: User top-4 pinned cinephile favorites.

Sequel also provides an **Export CSV Archive** option (`exportCsvZip()`) generating a zip with:
- `sequel_watched_movies.csv`
- `sequel_watched_episodes.csv`
- `sequel_watchlist.csv`
- `sequel_reviews_and_ratings.csv`

---

## 3. Background Processing & Data Integrity (`MediaImportWorker`)

### 3.1 Pipeline Stages & Integrity Guarantees:
1. **Streaming Parse & Security Verification**: Files are opened via `ContentResolver.openInputStream(uri)`. ZIP files pass through `ZipArchiveExtractor`, which verifies byte limits and protects against Zip Slip attacks.
2. **Local Cache First**: Before making outbound network requests to TMDB, the worker queries `showDao.findShowByTitle(title, mediaType)`. If the media is already present locally, remote lookups are skipped, significantly speeding up large imports and avoiding rate limits.
3. **Movie vs. TV Segregation**:
   - Movies are written directly to `watched_movies` (`WatchedMovieDao.upsertWatchedMovie`) to maintain schema segregation.
   - TV series episodes are fetched and written to `watched_episodes` (`WatchedEpisodeDao.upsertWatchedEpisode`) using validated TMDB episode IDs.
4. **Watchlist & Review Routing**:
   - Items tagged as watchlist are inserted to `watchlist` (`WatchlistDao.insertToWatchlist`).
   - Ratings and reviews are upserted into `reviews` (`ReviewDao.upsertRating` / `upsertReviewText`).
5. **Transactional Batching**: Writes are chunked and executed within `database.withTransaction { ... }` blocks to prevent SQLite database lock contention and variable limit errors.
6. **Rate Limiting & Exponential Backoff**: TMDB queries include throttling and automatic exponential backoff retry on HTTP 429 (Too Many Requests).
7. **Cloud Synchronization Dispatch**: On completion of an import batch, `SyncManager.syncAllNow()` is triggered to sync local additions with Supabase.

---

## 4. User Guide

### Importing Data
1. Navigate to **Profile** → **Settings** (Gear icon) → **Data & Migration** → **Import Data**.
2. Select your source:
   - **TV Time (GDPR ZIP or CSV)**
   - **Trakt (CSV or Raw JSON / ZIP)**
   - **Letterboxd (Full Export ZIP or CSV)**
   - **Sequel Backup (JSON)**
3. Select the file via the Android document picker.
4. Processing runs in the background. Progress notifications update dynamically, and you can freely leave the app.

### Exporting Data
1. Navigate to **Profile** → **Settings** (Gear icon) → **Data & Migration** → **Export Data**.
2. Select your export destination:
   - **Sequel Backup (JSON)**: Full-fidelity lossless backup (`.json`) with versioned schema.
   - **Letterboxd (CSV Archive)**: Complete 4-CSV zip archive (`watched.csv`, `ratings.csv`, `reviews.csv`, `watchlist.csv`) ready for Letterboxd import.
   - **Trakt (CSV)**: Standard Trakt history CSV format.
3. Choose where to save your export file via the system storage picker (`ActivityResultContracts.CreateDocument`).

---

## 5. Architecture & 10-Responsibility Ingestion Pipeline

The import/export subsystem follows a strictly decoupled 10-responsibility pipeline:

1. **Source Detection & Input Validation (`domain/importer/detector/SourceDetector.kt`)**: Sniffs file magic bytes (`PK\x03\x04`), JSON structure, CSV header signatures, and ZIP directory contents to identify the source and format without consuming streams.
2. **Safe Archive Extraction (`domain/importer/archive/ZipArchiveExtractor.kt`)**: Prevents Zip Slip path traversal and decompression bombs (100MB / 10,000 entries threshold) and ignores macOS hidden files (`__MACOSX/`, `.DS_Store`).
3. **Format-Specific Source Adapters (`domain/importer/adapter/SourceAdapter.kt`)**:
   - `TvTimeSourceAdapter`: Adapts GDPR records, `seen_episode.csv`, `followed_tv_show.csv`, and legacy CSV.
   - `TraktSourceAdapter`: Adapts Trakt CSV history and official JSON exports (`watched_movies.json`, etc.).
   - `LetterboxdSourceAdapter`: Adapts `watched.csv`, `diary.csv`, `ratings.csv`, `reviews.csv`, `watchlist.csv`.
   - `SequelBackupSourceAdapter`: Adapts native Sequel v1 JSON backups.
4. **Canonical Normalization (`domain/importer/normalizer/ImportNormalizer.kt`)**: Standardizes media titles, extracts release years formatted in titles (e.g., `"Inception (2010)"`), clamps ratings in discrete 0.5 steps to Sequel's 0.5–10.0 scale, validates ISO-8601 timestamps, and deduplicates identical records.
5. **External Identity Resolution (`domain/importer/resolver/IdentityResolver.kt`)**: Confers confidence scoring (`EXACT_ID`, `EXACT_TITLE_YEAR`, `LOCAL_CACHE_MATCH`, `FUZZY_MATCH`, `AMBIGUOUS`, `UNRESOLVED`). Employs a local Room database cache-first lookup strategy to cut external HTTP queries by >90%, with exponential backoff on TMDB HTTP 429 rate limits.
6. **Validation, Duplicate Detection & Conflict Analysis (`domain/importer/conflict/ConflictAnalyzer.kt`)**: Compares incoming records against existing local Room database entities (watches, ratings, reviews, watchlist) and categorizes conflicts (`DUPLICATE_IDENTICAL`, `RATING_DIVERGENCE`, `REVIEW_DIVERGENCE`, `STATUS_DIVERGENCE`).
7. **Import Preview & Conflict Resolution Policy (`domain/importer/core/MatchConfidence.kt` & `ImportPreview.kt`)**: Presents an interactive preview in Compose UI displaying record counts, conflict warnings, and a choice of resolution strategy:
   - `SKIP_EXISTING`: Preserve local data, only insert missing items.
   - `OVERWRITE_EXISTING`: Overwrite local entries with imported values.
   - `KEEP_HIGHER_RATING`: When ratings diverge, retain the highest score.
   - `KEEP_NEWER_TIMESTAMP`: Compare timestamps and retain the most recent state.
   - `MERGE_PREFER_EXISTING`: Combine fields without overwriting non-null local data.
8. **Reliable Import Coordinator (`domain/importer/coordinator/ImportCoordinator.kt` & `ImportApplier.kt`)**: Applies batches transactionally within Room SQLite transactions, guarantees parent show/season entity hydration before child foreign-key inserts, segregates movies to `WatchedMovieDao` and episodes to `WatchedEpisodeDao`, and triggers `syncManager.syncAllNow()`.
9. **Versioned Backup Serialization & Restoration (`domain/backup/SequelBackupService.kt`)**: Losslessly dumps and restores all local user state in a versioned format (`schemaVersion = 1`).
10. **Portable Destination Exporters (`domain/export/ExportCoordinator.kt`)**: Coordinated by `ExportCoordinator`, producing:
    - `SequelNativeBackupExporter` (`.json`)
    - `LetterboxdDataExporter` (`.zip` containing `watched.csv`, `ratings.csv`, `reviews.csv`, `watchlist.csv`)
    - `TraktDataExporter` (`history.csv`)

---

## 6. Source Capability Matrix

| Capability / Dimension | TV Time (GDPR & Legacy) | Trakt (CSV & JSON) | Letterboxd (CSV / ZIP) | Sequel Backup (v1 JSON) | Sequel Representation & Mapping Rules |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Supported Entities** | Movies, TV shows, seasons, episodes, follows (watchlist). | Movies, TV shows, seasons, episodes, watchlist, ratings, history. | Movies only, diary entries, ratings, reviews, watchlist, tags. | Movies, TV shows, seasons, episodes, watchlist, ratings, reviews, Mount Rushmore. | Full native support across all entities with strict Room relational foreign-key integrity. |
| **Primary Identifiers** | Internal TV Time IDs, legacy TVDB IDs, media title strings. | Trakt slug/ID, TMDB ID, IMDb ID, TVDB ID. | Letterboxd URI, slug, movie title + release year. | Canonical TMDB IDs (`id`, `tmdb_movie_id`, `tmdb_episode_id`). | Primary canonical key is always TMDB ID. External IDs mapped via `ExternalIdentifiers` and scoped by provider. |
| **Identifier Scoping & Collisions** | Scoped to TV Time. Numeric IDs (e.g. `100`) must never be assumed to match TMDB IDs. | Scoped to Trakt. Provides explicit nested `ids.tmdb` mapping where available. | Scoped to Letterboxd URIs (`boxd.it/...`). | Globally scoped canonical TMDB IDs. | Provider IDs are explicitly scoped under `SourceProvenance(source, sourceRecordId)` to avoid cross-contamination. |
| **Rating Scale & Precision** | 1.0–5.0 or 1–10 depending on export vintage. | 1–10 integer scale. | 0.5–5.0 stars with 0.5 step increments. | 0.5–10.0 scale in 0.5 increments. | Letterboxd scaled: $(\text{Rating} \times 2.0)$. Original scale & value preserved in `originalRating` and `originalRatingScale`. |
| **Watch State Semantics** | Mixed: show-level rows (`type=watch`, `entity_type=show`) and episode rows (`seen_episode.csv`). | Distinct show-level tracking and per-episode history. | Per-movie watch events in `watched.csv` and `diary.csv`. | Segregated `watched_movies` (movie ID) and `watched_episodes` (show ID, season, ep). | **Show-level invariant**: Sequel **never** marks all episodes watched when a show is imported as watched. Shows are cached/favorited; episode watches require explicit season/episode numbers. |
| **History & Rewatch Semantics** | Does not natively record repeated episode viewings; updates `updated_at`. | `plays` count and discrete history event timestamps. | Explicit `Rewatch` boolean flag and multiple diary entries on distinct dates. | Multiple entries allowed; `watchDate` and `watchCount` recorded in backup. | Preserves legitimate repeated watches on distinct dates. Dedup key incorporates date to prevent collapsing rewatches. |
| **Date-Only vs. Timestamp** | Mixed: `created_at` timestamps (`yyyy-MM-dd HH:mm:ss`) or ISO-8601. | Explicit ISO-8601 UTC timestamps with millisecond precision. | Date-only `YYYY-MM-DD` in `diary.csv`, `watched.csv`. | Epoch milliseconds (`Long`) with optional date-only string (`watchDate`). | Date-only imports are anchored at **12:00:00 UTC (noon UTC)** via `ImportDateParser` to completely prevent date shifting across worldwide timezones (-11 to +14). |
| **Null, Zero, Missing Values** | Null or empty ratings mean unrated; missing season/ep indicates movie or show-level. | Null ratings mean unrated; 0 is invalid and ignored. | Unrated movies have blank `Rating` column. | Explicit nulls for unrated/unreviewed items. | Distinctly handles `null` vs `0` vs `false`. Never treats an unrated item as rating `0.0`. |
| **Unicode & Special Characters** | May contain UTF-8 titles and notes. | Full UTF-8 JSON. | Full UTF-8 in `Review` and `Name` including emoji, line breaks, curly quotes. | Full UTF-8 escaped JSON. | Multi-line reviews with emoji, punctuation, and line breaks preserved verbatim without stripping or truncation. |

---

## 7. Canonical Field Ownership & Uniqueness Contract

| Canonical Field | Meaning & Constraints | Source of Truth in Sequel | Ingestion Mapping | Conflict Policy Behavior | Backup Serialization | Destination Export |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `tmdbId` | Unique TMDB integer identifier ($> 0$). | `shows.id`, `watched_movies.tmdb_movie_id` | Resolved from external ID or TMDB search cache. | Immutable primary key; records without resolved TMDB ID routed to `unmatchedDetails`. | `ShowBackupDto.id`, `WatchedMovieBackupDto.tmdbMovieId` | `tmdb_id` in CSV exports; nested in JSON. |
| `mediaType` | Media discriminator: `"movie"` or `"tv"`. | `shows.media_type`, `watchlist.media_type` | Inferred from entity type, filename, or season/episode presence. | Mismatches flagged as `STATUS_DIVERGENCE`. | `mediaType` string field. | `Type` column in Trakt; implied by file in Letterboxd. |
| `watchedAt` | Epoch milliseconds of watch event ($> 0$). | `watched_movies.watched_at`, `watched_episodes.watched_at` | Parsed via `ImportDateParser`. Date-only anchored to noon UTC. | `LATEST_TIMESTAMP` picks newest; `OVERWRITE_LOCAL` replaces; `KEEP_EXISTING` preserves local. | `watchedAt: Long` in DTOs. | ISO-8601 string or epoch timestamp. |
| `watchDate` | Canonical date-only string (`YYYY-MM-DD`). | Preserved in `CanonicalImportRecord` and backup DTOs. | Directly extracted from date-only column (e.g. Letterboxd `Watched Date`). | Preserved alongside `watchedAt`. | `watchDate: String?` in DTOs. | `Date` column (`YYYY-MM-DD`). |
| `rating` | User rating on Sequel's 0.5–10.0 scale. | `reviews.rating` | Normalized from source scale (Letterboxd $\times 2.0$, TV Time/Trakt mapped). | `KEEP_HIGHER_RATING` or policy-driven overwrite; divergence reported in preview. | `rating: Float?` in `ReviewBackupDto`. | Scaled back to target destination (e.g. $/ 2.0$ for Letterboxd). |
| `originalRating` | Raw unnormalized rating as exported by source. | `CanonicalImportRecord.originalRating` | Extracted directly from source file before transformation. | Retained in audit provenance and backup model. | `originalRating: Float?` in `ReviewBackupDto`. | Preserved for round-trip fidelity. |
| `reviewText` | User written review text (Unicode, multi-line). | `reviews.review_text` | Extracted verbatim with line breaks and emoji intact. | Divergence reported in preview; policy determines overwrite. | `reviewText: String?` in `ReviewBackupDto`. | Quoted and CSV-escaped in CSV exports. |
| `isSpoiler` | Spoiler warning flag (`Boolean`). | `reviews.is_spoiler` | Parsed from source boolean or tags. | Logical OR in merge; overwritten in replace. | `isSpoiler: Boolean` in `ReviewBackupDto`. | `Spoiler` boolean column. |
| `isRewatch` | Indicates a repeated viewing event. | `CanonicalImportRecord.isRewatch` | Extracted from `Rewatch` flag or repeated diary entries. | Multiple entries preserved on distinct dates. | `watchCount: Int` in backup DTOs. | `Rewatch` column in Letterboxd. |

---

## 8. Data Integrity Guarantees & Invariants

1. **No Fake Episode Watched States**: When importing a TV show marked as watched at the show level (common in TV Time and Trakt), Sequel caches the show entity and creates show-level review/rating records, but **never** fabricates fake episode watches (such as marking Season 1 Episode 1 or all episodes watched). Individual episode watch history strictly requires explicit season and episode numbers.
2. **Zero Silent Discard**: Every record in an export file is accounted for. Unmatched titles are collected in `unmatchedDetails` of `ImportReport`, and ambiguous titles (multiple TMDB candidates) are recorded in `ambiguousDetails` with their candidate lists so users can inspect and resolve them.
3. **No Calendar Drift Across Timezones**: Date-only diary entries (e.g. Letterboxd `2023-11-01`) are anchored to 12:00:00 UTC (noon UTC). Converting to any timezone worldwide (-11 to +14) remains strictly on the same calendar day, eliminating the common bug where western hemisphere users see dates shifted back by one day.
4. **Preservation of Legitimate Rewatch Events**: The normalizer and conflict analyzer deduplication keys incorporate the viewing date. Multiple diary entries for the same movie on different dates are preserved rather than collapsed into a single watch.
5. **Decoupled Presentation & Zero UI Clutter**: While `CanonicalImportRecord` and backup DTOs retain extensive provenance (`originalRating`, `originalRatingScale`, `sourceRecordId`, `rawDateString`), Sequel's local Room entities (`WatchedMovieEntity`, `ShowEntity`, `WatchlistEntity`) remain clean and minimalist, protecting Sequel's refined aesthetic.
6. **Full-Fidelity Round-Trip Restoration**: Sequel's JSON backup (`SequelBackupModel`) captures 100% of local user data (shows, movies, episodes, watchlist, ratings, reviews, Mount Rushmore) and can be restored into a completely empty database without requiring any original source services or external APIs.

---

## 9. Idempotency, Checkpointing, Batching & Crash Recovery

### 9.1 Idempotent Ingestion Guarantees
Repeatedly importing the same export archive or CSV file is guaranteed to be completely idempotent:
- **Zero Row Duplication**: Room tables use deterministic primary keys and unique indices (`watched_movies.tmdb_movie_id`, `watched_episodes.tmdb_episode_id`, `watchlist(tmdb_id, media_type)`, `reviews(media_id, media_type, season_num, episode_num)`).
- **Deterministic Conflict Policies**: Re-running imports under `KEEP_EXISTING`, `OVERWRITE_LOCAL`, `LATEST_TIMESTAMP`, or `MERGE_ADDITIVE` produces exact, repeatable states without appending duplicate rows or diverging counts.
- **Source Deduplication**: Duplicate rows within the source file itself (e.g., repeating the exact same movie on the same date) are captured during normalization and tallied under `duplicateInSourceCount`.

### 9.2 Transactional Batching & Checkpointing
Large imports are processed in discrete transactional batches (default 200 records per transaction chunk) using Room's `withTransaction`:
- **Atomic Commits**: Each chunk commits atomically to SQLite. If the process is halted or an error occurs mid-stream, previously committed batches remain intact.
- **Checkpoint Persistence (`ImportCheckpointManager`)**: After each batch commit, an `ImportCheckpoint` is persisted to disk under `files/import_checkpoints/checkpoint_<fingerprint>.json`:
  ```json
  {
    "importId": "d3b07384d113edec49eaa6238ad5ff00",
    "source": "LETTERBOXD",
    "fingerprint": "8f4e2...",
    "totalRecords": 500,
    "processedIndex": 200,
    "insertedCount": 180,
    "updatedCount": 0,
    "skippedCount": 20,
    "failedCount": 0,
    "policy": "KEEP_EXISTING",
    "status": "IN_PROGRESS",
    "lastUpdated": 1698451200000
  }
  ```
- **Crash Recovery & Resume**: When an import is resumed or retried with the same fingerprint, `ImportApplier` reads the checkpoint, verifies the `IN_PROGRESS` status, skips already committed records up to `processedIndex`, and resumes chunk processing without re-executing completed work or duplicating records. On full completion, the status transitions to `COMPLETED`.
- **Stateless Re-import Recovery**: Even if an import is retried from scratch without checkpoint resumption, Sequel's upsert semantics and conflict policies ensure that already-inserted records are recognized as existing and skipped, preventing duplicate records.

### 9.3 Foreign Key Relational Safety
SQLite enforces strict foreign key constraints across Sequel's relational schema:
- `reviews`, `seasons`, `episodes`, and `watched_episodes` all enforce foreign keys referencing `shows(id, media_type)`.
- `ImportApplier` guarantees that the parent `ShowEntity` is persisted in `showDao` before attempting any inserts into `reviews` or `watched_episodes`. If offline or unhydrated, a fallback parent entity is inserted first to eliminate any risk of `SQLiteConstraintException: FOREIGN KEY constraint failed`.

### 9.4 Transient Network & Rate-Limit Resilience
External identity resolution via `IdentityResolver` is protected against network drops and TMDB API throttling:
- **Local-First Resolution**: Checks local Room database cache before dispatching network requests, resolving existing titles in $< 5\text{ ms}$.
- **Exponential Backoff**: When remote queries encounter HTTP 429 (Too Many Requests), HTTP 500–599 server errors, or `java.io.IOException` (socket timeouts), requests are retried with exponential backoff ($400\text{ ms} \to 800\text{ ms} \to 1600\text{ ms}$) up to 3 times before gracefully falling back to local matches or `MatchConfidence.UNRESOLVED`.

### 9.5 Mathematical Consistency of Import Metrics
Every record in an import is tracked to its exact resolution. The `ImportReport` guarantees strict arithmetic equality across all execution paths:
$$\text{totalProcessed} = \text{insertedCount} + \text{updatedCount} + \text{skippedIdenticalCount} + \text{duplicateInSourceCount} + \text{failedCount}$$
Where:
- $\text{totalProcessed}$: Total lines/records parsed from source.
- $\text{duplicateInSourceCount}$: Duplicate records filtered out of the source stream during normalization.
- $\text{unresolvableCount} \le \text{failedCount}$: Titles that could not be matched against local or remote sources.
- $\text{ambiguousCount}$: Titles with multiple candidates that were resolved to the best candidate and flagged for user review.


