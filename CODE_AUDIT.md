# Audit kvality kódu — TKOlympApp KMP

> **Senior Developer review — March 12, 2026**  
> Rozsah: `shared/`, `composeApp/`, `gradle/`, build konfigurace  
> Cíl: Kotlin Multiplatform (KMP) + Compose Multiplatform

---

## Obsah

11. [Logger](#11-logger)
12. [Pojmenování](#12-pojmenování)
13. [Shrnutí a prioritizace](#13-shrnutí-a-prioritizace)
14. [Akční plán](#14-akční-plán)

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

| Priorita | # | Oblast | Soubory | Stav |
|---|---|---|---|---|
| 🔴 KRITICKÉ | 1 | Zachycení `CancellationException` | `LoginViewModel`, `OverviewViewModel`, `EventViewModel`, `CalendarViewModel`, `ProfileViewModel` | — |
|  VYSOKÉ | 3 | `List<Any>` v `OverviewState` | `OverviewViewModel.kt`, `OverviewScreen.kt` | — |
| 🟠 VYSOKÉ | 4 | ViewModely bez lifecycle (`viewModelScope`) | všechny `/viewmodels/*.kt` | — |
| 🟡 STŘEDNÍ | 5 | Hardcoded texty v `shared/commonMain` (notifikace, chyby) | `NotificationService.kt`, `LoginViewModel.kt` | — |
| 🟡 STŘEDNÍ | 6 | Hardcoded verze v build.gradle mimo toml | `shared/build.gradle.kts`, `composeApp/build.gradle.kts` | — |
| ✅ VYŘEŠENO | 10.1 | Certificate Pinning (Android) | `PlatformNetwork.kt` | ✅ |
| 🟢 NÍZKÉ | 7 | Logger (`println`, mutable, žádné úrovně) | `Logger.kt` | ✅ |
| ✅ VYŘEŠENO | 8 | Package naming (`Screens` s velkým S) | `screens/` (commonMain) | ✅ |
| 🟢 NÍZKÉ | 9 | Nepoužívaná Room dependency | `libs.versions.toml` | — |
| 🟢 NÍZKÉ | 10 | Alpha verze navigace | `libs.versions.toml` | — |

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
