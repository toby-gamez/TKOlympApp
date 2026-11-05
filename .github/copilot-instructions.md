<!-- Copilot instructions for TKOlympApp -->

# Quick AI guidance for contributors

Purpose: help an AI coding agent become productive quickly in this Kotlin Multiplatform + Compose project.

Big picture
- Kotlin Multiplatform project with three logical areas: the Android/Compose app (`composeApp`), shared Kotlin modules (`shared`), and iOS host apps (`iosApp`).
- Shared code lives under `shared/src/commonMain/kotlin` and is consumed by `composeApp` and the iOS frameworks.
- UI is Compose Multiplatform inside `composeApp/src/commonMain/kotlin` and platform-specific UI or bootstrapping exists in `iosApp/iosApp` (Swift/SwiftUI) and Android-specific folders.

Build & run (common tasks)
- Run full build: `./gradlew build`
- Build Android debug APK: `./gradlew :composeApp:assembleDebug` (described in top-level `README.md`).
- Open `iosApp` in Xcode to run iOS; the Kotlin framework is produced by the `shared` module as an iOS framework.

Project-specific conventions
- Follows Kotlin MPP conventions: source sets named `commonMain`, `androidMain`, `iosMain` under each module (see `composeApp/src` and `shared/src`).
- Dependencies are referenced via the version/catalog aliases in `gradle/libs.versions.toml` (see `composeApp/build.gradle.kts` and `shared/build.gradle.kts`).
- The `composeApp` module depends on the `shared` module via `implementation(projects.shared)` inside `commonMain`.

Where to look first
- `composeApp/build.gradle.kts` — Compose Multiplatform + Android app configuration and example dependencies.
- `shared/build.gradle.kts` — multiplatform targets (Android + iOS frameworks) and source-set layout.
- `gradle/libs.versions.toml` — dependency aliases used across modules.
- `README.md` — quick how-to for Android and iOS run workflows.

Patterns & examples to follow
- Add cross-platform logic in `shared/src/commonMain/kotlin` and platform-specific hooks in `shared/src/androidMain` or `shared/src/iosMain`.
- UI components go into `composeApp/src/commonMain/kotlin` if they target all Compose platforms; otherwise place platform code in `androidMain` or `iosMain`.
- Use existing dependency aliases (do not hardcode version numbers); add aliases to `gradle/libs.versions.toml` if needed.

Important rule for services and shared code
- Follow existing project structure for services: put service implementations under `shared/src/commonMain/kotlin/com/tkolymp/shared/...` in the appropriate subpackage (e.g. `shared.people`, `shared.event`, `shared.auth`).
- Do NOT create ad-hoc or "special" service implementations in other packages (for example `com.tkolymp.tkolympapp`) — match where other services live so platform initializers and `ServiceLocator` can find them.

Tests & CI notes
- Unit tests live in `*/src/commonTest` and module-specific test source sets. Run `./gradlew test` for JVM targets; platform-specific test tasks may vary.
- The project includes Gradle configuration reports (see `build/reports/`) — helpful when diagnosing configuration-cache and plugin compatibility issues.

Known quirks / gotchas
- This repo uses Kotlin MPP + Android Gradle Plugin; some older AGP usages can show deprecation/misconfiguration warnings (see `build/reports/problems/`).
- The `shared` module produces a static iOS framework (see `shared/build.gradle.kts` ios binaries configuration).
- Local secrets / SDK paths may be in `local.properties` — avoid committing secrets.

How to make small changes
- For dependency updates: add an entry in `gradle/libs.versions.toml`, then reference it via `libs.<alias>` in the module `build.gradle.kts`.
- To add a new cross-platform API: create Kotlin files under `shared/src/commonMain/kotlin`, update `composeApp` usage via `implementation(projects.shared)`.

If unsure, inspect these files in order: `README.md`, `composeApp/build.gradle.kts`, `shared/build.gradle.kts`, `gradle/libs.versions.toml`, `iosApp/iosApp`.

! All the logic for the app needs to be in the folders for iOS and android, not only in android screens. Your goal is: the screens must be as small as possible, all logic is cross-platform!

Ask the human: if you need to run device/emulator builds or Xcode tasks, request explicit permission and environment details (installed SDKs, connected devices).

-- End of file --
