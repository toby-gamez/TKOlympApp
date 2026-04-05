# Audit výkonu aplikace TKOlympApp

Krátké shrnutí: Cílem je rychlá a plynulá aplikace s nízkou latencí UI a rozumnou spotřebou CPU/memory. Níže jsou zjištění z rychlého průchodu kódem a doporučené kroky rozdělené podle priority.

**Souhrn nálezů**
- **Hlavní obrazovny (Compose)**: `StatsScreen` používá vertikální a horizontální scrolly a mnoho `remember{ mutableStateOf(...) }` a `LaunchedEffect` volání. (Viz [composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/StatsScreen.kt](composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/StatsScreen.kt#L80), [composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/StatsScreen.kt](composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/StatsScreen.kt#L135))
- **ViewModel / agregace**: `StatsViewModel` dělá rozsáhlé datové transformace (bucketing, agregace) na hlavní coroutines dispatcher `Dispatchers.Default` pomocí `withContext` — vhodné, ale je třeba sledovat alokace a opakované výpočty. (Viz [shared/src/commonMain/kotlin/com/tkolymp/shared/viewmodels/StatsViewModel.kt](shared/src/commonMain/kotlin/com/tkolymp/shared/viewmodels/StatsViewModel.kt#L137), [shared/src/commonMain/kotlin/com/tkolymp/shared/viewmodels/StatsViewModel.kt](shared/src/commonMain/kotlin/com/tkolymp/shared/viewmodels/StatsViewModel.kt#L158))
- **Caching**: `CacheService` je jednoduchý in-memory cache s `Dispatchers.Default.limitedParallelism(1)` — to může být úzké hrdlo pokud se často invaliduje/čte z více vláken. (Viz [shared/src/commonMain/kotlin/com/tkolymp/shared/cache/CacheService.kt](shared/src/commonMain/kotlin/com/tkolymp/shared/cache/CacheService.kt#L1))
- **Lazy vs eager rendering**: UI používá `verticalScroll` s velkými sloupci (všechny položky se vyrenderují najednou). Pokud dat je víc (např. monthly/trainer lists), je výnosnější použít `LazyColumn`/`LazyRow` pro omezování práce při scrolování.
- **Recompositions**: Mnohé `remember { mutableStateOf(...) }` a `collectAsState()` volání mohou způsobovat zbytečné recompositie, pokud jsou stavy širší než lokální (např. celé `state` je collectováno v mnoha composable). Preferovat granularitu stavů a oddělit expensive composable prosté od stavových změn.

**Prioritní doporučení (rychlé vítězství)**
- **1. Nahradit velké scroll-sloupce LazyColumn/LazyRow tam, kde je to možné.**
  - Např. v `StatsScreen` nahradit části, které vykreslují seznamy (monthly, trainer, type) do `LazyColumn`/`LazyRow` aby se omezila práce a paměť při scrollu. (Viz [StatsScreen.kt](composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/StatsScreen.kt#L135))
- **2. Snížit rozsah `collectAsState()`**
  - Místo `val state by viewModel.state.collectAsState()` rozdělit na menší `StateFlow`y nebo použít `derivedStateOf`/`remember` tak, aby recomposition zasahovala co nejmenší část UI.
- **3. CacheService: zvážit concurrent map + read-mostly strategie**
  - `Dispatchers.Default.limitedParallelism(1)` znamená sériové provádění cache operací. Pokud máte časté paralelní reads, použít `ConcurrentHashMap` (na Android/JVM) nebo implementaci se synchro na čtení, nebo zvážit read-write lock pattern.

**Střednědobé změny**
- **4. Optimalizovat agregace ve ViewModelu**
  - Agregační funkce (`buildWeeklyData`, `buildMonthlyData`, `buildTrainerData`) by měly být profiltrovány a optimalizovány: vyhýbat se zbytečným alokacím (např. opakované map/list tvorbě), používat sekvenční průchody, případně caching mezivýsledků pokud se data často znovu používají.
- **5. Omezit parsing řetězců a časových operací**
  - Funkce jako `durationMin` volá `Instant.parse` a fallback parsingy — pokud volání probíhá často, uvažovat o parsování jednou při načtení dat nebo přenášet epochy z backendu.
- **6. Debounce / throttle síťových volání**
  - Vyhnout se nadměrnému volání `viewModel.loadStats` při rychlém přepínání sezón, použít debouncing nebo zrušení předchozích požadavků.

**Dlouhodobé / architektonické**
- **7. Profilování a metriky**
  - Zapojit profilování: Android Studio CPU & Memory profiler, Perfetto traces, a sledovat Compose recomposition counts a skore. Přidat logování dělících bodů a časování v klíčových funkcích (např. čas vykonání `buildWeeklyData`).
- **8. Offload ne-UI práce**
  - Kontrolovat, že těžké výpočty běží mimo hlavní UI thread (na `Dispatchers.Default`) a že nejsou spouštěny znovu opakovaně při drobných UI změnách.

**Konkrétní kroky (checklist)**
- [ ] Nahradit `verticalScroll` velkých seznamů `LazyColumn`mi tam, kde je smysl.
- [ ] Rozdělit `state` v `StatsViewModel` na menší `StateFlow`y (např. weeklyFlow, monthlyFlow, metaFlow) pro menší recomposition.
- [ ] Upravit `CacheService` pro lepší paralelní čtení (Concurrent map nebo zrušit limitedParallelism(1)).
- [ ] Přidat měření do `StatsViewModel` (logovat dočasně délku výpočtů) pro identifikaci nejpomalejších funkcí.
- [ ] Použít `derivedStateOf` a `remember` pro drahé výpočty v composable, aby se nevypočítávaly při každé změně nesouvisejícího stavu.

**Profilování — doporučené nástroje a příkazy**
- Android Studio profiler (CPU/Memory/Network) — doporučený první krok.
- Perfetto traces (via Android Studio) pro hlubší trasování UI a janky.
- `adb shell dumpsys gfxinfo <package>` pro kontrolu rendering frame-times (starší metoda).

**Poznámky konkrétně z kódu**
- `StatsScreen`: používá `verticalScroll` a vykreslí mnoho sekcí najednou → přejít na lazy listy ([StatsScreen.kt#L135](composeApp/src/commonMain/kotlin/com/tkolymp/tkolympapp/screens/StatsScreen.kt#L135)).
- `StatsViewModel`: volání `eventService.fetchEventsGroupedByDay(...).values.flatten()` může alokovat velké množství malých objektů; zkontrolovat, zda `first=500` je potřebné, případně stránkovat nebo agregovat již server-side ([shared/src/commonMain/kotlin/com/tkolymp/shared/viewmodels/StatsViewModel.kt#L137]).
- `CacheService`: `limitedParallelism(1)` vede k sériovému přístupu; pokud cache často čte více vláken, změna ke konkorentní mapě zlepší latenci. ([shared/src/commonMain/kotlin/com/tkolymp/shared/cache/CacheService.kt](shared/src/commonMain/kotlin/com/tkolymp/shared/cache/CacheService.kt#L1))

**Další návrhy**
- Přidat metriky (timers) kolem `buildWeeklyData`, `buildMonthlyData` a `buildTrainerData` pro stanovení skutečných hotspotů.
- Kontrolovat alokace během navigace mezi screens (GPU/CPU spikes) v Android Profileru.

Pokud chcete, mohu nyní:
- provést rychlou patch změnu jednoho místa (např. přepsat jednu sekci ve `StatsScreen` z `verticalScroll` na `LazyColumn`) a spustit build nebo
- vytvořit checklist issue/PR se změnami a konkrétními diffy.

---
Audit vytvořen rychlým průchodem kódu; pro hloubkový audit doporučuji běžící profilování na reálném zařízení a měření recomposition counts v Compose.
