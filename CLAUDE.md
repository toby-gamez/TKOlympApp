# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Debug APK (output: androidApp/build/outputs/apk/debug/)
./gradlew :androidApp:assembleDebug

# Install on connected device/emulator
adb install androidApp/build/outputs/apk/debug/androidApp-debug.apk

# Release APK
./gradlew :androidApp:assembleRelease

# iOS framework (then open iosApp/iosApp.xcodeproj in Xcode)
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64

# Run unit tests
./gradlew :composeApp:testDebugUnitTest
./gradlew :shared:jvmTest
```

## Local Configuration

`local.properties` (git-ignored) must contain:
```
api.base.url=https://api.rozpisovnik.cz/graphql
tenant.id=<club-tenant-id>
```
These are injected as `BuildConfig.API_BASE_URL` and `BuildConfig.TENANT_ID` at compile time.

## Architecture

Two Gradle modules:

### `shared` — KMM business logic (`commonMain`)
All logic shared between Android and iOS lives here. Never import Android/UI framework classes.

- **`ServiceLocator`** — read-only singleton facade; call `ServiceLocator.init(container)` exactly once (done in `PlatformNetwork.kt`). Access services via `ServiceLocator.eventService`, etc.
- **`AppContainer`** — holds all service instances; constructed in `shared/src/androidMain/kotlin/com/tkolymp/shared/PlatformNetwork.kt` via `initNetworking()`.
- **Services** — one interface + one impl per domain (`IAuthService`/`AuthService`, `IEventService`/`EventService`, etc.). All GraphQL calls go through `GraphQlClientImpl` (Ktor + OkHttp with certificate pinning to `api.rozpisovnik.cz`).
- **ViewModels** — one per screen (`CalendarViewModel`, `EventsViewModel`, …), each implementing `ViewModelState` (has `isLoading: Boolean` and `error: String?`). Shared between Android and iOS.
- **`CacheService`** — in-memory LRU (max 200 entries, default 5-minute TTL). Services call `cache.get(key)` / `cache.put(key, value, ttl)` and invalidate by key or prefix on mutations.
- **Storage** — `TokenStorage`, `UserStorage`, `OnboardingStorage`, `LanguageStorage`, `CalendarPreferenceStorage`, `OfflineDataStorage`, `NotificationStorage`. Android implementations use the `ksafe` library.
- **Localization** — `AppStrings.current.*` provides all UI strings; `AppStrings.setLanguage(AppLanguage.XX)` switches at runtime and emits to `languageFlow` (triggers a `Crossfade` in `App.kt`). Add new strings to `Strings.kt` and all translation objects in `shared/src/commonMain/kotlin/com/tkolymp/shared/language/translations/` (CS, DE, SK, SL, UA, VI, EN, BRAINROT).
- **Calendar collision algorithm** — `CollisionDetectionAlgorithm` in `calendar/` assigns column positions to overlapping events (similar to Google Calendar).
- **Offline sync** — `OfflineSyncManager` calls `downloadAll()` on startup and after login when network is available.

### `composeApp` — Compose Multiplatform UI
Screens are kept thin; all logic lives in `shared`. Android-only code goes in `androidMain`.

- **Navigation** — `AppNavHost` in `composeApp/src/androidMain/kotlin/com/tkolymp/tkolympapp/App.kt` uses Jetpack Navigation Compose with string-based routes (`"event/{eventId}"`, `"person/{personId}"`, etc.). The bottom bar is visible only on the five main tabs: `overview`, `calendar`/`timeline`, `board`, `events`, `other`.
- **Theme** — `ui/theme/Color.kt` + `ui/theme/Theme.kt`. Always use `MaterialTheme` tokens; do not hard-code colors.
- **Screens** — `composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/`
- **Reusable components** — `composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/components/`
- **Platform implementations** — `composeApp/src/androidMain/kotlin/com/tkolymp/tkolympapp/platform/` (e.g., `HtmlText.kt`, `ShareUtils.android.kt`)

## Key Conventions

- Business logic belongs in `shared/src/commonMain` — screens should have no logic.
- New services go under `com.tkolymp.shared.<domain>/` with an interface and a `commonMain` impl.
- Platform-specific code in `androidMain` / `iosMain`.
- Never hard-code dependency versions — always use aliases from `gradle/libs.versions.toml`.
- Reusable composables accept `modifier: Modifier = Modifier` and apply it first; modifier order: size → padding → background → clickable → semantics.
- Stateless composables preferred: accept state and event lambdas, lift state to ViewModels.
- Touch targets ≥ 48 dp; provide `contentDescription` for non-text interactive elements.
- Integrity check (`IntegrityServiceAndroid`) is skipped in debug builds; release builds validate APK signing.
