# Sequel — Engineering Implementation Backlog & AI Agent Prompts

> **Document Version:** 1.0.0  
> **Target Platform:** Android (Jetpack Compose • Room SQLite • Supabase • TMDB)  
> **Workflow Pattern:** Vertical Slice $\rightarrow$ Automated Test $\rightarrow$ Verification $\rightarrow$ Next Feature  

---

## 1. Architectural Workflow: The 7-Step Feature Lifecycle

Every single task in this backlog must follow this standardized vertical-slice cycle before being marked complete:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   THE SEQUEL FEATURE LIFECYCLE                         │
├────────────────────────────────────────────────────────────────────────┤
│ 1. Schema & Migration   → Room Entity + Migration SQL + Room Test      │
│ 2. Domain & Repository  → UseCase + Domain Contract + Unit Test        │
│ 3. ViewModel & State    → StateFlow UI State + ViewModel Test (Turbine)│
│ 4. Compose UI & Tokens  → Aesthetic M3 Compose UI + Modifier.testTag() │
│ 5. Remote Sync (Opt.)   → Supabase PostgREST payload + Worker sync     │
│ 6. Automated Validation → Run JVM unit tests & Compose UI tests        │
│ 7. Audit & Sign-off     → Update docs & verify zero regressions        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Pillar 0: Foundation — Enterprise Automated Testing Harness

---

### Task 00: Scaffold Industry-Standard Testing Infrastructure
* **Priority:** P0 (Blocker for all subsequent feature development)
* **Goal:** Establish a robust automated testing harness covering JVM unit tests, Compose UI instrumentation tests with Hilt fakes, and Room in-memory migration testing.

#### Technical Scope
1. **Dependencies (`app/build.gradle.kts`)**:
   - `androidx.compose.ui:ui-test-junit4`
   - `androidx.compose.ui:ui-test-manifest` (debugImplementation)
   - `com.google.dagger:hilt-android-testing`
   - `app.cash.turbine:turbine` (for StateFlow coroutine testing)
   - `io.mockk:mockk`
   - `com.squareup.okhttp3:mockwebserver`
   - `androidx.room:room-testing`
2. **Hilt Test Runner**:
   - Create `app/src/androidTest/java/dev/sequel/app/HiltTestRunner.kt` extending `AndroidJUnitRunner`.
   - Update `testInstrumentationRunner` in `build.gradle.kts`.
3. **Test Tag Semantics Standard**:
   - Establish `dev.sequel.app.presentation.util.TestTags` object defining standard semantic keys for buttons, inputs, cards, and root screens.
4. **Fakes & Test Modules**:
   - Scaffold `FakeTmdbApiService` and `FakeSupabaseClient` in `androidTest`.

#### Acceptance Criteria
- [x] `./gradlew testDebugUnitTest` runs with zero compilation or setup errors.
- [x] A sample Compose UI test launches `MainActivity` with Hilt dependency injection without crashing.
- [x] Room in-memory test verifies that database creates and closes cleanly.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 00 — Scaffold Industry-Standard Testing Infrastructure for Sequel.

Context: Sequel is a Kotlin Jetpack Compose Android app with Room SQLite (v17), Hilt DI, Retrofit (TMDB), and Supabase. Currently, automated test coverage is 0%. We need a clean, industry-standard testing infrastructure before building features.

Requirements:
1. Update `app/build.gradle.kts` to add all required testing dependencies:
   - Turbine (app.cash.turbine:turbine) for coroutines StateFlow testing.
   - MockK (io.mockk:mockk and io.mockk:mockk-android).
   - Hilt Testing (com.google.dagger:hilt-android-testing and kaptTest / kaptAndroidTest).
   - Compose UI testing (androidx.compose.ui:ui-test-junit4 and ui-test-manifest).
   - Room Testing (androidx.room:room-testing).
   - MockWebServer (com.squareup.okhttp3:mockwebserver).
2. Configure `testInstrumentationRunner = "dev.sequel.app.HiltTestRunner"` in `defaultConfig`.
3. Create `app/src/androidTest/java/dev/sequel/app/HiltTestRunner.kt`:
   - Custom test runner providing HiltTestApplication.
4. Create `app/src/main/java/dev/sequel/app/presentation/util/TestTags.kt`:
   - Central object containing semantic testTag string constants:
     - Screens: SCREEN_HOME, SCREEN_PROFILE, SCREEN_DETAIL, SCREEN_SEARCH, SCREEN_WATCHLIST.
     - Buttons: BTN_ADD_WATCHLIST, BTN_RATE_PILL, BTN_SETTINGS, BTN_FILTER_MOVIES, BTN_FILTER_SHOWS.
     - Elements: INPUT_SEARCH, CARD_MEDIA_POSTER, SHEET_REVIEW_DRAWER.
5. Create a smoke test `app/src/test/java/dev/sequel/app/data/local/RoomDatabaseSmokeTest.kt`:
   - Uses Room.inMemoryDatabaseBuilder to verify DAOs instantiate and insert/query a dummy ShowEntity.
6. Create an instrumentation test `app/src/androidTest/java/dev/sequel/app/presentation/HomeScreenSmokeTest.kt`:
   - Verifies the app boots with Hilt and renders the root scaffold.

Follow established Kotlin code conventions. Do not break existing application code.
```

---

## 3. Pillar 1: Profile & Cinephile Sanctuary (Identity)

---

### Task 01: Top 4 "Mount Rushmore" Pinned Favorites (Idea 14)
* **Priority:** P1
* **Goal:** Allow users to pin their all-time top 4 defining movies or shows to the top of their profile, Letterboxd-style.

#### Technical Scope
1. **Room Database**:
   - Create entity `MountRushmoreEntity(val slotIndex: Int, val mediaId: Int, val mediaType: String, val title: String, val posterPath: String?, val year: String?, val rating: Float?)` with primary key `slotIndex` (constrained 0..3).
   - Create `MountRushmoreDao` with `observeAllSlots(): Flow<List<MountRushmoreEntity>>` and `setSlot(entity: MountRushmoreEntity)`.
   - Add to `SequelDatabase` with migration `MIGRATION_17_18`.
2. **Domain**:
   - `GetMountRushmoreUseCase`, `SetMountRushmoreSlotUseCase`.
3. **Presentation**:
   - Update `ProfileViewModel` to expose `mountRushmore: StateFlow<List<MountRushmoreSlotUi>>`.
   - In `ProfileScreen.kt`, add `MountRushmoreSection` with 4 poster slots, gold border highlights for filled slots, and an empty slot placeholder with a `+` icon.
   - Clicking an empty or filled slot launches a search/picker dialog to select a title.

#### Acceptance Criteria
- [x] Users can pin up to 4 titles into slots 0, 1, 2, and 3.
- [x] Tapping a slot opens a search picker or opens the title's detail screen.
- [x] Pinned items persist across app reboots via Room.
- [x] Offline reads happen instantly from local SQLite.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 01 — Top 4 "Mount Rushmore" Pinned Favorites for Sequel.

Context: In Sequel, cinephiles want to pin their all-time top 4 favorite movies or shows (Letterboxd Mount Rushmore style) at the top of their profile. Room is the SSOT (current version 17).

Requirements:
1. Room Database:
   - Create `app/src/main/java/dev/sequel/app/data/local/entity/MountRushmoreEntity.kt` with fields: `slotIndex` (Int, PK, 0..3), `mediaId` (Int), `mediaType` (String), `title` (String), `posterPath` (String?), `year` (String?), `rating` (Float?), `updatedAt` (Long).
   - Create `MountRushmoreDao.kt` with `observeSlots(): Flow<List<MountRushmoreEntity>>`, `getSlot(slotIndex: Int): MountRushmoreEntity?`, and `upsertSlot(slot: MountRushmoreEntity)`.
   - Update `SequelDatabase.kt` to register entity, DAO, bump version to 18, and implement `MIGRATION_17_18`.
2. Domain & Repository:
   - Create `MountRushmoreRepository` and implementation.
   - Use cases: `GetMountRushmoreUseCase` and `SetMountRushmoreSlotUseCase`.
3. UI in ProfileScreen:
   - In `ProfileScreen.kt`, place the "Mount Rushmore" section immediately below the Profile Header.
   - Display 4 poster cards in a 4-column horizontal grid with 12.dp corner radius, subtle ambient purple/gold glow, and slot numbers (I, II, III, IV).
   - Empty slots show a dashed outline and "+" icon with testTag `TestTags.RUSH_SLOT_EMPTY_x`.
   - Filled slots show the high-res poster and title with testTag `TestTags.RUSH_SLOT_FILLED_x`.
   - Clicking an empty slot opens a media search dialog; clicking a filled slot allows re-selection or navigation to detail.
4. Testing:
   - Write unit test in `app/src/test/java/dev/sequel/app/domain/usecase/MountRushmoreUseCaseTest.kt` verifying slot bounds (0..3) and upsert logic.
   - Write Compose UI test in `app/src/androidTest/java/dev/sequel/app/presentation/profile/MountRushmoreComposeTest.kt` verifying slots render and open picker.

Ensure offline-first behavior and zero network blocking on profile render.
```

---

### Task 02: Profile Watch History with Movie/Show Segregation & Sorting (Idea 1)
* **Priority:** P1
* **Goal:** Enable quick navigation through watch history in Profile with separate movie and show toggles and multiple sorting filters.

#### Technical Scope
1. **Data Layer**:
   - Enhance `WatchedEpisodeDao` and `WatchedMovieDao` with sorting queries (by date watched DESC/ASC, by title, by user rating).
2. **ViewModel**:
   - Expose filter state: `WatchHistoryFilter(mediaType: MediaTypeFilter = ALL, sortOrder: SortOrder = DATE_DESC)`.
3. **UI**:
   - Segmented filter chips: `[All]` | `[Movies]` | `[TV Series]`.
   - Dropdown menu for sorting: `[Recently Watched]`, `[Highest Rated]`, `[Runtime]`.
   - Card displays: Poster, Title, Date Watched, Season/Episode tag (for TV), User Red Pill rating badge.

#### Acceptance Criteria
- [x] Quick navigation through watch history in Profile with separate movie and show toggles.
- [x] Sorting filters supported (Recently Watched, Oldest, Highest Rated, Runtime).
- [x] User Red Pill rating badge visible on rated cards.
- [x] Reactive local Room updates with zero network latency.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 02 — Profile Watch History with Movie/Show Segregation & Sorting for Sequel.

Requirements:
1. Room DAOs:
   - Add unified or distinct queries in `WatchedMovieDao` and `WatchedEpisodeDao` supporting sorting:
     - By `watched_at` DESC / ASC
     - By rating (joining with reviews table where applicable)
2. ProfileViewModel:
   - Add `WatchHistoryFilter` state flow with options: MediaType (ALL, MOVIE, TV) and SortBy (DATE_DESC, DATE_ASC, RATING_DESC).
   - Combine Room flows so changing the filter or sort updates the list reactively without network calls.
3. ProfileScreen UI:
   - In the "Recent Activity / Watch History" section of `ProfileScreen.kt`, add a modern pill bar:
     - Filter chips: "All", "Movies", "TV Shows" with testTag `TestTags.FILTER_HISTORY_x`.
     - Sort icon button opening a dropdown menu ("Recently Logged", "Highest Rated").
   - Display items using aesthetic glass cards with poster thumbnail, title, formatted relative watch time ("Yesterday", "Oct 24"), and user rating pills if rated.
4. Testing:
   - Add unit tests in `ProfileViewModelTest.kt` verifying filtering and sorting updates `uiState.watchHistoryItems` correctly.
```

---

### Task 03: Profile Reviews Tab with Movie/Show Segregation & Reactions (Idea 2)
* **Priority:** P1
* **Goal:** A dedicated profile tab to browse all user-authored reviews, filterable by movie vs. TV show, displaying Red Pill scores and reactions.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 03 — Profile Reviews Tab with Movie/Show Segregation for Sequel.

Requirements:
1. Room & Domain:
   - Query `ReviewDao` for all reviews authored by the user where `is_rating_only == 0` (or all reviews), sorted by `updated_at` DESC.
   - Support filtering by `media_type` ('movie' vs 'tv').
2. UI:
   - Add a tab switcher or segmented control in `ProfileScreen.kt`: `[Watch History]` | `[My Reviews]`.
   - In the Reviews view, provide `[All]` | `[Movies]` | `[TV Shows]` chips.
   - Each review card displays: Media Poster, Title, Year, Red Pill badge (e.g. 💊 8.5), Primary Emotion chip (e.g. "Mindfuck"), spoiler tag, review text snippet, and reaction counts.
3. Tests:
   - Unit test verifying reviews list updates when switching filter between movie and tv.
   - Compose test asserting review card renders text, emotion chip, and rating badge.
```

---

### Task 04: Taste Identity Card (Idea 16)
* **Priority:** P2
* **Goal:** Automatically generate an algorithmic cinephile identity card from local Room DB history (Primary Obsession, Top Auteur, Controversial Takes).

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 04 — Taste Identity Card Generator for Sequel.

Requirements:
1. Domain:
   - Create `app/src/main/java/dev/sequel/app/domain/usecase/GenerateTasteIdentityUseCase.kt`.
   - Algorithmic analysis of local Room DB:
     - Primary Obsession: Top genre by total hours watched (e.g. "Cyberpunk & Sci-Fi" or "90s Crime Thrillers").
     - Top Director / Showrunner: Aggregated from cached shows/movies directors where watched count is highest.
     - Controversial Hot Takes: Titles where user gave $\ge 8.5$ Red Pills while TMDB critic vote average is $\le 6.5$, or vice versa.
2. UI Component:
   - Create `TasteIdentityCard.kt` in `dev.sequel.app.presentation.screens.profile.components`:
     - Cyberpunk luxury gradient card (`#161920` with `#A855F7` neon accents).
     - Displays "CINEPHILE DNA", user handle, primary obsession tag, top auteur badge, and total hours logged.
     - Include a "Share DNA" button with testTag `TestTags.BTN_SHARE_TASTE_CARD`.
3. Tests:
   - Unit test in `GenerateTasteIdentityUseCaseTest.kt` with deterministic mock watch records asserting correct top genre, director, and hot takes.
```

---

## 4. Pillar 2: Cultured Rating & Review Engine

---

### Task 05: 0.5-to-10.0 Red Pill Rating System (Per-Movie & Per-Season Scope) (Ideas 9, 21, 22)
* **Priority:** P0 (Core Signature Feature)
* **Goal:** Replace standard 5-star ratings with a 0.5–10.0 Red Pill rating scale (20 discrete half-pill steps) scoped exclusively to Movies and TV Seasons.

#### Technical Scope
1. **Scope Boundary**:
   - Strictly per-movie and per-season. Individual episodes only support binary watched/unwatched toggles.
2. **Room Database**:
   - Update `ReviewEntity`: `rating_pills: Float` (0.5 to 10.0, step 0.5), `season_num: Int?`, `episode_num: Int? = NULL` (deprecated for ratings).
   - Migration `MIGRATION_18_19`.
3. **UI Rating Component**:
   - Create `RedPillRatingBar.kt` in `presentation/components`:
     - Visual row of 10 pill icons with filled, half-filled, and empty states.
     - Interactive drag slider or tap with 0.5 step haptic detents.
     - Live verdict badge:
       - 9.0–10.0: 💊 *Absolute Cinema* (Crimson Glow)
       - 7.5–8.5: ⭐ *Certified Banger* (Teal Glow)
       - 5.0–7.0: ⚖️ *Decent / Mid* (Neutral Dim)
       - <5.0: 🗑️ *Blue Pill / Slop* (Muted Slate)

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 05 — 0.5-to-10.0 Red Pill Rating System (Per-Movie & Per-Season) for Sequel.

Context: Sequel is replacing generic star ratings with its signature 10 Red Pills scale (using 0.5 steps = 20 increments). Ratings are scoped per-movie and per-season; individual episode rating is omitted by design.

Requirements:
1. Room Database:
   - In `ReviewEntity.kt`, ensure `rating` stores a Float in 0.5 steps (e.g. 7.5, 8.0, 8.5).
   - Ensure `seasonNum` is populated when rating a season; `episodeNum` must remain null for ratings.
   - Register migration in `SequelDatabase.kt`.
2. Rating Slider Component:
   - Create `app/src/main/java/dev/sequel/app/presentation/components/RedPillRatingSlider.kt`.
   - Render 10 Pill icons (`#F43F5E` red gradient).
   - Support dragging and tapping with smooth 0.5 snap increments.
   - Display numerical badge (e.g. "💊 7.5 / 10.0") and status phrase:
     - 9.0..10.0: "Absolute Cinema • Transcendent Masterpiece"
     - 7.5..8.5: "Certified Banger • Essential Viewing"
     - 5.0..7.0: "Watchable Mid • Casual Viewing"
     - 0.5..4.5: "Blue Pill • Avoid / Slop"
   - Trigger light haptic click on each 0.5 change.
3. Rating Sheet:
   - Create `RatingModalBottomSheet.kt` invoked from ShowDetailScreen (for movie or season).
   - Includes Save and Clear buttons with testTag `TestTags.BTN_SAVE_RATING`.
4. Tests:
   - Unit test verifying rating values are clamped to 0.5..10.0 in 0.5 multiples.
   - Compose test verifying dragging slider updates rating text and status phrase.
```

---

### Task 06: 12-Vector Emotional Taxonomy (Vibe Chips) (Idea 9)
* **Priority:** P1
* **Goal:** Require 1 primary emotion and allow up to 3 secondary vibe chips with every rating/review.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 06 — 12-Vector Emotional Taxonomy (Vibe Chips) for Sequel.

Requirements:
1. Data Model:
   - Create enum `VibeEmotion(val key: String, val label: String, val emoji: String, val category: String)`:
     - ABSOLUTE_CINEMA ("Absolute Cinema", "🏛️")
     - MINDFUCK ("Mindfuck", "🌀")
     - VISUAL_FEAST ("Visual Feast", "🎨")
     - TEARJERKER ("Tearjerker", "💔")
     - EUPHORIC ("Euphoric", "✨")
     - HILARIOUS ("Hilarious", "😂")
     - DISGUSTED ("Disgusted / Puke", "🤮")
     - EXISTENTIAL_DREAD ("Existential Dread", "🕳️")
     - HEART_POUNDING ("Heart-Pounding", "⚡")
     - COMFORT_WATCH ("Comfort Watch", "☕")
     - BRAIN_DEAD_FUN ("Brain-Dead Fun", "🍿")
     - MID ("Mid / Snoozefest", "🥱")
2. Room & ReviewEntity:
   - Add columns `primary_emotion TEXT` and `secondary_emotions TEXT` (JSON list or comma-separated) to `ReviewEntity`.
3. UI Selection Grid:
   - In `RatingModalBottomSheet.kt`:
     - Display chips in a flexible flow row.
     - User selects 1 mandatory Primary emotion (highlighted with primary color glow).
     - User can optionally tap up to 3 Secondary vibes (accent border).
4. Tests:
   - Unit test verifying validation: Cannot save review without primary emotion. Cannot select >3 secondary vibes.
   - Compose test selecting primary and secondary chips and verifying selection state.
```

---

### Task 07: Cultured Emoji Reactions & Threaded Replies (Idea 9)
* **Priority:** P2
* **Goal:** Replace standard likes on reviews with a ranked cultural emoji hierarchy (`Red Pill > Fire > Scale > Trash > Clown > Blue Pill`).

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 07 — Cultured Emoji Reactions on Reviews for Sequel.

Requirements:
1. Data & Domain:
   - Define enum `ReviewReaction(val symbol: String, val title: String, val weight: Int)`:
     - RED_PILL ("💊", "Transcendent", 5)
     - FIRE ("🔥", "Certified Banger", 4)
     - SCALE ("⚖️", "Balanced / Mid", 3)
     - TRASH ("🗑️", "Waste of Time", 2)
     - CLOWN ("🤡", "Absurd Joke", 1)
     - BLUE_PILL ("🔵", "Normie Coping", 0)
   - Add local tracking and Supabase sync for user reactions on review IDs.
2. UI:
   - Under each review card, render an emoji reaction bar with counts.
   - Tapping an emoji toggles user's reaction with bouncy spring animation.
3. Tests:
   - Unit test verifying user can select one primary reaction per review.
   - Compose test verifying clicking emoji increments reaction counter.
```

---

### Task 08: Aggregated Community Pulse on Media Detail (Idea 13)
* **Priority:** P1
* **Goal:** Display authentic emotional consensus (e.g. "68% Absolute Cinema • Top Vibe: Mindfuck") on Movie & Show detail screens.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 08 — Aggregated Community Pulse on Media Detail for Sequel.

Requirements:
1. Data & Remote:
   - Create domain model `CommunityPulse(val absoluteCinemaPercent: Int, val bangerPercent: Int, val midPercent: Int, val topVibes: List<String>, val totalRatings: Int)`.
   - In `ShowRepository`, fetch aggregated consensus from Supabase RPC `get_community_pulse(media_id, media_type)` with graceful offline fallback to local calculations.
2. UI in ShowDetailScreen:
   - Place `CommunityPulseBanner` directly below the media title/genres and above the rating button.
   - Render a segmented horizontal ratio bar (Crimson for Absolute Cinema, Teal for Banger, Dim Gray for Mid).
   - Display prominent text: "68% Absolute Cinema • Top Vibes: Mindfuck & Visual Feast".
3. Tests:
   - Unit test verifying fallback when offline (shows local rating or empty state without crashing).
   - Compose test asserting community pulse bar renders percentage labels.
```

---

### Task 09: Theme Adaptability (Alternate Rating Tokens) (Idea 21)
* **Priority:** P3
* **Goal:** Allow users to customize rating tokens via Theme Settings (Default: Red Pills; Unlocked: Neon Popcorn, Film Reels, Cinema Tickets).

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 09 — Theme Adaptability & Alternate Rating Tokens for Sequel.

Requirements:
1. DataStore Preferences:
   - Add `rating_token_theme` key in `UserPreferencesRepository` with values: `RED_PILL` (default), `POPCORN`, `CINEMA_TICKET`, `FILM_REEL`.
2. UI Integration:
   - `RedPillRatingSlider.kt` and all rating badges observe the active rating token preference.
   - When set to `POPCORN`, icons change to 🍿; `CINEMA_TICKET` to 🎟️; `FILM_REEL` to 🎞️.
   - In SettingsScreen, add a "Rating Appearance" picker with live preview.
3. Tests:
   - Unit test asserting changing preference emits new token glyph across UI State.
```

---

## 5. Pillar 3: Discovery, Streaming & Quick Actions

---

### Task 10: "Where to Stream" TMDB Watch Providers Integration (Idea 18)
* **Priority:** P1
* **Goal:** Display local streaming availability (Max, Netflix, Prime Video, Apple TV) powered by TMDB / JustWatch data.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 10 — "Where to Stream" TMDB Watch Providers Integration for Sequel.

Requirements:
1. TMDB API Integration:
   - Add Retrofit endpoints in `TmdbApiService`:
     - `@GET("3/movie/{id}/watch/providers") suspend fun getMovieWatchProviders(@Path("id") id: Int): WatchProvidersResponse`
     - `@GET("3/tv/{id}/watch/providers") suspend fun getTvWatchProviders(@Path("id") id: Int): WatchProvidersResponse`
2. Domain & Data:
   - Create `WatchProviderUi(val providerName: String, val logoPath: String, val type: ProviderType)` where type is FLATRATE (Subscription), RENT, or BUY.
   - Filter response by device country code (`Locale.getDefault().country`).
3. UI:
   - On `ShowDetailScreen.kt`, add a "Where to Stream" horizontal row displaying provider logos with "Stream 4K", "Rent", or "Buy" badges.
   - Also display streaming icon on `Cine-Swipe` cards.
4. Tests:
   - Unit test with mock JSON verifying parsing of country-specific provider lists.
   - Compose test asserting provider logos render properly in row.
```

---

### Task 11: Quick-Peek Detail Cards on Home Screen (Idea 3)
* **Priority:** P1
* **Goal:** Add an "Info" button next to "Add to Watchlist" on Home cards that opens an ambient preview bottom sheet without navigating away.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 11 — Quick-Peek Detail Cards on Home Screen for Sequel.

Requirements:
1. UI Component:
   - Create `QuickPeekBottomSheet.kt` in `dev.sequel.app.presentation.components`:
     - Backdrop image, Title, Certification (PG-13, R, TV-MA), Runtime, TMDB Critic Score, Sequel Community Pulse.
     - Synopsis preview (max 4 lines with 'read more').
     - Direct CTA: "Add to Watchlist", "Mark Watched", and "Full Details".
2. HomeScreen Integration:
   - In `ShowCard.kt` (used across Home shelves), add an "Info" (ⓘ) button adjacent to the bookmark button with testTag `TestTags.BTN_QUICK_PEEK_x`.
   - Tapping it loads the media summary and displays `QuickPeekBottomSheet` with smooth slide-up animation.
3. Tests:
   - Compose test clicking info button opens sheet and clicking outside dismisses it.
```

---

### Task 12: Home Shelves: Binge-Ready & "Because You Watched" (Ideas 7 & 8)
* **Priority:** P2
* **Goal:** Add two curated shelves to Home: Concluded/Completed TV series only, and recommendations seeded by the last tracked title.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 12 — Binge-Ready & "Because You Watched" Shelves on Home Screen for Sequel.

Requirements:
1. Domain Use Cases:
   - `GetBingeReadyShowsUseCase`: Queries TMDB discovery/trending filtering strictly for `status == 'Ended'` or `'Canceled'` so users only see complete stories.
   - `GetBecauseYouWatchedUseCase`: Queries local Room for the most recently logged movie or season, then calls TMDB recommendations for that title, excluding already watched items.
2. HomeViewModel & HomeScreen:
   - Add `bingeReadyShows` and `becauseYouWatched` state flows to `HomeUiState`.
   - Render two horizontal scrolling carousels in `HomeScreen.kt`:
     - "🏁 Binge-Ready: Concluded Series"
     - "⚡ Because You Watched [Title]"
3. Tests:
   - Unit test ensuring ongoing shows (`status == 'Returning Series'`) are excluded from Binge-Ready.
   - Compose test asserting carousel headers render on Home.
```

---

### Task 13: Watchlist Roulette (Shake-to-Pick with SensorManager) (Idea 17)
* **Priority:** P2
* **Goal:** Physical device shake or FAB tap picks a random title from the user's watchlist with quick runtime and mood constraints.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 13 — Watchlist Roulette (Shake-to-Pick) for Sequel.

Requirements:
1. Accelerometer Sensor:
   - Create `ShakeDetector.kt` utility listening to `SensorManager.SENSOR_DELAY_UI` to detect physical phone shakes cleanly without false positives.
2. Roulette Dialog & Constraints:
   - Create `RouletteModalSheet.kt`:
     - Toggles: "From My Watchlist" vs "Curated Masterpieces".
     - Runtime Slider: e.g. `< 100 mins` or `Any`.
     - Vibe Filter chip: `Fast-Paced`, `Mindfuck`, `Comfort`.
   - "SPIN" button with spinning slot-machine poster animation that lands on the picked title with celebratory haptics.
3. Tests:
   - Unit test verifying roulette candidate filtering satisfies runtime and watchlist constraints.
```

---

### Task 14: Surprise Pick / Discovery Wizard (Idea 4)
* **Priority:** P2
* **Goal:** A 4-step questionnaire wizard in Search to cure decision paralysis and pick the perfect title.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 14 — Surprise Pick Discovery Wizard for Sequel.

Requirements:
1. Multi-Step Modal Flow:
   - Step 1: Media Type (`[Movies]` vs `[TV Series]`).
   - Step 2: Target Vibe (select from 6 top vibes: Mindfuck, Tearjerker, Visual Feast, Comfort, Adrenaline, Brain-Off).
   - Step 3: Runtime limit (`< 90m`, `90-120m`, `120m+`).
   - Step 4: My Streaming Providers (Netflix, Max, Prime, Apple TV+).
2. Result View:
   - Dramatic card reveal with match score and "Start Watching Tonight" button.
3. Tests:
   - Unit test with mock TMDB query parameters asserting correct filters applied.
```

---

### Task 15: Cine-Swipe (Tinder-Style Vibe Card Deck) (Idea 5)
* **Priority:** P2
* **Goal:** A tactile gesture-based discovery deck in Search where users swipe right to add to watchlist and left to skip.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 15 — Cine-Swipe Gesture Discovery Deck for Sequel.

Requirements:
1. Gesture Engine:
   - Create `CineSwipeDeck.kt` in `dev.sequel.app.presentation.screens.search.components`.
   - Implement card stack gesture using Compose `Modifier.pointerInput` and `detectDragGestures`:
     - Dragging right tilts card and shows green "WATCHLIST" badge $\rightarrow$ releasing past threshold adds to local Room `watchlist`, triggers haptic pulse, and animates off-screen.
     - Dragging left tilts card and shows gray "PASS" badge $\rightarrow$ dismisses card.
2. Card Design:
   - Full-bleed poster, Title, Year, TMDB + Sequel Red Pill score, Top 3 Emotion tags, and Streaming service badge.
3. Tests:
   - Unit test asserting swipe right invokes `WatchlistRepository.addToWatchlist()`.
   - Compose test asserting card count decreases upon swipe.
```

---

## 6. Pillar 4: Social Graph, Public Profiles & Virality

---

### Task 16: Public Profiles & Customization (Themes, Banners, Badges) (Idea 10)
* **Priority:** P2
* **Goal:** Enable viewing other users' public profiles with custom banners, VIP badges, top 4 Mount Rushmore, and public watchlists.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 16 — Public Profiles & Profile Customization for Sequel.

Requirements:
1. Supabase Profiles Schema:
   - Supabase table `profiles`: `id (uuid, PK)`, `handle (text, unique)`, `display_name`, `avatar_url`, `banner_url`, `theme_pack`, `vip_badge`, `is_public (boolean)`.
2. Public Profile Screen:
   - Create `PublicProfileScreen.kt` navigable via route `profile/{handle}`.
   - Shows user's banner, avatar, VIP badge, Mount Rushmore grid, public ratings count, and taste compatibility button.
3. Tests:
   - Unit test for profile handle validation (alphanumeric, 3-20 chars).
   - Compose test rendering public profile headers and Mount Rushmore.
```

---

### Task 17: Taste Compatibility Match Score (Idea 15)
* **Priority:** P2
* **Goal:** When viewing another user's profile, calculate an instant taste match index (e.g. "🔥 84% Taste Match — 14 shared Absolute Cinema titles").

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 17 — Taste Compatibility Match Score for Sequel.

Requirements:
1. Algorithm:
   - Create `CalculateTasteCompatibilityUseCase.kt`:
     - Compares current user's high ratings ($\ge 7.5$ Red Pills) with the target user's ratings.
     - Jaccard similarity formula:
       $$\text{Score} = \frac{|A \cap B|}{|A \cup B|} \times 100\%$$
     - Highlights shared "Absolute Cinema" titles and interesting divergences.
2. UI Badge:
   - Display prominent pill on `PublicProfileScreen.kt`: "🔥 84% Taste Match With You".
   - Tapping opens a comparison sheet showing titles you both crowned masterpieces.
3. Tests:
   - Unit test with mock user profiles asserting correct percentage calculation.
```

---

### Task 18: Aesthetic Viral Share Cards (9:16 Instagram Story Export) (Ideas 12 & 19)
* **Priority:** P1
* **Goal:** Generate sleek 9:16 dark-mode gradient cards rendered into an Android Bitmap in Compose for sharing to Instagram Stories, X, and WhatsApp.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 18 — Aesthetic Viral Share Cards (Instagram Stories Export) for Sequel.

Requirements:
1. Off-Screen Compose Renderer:
   - Create `AestheticStoryCard.kt` formatted for 9:16 aspect ratio (1080 x 1920 px):
     - High-res poster backdrop with dark gradient blur.
     - Glowing 3D Red Pill verdict badge (e.g. "💊 9.5 ABSOLUTE CINEMA").
     - User quote / review text in editorial typography.
     - Top vibe chips and Sequel watermark branding.
2. Bitmap Generation:
   - Use Compose `rememberGraphicsLayer()` or Android `Canvas` rendering to turn the composable into an Android `Bitmap`.
   - Save temporarily to cache directory and launch Android `Intent.ACTION_SEND` with `image/png`.
3. Tests:
   - Unit test verifying bitmap generation runs without memory leaks or OOM.
```

---

### Task 19: Universal Link User Invite System (Idea 23)
* **Priority:** P2
* **Goal:** Fix and complete the half-implemented user invite system with deep linking (`sequel.app/invite/{code}`).

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 19 — Universal Link User Invite System for Sequel.

Requirements:
1. Deep Linking:
   - In `AndroidManifest.xml`, configure intent filter for `https://sequel.app/invite/{code}` and `sequel://invite/{code}`.
   - Update `SequelNavGraph.kt` to parse incoming invite codes on app launch.
2. Supabase Integration:
   - RPC function `redeem_invite(invite_code)` linking inviter and new user to unlock the "Founding Cinephile" profile badge.
3. Invite Dialog:
   - In Settings and Profile, add "Invite a Friend" button generating shareable text and link.
4. Tests:
   - Unit test for invite code parsing and URI pattern matching.
```

---

## 7. Pillar 5: Deep Cine-Analytics & Sequel Wrapped

---

### Task 20: Deep Personal Analytics & Genre Fingerprint (Idea 6)
* **Priority:** P2
* **Goal:** Calculate and display lifetime/monthly watch time, completion rates, and an interactive genre fingerprint slider/radar.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 20 — Deep Personal Analytics & Genre Fingerprint for Sequel.

Requirements:
1. Analytics Engine:
   - Create `CalculatePersonalAnalyticsUseCase.kt` querying Room DB:
     - Lifetime, yearly, and monthly watch time in days/hours.
     - Total movies watched and TV episodes crushed.
     - TV completion rate (seasons finished vs started).
     - Genre Fingerprint: Percentage distribution across all watched genres.
2. UI Screen:
   - Create `AnalyticsScreen.kt` navigable from Profile:
     - Animated counter cards for watch time.
     - Custom Canvas-drawn Genre Fingerprint bar charts with percentage sliders.
3. Tests:
   - Unit test verifying calculations against synthetic Room watch records.
```

---

### Task 21: Sequel Wrapped (Monthly & Yearly Cine-Reels Story Deck) (Idea 20)
* **Priority:** P3
* **Goal:** Spotify Wrapped style multi-slide story recap for movies and TV shows, exportable as a social reel.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 21 — Sequel Wrapped (Monthly & Yearly Story Deck) for Sequel.

Requirements:
1. Story Deck Presentation:
   - Create `SequelWrappedScreen.kt`:
     - Slide 1: Total minutes watched & all-nighter binge records.
     - Slide 2: Top Auteur / Director and Actor of the Year.
     - Slide 3: Defining Vibe & Genre Fingerprint.
     - Slide 4: Your #1 Masterpiece (Highest rated Red Pill title).
     - Slide 5: Shareable Summary Card with "Export to Instagram" CTA.
   - Implement Instagram-style story timer bars at top with tap-to-advance and hold-to-pause.
2. Tests:
   - Compose test asserting story slide progression and timer pauses on touch.
```

---

## 8. Pillar 6: Settings, Privacy & Pro Monetization

---

### Task 22: Comprehensive Settings & Data Portability (Idea 11)
* **Priority:** P2
* **Goal:** Profile privacy toggles, appearance, dynamic app icons, local JSON backup/export, and complete account deletion.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 22 — Comprehensive Settings & Data Portability for Sequel.

Requirements:
1. Features:
   - Privacy Settings: Toggle Profile Public/Private, Hide ratings from public feed.
   - Dynamic App Icons: Android activity-alias switcher (Default Crimson, OLED Gold, 90s VHS).
   - Data Export & Backup: Export complete Room watch history and reviews to encrypted JSON or standard CSV.
   - Danger Zone: Clear local cache, erase all data, and delete Supabase account.
2. Tests:
   - Unit test asserting JSON backup file generates valid, restorable schema.
```

---

### Task 23: Sequel Pro Architecture & Monetization Engine (Pro Matrix)
* **Priority:** P3
* **Goal:** Free vs. Pro tier separation, Tenor/Giphy GIF search, AWS S3 image uploads for reviews, and Pro theme locks.

#### Copy-Paste AI Agent Prompt
```text
Task: Implement Task 23 — Sequel Pro Architecture & Monetization Engine for Sequel.

Requirements:
1. Entitlements & Subscription:
   - Create `ProSubscriptionManager` checking Pro status locally and from Supabase.
   - Free Tier: Core offline tracking, Red Pills ratings, Community Pulse, standard themes.
   - Pro Tier: S3 Photo uploads in reviews, dynamic app icons, VIP profile badge, Lifetime Wraps.
2. Image Upload Pipeline (Pro):
   - Client-side image compression down to $\le 1080$px WebP.
   - Secure upload to Supabase Storage or S3 signed URL.
3. Tests:
   - Unit test ensuring Pro features gate gracefully for Free users without crashing.
```

---

## 9. Verification Summary & Next Steps

Follow this strict progression:
1. Start with **Task 00 (Testing Foundation)**. Do not proceed to feature development until Task 00 is verified.
2. Tackle **Pillar 1 (Tasks 01 to 04)** for immediate identity and profile enhancement.
3. Execute **Pillar 2 (Tasks 05 to 09)** to establish the signature Red Pill rating engine.
4. Continue sequentially through Discovery, Social, Analytics, and Pro monetization.
