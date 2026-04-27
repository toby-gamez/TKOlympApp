# Plán: Hledání volných lekcí (Free Lesson Finder)

## TL;DR

FAB v `CalendarScreen` otevírá novou `FreeLessonsScreen`, která najde volné lekce za následující 2 týdny, skóruje je podle dostupnosti uživatele a rozdělí do kategorií. Červená tečka na FABu signalizuje zrušenou „moji" lekci, kterou lze nahradit.

---

## Rozhodnutí

- **„Moje zrušená lekce"** = `fetchEventsGroupedByDay(onlyMine=true)` + `isCancelled=true` (přesně co zobrazuje záložka Mine)
- **Rozsah hledání:** následující 2 týdny
- **UI:** nová celá obrazovka (`FreeLessonsScreen`)
- **Nejlepší nálezy:** top 5 výsledků (bez překrytí s tréninkem)
- **„Neobtěžuj mě":** per zrušená `EventInstance` (podle `id`)
- **Dismissed IDs:** uloženy v `CacheService`, klíč `"dismissed_cancelled_replacements"`
- Lekce překrývající se s tréninkem → nevstupují do „Nejlepší nálezy", mohou být v „Ostatní" s tipem

---

## Kategorie

| Stav | Sekce |
|------|-------|
| Bez zrušených | „Nejlepší nálezy" (top 5, bez překrytí) + „Ostatní" (zbytek vč. překrytí) |
| Se zrušenými (nezamítnutými) | „Nahradit za zrušené" + „Nejlepší nálezy" (top 5, bez překrytí) |

Když jsou zrušené lekce, sekce „Nejlepší nálezy" kombinuje to, co by jinak bylo „Nejlepší nálezy" + „Ostatní".

---

## Scoring (per volná `EventInstance`)

| Pravidlo | Hodnota |
|----------|---------|
| Základ | +100 |
| Překrytí s tréninkem | → zařadit do Ostatní (nevstupuje do top 5) |
| Každý den od dneška | −5 (max −70 za 14 dní) |
| Uživatel nemá žádný trénink ten den | +30 |
| Stejná lokace jako jiné uživatelovy události ten den | +20 |

---

## Tipy (zobrazeny pod názvem na kartě)

Vybere se nejdůležitější (první platný v pořadí):

| # | Podmínka | Tip |
|---|----------|-----|
| 1 | Překrytí s tréninkem | „Kryje se s {název tréninku}" |
| 2 | Stejný sál + gap > 45 min | „Mezi tréninky je dlouhé čekání" |
| 3 | Jiný sál + gap < 15 min | „Je to na jiném sále, nemáš dostatečný čas na přesun" |
| 4 | Jiný sál + gap ≥ 15 min | „Je to na jiném sále, ale máš čas na přesun" |
| 5 | Nejvyšší skóre ze všech | „Toto je nejlepší volba" |
| 6 | V sekci Nejlepší nálezy, bez předchozího tipu | „Taky dobrá volba" |
| 7 | Jinak | _(žádný tip)_ |

> **Poznámka:** Stejný sál + přiměřený gap (< 45 min) = ideální stav, žádný tip.

---

## Filtry pro „Nahradit za zrušené"

Náhradní lekce musí splňovat:
- Stejný trenér jako zrušená lekce
- Stejný týden jako zrušená lekce
- V budoucnosti
- Volná (free slot — `eventRegistrationsList.isEmpty()`)

---

## Fáze 1: Shared logika

### Nový soubor
`shared/src/commonMain/kotlin/com/tkolymp/shared/viewmodels/FreeLessonsViewModel.kt`

**`FreeLessonsState`:**
```
cancelledMineInstances: List<EventInstance>
replacementResults: Map<String, List<FreeLessonResult>>   // cancelled instanceId → náhrady
bestFinds: List<FreeLessonResult>
otherFinds: List<FreeLessonResult>
hasCancelledToShow: Boolean
dismissedInstanceIds: Set<String>
isLoading: Boolean
error: String?
```

**`FreeLessonResult`:**
```
instance: EventInstance
score: Int
hasConflict: Boolean
conflictName: String?
dayDistance: Int
sameLocation: Boolean
tip: String?
```

**Metody:**
- `load()` — dvě volání paralelně:
  - `fetchEventsGroupedByDay(onlyMine=true)` → detekce zrušených + moje tréninky pro scoring/konflikt
  - `fetchEventsGroupedByDay(onlyMine=false)` → pool volných lekcí
  - Výsledky: filtruje volné, skóruje, generuje tipy, kategorizuje
- `dismissCancelled(instanceId: String)` → uloží do `CacheService` + přepočet stavu
- `scoreInstance(freeInstance, myEventsByDay, now): Int`
- `computeTip(result, myEventsByDay, isBestSection: Boolean): String?`

### Upravený soubor
`shared/src/commonMain/kotlin/com/tkolymp/shared/viewmodels/CalendarViewModel.kt`

- Přidat `hasCancelledMineToShow: Boolean` do `CalendarState`
- V `load()`: z Mine fetche filtrovat `isCancelled=true`, odečíst dismissed IDs z `CacheService` → nastavit flag

---

## Fáze 2: UI

### Nový soubor
`composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/FreeLessonsScreen.kt`

- Sekce **„Nahradit za zrušené"** (pokud `hasCancelledToShow`):
  - Pro každou zrušenou lekci: collapsed/expanded seznam náhrad
  - Tlačítko „Neobtěžuj mě pro tuto lekci" → `viewModel.dismissCancelled(id)`
- Sekce **„Nejlepší nálezy"** (top 5) + **„Ostatní"** (zbytek)
  - Pokud `hasCancelledToShow`: obě sekce sloučeny do jedné „Nejlepší nálezy"
- Karta výsledku: název lekce, datum/čas, lokace, tip pod názvem, tap → `onOpenEvent(id)`

### Upravené soubory

**`composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/CalendarScreen.kt`**
- Přidat parametr `onFindFreeLessons: () -> Unit`
- `FloatingActionButton` v `Scaffold`
- Červená `Badge` tečka pokud `state.hasCancelledMineToShow`

**`composeApp/src/androidMain/kotlin/com/tkolymp/tkolympapp/App.kt`**
- Přidat route `"free-lessons"` → `FreeLessonsScreen`
- Instancovat `FreeLessonsViewModel` přes `viewModel()`
- `onFindFreeLessons = { navController.navigate("free-lessons") }` předat do `CalendarScreen`

---

## Verification

1. `./gradlew :composeApp:assembleDebug` projde bez chyb
2. FAB viditelný v `CalendarScreen`
3. Červená tečka = Mine záložka má zrušenou nezamítnutou lekci
4. Tap FAB → `FreeLessonsScreen` se správnými kategoriemi
5. Tap na lekci → `EventScreen`
6. „Neobtěžuj mě" → lekce zmizí ze sekce + badge aktualizována
7. Dismissed IDs přežijí restart aplikace (CacheService)
