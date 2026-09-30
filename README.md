# Sequel

**Modern Show & Movie Tracking for Android**

Track what you watch, manage your watchlist, sync across devices, and never lose your place in a season again. Sequel is built for people who are tired of spreadsheets and half-baked tracker apps.

---

## Tech Stack

| Layer            | Technology                                        |
| ---------------- | ------------------------------------------------- |
| **UI**           | Jetpack Compose, Material 3, Compose Navigation   |
| **Architecture** | Clean Architecture (data / domain / presentation) |
| **Local DB**     | Room + Paging 3                                   |
| **Networking**   | Retrofit + OkHttp, Kotlinx Serialization          |
| **Backend**      | Supabase (Auth, Postgrest, Realtime)              |
| **Media API**    | TMDB (The Movie Database)                         |
| **DI**           | Hilt                                              |
| **Async**        | Kotlin Coroutines & Flow                          |
| **Background**   | WorkManager (Hilt-injected workers)               |
| **Preferences**  | Jetpack DataStore                                 |

## Features

- **Watchlist management** — Add shows and movies, track progress per-season and per-episode.
- **Cloud sync** — Supabase-powered sync keeps your library consistent across devices.
- **Search & discover** — Full TMDB search with trending shows on the home screen.
- **Episode tracking** — Mark episodes watched, see what's next, track air dates.
- **Reviews** — Write and sync personal reviews.
- **Data import** — Import your history from TV Time, Trakt, and CSV exports.
- **Offline-first** — Room database with Paging 3 means the app works without a connection.

## Local Development Setup

### Prerequisites

- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35

### 1. Clone the repo

```bash
git clone https://github.com/<your-org>/Sequel.git
cd Sequel
```

### 2. Configure API keys

Create `local.properties` in the project root:

```properties
sdk.dir=/path/to/your/Android/Sdk

TMDB_API_KEY=your_tmdb_v3_api_key
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_supabase_anon_key
```

Get your TMDB key at [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api).  
Create a Supabase project at [supabase.com](https://supabase.com).

### 3. Build & run

```bash
./gradlew assembleDebug
```

Or open the project in Android Studio and hit **Run**.

## Release Builds

Release builds require a `keystore.properties` file in the project root:

```properties
KEYSTORE_FILE=../release.keystore
KEYSTORE_PASSWORD=your_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

This file is git-ignored. If it's missing, the build falls back to debug signing automatically — development and CI won't break.

Build a signed release APK:

```bash
./gradlew assembleRelease
```

Build a signed release bundle (for Play Store):

```bash
./gradlew bundleRelease
```

R8 minification and resource shrinking are enabled for release builds.

## Project Structure

```
app/src/main/java/dev/sequel/app/
├── data/
│   ├── local/          # Room DB, DAOs, entities, converters
│   ├── paging/         # Paging 3 remote mediators
│   ├── remote/         # TMDB & Supabase API clients + DTOs
│   ├── repository/     # Repository implementations
│   ├── sync/           # WorkManager sync workers
│   └── worker/         # Background import workers
├── di/                 # Hilt modules
├── domain/
│   ├── error/          # Error models
│   ├── importer/       # CSV/Trakt import parsers
│   ├── repository/     # Repository interfaces
│   └── usecase/        # Business logic use cases
├── presentation/
│   ├── components/     # Shared Compose components
│   ├── navigation/     # Nav graph & routes
│   ├── screens/        # Feature screens (home, search, etc.)
│   ├── state/          # UI state models
│   └── theme/          # Material 3 theming
└── util/               # Extensions & helpers
```

## License

All rights reserved. This is proprietary software.
