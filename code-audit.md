# Code Audit — TKOlympApp

**Date:** April 25, 2026  
**Reviewer:** GitHub Copilot (Claude Sonnet 4.6)  
**Scope:** Full codebase review (Kotlin Multiplatform / Compose, Android, shared logic, tooling)

---

## Summary

| Severity | Count |
|---|---|
| High | 5 |
| Medium | 8 |
| Low | 6 |

---

## Medium

---

### 10. N+1 API calls during offline sync and registration name loading

- **Files:** `shared/src/commonMain/.../sync/OfflineSyncManager.kt` `syncPeople()` (~line 240); `shared/src/commonMain/.../viewmodels/RegistrationViewModel.kt` `loadNames()`; `OfflineSyncManager.kt` event detail loop
- **What:** `syncPeople()` calls `peopleService.fetchPerson(p.id)` individually for every person (with up to 3 retries each). With 100 members that is up to 300 sequential network requests. `loadNames()` does the same per trainer and per registration. The calendar sync also fetches each event detail individually.
- **Fix:** Add a batch-fetch GraphQL query that accepts a list of IDs and returns all results in one round trip.

---

### 11. `CacheService` has no memory bound

- **File:** `shared/src/commonMain/.../cache/CacheService.kt`
- **What:** The in-memory cache `mutableMapOf<String, CacheEntry<*>>()` grows unboundedly. Storing full event lists, people lists, and payment data per cache key for multiple weeks and buckets simultaneously creates GC pressure and risks OOM on low-memory devices.
- **Fix:** Add a max-entries cap (e.g., 200) with LRU eviction, or back large entries with `SoftReference`.

---

### 12. Hardcoded Czech fallback error strings bypass localization

- **Files:** `LoginViewModel.kt` line 57; `ProfileViewModel.kt` line 64; `RegistrationViewModel.kt` line 75; multiple other ViewModels
- **What:** `error = ex.message ?: "Chyba při přihlášení"`, `"Chyba při načítání profilu"`, `"Chyba při načítání jmen"` etc. are hardcoded Czech strings used as `?: ` fallbacks throughout ViewModels. If `AppStrings` supports another language, these fallbacks break the language setting.
- **Fix:** Add a generic error key to `AppStrings` (e.g., `AppStrings.current.errorMessages.generic`) and replace every hardcoded Czech fallback string.

---

### 13. `isUnpaid` parsed as string instead of as a JSON boolean

- **File:** `shared/src/commonMain/.../payments/PaymentService.kt` line 101
- **What:** `obj["isUnpaid"]?.jsonPrimitive?.contentOrNull?.let { it == "true" }` converts a JSON boolean to a string then string-compares. This is fragile — `contentOrNull` on a boolean `JsonPrimitive` does return `"true"`/`"false"` currently, but this is an implementation detail. Idiomatic usage is `jsonPrimitive.booleanOrNull`.
- **Fix:** `obj["isUnpaid"]?.jsonPrimitive?.booleanOrNull`

---

### 14. Cache validity checked via raw JSON substring search

- **File:** `shared/src/commonMain/.../viewmodels/ProfileViewModel.kt` lines 47–51
- **What:** `cachedPersonJson.contains("activeCouplesList") && cachedPersonJson.contains("cohortMembershipsList") && (cachedPersonJson.contains("email") || cachedPersonJson.contains("uEmail"))` — presence of fields is verified by substring search on raw JSON. A JSON string value containing the word "email" (e.g., in a biography) would falsely satisfy the check.
- **Fix:** Parse the JSON and check for non-null fields, or use a schema version stamp / cache timestamp to determine staleness.

---

### 15. `PersonalEventService.save()` and `delete()` have a race condition

- **File:** `shared/src/commonMain/.../personalevents/PersonalEventService.kt`
- **What:** Both `save()` and `delete()` perform a read-modify-write on the stored JSON list without a mutex. Two concurrent invocations (e.g., a recurrence reschedule triggered alongside a user edit) can produce a lost update: the second writer overwrites with the version it read before the first writer committed.
- **Fix:** Add a `Mutex` at the service level, matching the pattern already used in `LoginViewModel` and `UserService`.

---

### 16. Hash collision in personal event occurrence IDs

- **Files:** `shared/src/commonMain/.../viewmodels/CalendarViewModel.kt` line 280; `CalendarViewViewModel.kt` line 455
- **What:** `(ev.id + date.toString()).hashCode().toLong()` — `hashCode()` returns `Int` (32-bit), widened to `Long`. With many recurring events across many dates, hash collisions are statistically certain. Colliding IDs break deduplication in `distinctBy` and `EventInstance` equality checks.
- **Fix:** Use a stable composite string key: `"${ev.id}_${date}".hashCode().toLong()` with an XOR shift to spread bits, or use a proper 64-bit hash. Better: store occurrence IDs as strings in the data model and avoid numeric IDs for synthetic occurrences altogether.

---

### 17. `ServiceLocator.init()` allows silent double-initialization

- **File:** `shared/src/commonMain/.../ServiceLocator.kt` line 40
- **What:** `fun init(container: AppContainer) { _container = container }` can be called multiple times. On the second call, the old container (including the open Ktor `HttpClient` and its OkHttp connection pool) is silently discarded without being closed, leaking connections and threads.
- **Fix:**
  ```kotlin
  fun init(container: AppContainer) {
      check(_container == null) { "ServiceLocator already initialized" }
      _container = container
  }
  ```

---

### 18. `PaymentsScreen` never loads data on first composition

- **File:** `composeApp/src/commonMain/.../screens/PaymentsScreen.kt`
- **What:** `PaymentsViewModel` is created with `remember {}` (bypasses `ViewModel` config-change survival). More critically, there is no `LaunchedEffect` to trigger `vm.load()` on first composition. The screen displays an empty state indefinitely until the user manually swipe-to-refreshes.
- **Fix:** Add `LaunchedEffect(Unit) { vm.load() }` and switch from `remember { PaymentsViewModel() }` to `viewModel<PaymentsViewModel>()`.

---

## Low

---

### 19. Offline data stored in `SharedPreferences` — wrong API for large blobs

- **File:** `shared/src/androidMain/.../storage/OfflineDataStorage.android.kt`
- **What:** `SharedPreferences` is XML-backed and loads its entire file into memory on first access. Storing full JSON for multiple weeks of calendar data, all club members, and payments in one preference file will be slow to parse on low-end devices and may hit Android's practical XML size limit on large clubs.
- **Fix:** Replace with `Room` (SQLite) or write each key to a separate file in the app's internal storage (`Context.filesDir`).

---

### 20. `fetch_cohorts()` in `send_push.py` has no error handling

- **File:** `tools/send_push.py` lines 35–51
- **What:** The GraphQL response is accessed directly as `data["data"]["cohortsList"]` with no check for an `"errors"` key. If the endpoint requires authentication or returns a GraphQL error, this raises a `KeyError` with no useful message.
- **Fix:**
  ```python
  if "errors" in data:
      raise RuntimeError(f"GraphQL errors: {data['errors']}")
  return data["data"]["cohortsList"]
  ```

---

### 21. `syncAll()` has no rate limiting or debounce

- **File:** `shared/src/commonMain/.../sync/OfflineSyncManager.kt` `syncAll()`
- **What:** `syncAll()` performs a full sync (3 buckets × 4 weeks of calendar + people + announcements + club + payments) with no guard against rapid successive calls. Multiple overlapping sync jobs would hammer the API simultaneously.
- **Fix:** Check the saved last-sync timestamp at the start of `syncAll()` and skip if synced within the last N minutes (e.g., 5 minutes).

---

### 22. `Logger.w` and `Logger.e` log in release builds

- **File:** `shared/src/androidMain/.../Logger.kt`
- **What:** Only `Logger.d` is gated by `BuildConfig.DEBUG`. `Logger.w` and `Logger.e` emit on production builds. Given how frequently they are used to log failed API calls (including raw server error bodies), this exposes internal server error messages to logcat on production devices.
- **Fix:** Gate `Logger.w` and `Logger.e` with `BuildConfig.DEBUG` as well, or route them through a crash reporting SDK (e.g., Firebase Crashlytics) instead of logcat in release builds.

---

### 23. FCM token is never sent to the server (incomplete feature)

- **Files:** `composeApp/src/androidMain/.../FirebaseMessagingService.kt` line 24; `composeApp/src/androidMain/.../MainActivity.kt` line 77
- **What:** The FCM token is fetched and logged but never uploaded to the backend. Both locations contain `// TODO: Odeslat token na server`. Without server-side token registration, per-device targeted push notifications are impossible. This is a shipped incomplete feature.
- **Fix:** Implement a GraphQL mutation or REST call to register the token after it is obtained in `onNewToken()` and after login. Store the last-uploaded token in `SharedPreferences` to avoid redundant uploads on every launch.
