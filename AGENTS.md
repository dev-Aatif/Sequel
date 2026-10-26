# Sequel — Developer & AI Agent Guidelines

This document defines the behavior, testing tiers, and operational rules for developing, maintaining, and testing Sequel.

---

# 1. Automated Testing Strategy & Tiers

Running full clean builds and all test tasks for every small edit causes unnecessary build-overhead and swap contention. Use the 3-level testing strategy based on the scope of change:

### Level 1: While Developing (Micro-iterations)
* **When**: Iterating on a single feature, fixing a specific bug, or modifying a specific function/file.
* **Action**: Run **only** the relevant unit test class or method instead of the entire test suite.
* **Commands**:
  ```bash
  # Test a single class
  ./gradlew testDebugUnitTest --tests "dev.sequel.app.presentation.MainViewModelTurbineTest"

  # Test a single method
  ./gradlew testDebugUnitTest --tests "dev.sequel.app.data.local.RoomDatabaseSmokeTest.insertAndQueryShowEntity_returnsExpectedEntity"

  # Test a specific package
  ./gradlew testDebugUnitTest --tests "dev.sequel.app.presentation.screens.onboarding.*"
  ```

### Level 2: Before Committing a Meaningful Change
* **When**: Completing a feature milestone, finishing a vertical slice, or preparing a PR/commit.
* **Action**: Run the complete JVM unit test suite and local Compose smoke tests together to ensure no component regressions:
  ```bash
  ./gradlew testDebugUnitTest
  ./gradlew connectedDebugAndroidTest
  ```

### Level 3: Before Release or Major Migrations
* **When**: Pre-release audits, Room database schema version bumps (e.g., v17 → v18), major dependency upgrades, or CI validation.
* **Action**: Comprehensive regression verification covering all JVM tests, Android instrumented tests on an emulator/device, and remote Supabase sync sanity checks.

---

# 2. Test Suite Architecture

* **Pure Unit Tests (JVM)**: POJO mappers (`TmdbMapperTest`), Room converters (`ConvertersTest`), and ViewModel logic using fake repositories (`OnboardingViewModelTest`, `LoginViewModelGoogleTest`, `MainViewModelTurbineTest`). These run in milliseconds.
* **In-Memory Database Tests (Robolectric)**: Room database and DAO sanity tests (`RoomDatabaseSmokeTest`). Configured with `@Config(application = Application::class)` to run hermetically without booting the full application graph.
* **Instrumentation & Compose UI Tests**: Hilt-injected activity tests (`HomeScreenSmokeTest`) running under `HiltTestRunner` with test module doubles (`TestNetworkModule`, `TestSupabaseModule`).

---

# 3. Core Principles

- Do not attempt to build, run, or execute long-running tasks on resource-constrained agent environments when safe static analysis and targeted inspection are available.
- Complete tasks with precision, verify types and schemas through static inspection, and provide exact local execution commands.
