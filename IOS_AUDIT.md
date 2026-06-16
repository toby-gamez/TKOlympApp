# iOS Readiness Audit

Date: 2026-06-16

## TL;DR

The `shared` module is in good shape — it already targets `iosArm64`/`iosSimulatorArm64`, has proper `expect`/`actual` for most platform concerns, and all business logic/ViewModels live in `commonMain`. **`composeApp` does not target iOS at all yet.** The biggest blocker isn't "shared vs. UI code" — it's that the app's entire navigation shell (`App.kt`, 1230 lines) and bootstrap (`initNetworking`) only exist in `androidMain`, and a few platform composables have no iOS `actual`. Closing those gaps is mostly mechanical (a few days), not a redesign.

Given you'll use native SwiftUI for some screens (liquid glass), the right target architecture is: **shared KMP module for all logic + most UI in Compose Multiplatform, with specific screens swapped for native SwiftUI via a thin interop seam** — not "two separate apps."

---

## 1. What's already shared (good news)

- All ViewModels (`EventsViewModel`, `LoginViewModel`, `GroupsViewModel`, etc.) live in `shared/commonMain` and have zero Android imports.
- All services (`EventService`, `AuthService`, `PaymentService`, ...) and `GraphQlClientImpl` are common.
- `CacheService`, GraphQL models, localization (`AppStrings`), `CollisionDetectionAlgorithm`, `OfflineSyncManager` — all common.
- Storage already has the full `expect`/`actual` split with iOS implementations present: `TokenStorage`, `UserStorage`, `OnboardingStorage`, `LanguageStorage`, `CalendarPreferenceStorage`, `AnnouncementBadgeStorage`, `NotificationStorage`, `SystemCalendarService`, `DeviceLanguage`, `Logger`, `IntegrityService`.
- `composeApp/commonMain` (55 files: screens + components) has **no leaked `android.*` imports** — it was written cleanly against Compose Multiplatform APIs.
- Platform composables that do need a native implementation (`HtmlText`, `AppLogo`, `FullscreenImageViewer`, `NotificationFileButtons`, `ShareUtils`) are already declared as `expect` in `commonMain` — only the Android `actual` exists today, but the seam is already there.

This means most of the "make it shared" work is already done. The gaps below are what's left.

---

## 2. Gaps that block an iOS build today

### 2.1 `composeApp` has no iOS target (build-config level)
`composeApp/build.gradle.kts` only declares `androidTarget()`. There is no `iosArm64()`/`iosSimulatorArm64()` block, no `iosMain` source set, and no framework export — unlike `shared/build.gradle.kts`, which already has this. Nothing in `composeApp` can run on iOS until this is added.

### 2.2 App shell only exists for Android
`composeApp/androidMain/.../App.kt` (1230 lines) contains `AppNavHost`, all navigation routes, and top-level scaffolding (bottom bar, etc). `MainActivity.kt` wires this to Android's `Activity`. There is no common or iOS equivalent — this is the single biggest item, not because it's hard, but because it's large and currently 100% Android.

### 2.3 No iOS bootstrap / DI entry point
`shared/androidMain/PlatformNetwork.kt`'s `initNetworking(context, baseUrl, tenantId)` builds the Ktor `HttpClient` (OkHttp engine), constructs every service, and calls `ServiceLocator.init(container)`. There is **no iOS counterpart** — `ServiceLocator.init` is never called from `iosMain`, so today the iOS framework target builds (since `shared` compiles) but would crash at runtime the moment any screen touches `ServiceLocator`.

### 2.4 Platform service `actual`s missing for iOS
These have an `interface`/`expect` in `commonMain` and an Android implementation, but **no iOS implementation**:
- `NetworkMonitor` (`NetworkMonitorAndroid` only)
- `OfflineDataStorage` (`OfflineDataStorageAndroid` only)
- `INotificationScheduler` (`NotificationSchedulerAndroid` only — needs a `UNUserNotificationCenter`-backed iOS implementation)
- `HtmlFormatter` — has `expect` in common; confirm an iOS `actual` exists (saw only `.android.kt` for it explicitly, double check before iOS build)

### 2.5 `composeApp` platform composables missing iOS `actual`
- `HtmlText`, `AppLogo`, `FullscreenImageViewer`, `NotificationFileButtons`, `ShareUtils` — all `expect` in `commonMain`, Android `actual` only.
- `BarcodeScreen.kt` lives directly in `androidMain` (not even behind an `expect`) and is referenced from `App.kt`. Needs either a common interface + iOS actual, or to be excluded from the iOS nav graph if barcode scanning isn't an iOS requirement.

### 2.6 Notifications
Firebase Cloud Messaging (`FirebaseMessagingService.kt`, `AndroidTopicManager.kt`) is Android-only by nature — iOS needs APNs + a Swift `AppDelegate` hook, with the registration/token logic exposed through a small common interface the same way `NotificationStorage`/`INotificationScheduler` already are. This is unavoidable platform-specific code, not a sharing gap — budget for it but don't try to "share" it.

### 2.7 `ksafe` storage library
All storage `actual`s for Android use `ksafe`; iOS `actual`s already exist for storage (`TokenStorage.ios.kt` etc.) — check what backing store those use (likely raw `NSUserDefaults`/Keychain) and confirm parity with Android's behavior (e.g., is the token actually in Keychain, not just `UserDefaults`?). Worth a quick read-through since this is auth-token storage.

---

## 3. Where native SwiftUI (liquid glass) fits in

Compose Multiplatform for iOS renders through its own Skia-based canvas — it does **not** use UIKit/SwiftUI components, so you cannot drop a `UIVisualEffectView`/`.glassEffect()` into a Compose tree directly. For screens where you want genuine native liquid glass, you have two clean options, both compatible with the architecture above:

1. **Whole-screen native swap**: keep that one screen as SwiftUI, navigated to from the iOS host app (`iosApp/iosApp`), and have it call into `shared`'s services/ViewModels directly via the generated Obj-C/Swift interop (`Shared.framework`). This is the simplest and most robust — no interop layer inside Compose, just shared business logic underneath a native screen.
2. **UIKit/SwiftUI view embedded in Compose** via `UIKitView`/`UIKitViewController` interop (Compose Multiplatform supports this on iOS). Use this only for small native chrome (e.g., a glass tab bar or a card) inside an otherwise-Compose screen, since this interop has known limitations with gestures/scrolling.

Recommendation: pick the screens that most benefit from liquid glass (likely things like the tab bar / now-playing-style overlays / sheets) and make those whole native SwiftUI screens reading from shared ViewModels. Keep the bulk of the app (forms, lists, calendar) in Compose Multiplatform — rewriting all ~30 screens in SwiftUI would defeat the purpose of having a KMP shared module at all.

---

## 4. Suggested order of work (before/during iOS week)

1. **Add iOS targets to `composeApp/build.gradle.kts`** (`iosArm64()`, `iosSimulatorArm64()`, framework block mirroring `shared`'s). Confirms how much of `commonMain` compiles as-is.
2. **Write the iOS `actual`s for the 3-4 missing common interfaces**: `NetworkMonitor`, `OfflineDataStorage`, `INotificationScheduler`, confirm `HtmlFormatter`. These are small, mechanical, and unblock everything else.
3. **Write `initNetworking`-equivalent for iOS** (`shared/iosMain/PlatformNetwork.ios.kt`): swap the OkHttp engine for `Darwin`, drop the `Context` parameter, otherwise mirror the Android version almost line for line.
4. **Extract `AppNavHost`/`App.kt` into `composeApp/commonMain`**, replacing any Android-only calls inside it (check for `Context`, `Activity`, intents) with the existing `expect` composables. This is the biggest single chunk of work but it's a move + cleanup, not new design.
5. **Add `composeApp/iosMain` `actual`s** for `HtmlText`, `AppLogo`, `FullscreenImageViewer`, `NotificationFileButtons`, `ShareUtils`, and decide what happens to `BarcodeScreen` on iOS (skip route, or implement with `AVFoundation`).
6. **Wire `iosApp/iosApp/iOSApp.swift`** to call `initNetworking()` on launch and host the Compose root (`ComposeUIViewController`), same role as `MainActivity.kt` today.
7. Only after the above builds and runs: carve out the specific screens you want as native SwiftUI/liquid-glass and wire them to the relevant shared ViewModel via the Swift interop.

Steps 2-3 are a day or so each; step 4 is the bulk of the remaining effort but is mechanical extraction, not redesign, since the screens it routes to are already common.
