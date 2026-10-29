# Sequel — Setup & Contributor Guide

Welcome to the Sequel developer community! This guide provides the complete setup, architectural rules, and contribution workflow required to build and contribute to Sequel.

---

## 1. Prerequisites & Environment

To develop Sequel, ensure your machine has the following tools installed:

- **JDK**: Java Development Kit **17** (Temurin 17 or Android Studio bundled JDK).
- **IDE**: **Android Studio Ladybug (2024.2.1)** or newer.
- **Android SDK**:
  - `compileSdk`: **35** (Android 15)
  - `targetSdk`: **35**
  - `minSdk`: **26** (Android 8.0 Oreo)
  - Android SDK Build-Tools 35.0.0
  - Android Emulator or physical device running Android 8.0+ (Android 12+ recommended for dynamic colors and splash screen).

---

## 2. Local Setup & Configuration

### 2.1 Clone the Repository
```bash
git clone https://github.com/dev-Aatif/Sequel.git
cd Sequel
```

### 2.2 Configure `local.properties`
Create or edit `local.properties` in the project root. This file is ignored by git and contains your private API keys:

```properties
sdk.dir=/path/to/your/Android/Sdk

# The Movie Database (TMDB) API v3 Key
# Register at: https://www.themoviedb.org/settings/api
TMDB_API_KEY=your_tmdb_api_key_here

# Supabase Project Credentials
# Create a free project at: https://supabase.com
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_public_key_here
```

### 2.3 Supabase Database Provisioning
In your Supabase project dashboard:
1. Open the **SQL Editor**.
2. Copy and paste the DDL script from [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md#5-supabase-cloud-postgresql-schema-ready-to-deploy-ddl).
3. Execute the script to create tables (`user_watchlist`, `watched_movies`, `watched_episodes`, `reviews`) and activate Row-Level Security (RLS) policies.
4. Navigate to **Authentication** → **URL Configuration** and add the following redirect URL:
   ```
   sequel://login-callback
   ```

### 2.4 Local Supabase with Docker & Safe Test-Data Cleanup
For offline or local integration testing without impacting production cloud projects:

1. **Start Local Docker Containers**:
   ```bash
   supabase start
   supabase status
   ```
2. **Point `local.properties` to Local Emulator Host**:
   ```properties
   SUPABASE_URL=http://10.0.2.2:54321
   SUPABASE_ANON_KEY=<anon-key-from-supabase-status>
   ```
3. **Safe Test-Data Cleanup**:
   To clear test records without dropping user accounts or migrations, run the following in Supabase Studio (`http://localhost:54323` SQL Editor):
   ```sql
   BEGIN;
   TRUNCATE TABLE 
       public.watched_movies, 
       public.watched_episodes, 
       public.reviews, 
       public.user_watchlist 
   CASCADE;
   COMMIT;
   ```
4. **Stop Local Containers**:
   ```bash
   supabase stop
   ```

---

## 3. Building & Testing

### 3.1 Three-Tier Testing Strategy
To prevent long build and test execution cycles on every minor change, use the appropriate test tier:

* **Level 1 — While Developing (Micro-iterations)**:
  Run only the relevant test class or method. Ideal for iterating on a single feature or bugfix:
  ```bash
  # Test a single class
  ./gradlew testDebugUnitTest --tests "dev.sequel.app.presentation.MainViewModelTurbineTest"

  # Test Phase 8 Performance & Correctness Gates
  ./gradlew testDebugUnitTest --tests "dev.sequel.app.domain.importer.gate.PerformanceAndCorrectnessGateTest"

  # Test Data Importer Adapters
  ./gradlew testDebugUnitTest --tests "dev.sequel.app.domain.importer.adapter.SourceAdapterTest"

  # Test Synthetic Stress Datasets by Profile
  # Profile 1 (Smoke, 50 rows)
  ./gradlew testDebugUnitTest --tests "dev.sequel.app.domain.importer.ImportStressAndScalabilityTest.profile1_smokeSyntheticDataset_50Rows"
  # Profile 2 (Standard, 1,000 rows)
  ./gradlew testDebugUnitTest --tests "dev.sequel.app.domain.importer.ImportStressAndScalabilityTest.profile2_standardSyntheticDataset_1000Rows"
  # Profile 3 (Large, 5,000 rows)
  ./gradlew testDebugUnitTest --tests "dev.sequel.app.domain.importer.ImportStressAndScalabilityTest.profile3_largeSyntheticDataset_5000Rows"
  ```

* **Level 2 — Before Committing a Meaningful Change**:
  Run the unit test suite and UI smoke tests together:
  ```bash
  ./gradlew testDebugUnitTest
  ./gradlew connectedDebugAndroidTest
  ```

* **Level 3 — Before Release or After Major Migrations**:
  Run the complete end-to-end regression run, Room migration validation, and hosted sync checks:
  ```bash
  ./gradlew testDebugUnitTest connectedDebugAndroidTest
  ```

### 3.2 Terminal Build Commands
Use the Gradle wrapper to build the application:

```bash
# Compile and build debug APK
./gradlew assembleDebug

# Run unit test suite
./gradlew testDebugUnitTest

# Run Compose and Android instrumentation tests (requires emulator/device)
./gradlew connectedDebugAndroidTest

# Check Kotlin code quality and lints
./gradlew lintDebug
```

> **Note for Local Execution:** Run these commands directly in your local terminal.

### Testing Deep Links Locally
Verify Supabase email verification and password recovery deep links via Android Debug Bridge (ADB):

```bash
# Test password recovery deep link callback:
adb shell am start -W -a android.intent.action.VIEW \
  -d "sequel://login-callback#access_token=test&refresh_token=test&type=recovery" \
  dev.sequel.app.debug
```

---

## 4. Code Style & Architectural Guidelines

### 4.1 Strict Layering Boundaries
To maintain a clean separation of concerns, the following architectural boundaries are strictly enforced:
- **Presentation Layer**: Must *never* directly inject or reference Room DAOs or Retrofit services. It must interact exclusively with ViewModels, which interact with Use Cases or Domain Repositories.
- **Domain Layer**: Must be pure Kotlin. Never import `android.*` packages (with the exception of system `Uri` abstractions where required for file imports).
- **Data Layer**: Must return immutable domain models or Room entities; it should never expose raw OkHttp/Retrofit network response objects to the presentation layer.

### 4.2 Kotlin & Compose Best Practices
- **Immutable State in ViewModels**: Expose UI state as `StateFlow<UiState>` using private `_uiState = MutableStateFlow(...)`.
- **Compose Stability**: Avoid passing unstable mutable objects into Composable functions. Use primitive types, immutable data classes, or `ImmutableList`.
- **Coroutine Dispatchers**: Never hardcode `Dispatchers.IO` in repositories or use cases without dependency injection. Inject dispatchers or use `withContext(Dispatchers.IO)` within repository implementations.
- **No GlobalScope**: Never use `GlobalScope.launch`. Use `viewModelScope` for UI lifecycles or WorkManager for background execution.

---

## 5. Contribution & Git Workflow

### 5.1 Branch Naming Convention
- `feat/feature-name` (e.g., `feat/add-genre-filter`)
- `fix/issue-description` (e.g., `fix/sync-worker-cancellation`)
- `docs/documentation-update` (e.g., `docs/update-architecture-guide`)
- `refactor/component-name` (e.g., `refactor/extract-sync-helper`)

### 5.2 Conventional Commits
All commit messages must follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

- `feat: implement password reset and recovery flow`
- `fix: prevent duplicate review records for movie media type`
- `docs: add comprehensive sync engine specification`
- `refactor: extract review sync logic into reusable helper`
- `test: add unit tests for TvTimeCsvParser`

### 5.3 Pull Request Checklist
Before opening a pull request, ensure:
1. `./gradlew assembleDebug` succeeds without compilation warnings.
2. Unit tests pass: `./gradlew testDebugUnitTest`.
3. Database migrations (if any entity was altered) have a corresponding migration object and bump the Room version in `SequelDatabase.kt`.
4. Relevant documentation in `docs/` is updated.
