# CLAUDE.md

## Governance: Taskino Is the System of Record

Web UI: <http://localhost:8081>. The MCP server is registered in `.mcp.json`, and every tool
defaults to the project `TKOlympApp`. Tool reference:
[/home/tobias/Projects/external/taskino/mcp/docs/agent-guide.md](/home/tobias/Projects/external/taskino/mcp/docs/agent-guide.md).

- **Preflight first,** before any work that creates, changes, or completes something in this
  repo: make a real `list_tasks` call and check that it returns. Seeing the tools listed doesn't
  prove they work. Answering questions or reading code needs no check.
- **If the call fails:** say that Taskino is unreachable, then **stop**. No fallback task file, no
  TODO list, and no carrying on with the plan to file tasks later. **Never start, restart, or
  rebuild Taskino or the MCP build** (systemd user service `taskino`, or
  `PORT=8081 NODE_ENV=production mise exec node@22 -- node server/dist/server.js` in
  `~/Projects/external/taskino`; MCP is the local `node` build at `mcp/dist/index.js`). They belong to the owner. Only the
  owner can override this, explicitly, and only for that conversation.
- **Work from tasks, not from prompts.** When the owner asks for work in a prompt, find the
  matching Taskino task or create one first, then work from it. Put scope, progress and results
  in the task, not only in the chat. Skip this only when the owner explicitly says to work
  without a task.
- **Code modification guard:** change code only when a Taskino task exists in `TKOlympApp`
  and the change fits its scope. No task: propose one instead of changing code. Authorized but
  not filed: create the task first.
- Before starting, check whether the task is already partly or fully built. If it's done, say so
  and stop. If it's partial, build only what's missing. Set the task `in_progress` when you
  start, and log progress with `add_note`.
- **Finishing:** run `./gradlew :shared:testAndroidHostTest` for shared logic and `./gradlew :androidApp:assembleDebug` for UI/app changes (add `:androidApp:lintDebug` when touching resources/manifest). Then add a note saying what changed
  and how it was verified, and set the task to `verification`. **Never set `done` (or
  `isDone`) without the user's explicit confirmation.**
- **Channels are not interchangeable:**
  - The **Developer Journal** is lasting engineering context. Write it once the work is verified.
  - `add_note` is progress on one task.
  - **Documents** (HTML) are long-form docs and plans.
- **Identity:** you write as the agent account `claude`. Its key reaches the MCP server
  through `TASKINO_API_KEY` in the gitignored `.claude/settings.local.json`. If your notes show
  **Guest**, the key isn't loaded, so tell the owner. Never copy the key into a tracked file.
- Never link a task to a version unless the owner names one.
- **Roadmap:** what's next lives in Taskino. Read the open tasks in `TKOlympApp`, and don't
  keep a roadmap in this file.


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

# Run shared module unit tests (commonTest, run on the JVM against the android target)
./gradlew :shared:testAndroidHostTest

# Run a single test class/method
./gradlew :shared:testAndroidHostTest --tests "com.tkolymp.tkolympapp.CacheServiceTest"

# Run shared tests across all targets (android host + iOS simulator; iOS leg requires macOS/Xcode)
./gradlew :shared:allTests

# Lint
./gradlew :androidApp:lintDebug
```

There is no `shared:jvmTest` task — `shared` targets `android` + `iosArm64`/`iosSimulatorArm64` only, no plain JVM target. Tests live in `shared/src/commonTest`; `composeApp` has no wired-up unit test task.

## Local Configuration

`local.properties` (git-ignored) must contain:
```
api.base.url=https://tkolymp.cz/graphql
tenant.id=<club-tenant-id>
```
These are injected as `BuildConfig.API_BASE_URL` and `BuildConfig.TENANT_ID` at compile time.

To produce a signed `:androidApp:assembleRelease`/`bundleRelease` artifact, also add:
```
release.keystore.path=/absolute/path/to/upload-keystore.jks
release.keystore.password=<keystore password>
release.key.alias=<key alias>
release.key.password=<key password>
```
Without these, release builds still compile (minified/shrunk) but are unsigned. Never commit the `.jks` file itself (`*.jks`/`*.keystore` are git-ignored).

## Architecture

Four Gradle modules. `androidApp` is the installable Android application shell (applicationId, version, signing, `BuildConfig` fields); `appRes` holds shared Android resources (icons, strings XML, manifest bits) consumed by `androidApp`; `composeApp` and `shared` are KMM library modules also built for iOS and consumed by `iosApp`'s Xcode project.

### `shared` — KMM business logic (`commonMain`)
All logic shared between Android and iOS lives here. Never import Android/UI framework classes.

- **`ServiceLocator`** — read-only singleton facade; call `ServiceLocator.init(container)` exactly once (done inside each platform's `initNetworking()`). Access services via `ServiceLocator.eventService`, etc.
- **`AppContainer`** — holds all service instances; constructed by `initNetworking()`, once per platform: `shared/src/androidMain/.../PlatformNetwork.kt` (Android, OkHttp engine + certificate pinning) and `shared/src/iosMain/.../PlatformNetwork.ios.kt` (iOS, Darwin engine).
- **Services** — one interface + one impl per domain (`IAuthService`/`AuthService`, `IEventService`/`EventService`, etc.), under `event/`, `people/`, `club/`, `announcements/`, `payments/`, `registration/`, `competitions/`, `personalevents/`, `notification/`, `achievements/`, `campschedule/`, `feedback/`, `systemcalendar/`, `user/`, plus cross-cutting singletons in `appearance/`, `device/`, `tutorial/`. All GraphQL calls go through `GraphQlClientImpl` (Ktor); the Android engine is OkHttp with certificate pinning to `tkolymp.cz`, iOS uses Darwin.
- **ViewModels** — one per screen (`CalendarViewModel`, `EventsViewModel`, …), each implementing `ViewModelState` (has `isLoading: Boolean` and `error: String?`). Shared between Android and iOS.
- **`CacheService`** — in-memory LRU (max 200 entries, default 5-minute TTL). Services call `cache.get(key)` / `cache.put(key, value, ttl)` and invalidate by key or prefix on mutations.
- **Storage** — `TokenStorage`, `UserStorage`, `OnboardingStorage`, `LanguageStorage`, `CalendarPreferenceStorage`, `OfflineDataStorage`, `NotificationStorage`. Android implementations use the `ksafe` library.
- **Localization** — `AppStrings.current.*` provides all UI strings; `AppStrings.setLanguage(AppLanguage.XX)` switches at runtime and emits to `languageFlow` (triggers a `Crossfade` in `AppContent.kt`). Add new strings to `Strings.kt` and all translation objects in `shared/src/commonMain/kotlin/com/tkolymp/shared/language/translations/` (CS, DE, SK, SL, UA, VI, EN, BRAINROT).
- **Calendar collision algorithm** — `CollisionDetectionAlgorithm` in `calendar/` assigns column positions to overlapping events (similar to Google Calendar).
- **Offline sync** — `sync/OfflineSyncManager` calls `downloadAll()` on startup and after login when network is available.
- **Error reporting** — `errorreporting/ErrorReporter` auto-submits handled and fatal errors as bug reports through `IFeedbackService` (deduped per session, fire-and-forget); it is independent of the Firebase Crashlytics/Analytics wiring in `composeApp`/`androidApp`, which handles native crash telemetry separately.

### `composeApp` — Compose Multiplatform UI
Targets both `android` and `iosArm64`/`iosSimulatorArm64`. Screens are kept thin; all logic lives in `shared`. Platform-only code goes in `androidMain`/`iosMain`.

- **Navigation** — `AppContent`/`AppNavHost` live in `composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/AppContent.kt` (shared by both platforms) and use Jetpack Navigation Compose with string-based routes (`"event/{eventId}"`, `"person/{personId}"`, etc.). Each platform has only a thin entry point: `androidMain/.../App.kt` (delegates to `AppContent`) and `iosMain/.../App.ios.kt` + `MainViewController.kt`. The bottom bar is visible only on five routes: `overview`, `calendar`, `board`, `events`, `other` (calendar vs. timeline is a view-mode toggle within the `calendar` route, not a separate tab).
- **Theme** — `ui/theme/Color.kt` + `ui/theme/Theme.kt`. Always use `MaterialTheme` tokens; do not hard-code colors or numeric sizes.
- **Screens** — `composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/`
- **Reusable components** — `composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/components/`
- **Platform implementations** — declared as `expect` in `commonMain/.../platform/`, with `androidMain`/`iosMain` `actual`s (e.g., `HtmlText`, `AppLogo`, `ShareUtils`, `FullscreenImageViewer`, `NotificationFileButtons`). Firebase Cloud Messaging, barcode scanning, and home-screen widgets remain Android-only.
- **Home-screen widgets (Android-only)** — `androidMain/.../widget/` (`MyEventsWidget`, `MyNearestEventWidget`, `BirthdaysWidget`, `NextCompetitionWidget`, `ToolboxWidget`) are Glance app widgets; `WidgetUpdateWorker` (WorkManager, 30-min periodic) refreshes them via `WidgetDataProvider`.
- **Camp schedule OCR (Android-only)** — `androidMain/.../campschedule/` (`GridDetector`, `CellOcr`) uses OpenCV to find the schedule grid in a photo and ML Kit text recognition to read cells; parsed results feed the shared `campschedule/CampScheduleService`.

## Key Conventions

- Business logic belongs in `shared/src/commonMain` — screens should have no logic.
- New services go under `com.tkolymp.shared.<domain>/` with an interface and a `commonMain` impl.
- Platform-specific code in `androidMain` / `iosMain`.
- Never hard-code dependency versions — always use aliases from `gradle/libs.versions.toml`.
- Reusable composables accept `modifier: Modifier = Modifier` and apply it first; modifier order: size → padding → background → clickable → semantics.
- Stateless composables preferred: accept state and event lambdas, lift state to ViewModels.
- Touch targets ≥ 48 dp; provide `contentDescription` for non-text interactive elements.
- New/changed composables should have a `@Preview` covering light and dark theme (see `docs/COMPOSABLES_PR_CHECKLIST.md`).
