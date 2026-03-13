# Audit kvality kódu — TKOlympApp KMP

> **Senior Developer review — March 12, 2026**  
> Rozsah: `shared/`, `composeApp/`, `gradle/`, build konfigurace  
> Cíl: Kotlin Multiplatform (KMP) + Compose Multiplatform

---

## Obsah

3. [Spolykané výjimky a CancellationException](#3-spolykané-výjimky-a-cancellationexception)
4. [Type Safety](#4-type-safety)
5. [Hardcoded řetězce navzdory i18n systému](#5-hardcoded-řetězce-navzdory-i18n-systému)
6. [ViewModel Design](#6-viewmodel-design)
7. [CacheService — nadbytečný mutex](#7-cacheservice--nadbytečný-mutex)
8. [Duplicitní kód](#8-duplicitní-kód)
9. [Build konfigurace](#9-build-konfigurace)
10. [Bezpečnost](#10-bezpečnost)
11. [Logger](#11-logger)
12. [Pojmenování](#12-pojmenování)
13. [Shrnutí a prioritizace](#13-shrnutí-a-prioritizace)
14. [Akční plán](#14-akční-plán)

---

## 3. Spolykané výjimky a CancellationException

### 3.1 Zachycení CancellationException ❌ KRITICKÉ

Projekt je doslova posypán prázdnými catch bloky zachytávajícími `Throwable`:

```kotlin
// LoginViewModel.kt
try { userService.fetchAndStoreActiveCouples() } catch (_: Throwable) {}
try { userService.fetchAndStorePersonDetails(personId) } catch (_: Throwable) {}

// OverviewViewModel.kt
val events = try { ... } catch (_: Throwable) { emptyList<Any>() }
val announcements = try { ... } catch (_: Throwable) { emptyList<Any>() }
val pid = try { ... } catch (_: Throwable) { null }
val cids = try { ... } catch (_: Throwable) { emptyList<String>() }

// CalendarViewModel.kt
if (forceRefresh) {
    try { cache.invalidatePrefix("calendar_") } catch (_: Throwable) {}
}

// EventViewModel.kt
try { withContext(Dispatchers.IO) { ServiceLocator.authService.initialize() } } catch (_: Throwable) {}
```

**Kritický problém**: `Throwable` zachytí i `kotlinx.coroutines.CancellationException`, která je v Kotlin Coroutines **vyhrazena pro kooperativní zrušení coroutine**. Zachycení a ignorování `CancellationException` způsobuje:

- **Memory leaky** — coroutine se tváří hotová, ale nadřazený `CoroutineScope` o zrušení neví
- **Zombie coroutines** — coroutine pokračuje v práci i poté, co by měla být zrušena (např. po navigaci pryč z obrazovky)
- **Nekorektní cancellation propagation** — strukturovaný paralelismus přestává fungovat

**Správná oprava** — vždy rethrow `CancellationException`:

```kotlin
// ŠPATNĚ:
} catch (_: Throwable) { null }

// SPRÁVNĚ:
} catch (e: CancellationException) {
    throw e  // VŽDY rethrow — nezbytné pro structured concurrency
} catch (e: Exception) {
    Logger.d("Tag", "Chyba: ${e.message}")
    null
}
```

### 3.2 Přílišné mlčení chybových stavů ❌ STŘEDNÍ

Mnoho chybových cest skrývá informaci totálně:

```kotlin
try { userService.fetchAndStoreActiveCouples() } catch (_: Throwable) {}
```

Pokud tato operace selže, uživatel nikdy nedostane žádnou zpětnou vazbu. Přinejmenším by chyba měla být zalogována:

```kotlin
try {
    userService.fetchAndStoreActiveCouples()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Logger.d("LoginViewModel", "fetchAndStoreActiveCouples failed: ${e.message}")
    // záměrně ignorováno — nekritická operace
}
```

---

## 4. Type Safety

### 4.1 `List<Any>` v OverviewState ❌ VYSOKÉ

```kotlin
// OverviewViewModel.kt
data class OverviewState(
    val upcomingEvents: List<Any> = emptyList(),       // ❌
    val recentAnnouncements: List<Any> = emptyList(),  // ❌
    // ...
)
```

Poté v screenu nutné runtime casty:

```kotlin
// OverviewScreen.kt
val trainings = state.upcomingEvents.filterIsInstance<EventInstance>()
val announcements = state.recentAnnouncements.filterIsInstance<Announcement>()
```

Kotlin generics exisují přesně proto, aby se `filterIsInstance` nemuselo volat. Pokud `filterIsInstance` vrátí prázdný seznam (kvůli špatnému typu), uživatel nevidí data aniž by byl logován jakýkoli error.

**Oprava**:

```kotlin
data class OverviewState(
    val upcomingEvents: List<EventInstance> = emptyList(),
    val recentAnnouncements: List<Announcement> = emptyList(),
    // ...
)
```

### 4.3 Přístup k JSON přes lokální lambda v EventScreen ❌ NÍZKÉ

```kotlin
// EventScreen.kt — definováno uvnitř composable funkce
fun JsonObject.str(key: String) = try { this[key]?.jsonPrimitive?.contentOrNull } catch (_: Exception) { null }
fun JsonObject.int(key: String) = try { this[key]?.jsonPrimitive?.intOrNull } catch (_: Exception) { null }
fun JsonObject.bool(key: String) = try { this[key]?.jsonPrimitive?.booleanOrNull } catch (_: Exception) { null }
```

Rozšiřující funkce definované uvnitř composable jsou přecompilovány při každé rekomposition. Měly by být top-level extension functions v sdíleném utility souboru.

---

## 5. Hardcoded řetězce navzdory i18n systému

Přestože projekt má propracovaný `AppStrings` systém s **8 jazyky** (CS, DE, SK, SL, UA, VI, EN, BRAINROT), hardcoded texty se objevují všude — především v `shared/commonMain`, kde nemůže být použit ani `AppStrings`:

### shared/commonMain (iOS i Android — žádný AppStrings!)

| Soubor | Hardcoded string | Problém |
|---|---|---|
| `LoginViewModel.kt` | `"Přihlášení selhalo"` | Chybová zpráva pro uživatele v jednom jazyce |
| `LoginViewModel.kt` | `"Nelze získat personId po přihlášení"` | Technický text viditelný uživateli |
| `NotificationService.kt` | `"Výchozí pravidlo"` | Název pravidla v UI |
| `NotificationService.kt` | `"Událost začíná za $minutesBefore minut"` | Push notifikace — text vidí uživatel |
| `NotificationService.kt` | `"Lekce"`, `"Událost"` | Fallback texty v notifikacích |
| `EventService.kt` | `"LESSON"`, `"CAMP"` (enum-like strings) | Měly by být konstanty |

### composeApp/androidMain (AppStrings dostupný, ale nepoužitý)

| Soubor | Hardcoded string |
|---|---|
| `EventScreen.kt` | `"(bez názvu)"`, `"Lekce"`, `"Událost"` |
| `OverviewScreen.kt` | `"(bez názvu)"` |
| `LoginScreen.kt` | `"TK Olymp"`, `"Přihlaste se do svého účtu"` |
| `CalendarScreen.kt` | Různé hardcoded texty |

**Zvláštní problém**: `NotificationService` leží v `shared/commonMain` a generuje push notifikační texty fixně v češtině — německý i slovenský uživatel dostane notifikaci v cizím jazyce.

**Oprava**: Přidat chybějící klíče do `Strings` interface a všech překladů. Pro `commonMain` moduly předat lokalizaci jako parametr nebo callback.

---

## 6. ViewModel Design

### 6.1 ViewModely nejsou `androidx.lifecycle.ViewModel` ❌ VYSOKÉ

Všechna ViewModely jsou prosté Kotlin třídy:

```kotlin
// LoginViewModel.kt
class LoginViewModel() {  // žádné dědění z ViewModel
```

Coroutiny jsou spouštěny v screen:

```kotlin
// LoginScreen.kt
val scope = rememberCoroutineScope()
// ...
scope.launch {
    val ok = viewModel.login()
    if (ok) onSuccess()
}
```

**Dopad na Android**:

| Problém | Důsledek |
|---|---|
| Žádný `viewModelScope` | Coroutiny nejsou automaticky zrušeny po navigation/rotation |
| Žádný `SavedStateHandle` | State se neobnoví po process death |
| Žádná lifecycle awareness | ViewModel žije tak dlouho, jak si ho screen pamatuje přes `remember {}` — toto nestačí |
| Back stack neuchovává data | Návrat na obrazovku vynutí nové načtení dat |

**Doporučená oprava** pro KMP:
- Použít `androidx.lifecycle.ViewModel` v `composeApp/androidMain`
- Pro KMP sdílené ViewModely zvážit `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` (Compose Multiplatform lifecycle)

```kotlin
// S Compose Multiplatform lifecycle:
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

class LoginViewModel : ViewModel() {
    fun login() {
        viewModelScope.launch {
            // automaticky zrušeno při destroy ViewModelu
        }
    }
}
```

### 6.2 `initialize()` voláno opakovaně ❌ STŘEDNÍ

```kotlin
// EventViewModel.kt — při každém načtení eventu
suspend fun loadEvent(eventId: Long, forceRefresh: Boolean = false) {
    _state.value = _state.value.copy(isLoading = true, error = null)
    try {
        try { withContext(Dispatchers.IO) { ServiceLocator.authService.initialize() } } catch (_: Throwable) {}
        // ...
    }
}
```

`initialize()` čte token ze storage (I/O operace) — zbytečně se volá při každém otevření detailu eventu. Token je již načten při startu aplikace.

**Oprava**: Volat `initialize()` pouze jednou při startu, nikoli v každém ViewModelu.

### 6.3 Nedetekovatelný stav before-init ❌ NÍZKÉ

`remember { LoginViewModel() }` vytváří novou instanci ViewModelu při každé recomposition z `remember` klíče. Pokud se composable zrecomponuje před dokončením přihlašování, stav se resetuje.

---

## 7. CacheService — nadbytečný mutex

**Soubor**: `shared/src/commonMain/kotlin/com/tkolymp/shared/cache/CacheService.kt`

```kotlin
class CacheService {
    private val cache = mutableMapOf<String, CacheEntry<*>>()
    private val mutex = Mutex()
    private val cacheDispatcher = Dispatchers.Default.limitedParallelism(1)  // ← sériové provádění

    suspend fun <T> get(key: String): T? = withContext(cacheDispatcher) {
        mutex.withLock {   // ← REDUNDANTNÍ! cacheDispatcher již garantuje sériovost
            // ...
        }
    }
}
```

`limitedParallelism(1)` garantuje, že se nikdy nevykonávají dvě operace souběžně. Přidaný `Mutex` je tedy redundantní a přidává zbytečný overhead (lock contention, memory).

**Další problémy**:
- Žádný **size limit** — cache může neomezeně růst v paměti při intenzivním používání
- Žádná **LRU eviction policy** — staré záznamy zůstávají dokud neexpirují TTL
- Cache je **in-memory only** — restart aplikace smaže všechna cached data

**Oprava**:
```kotlin
// Odstranit mutex, ponechat pouze cacheDispatcher
suspend fun <T> get(key: String): T? = withContext(cacheDispatcher) {
    // žádný mutex.withLock {} potřeba
    val entry = cache[key] as? CacheEntry<T>
    // ...
}
```

---

## 8. Duplicitní kód

### 8.2 Hardcoded ISO date ranges jsou nekonzistentní ❌ NÍZKÉ

```kotlin
// EventsViewModel.kt
val startIso = "2023-01-01T00:00:00Z"   // ← z minulosti?
val endIso = "2100-01-01T23:59:59Z"

// OverviewViewModel.kt defaultní parametry
startIso: String = "1970-01-01T00:00:00Z"  // ← Unix epoch?
endIso: String = "2100-01-01T00:00:00Z"
```

Tři různé "open past" hodnoty (`2023`, `1970`, dynamicky počítaný `LocalDate.now()`) pro stejný záměr. Mělo by být definováno jako konstanty:

```kotlin
object DateRangeConstants {
    const val FAR_PAST = "1970-01-01T00:00:00Z"
    const val FAR_FUTURE = "2100-01-01T00:00:00Z"
}
```

### 8.3 Duplicitní `EventUtils.kt` ❌ NÍZKÉ

```
composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/EventUtils.kt
shared/src/commonMain/kotlin/com/tkolymp/shared/utils/EventUtils.kt
```

Dvě implementace utility. Pokud leží business logika v `composeApp/commonMain`, je nedostupná pro `shared` modul a iOS.

---

## 9. Build konfigurace

### 9.1 Hardcoded verze mimo `libs.versions.toml` ❌ STŘEDNÍ

**`shared/build.gradle.kts`**:
```kotlin
implementation("androidx.core:core-ktx:1.9.0")  // ❌ hardcoded, navíc zastaralá verze (catalog má 1.17.0)
```

**`composeApp/build.gradle.kts`**:
```kotlin
implementation(platform("androidx.compose:compose-bom:2025.01.00"))  // ❌ hardcoded, nepoužívá alias
implementation("androidx.compose.material:material-icons-extended")   // ❌ bez verze (závisí na BOM, ale nedefinováno v toml)
```

**Dopad**: `core-ktx:1.9.0` v `shared` a `core-ktx:1.17.0` v toml — dvě různé verze stejné library v jednom projektu způsobují dependency conflict.

### 9.2 Citlivá data přímo v `build.gradle.kts` ❌ STŘEDNÍ

```kotlin
// composeApp/build.gradle.kts — committed v repozitáři
buildConfigField("String", "API_BASE_URL", "\"https://api.rozpisovnik.cz/graphql\"")
buildConfigField("String", "TENANT_ID", "\"1\"")
```

Produkční URL a tenant ID jsou viditelné v git historii. Správně by měly být v `local.properties`:

```properties
# local.properties (v .gitignore)
api.base.url=https://api.rozpisovnik.cz/graphql
tenant.id=1
```

```kotlin
// build.gradle.kts
val localProps = Properties().apply { load(rootProject.file("local.properties").reader()) }
buildConfigField("String", "API_BASE_URL", "\"${localProps["api.base.url"]}\"")
```

### 9.3 Nepoužívaná závislost v katalogu ❌ NÍZKÉ

```toml
# libs.versions.toml
roomKtx = "2.8.4"
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "roomKtx" }
```

Room není nigde importován ani použit. Přidává zbytečný dependency overhead a mate čtenáře.

### 9.4 Alpha verze navigace v produkci ❌ NÍZKÉ

```toml
androidx-navigation = "2.8.0-alpha10"  # ❌ alpha v produkčním buildu
```

Stable verze Navigation Compose je již dostupná (`2.8.x` stable). Alpha API se může měnit bez zpětné kompatibility.

### 9.5 Zbytečná závislost `compose-uiToolingPreview` v commonMain ❌ NÍZKÉ

```kotlin
// composeApp/build.gradle.kts
commonMain.dependencies {
    implementation(libs.compose.uiToolingPreview)  // ❌ UI tooling patří do debugImplementation
}
androidMain.dependencies {
    implementation(libs.compose.uiToolingPreview)  // ❌ duplicitní
}
```

`ui-tooling-preview` je development nástroj, neměl by být v `implementation` (přijde do release APK). Správně:
```kotlin
debugImplementation(libs.compose.uiTooling)
```

---

## 10. Bezpečnost

### 10.1 Žádný Certificate Pinning ⚠️ STŘEDNÍ

Ktor klient (`GraphQlClientImpl`) nekonfiguruje certificate pinning pro `api.rozpisovnik.cz`. Man-in-the-Middle útok na veřejné WiFi je teoreticky možný — útočník může zachytit JWT tokeny i GraphQL odpovědi.

**Doporučení** pro produkční aplikaci s citlivými uživatelskými daty:
```kotlin
// PlatformNetwork.kt (Android)
HttpClient(OkHttp) {
    engine {
        config {
            certificatePinner(
                CertificatePinner.Builder()
                    .add("api.rozpisovnik.cz", "sha256/HASH_CERTIFIKATU")
                    .build()
            )
        }
    }
}
```

### 10.3 JWT token v in-memory proměnné ⚠️ NÍZKÉ

```kotlin
// AuthService.kt
private var currentToken: String? = null
```

Token je cached v paměti vedle secure storage. Při memory dump (root device, debug build) je přístupný. Akceptovatelné pro produkci, ale stojí za vědomí.

---

## 11. Logger

**Soubor**: `shared/src/commonMain/kotlin/com/tkolymp/shared/Logger.kt`

```kotlin
object Logger {
    var isDebugEnabled = false  // ❌ var — kdokoli může přepnout
    fun d(tag: String, msg: String) {
        if (isDebugEnabled) println("[$tag] $msg")  // ❌ println místo android.util.Log
    }
}
```

**Problémy**:

| Problém | Dopad |
|---|---|
| `println()` místo `Log.d()` | Výstup není kategorizován v Android logcatu (žádný tag/PID filtr) |
| `var isDebugEnabled` | Mutable globální state — může být omylem zapnutý v release buildu |
| Chybí automatická detekce debug buildu | Logování musí být ručně zapnuto někde v inicializaci |
| Chybí úrovně `warn`, `error` | Všechno stejná priorita — není možné filtrovat jen chyby |
| Žádné výchozí zapnutí pro debug | V aktuálním kódu se nikde `isDebugEnabled = true` nenastavuje pro debug buildy |

**Doporučená oprava**:
```kotlin
// shared/commonMain
expect object Logger {
    fun d(tag: String, msg: String)
    fun w(tag: String, msg: String)
    fun e(tag: String, msg: String, throwable: Throwable? = null)
}

// shared/androidMain
actual object Logger {
    actual fun d(tag: String, msg: String) = if (BuildConfig.DEBUG) Log.d(tag, msg) else Unit
    actual fun e(tag: String, msg: String, throwable: Throwable?) = Log.e(tag, msg, throwable)
    // ...
}
```

---

## 12. Pojmenování

### 12.1 Package s velkým písmenem ❌ NÍZKÉ

```
composeApp/src/androidMain/kotlin/com/tkolymp/tkolympapp/Screens/  ❌
```

Kotlin (i Java) konvence: packages jsou vždy lowercase. `Screens` s velkým S je:
- Anti-pattern
- Způsobuje varování v linteru
- Nekompatibilní s Kotlin coding conventions

**Oprava**: Přejmenovat na `screens` (nebo lépe při přesunu do commonMain rovnou dodržet konvenci).

### 12.2 Nekonzistentní pojmenování ViewModelů ❌ NÍZKÉ

```
shared/src/commonMain/kotlin/com/tkolymp/shared/calendar/CalendarViewModel.kt    # package calendar
shared/src/commonMain/kotlin/com/tkolymp/shared/viewmodels/CalendarViewModel.kt   # package viewmodels
```

Část ViewModelů je v `viewmodels/`, část v doménových packages (`calendar/`). Chybí konzistentní strategie.

---

## 13. Shrnutí a prioritizace

| Priorita | # | Oblast | Soubory |
|---|---|---|---|
| 🔴 KRITICKÉ | 1 | Zachycení `CancellationException` | `LoginViewModel`, `OverviewViewModel`, `EventViewModel`, `CalendarViewModel`, `ProfileViewModel` |
|  VYSOKÉ | 3 | `List<Any>` v `OverviewState` | `OverviewViewModel.kt`, `OverviewScreen.kt` |
| 🟠 VYSOKÉ | 4 | ViewModely bez lifecycle (`viewModelScope`) | všechny `/viewmodels/*.kt` |
| 🟡 STŘEDNÍ | 5 | Hardcoded texty v `shared/commonMain` (notifikace, chyby) | `NotificationService.kt`, `LoginViewModel.kt` |
| 🟡 STŘEDNÍ | 6 | Hardcoded verze v build.gradle mimo toml | `shared/build.gradle.kts`, `composeApp/build.gradle.kts` |
| 🟢 NÍZKÉ | 7 | Logger (`println`, mutable, žádné úrovně) | `Logger.kt` |
| 🟢 NÍZKÉ | 8 | Package naming (`Screens` s velkým S) | `Screens/` |
| 🟢 NÍZKÉ | 9 | Nepoužívaná Room dependency | `libs.versions.toml` |
| 🟢 NÍZKÉ | 10 | Alpha verze navigace | `libs.versions.toml` |

---

## 14. Akční plán

### Sprint 1 — Opravy bez refaktoru (2–4 hodiny)

1. **CancellationException fix** — přidat `catch (e: CancellationException) { throw e }` před každý `catch (_: Throwable)` v celém projektu  
   *Minimální změna, maximální dopad na stabilitu*

2. **`List<Any>` → `List<EventInstance>`** v `OverviewState`

3. **Opravit verze v build.gradle** — přidat `core-ktx` do `libs.versions.toml`, odstranit hardcoded verze

4. **Redundantní mutex v CacheService** — odstranit `mutex.withLock {}` (ponechat `cacheDispatcher`)

### Sprint 2 — Větší architekturní změny (3–5 dní)

5. **Lifecycle ViewModely** — přidat `androidx.lifecycle.ViewModel` dědění, `viewModelScope`

7. **i18n chybějící klíče** — přidat error messages a notifikační texty do všech `Strings*.kt`

8. **API URL do `local.properties`** — přidat Secrets Gradle Plugin

---

*Audit vypracoval: GitHub Copilot (Claude Sonnet 4.6) na základě statické analýzy zdrojového kódu.*  
*Datum: 12. března 2026*
