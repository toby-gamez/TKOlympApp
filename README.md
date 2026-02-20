# TKOlympApp

Mobilní aplikace pro TK Olymp — tenisový klub. Postavena na **Kotlin Multiplatform** s **Compose Multiplatform** UI, cílí na Android a iOS z jediné sdílené codebase.

---

## Technologie

| Oblast | Technologie |
|---|---|
| Jazyk | Kotlin 2.3.0 |
| UI | Compose Multiplatform 1.10.0 + Material 3 |
| Síť | Ktor 2.3.4 (GraphQL přes OkHttp/Darwin) |
| Serializace | kotlinx-serialization 1.6.0 |
| Datum/čas | kotlinx-datetime 0.6.0 |
| Navigace | Jetpack Navigation Compose |
| Android min SDK | 31 |
| Android target SDK | 36 |

---

## Architektura

Projekt je rozdělen do dvou modulů:

### `shared`
Sdílená business logika — platformově nezávislá. Obsahuje:
- **Services** — `AuthService`, `UserService`, `EventService`, `PeopleService`, `ClubService`, `AnnouncementService`, `NotificationService`
- **ViewModels** — jeden ViewModel na každou obrazovku (sdílený mezi Android a iOS)
- **Network** — `GraphQlClientImpl` (Ktor)
- **Storage** — `TokenStorage`, `UserStorage`, `OnboardingStorage`, `NotificationStorage`
- **ServiceLocator** — DI entry point

### `composeApp`
Compose UI vrstva (aktuálně Android). Obsahuje pouze tenké obrazovky, veškerá logika je v `shared`.

**Obrazovky:**
- Onboarding / Login / Registrace
- Přehled (Overview)
- Události (Events, EventDetail)
- Kalendář + zobrazení kalendáře (kolizní algoritmus)
- Žebříček (Leaderboard)
- Nástěnka (Board)
- Lidé (People, PersonDetail)
- Skupiny (Groups)
- Trenéři – lokace (TrainersLocations)
- Oznámení (Notifications settings)
- Profil
- Ostatní / O aplikaci / Ochrana soukromí

---

## Spuštění

### Android

```bash
./gradlew :composeApp:assembleDebug
```

APK se nachází v `composeApp/build/outputs/apk/debug/`.

Instalace na zařízení/emulátor:

```bash
adb install composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

### iOS

1. Sestavte Kotlin framework:
   ```bash
   ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
   ```
2. Otevřete `iosApp/iosApp.xcodeproj` v Xcode.
3. Spusťte `Run` (⌘R).

---

## Struktura projektu

```
TKOlympApp/
├── composeApp/                  # Compose UI (Android)
│   └── src/
│       ├── androidMain/kotlin/  # Obrazovky, navigace, MainActivity
│       └── commonMain/kotlin/   # Sdílené UI utility, téma
├── shared/                      # Sdílená business logika (Android + iOS)
│   └── src/commonMain/kotlin/com/tkolymp/shared/
│       ├── auth/                # Autentizace
│       ├── event/               # Události
│       ├── people/              # Lidé
│       ├── club/                # Klub
│       ├── calendar/            # Kalendář + kolizní algoritmus
│       ├── announcements/       # Oznámení
│       ├── notification/        # Notifikace
│       ├── network/             # GraphQL klient (Ktor)
│       ├── storage/             # Lokální úložiště
│       ├── viewmodels/          # ViewModels (sdílené)
│       └── ServiceLocator.kt    # Dependency injection
├── iosApp/                      # iOS Swift/SwiftUI host
└── gradle/
    └── libs.versions.toml       # Centrální správa závislostí
```

---

## Přidání závislosti

1. Přidejte verzi a alias do `gradle/libs.versions.toml`.
2. Odkazujte se na ni přes `libs.<alias>` v příslušném `build.gradle.kts`.

```toml
# gradle/libs.versions.toml
[versions]
myLib = "1.2.3"

[libraries]
my-lib = { module = "com.example:mylib", version.ref = "myLib" }
```

---

## Konvence

- Veškerá business logika patří do `shared/src/commonMain` — obrazovky jsou co nejtenčí.
- Nové služby zakládejte do příslušného subpackage v `com.tkolymp.shared.*`.
- Platformně specifický kód umístěte do `androidMain` / `iosMain`.
- Nikdy nepoužívejte hardcoded verze závislostí — pouze aliasy z `libs.versions.toml`.
