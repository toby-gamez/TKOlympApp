# Audit: Logika v Composable obrazovkách vs. ViewModely

## Celkový přehled

| Obrazovka | Řádky Screen | Řádky VM | Ratio | Priorita |
|---|---|---|---|---|
| `RegistrationScreen` | 655 | 70 | **9.4 : 1** | 🔴 Kritická |
| `NotificationsSettingsScreen` | 718 | 107 | **6.7 : 1** | 🔴 Kritická |
| `ProfileScreen` | 543 | 61 | **8.9 : 1** | 🟠 Vysoká |
| `CalendarViewScreen` | 758 | 262 | **2.9 : 1** | 🟡 Střední |
| `StatsScreen` | 619 | 392 | **1.6 : 1** | ✅ Dobrý stav |

---

## 🔴 Kritické — nejvíc logiky mimo VM

### `RegistrationScreen.kt` (VM je jen 70 řádků)

Co patří do VM nebo shared:

- **`sealed class RegMode`, `data class LessonInput`, `data class RegistrationInput`** — doménové typy definované přímo v screen souboru
- **`registeredPersonIds`/`registeredCoupleIds`** computation z JSON — `remember {}` s business logikou v Composable
- **`selectedRegistrant` initial value** ("preferuj sebe, pokud ještě není přihlášen") — selekční logika
- **Display name fetching**: `LaunchedEffect` volající `ServiceLocator.peopleService.fetchPersonDisplayName()` přímo z UI — 3× v různých módech
- **Cache invalidation** na `ON_RESUME`: `ServiceLocator.cacheService.invalidatePrefix("person_")` — přímé volání service z obrazovky
- **`computeInitCountsFor(regId)`** — pure funkce parsující JSON demands

### `NotificationsSettingsScreen.kt` (VM je jen 107 řádků)

Co patří do VM:

- **`persist()`** funkce — přímo volá `ServiceLocator.notificationService.updateSettings()` z UI
- **`availableLocations`/`availableTrainers` fetching** — `LaunchedEffect(Unit)` volající `ServiceLocator.clubService.fetchClubData()` v screen
- **`rules` a `globalEnabled`** state — Screen si udržuje vlastní kopii dat z VM (`mutableStateListOf`), synchronizuje se přes `LaunchedEffect(vmState.settings)` — zbytečná duplicita
- **Time-ago formátování** zpráv trenéra: `val mins = ((now - msg.epochMs) / 60000)` a text generování
- **Vytváření `NotificationRule` objektů** v dialog confirm buttonu

---

## 🟠 Vysoká priorita

### `ProfileScreen.kt` (VM je jen 61 řádků)

Co patří do VM:

- **`LaunchedEffect(profileState)` blok** (40+ řádků) — celé zpracování `profileState` do `activeCoupleNames`, `cohortItems`, `titleText`, `bioText`, `addressFields`, atd. — to je derivovaný state, patří do VM
- **`buildPersonFieldList()`, `buildCurrentUserFieldList()`** — pure transformace dat, patří do shared
- **`mergedFields` computation** + kategorizace do `personalList`, `contactList`, `externalList`, `otherList`
- **`data class CohortDisplay`** — domain typ definovaný v screen souboru
- **`ServiceLocator.tokenStorage.clear()` / `userService.clear()`** v logout handleru — přímé service volání z UI bez VM


## 🟡 Střední priorita

### `CalendarViewScreen.kt` (VM je 262 řádků — nejlépe vybavený)

- **`getCoupleInfo()`** — pure funkce definovaná v screen souboru, patří do shared utils
- **`parseEventColor()`** — color computation logika patří do shared utils (barva záleží na business pravidlech)
- **`CoupleInfo` data class** — definovaná v screen souboru

### `StatsScreen.kt` (nejlepší stav, ratio 1.6 : 1)

- **`stripTitles()`** a **`roundTo1dp()`** — utility funkce v screen souboru, patří do shared utils
- Zbytek je OK — VM je robustní (392 řádků)

---

## Klíčové vzory, které se opakují

| Vzor | Výskyt |
|---|---|
| Přímé volání `ServiceLocator.*` z UI | `NotificationsSettingsScreen`, `RegistrationScreen`, `ProfileScreen` |
| `LaunchedEffect` s data-fetching logikou v Composable místo VM | Všechny kritické obrazovky |
| `remember {}` s business logikou místo `StateFlow` ve VM | `EventScreen`, `RegistrationScreen` |
| Domain typy / `data class` definované v screen souborech | `RegistrationScreen` (`RegMode`, `LessonInput`, `RegistrationInput`), `ProfileScreen` (`CohortDisplay`), `CalendarViewScreen` (`CoupleInfo`) |
| Pure transformace dat (JSON parsování, field mapping) inline v Composable | `EventScreen`, `RegistrationScreen` |
| Duplikace stavu — screen kopíruje VM state do lokálního `mutableStateListOf` | `NotificationsSettingsScreen` |

---

## Doporučené pořadí refactoringu

1. **`EventScreen`** — nejhorší ratio (11.6 : 1), `isPast` používá non-multiplatform API
2. **`NotificationsSettingsScreen`** — nejvíc přímých service volání + duplicitní state
3. **`RegistrationScreen`** — domain typy mimo shared, opakované service volání 3× v UI
4. **`ProfileScreen`** — velký derivovaný state blok patří do VM
