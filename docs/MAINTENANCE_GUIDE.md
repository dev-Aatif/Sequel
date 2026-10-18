# Sequel — Documentation Maintenance & Evolution Guide

## 1. Overview

This guide is the **developer's operational manual** for maintaining Sequel's documentation suite. Whenever you write code, refactor an architecture component, add a database migration, or modify an API endpoint, consult this guide to identify **which documentation files must be updated and what details must be recorded**.

Maintaining accurate documentation ensures:
- New contributors can onboard in minutes without asking tribal knowledge questions.
- Database schemas between client Room and cloud Supabase never drift out of sync.
- Production release playbooks and security policies reflect actual code behavior.
- App store submissions and compliance audits (GDPR/CCPA) remain frictionless.

---

## 2. Change-to-Documentation Trigger Matrix

| When You Change / Add... | Documentation Files to Update | Section to Edit | Priority |
| :--- | :--- | :--- | :--- |
| **Room Entity / DAO / Database Migration** | `docs/DATABASE_SCHEMA.md`<br>`README.md`<br>`web/index.html` | Schema definitions, Migration table (v1→vN), ER diagram, version badge | **P0 (Critical)** |
| **Supabase Cloud Schema / RLS Policy / Tables** | `docs/DATABASE_SCHEMA.md`<br>`docs/API_INTEGRATIONS.md`<br>`docs/SECURITY_AND_PRIVACY.md`<br>`web/index.html` | Supabase DDL SQL script, PostgREST table constants, RLS policy breakdown, Interactive SQL tab | **P0 (Critical)** |
| **WorkManager Sync Worker / SyncManager Policy** | `docs/SYNC_ENGINE.md`<br>`docs/ARCHITECTURE.md` | Worker catalog table, enqueue policy (`APPEND_OR_REPLACE`), state machine, sequence diagrams | **P0 (Critical)** |
| **TMDB API Endpoints / DTOs / Image Sizes** | `docs/API_INTEGRATIONS.md`<br>`docs/DATABASE_SCHEMA.md` (if cached) | TMDB endpoint reference table, query params, image CDN dimensions | **P1 (High)** |
| **New Screen / Navigation Route / UI ViewModel** | `docs/ARCHITECTURE.md`<br>`README.md`<br>`web/index.html` | Presentation layer routes, screen catalog, feature list, architecture explorer | **P1 (High)** |
| **New Data Importer (e.g. Letterboxd, Simkl)** | `docs/DATA_PORTABILITY_AND_IMPORTS.md`<br>`README.md`<br>`web/index.html` | Parser grammar, recognized CSV headers, background import pipeline, feature list | **P1 (High)** |
| **Release Version Bump / ProGuard / Signing** | `docs/RELEASE_AND_DEPLOYMENT.md`<br>`docs/SETUP_AND_CONTRIBUTING.md`<br>`README.md` | Version history, ProGuard keep rules, SDK version requirements, platform badges | **P1 (High)** |
| **Android Permissions / Data Collection / GDPR** | `docs/SECURITY_AND_PRIVACY.md`<br>`docs/privacy.html` | Data classification table, permissions list, in-app privacy policy & "Last Updated" date | **P0 (Legal/Compliance)** |
| **Major Architectural or Dependency Decision** | `docs/decisions/` (`ADR-00X-*.md`)<br>`docs/decisions/README.md`<br>`docs/ARCHITECTURE.md` | Create new ADR, update ADR index table, update system architecture overview | **P1 (High)** |
| **Brand Colors, Logo Assets, or Icons** | `docs/assets/`<br>`web/assets/`<br>`web/css/style.css`<br>`docs/privacy.html` | Replace `playstore_icon.png` / `app_logo.jpg`, update CSS color variables | **P2 (Normal)** |

---

## 3. Trigger-by-Trigger Maintenance Procedures

### 3.1 Trigger: Modifying or Adding a Room Database Entity / Migration
**Relevant Code Locations:**
- `app/src/main/java/dev/sequel/app/data/local/entity/*`
- `app/src/main/java/dev/sequel/app/data/local/dao/*`
- `app/src/main/java/dev/sequel/app/data/local/SequelDatabase.kt`

**Actions Required:**
1. Open `docs/DATABASE_SCHEMA.md`:
   - If an entity was added or columns were altered, update the corresponding SQL block in **Section 3 (Local Room SQLite Schema)**.
   - If a new migration was added (e.g., `MIGRATION_17_18`), append a new row to the table in **Section 4 (Room Database Migration History)** documenting the version transition, exact SQL, and architectural reason.
   - If entity relationships changed, update the **Section 2 (ER Diagram)** Mermaid diagram.
2. Open `README.md`:
   - If Room version bumped (e.g. from v17 to v18), update the version mention in the features and spec highlights.
3. Open `web/index.html`:
   - Update the Room schema version badge in the Hero spec bar.

---

### 3.2 Trigger: Modifying Supabase Tables, RLS Policies, or Cloud Endpoints
**Relevant Code Locations:**
- `app/src/main/java/dev/sequel/app/data/remote/supabase/SupabaseSyncService.kt`
- `app/src/main/java/dev/sequel/app/data/remote/supabase/dto/*`

**Actions Required:**
1. Open `docs/DATABASE_SCHEMA.md`:
   - Update **Section 5 (Supabase Cloud PostgreSQL Schema DDL)** with the new `CREATE TABLE`, `ALTER TABLE`, or `CREATE POLICY` statements so users/developers deploying a new instance have copy-paste working DDL.
2. Open `docs/API_INTEGRATIONS.md`:
   - Update **Section 3.2 (PostgREST Database API)** if table constants (`TABLE_*`), conflict targets (`onConflict`), or DTO mappings change.
3. Open `docs/SECURITY_AND_PRIVACY.md`:
   - If RLS policies were modified, update **Section 3 (Cloud Security & Supabase Row-Level Security)**.
4. Open `web/index.html`:
   - Update the interactive SQL viewer snippet under the `#schema` section.

---

### 3.3 Trigger: Modifying WorkManager Sync Worker or SyncManager Policy
**Relevant Code Locations:**
- `app/src/main/java/dev/sequel/app/data/sync/*`
- `app/src/main/java/dev/sequel/app/data/worker/*`

**Actions Required:**
1. Open `docs/SYNC_ENGINE.md`:
   - If a new worker was created, add it to **Section 3.1 (WorkManager Worker Catalog)** with its responsibility, trigger type, and network constraints.
   - If work policies (`ExistingWorkPolicy`) or backoff strategies were altered, update **Section 4 (Rapid Interaction Resilience)** and **Section 7 (Retry Policies)**.
   - If conflict resolution rules changed, update **Section 5 (Conflict Resolution: Last-Write-Wins)**.
2. Open `docs/ARCHITECTURE.md`:
   - Update the Data Layer section and sync sequence diagrams.

---

### 3.4 Trigger: Adding/Modifying TMDB API Endpoints or Media Metadata
**Relevant Code Locations:**
- `app/src/main/java/dev/sequel/app/data/remote/tmdb/TmdbApiService.kt`
- `app/src/main/java/dev/sequel/app/data/remote/tmdb/dto/*`
- `app/src/main/java/dev/sequel/app/data/remote/tmdb/TmdbImageUtil.kt`

**Actions Required:**
1. Open `docs/API_INTEGRATIONS.md`:
   - Add or modify the endpoint in the **Section 2.2 (TMDB Endpoints Reference)** table.
   - If new image sizes or paths were added, update the **Section 2.3 (Image CDN URL Structure)** table.
2. Open `docs/DATABASE_SCHEMA.md`:
   - If new metadata fields from TMDB are now stored in Room (e.g. director, certification, streaming provider), document the new columns in `shows` or `episodes`.

---

### 3.5 Trigger: Adding a New Screen or Navigation Route
**Relevant Code Locations:**
- `app/src/main/java/dev/sequel/app/presentation/navigation/Screen.kt`
- `app/src/main/java/dev/sequel/app/presentation/navigation/SequelNavGraph.kt`
- `app/src/main/java/dev/sequel/app/presentation/screens/*`

**Actions Required:**
1. Open `docs/ARCHITECTURE.md`:
   - Add the new screen to **Section 2.1 (Presentation Layer)** and note any arguments (e.g. `show_detail/{showId}/{mediaType}`).
2. Open `README.md`:
   - If the screen represents a major new feature (e.g. statistics, calendar, social sharing), add a bullet to the **Key Features** section.
3. Open `web/index.html`:
   - Update the feature cards or interactive architecture explorer.

---

### 3.6 Trigger: Adding or Updating Data Importers (e.g. TV Time, Trakt, Letterboxd)
**Relevant Code Locations:**
- `app/src/main/java/dev/sequel/app/domain/importer/*`
- `app/src/main/java/dev/sequel/app/data/worker/MediaImportWorker.kt`

**Actions Required:**
1. Open `docs/DATA_PORTABILITY_AND_IMPORTS.md`:
   - Add a new subsection under **Section 2 (Supported Export Formats & Grammar)** documenting recognized column headers, date format patterns, and media type inference.
   - Add a sample CSV/JSON export snippet.
   - Update the user import steps in **Section 4 (User Import Guide)**.
2. Open `README.md` & `web/index.html`:
   - Update the importer feature descriptions.

---

### 3.7 Trigger: App Release Version Bump or Build/ProGuard Rules Change
**Relevant Code Locations:**
- `app/build.gradle.kts`
- `app/proguard-rules.pro`
- `gradle.properties`

**Actions Required:**
1. Open `docs/RELEASE_AND_DEPLOYMENT.md`:
   - Document any new R8 `-keep` rules added to prevent serialization or reflection crashes.
   - Review and verify the **Section 4 (Google Play Store Release Checklist)** and **Section 6 (Pre-Flight QA Checklist)**.
2. Open `docs/SETUP_AND_CONTRIBUTING.md`:
   - If minimum SDK, target SDK, or JDK versions change, update **Section 1 (Prerequisites & Environment)**.
3. Open `README.md`:
   - Update platform badges (e.g. `API 26--35`, `Kotlin 2.0+`) if version minimums increase.

---

### 3.8 Trigger: Modifying Privacy, Permissions, or Data Retention Practices
**Relevant Code Locations:**
- `app/src/main/AndroidManifest.xml` (Permissions)
- `app/src/main/java/dev/sequel/app/data/repository/AuthRepositoryImpl.kt` (Account deletion)

**Actions Required:**
1. Open `docs/SECURITY_AND_PRIVACY.md`:
   - Update **Section 2 (Data Classification Matrix)** if new user data types are stored.
   - Update **Section 5 (Account Deletion Protocol)** if deletion semantics change.
2. Open `docs/privacy.html`:
   - Update the relevant policy section.
   - **MANDATORY**: Bump the `Last Updated: [Current Month Day, Year]` line in the header.

---

### 3.9 Trigger: Making a Major Architectural Decision
**Relevant Code Locations:**
- Framework changes, library additions, database paradigm shifts.

**Actions Required:**
1. Create a new markdown file in `docs/decisions/`:
   - Name format: `docs/decisions/ADR-00X-descriptive-title.md` (e.g. `ADR-006-adoption-of-kmp-storage.md`).
   - Use the standard template: Context, Decision, Alternatives Considered, Consequences.
2. Open `docs/decisions/README.md`:
   - Append the new ADR to the **Record Index** table with its status and date.
3. Open `README.md`:
   - Append the ADR link under the **Architecture Decision Records** list.

---

### 3.10 Trigger: Updating Brand Assets, Logos, or Color Tokens
**Relevant Code Locations:**
- `app/src/main/res/drawable/ic_sequel_logo.xml`
- `app/src/main/res/drawable/ic_launcher_*.xml`
- `app/src/main/java/dev/sequel/app/presentation/theme/Color.kt`

**Actions Required:**
1. Update docs & web image assets:
   - Export and copy the new raster assets to `docs/assets/app_logo.jpg` and `docs/assets/playstore_icon.png` (512×512).
   - Copy the web logo to `web/assets/logo.jpg`.
2. Update web stylesheets:
   - In `web/css/style.css` and `docs/privacy.html`, update CSS custom properties (`--accent`, `--secondary`, `--bg`, `--surface`) if primary hex tokens changed.

---

## 4. Pre-Commit Documentation Hygiene Checklist

Before pushing commits or opening a PR, run this 5-point verification:
- [ ] **Zero Absolute Local Paths**: Ensure no `file:///home/...` or local username paths were added in markdown links (use clean relative links like `docs/FILE.md` or `../docs/FILE.md`).
- [ ] **Zero Secret Leaks**: Ensure no real API keys (`TMDB_API_KEY`), Supabase project URLs, or keystore passwords appear in documentation files (use placeholders like `your_api_key_here`).
- [ ] **Relative Asset Links**: Verify image tags in HTML and Markdown use relative paths (`docs/assets/...` or `assets/...`).
- [ ] **Link Integrity**: Click through updated markdown links to ensure anchor targets exist.
- [ ] **Gitignore Integrity**: Confirm `git status` does not show untracked build artifacts, secrets (`local.properties`, `keystore.properties`), or JVM crash logs.
