# Sequel — Security, Privacy & Data Governance

## 1. Security Architecture & Core Tenets

Sequel is architected to provide an uncompromising standard of user privacy:
- **Zero Third-Party Ad Trackers**: Sequel contains no advertising SDKs, behavioral analytics trackers, or commercial data brokers (no Facebook SDK, no Google AdMob, no AppsFlyer).
- **Zero Third-Party Telemetry**: Crash reports and performance metrics are not transmitted to third parties without explicit user consent.
- **Minimal Android Permissions**: Requests only `INTERNET` and `ACCESS_NETWORK_STATE`. No location, camera, microphone, contacts, or phone state permissions are requested.
- **Strict Row-Level Security (RLS)**: Cloud data isolation is guaranteed by PostgreSQL kernel policies, making data access impossible for unauthorized tenants.

---

## 2. Data Classification Matrix

| Data Category | Examples | Storage Location | Protection Mechanism | Retention Period |
| ------------- | -------- | ---------------- | -------------------- | ---------------- |
| **Authentication Credentials** | Email, bcrypt password hash, session JWT | Supabase Auth (GoTrue) & Android DataStore | Transmitted over TLS 1.3; hashed with bcrypt | Until account deletion |
| **User Library & Tracking History** | Watched episodes, watch dates, ratings, personal reviews | Local Room SQLite DB & Supabase PostgreSQL | Protected by PostgreSQL RLS (`auth.uid() = user_id`) | Until account deletion |
| **Cached Media Metadata** | Show titles, overviews, poster image paths | Local Room SQLite DB | Ephemeral public cache (TMDB data) | Evicted on cache refresh or app clear |
| **Device & Network Diagnostics** | Connectivity state (WiFi / Cellular) | In-memory only (NetworkCallback) | Never persisted or transmitted | Transient runtime only |

---

## 3. Cloud Security & Supabase Row-Level Security (RLS)

### 3.1 Why the Supabase Anon Key is Safe
Sequel includes `SUPABASE_ANON_KEY` in the compiled application binary. In traditional backends, exposing API keys is a vulnerability. In Supabase, the **Anon Key** is designed to be public because data access is strictly governed by **Row-Level Security (RLS)** policies inside PostgreSQL.

Every request made by the client passes an authenticated JWT containing the user's UUID (`auth.uid()`). The PostgreSQL engine automatically checks this identity before returning or modifying any row:

```sql
-- Example RLS Policy for Watched Episodes:
CREATE POLICY "Users can manage their watched episodes"
ON public.watched_episodes
FOR ALL
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);
```

Even if a malicious user inspects network traffic or extracts the anon key, they cannot read, alter, or delete another user's tracking data.

---

## 4. Local Android Hardening

### 4.1 Backup Prevention (`android:allowBackup="false"`)
In `AndroidManifest.xml`, `android:allowBackup` is explicitly disabled:
```xml
<application
    android:name=".SequelApplication"
    android:allowBackup="false" ...>
```
This prevents attackers with physical access to an unlocked device from executing `adb backup` to extract the unencrypted SQLite database containing the user's local watch history and tokens.

### 4.2 ProGuard & R8 Obfuscation
Release binaries are shrunk and obfuscated using R8, stripping source line numbers, remapping class identifiers, and dead-code-stripping unused networking paths.

### 4.3 Deep Link Sanitization
Deep link intents handled in `MainActivity.kt` (`sequel://login-callback`) validate callback parameters and ensure tokens are routed directly to the Supabase authentication handler without exposing raw tokens in logs or passing them to insecure web views.

---

## 5. GDPR & CCPA Compliance: Right to Erasure

Under Article 17 of the GDPR and California Consumer Privacy Act (CCPA), users have the "Right to Erasure" (the right to be forgotten).

### 5.1 Account Deletion Protocol
Sequel implements a one-tap account deletion flow in the Settings screen:
1. **Cloud Deletion**: Invokes Supabase RPC or auth API to delete the user record in `auth.users`.
2. **Cascading SQL Truncation**: All foreign keys in `user_watchlist`, `watched_movies`, `watched_episodes`, and `reviews` are configured with `ON DELETE CASCADE`. Deleting the user automatically and permanently erases all associated rows from the database.
3. **Local Database Wipe**: Clears the local Room database (`db.clearAllTables()`) and deletes saved DataStore session preferences.
4. **Immediate Logout**: Returns the user to the initial login screen.
