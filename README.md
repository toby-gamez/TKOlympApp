# TKOlymp

Mobilní aplikace pro TK Olymp — taneční klub. Postavena na **Kotlin Multiplatform** s **Compose Multiplatform** UI, cílí na Android a iOS z jediné sdílené codebase.

---

## Technologie

| Oblast | Technologie |
|---|---|
| Jazyk | Kotlin 2.4.0 |
| UI | Compose Multiplatform 1.11.1 + Material 3 |
| Síť | Ktor 3.5.0 (GraphQL přes OkHttp/Darwin) |
| Serializace | kotlinx-serialization 1.11.0 |
| Datum/čas | kotlinx-datetime 0.8.0 |
| Navigace | Jetpack Navigation Compose |
| Android widgety | Glance (App Widgets) + WorkManager |
| Android min SDK | 31 |
| Android target SDK | 36 |
| Android compile SDK | 37 |

---

## Architektura

Projekt je rozdělen do čtyř Gradle modulů:

### `shared`
Sdílená business logika (Android + iOS) — platformově nezávislá, nikdy neimportuje Android/UI třídy. Obsahuje:
- **Services** — jedno rozhraní + jedna implementace na doménu, např. `AuthService`, `UserService`, `EventService`, `PeopleService`, `ClubService`, `AnnouncementService`, `NotificationService`, `PaymentsService`, `RegistrationService`, `CompetitionsService`, `PersonalEventsService`, `AchievementsService`, `CampScheduleService`, `FeedbackService`, `SystemCalendarService`
- **ViewModels** — jeden ViewModel na každou obrazovku (sdílený mezi Android a iOS), implementuje `ViewModelState`
- **Network** — `GraphQlClientImpl` (Ktor); Android engine je OkHttp s certificate pinningem na `api.rozpisovnik.cz`, iOS používá Darwin
- **`CacheService`** — in-memory LRU cache (max 200 položek, výchozí TTL 5 minut)
- **Storage** — `TokenStorage`, `UserStorage`, `OnboardingStorage`, `LanguageStorage`, `CalendarPreferenceStorage`, `OfflineDataStorage`, `NotificationStorage` (na Androidu přes knihovnu `ksafe`)
- **Offline sync** — `OfflineSyncManager` stahuje data při startu a po přihlášení
- **Error reporting** — `ErrorReporter` automaticky odesílá zachycené i fatální chyby jako bug report
- **`ServiceLocator`** — DI entry point (read-only fasáda nad `AppContainer`)

### `composeApp`
Compose UI vrstva pro Android i iOS (`iosArm64`/`iosSimulatorArm64`). Obrazovky jsou co nejtenčí, veškerá logika je v `shared`. Platformně specifický kód je v `androidMain`/`iosMain`.

**Obrazovky:**
- Onboarding / Login / Registrace / Souhlas se zpracováním údajů
- Přehled (Overview)
- Události (Events, EventDetail) + osobní události (PersonalEvents)
- Kalendář + zobrazení kalendáře (kolizní algoritmus, kalendář vs. timeline přepínač)
- Žebříček (Leaderboard) + statistiky (Stats) se sdílecí kartou
- Nástěnka (Board)
- Lidé (People, PersonDetail) + skupiny (Groups)
- Soutěže (Competitions) + úspěchy (Achievements)
- Platby (Payments)
- Trenéři – lokace (TrainersLocations)
- Volné lekce (FreeLessons)
- Skenování rozvrhu tábora (QR/čárový kód, foto rozvrhu s OCR — pouze Android)
- Oznámení (Notifications settings, narozeninová oznámení)
- Nastavení / Jazyk / Profil
- Ostatní / O aplikaci / Ochrana soukromí / Zpětná vazba

**Android-only funkce:** Firebase Cloud Messaging, skenování čárových kódů, OCR rozvrhu tábora (ML Kit + OpenCV), widgety na plochu (Glance: nejbližší událost, moje události, narozeniny, další soutěž).

### `androidApp`
Instalovatelný Android shell — `applicationId`, verze, podepisování, `BuildConfig` pole. Obsahuje pouze `TKOlympApplication` a závisí na `composeApp`, `shared` a `appRes`.

### `appRes`
Sdílené Android zdroje (ikony, `strings.xml`, manifest bity) používané modulem `androidApp`.

---

## Spuštění

### Android

```bash
./gradlew :androidApp:assembleDebug
```

APK se nachází v `androidApp/build/outputs/apk/debug/`.

Instalace na zařízení/emulátor:

```bash
adb install androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### iOS

1. Sestavte Kotlin framework:
   ```bash
   ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
   ```
2. Otevřete `iosApp/iosApp.xcodeproj` v Xcode.
3. Spusťte `Run` (⌘R).

### Testy

```bash
# Testy shared modulu (commonTest, spuštěné na JVM proti android targetu)
./gradlew :shared:testAndroidHostTest

# Testy napříč všemi targety (android host + iOS simulátor; iOS větev vyžaduje macOS/Xcode)
./gradlew :shared:allTests
```

### Lokální konfigurace

`local.properties` (git-ignored) musí obsahovat:
```
api.base.url=https://api.rozpisovnik.cz/graphql
tenant.id=<club-tenant-id>
```

Pro podepsaný release build (`:androidApp:assembleRelease`/`bundleRelease`) je potřeba navíc:
```
release.keystore.path=/absolute/path/to/upload-keystore.jks
release.keystore.password=<keystore password>
release.key.alias=<key alias>
release.key.password=<key password>
```

---

## Struktura projektu

```
TKOlympApp/
├── androidApp/                  # Instalovatelný Android shell (BuildConfig, signing)
├── appRes/                      # Sdílené Android zdroje (ikony, strings.xml)
├── composeApp/                  # Compose UI (Android + iOS)
│   └── src/
│       ├── androidMain/kotlin/  # Obrazovky-specifický kód, widgety, OCR, MainActivity
│       ├── iosMain/kotlin/      # iOS entry point (App.ios.kt, MainViewController)
│       └── commonMain/kotlin/   # Obrazovky, navigace (AppContent.kt), téma, komponenty
├── shared/                      # Sdílená business logika (Android + iOS)
│   └── src/commonMain/kotlin/com/tkolymp/shared/
│       ├── auth/, user/         # Autentizace, uživatel
│       ├── event/, personalevents/, competitions/, registration/, campschedule/
│       ├── people/, club/, achievements/
│       ├── calendar/            # Kalendář + kolizní algoritmus
│       ├── announcements/, notification/, feedback/
│       ├── payments/, systemcalendar/, appearance/, device/, tutorial/
│       ├── network/             # GraphQL klient (Ktor)
│       ├── storage/, cache/     # Lokální úložiště, in-memory cache
│       ├── sync/                # Offline sync (OfflineSyncManager)
│       ├── errorreporting/      # Automatické hlášení chyb
│       ├── language/            # Lokalizace (AppStrings, překlady)
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
- Nové služby zakládejte do příslušného subpackage v `com.tkolymp.shared.*` (rozhraní + implementace).
- Platformně specifický kód umístěte do `androidMain` / `iosMain`.
- Nikdy nepoužívejte hardcoded verze závislostí — pouze aliasy z `libs.versions.toml`.

Podrobnější pokyny pro AI asistenty (Claude Code) najdete v [`CLAUDE.md`](CLAUDE.md).
