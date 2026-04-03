# Audit: Logika v Composable obrazovkách vs. ViewModely

## Celkový přehled

| Obrazovka | Řádky Screen | Řádky VM | Ratio | Priorita |
|---|---|---|---|---|
| `RegistrationScreen` | 655 | 70 | **9.4 : 1** | 🔴 Kritická |
| `CalendarViewScreen` | 758 | 262 | **2.9 : 1** | 🟡 Střední |
| `StatsScreen` | 619 | 392 | **1.6 : 1** | ✅ Dobrý stav |

---

## 🔴 Kritické — nejvíc logiky mimo VM

### `RegistrationScreen.kt` (VM je jen 70 řádků)

Co patří do VM nebo shared:

- ~~**`sealed class RegMode`, `data class LessonInput`, `data class RegistrationInput`** — doménové typy definované přímo v screen souboru~~ ✅ Přesunuto do `shared/.../registration/RegistrationInput.kt`
- **`registeredPersonIds`/`registeredCoupleIds`** computation z JSON — `remember {}` s business logikou v Composable
- **`selectedRegistrant` initial value** ("preferuj sebe, pokud ještě není přihlášen") — selekční logika
- ~~**Display name fetching**: `LaunchedEffect` volající `ServiceLocator.peopleService.fetchPersonDisplayName()` přímo z UI — Register mode~~ ✅ Odstraněno (ViewModel `loadNames` + sync LaunchedEffect postačuje)
- ~~**Cache invalidation** na `ON_RESUME`: `ServiceLocator.cacheService.invalidatePrefix("person_")` — přímé volání service z obrazovky~~ ✅ Přesunuto do `RegistrationViewModel.invalidateAndRefresh()`
- **`computeInitCountsFor(regId)`** — pure funkce parsující JSON demands
- Display name fetching v Edit/Delete mode — `LaunchedEffect` stále volá `ServiceLocator.peopleService` přímo (2× zbývá)

---

---

## 🟡 Střední priorita

### `CalendarViewScreen.kt` (VM je 262 řádků — nejlépe vybavený)

- Zbytek je OK — VM je robustní (262 řádků)

### `StatsScreen.kt` (nejlepší stav, ratio 1.6 : 1)

- Zbytek je OK — VM je robustní (392 řádků)

---

## Klíčové vzory, které se opakují

| Vzor | Výskyt |
|---|---|
| Přímé volání `ServiceLocator.*` z UI | `RegistrationScreen` |
| `LaunchedEffect` s data-fetching logikou v Composable místo VM | Všechny kritické obrazovky |
| `remember {}` s business logikou místo `StateFlow` ve VM | `EventScreen`, `RegistrationScreen` |
| Domain typy / `data class` definované v screen souborech | `RegistrationScreen` (`RegMode`, `LessonInput`, `RegistrationInput`), `CalendarViewScreen` (`CoupleInfo`) |
| Pure transformace dat (JSON parsování, field mapping) inline v Composable | `EventScreen`, `RegistrationScreen` |

---

## Doporučené pořadí refactoringu

1. **`EventScreen`** — nejhorší ratio (11.6 : 1), `isPast` používá non-multiplatform API
2. **`RegistrationScreen`** — domain typy mimo shared, opakované service volání 3× v UI
