# Sequel — Release & Deployment Playbook

This document details the production engineering, automated signing, ProGuard/R8 optimization, and deployment procedures for releasing **Sequel** to the Google Play Store and direct distribution channels.

---

## 1. Keystore Configuration & Automated Signing

Sequel uses a secure signing configuration that cleanly decouples release credentials from version control:

### 1.1 Generating a Production Keystore
Generate a release key using the Java `keytool` utility:

```bash
keytool -genkey -v -keystore release.keystore -alias sequel_release \
  -keyalg RSA -keysize 2048 -validity 10000
```

### 1.2 Configuring `keystore.properties`
Create `keystore.properties` in the project root (this file is excluded by `.gitignore`):

```properties
KEYSTORE_FILE=../release.keystore
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=sequel_release
KEY_PASSWORD=your_key_password
```

### 1.3 Automatic CI/CD Fallback Behavior
In `app/build.gradle.kts`, the build system checks for the presence of `keystore.properties`:
- **If present**: The `release` signing configuration attaches the production credentials.
- **If missing**: The build system automatically falls back to the default Android debug keystore and logs a warning. This ensures pull requests, continuous integration (CI) test runs, and fresh clones build successfully without requiring production secrets.

---

## 2. ProGuard & R8 Code Shrinking

Release builds enable full code shrinking, dead code elimination, and resource optimization:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}
```

### Critical R8 Keep Rules (`app/proguard-rules.pro`)
To prevent runtime serialization failures when obfuscated, the following rules preserve DTOs, Room reflection, and Supabase data models:

```pro
# Preserve stack trace line numbers for Crashlytics deobfuscation
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Kotlin Serialization Models
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
    *** Companion;
}
-keep,allowobfuscation,allowshrinking class * {
    @kotlinx.serialization.Serializable class *;
}

# Preserve Room Database and Generated Implementations
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class dev.sequel.app.data.local.SequelDatabase { *; }
-keep class dev.sequel.app.data.local.entity.** { *; }
-keep interface dev.sequel.app.data.local.dao.** { *; }
-keep class dev.sequel.app.data.local.converter.** { *; }

# Preserve Supabase PostgREST & Auth DTOs
-keep class dev.sequel.app.data.remote.supabase.dto.** { *; }
-keep class dev.sequel.app.data.remote.supabase.**Dto { *; }

# Preserve TMDB Retrofit DTOs
-keep class dev.sequel.app.data.remote.tmdb.dto.** { *; }
-keep class dev.sequel.app.data.remote.tmdb.**Dto { *; }

# Preserve Domain Backup, Import Checkpoint & Core Data Models
-keep class dev.sequel.app.domain.backup.** { *; }
-keep class dev.sequel.app.domain.importer.checkpoint.** { *; }
-keep class dev.sequel.app.domain.importer.core.** { *; }
-keep class dev.sequel.app.**.*Dto { *; }
```

---

## 3. Building Production Artifacts

### 3.1 Google Play App Bundle (AAB)
Google Play requires publishing via Android App Bundle (.aab) format to enable dynamic delivery and split APK optimization:

```bash
./gradlew bundleRelease
```
**Output Location:** `app/build/outputs/bundle/release/app-release.aab`

### 3.2 Standalone Release APK
For GitHub releases, internal testing, or sideloading:

```bash
./gradlew assembleRelease
```
**Output Location:** `app/build/outputs/apk/release/app-release.apk`

---

## 4. Google Play Store Release Checklist

| Step | Requirement | Asset / Location | Status |
| ---- | ----------- | ---------------- | ------ |
| 1 | **High-Res App Icon** | 512 × 512 PNG, 32-bit color, no transparency | `docs/assets/playstore_icon.png` |
| 2 | **Feature Graphic** | 1024 × 500 PNG, 24-bit RGB | `docs/assets/playstore_feature_graphic.png` |
| 3 | **Phone Screenshots** | 1080 × 2400 PNG, 24-bit RGB (Min 4, Max 8) | `docs/assets/playstore_screenshots/` |
| 4 | **Privacy Policy URL** | Live accessible HTTPS web URL | `https://dev-aatif.github.io/Sequel/privacy.html` |
| 5 | **Target SDK** | Android 15 (API level 35) | `compileSdk = 35`, `targetSdk = 35` |
| 6 | **Data Safety Form** | User library storage, email authentication, Crashlytics diagnostics | Fully documented in `docs/SECURITY_AND_PRIVACY.md` and `docs/privacy.html` |
| 7 | **App Permissions** | `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS` | Minimal runtime permissions |


---

## 5. Versioning Strategy

Sequel follows **Semantic Versioning (SemVer)**:
- `versionCode` (Integer): Monotonically increasing counter incremented on every release (e.g. `1`, `2`, `3`).
- `versionName` (String): `MAJOR.MINOR.PATCH` (e.g. `1.0.0`).
  - `MAJOR`: Fundamental architectural overhaul or breaking schema changes.
  - `MINOR`: New user-facing features (e.g. added new importer, social sharing).
  - `PATCH`: Bug fixes, sync optimizations, or performance patches.

---

## 6. Pre-Flight Quality Assurance Checklist

Before promoting any build to production:
- [ ] **Offline Flight Mode Test**: Mark 5 episodes watched and 1 review while in airplane mode. Verify UI renders instantly from Room without error dialogs.
- [ ] **Reconnection Test**: Re-enable network. Verify WorkManager triggers `SyncWatchedWorker` and updates `sync_status` to `SYNCED`.
- [ ] **Account Deletion Test**: In Settings, verify the account deletion flow securely deletes cloud data and signs out.
- [ ] **Deep Link Recovery Test**: Send test password reset link and verify deep link opens `SetNewPasswordScreen`.
- [ ] **Database Migration Test**: Install previous version APK, upgrade to release build, and verify Room migrations execute without `IllegalStateException`.
