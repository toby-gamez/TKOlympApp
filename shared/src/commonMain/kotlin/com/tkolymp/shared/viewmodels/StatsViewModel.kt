package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.people.ScoreboardEntry
import com.tkolymp.shared.utils.parseToLocal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

// ─── Data models ──────────────────────────────────────────────────────────────

/** Represents a single season (Sep 1 -> Aug 31) with a display label. */
data class SeasonSelection(val start: LocalDate, val end: LocalDate, val label: String) {
    companion object {
        private fun forYear(startYear: Int): SeasonSelection {
            val s = LocalDate(startYear, 9, 1)
            val e = LocalDate(startYear + 1, 8, 31)
            val shortNext = ((startYear + 1) % 100).toString().padStart(2, '0')
            val label = "$startYear/$shortNext"
            return SeasonSelection(s, e, label)
        }

        fun current(today: LocalDate = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())): SeasonSelection {
            val year = if (today.monthNumber >= 9) today.year else today.year - 1
            return forYear(year)
        }

        /** Returns recent seasons, newest first (default 4 seasons). */
        fun recent(count: Int = 4, today: LocalDate = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())): List<SeasonSelection> {
            val currentYear = if (today.monthNumber >= 9) today.year else today.year - 1
            return (0 until count).map { offset -> forYear(currentYear - offset) }
        }

        fun default(): SeasonSelection = current()
    }
}

/** Stats for a single calendar week (Mon–Sun). */
data class WeekStats(
    /** Short label for display (e.g. "1.9." or just the week index). */
    val weekLabel: String,
    /** ISO date of Monday of this week (yyyy-MM-dd) */
    val weekStartIso: String,
    val count: Int,
    val minutes: Long,
    /** True if this is the week containing today. */
    val isCurrent: Boolean
)

data class MonthStats(
    /** Localized label such as "09/2025" */
    val monthLabel: String,
    /** Raw key "yyyy-MM" for sorting */
    val yearMonth: String,
    val count: Int,
    val minutes: Long
)

data class TypeStat(
    /** Raw type string from API (e.g. "lesson", "group") */
    val type: String,
    /** Translated/display name */
    val displayName: String,
    val count: Int
)

data class TrainerStat(val name: String, val count: Int, val minutes: Long)

/** Summary stats for one season used in cross-season comparison. */
data class SeasonSummary(
    val season: SeasonSelection,
    val totalSessions: Int,
    val totalMinutes: Long,
    val avgSessionsPerWeek: Double
)

/** Full breakdown for one season used in the side-by-side detail comparison. */
data class SeasonDetailStats(
    val season: SeasonSelection,
    val totalSessions: Int,
    val totalMinutes: Long,
    val avgSessionsPerWeek: Double,
    val monthlyData: List<MonthStats>,
    val typeData: List<TypeStat>,
    val trainerData: List<TrainerStat>
)

data class StatsState(
    val totalSessions: Int = 0,
    val totalMinutes: Long = 0L,
    val avgSessionsPerWeek: Double = 0.0,
    val currentStreak: Int = 0,
    /** Last 16 weeks, oldest first, newest last. */
    val weeklyData: List<WeekStats> = emptyList(),
    val monthlyData: List<MonthStats> = emptyList(),
    val typeData: List<TypeStat> = emptyList(),
    /** Top-5 trainers by session count. */
    val trainerData: List<TrainerStat> = emptyList(),
    val scoreEntry: ScoreboardEntry? = null,
    val selectedSeason: SeasonSelection = SeasonSelection.default(),
    val comparisonData: List<SeasonSummary> = emptyList(),
    val isLoadingComparison: Boolean = false,
    /** Up to 5 comparison slots (A–E). Indexed 0–4. */
    val compareSeasons: List<SeasonSelection?> = List(5) { null },
    val compareData: List<SeasonDetailStats?> = List(5) { null },
    val isLoadingCompare: List<Boolean> = List(5) { false },
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

// ─── ViewModel ────────────────────────────────────────────────────────────────

class StatsViewModel(
    private val eventService: com.tkolymp.shared.event.IEventService = ServiceLocator.eventService,
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService,
    private val cacheService: com.tkolymp.shared.cache.CacheService = ServiceLocator.cacheService
) : ViewModel() {

    private val _state = MutableStateFlow(StatsState())
    val state: StateFlow<StatsState> = _state.asStateFlow()

    /** Load (or re-load) statistics for the given season. */
    suspend fun loadStats(season: SeasonSelection = _state.value.selectedSeason, forceRefresh: Boolean = false) {
        _state.value = _state.value.copy(isLoading = true, error = null, selectedSeason = season)

        if (forceRefresh) {
            try { cacheService.invalidatePrefix("stats_") } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }

        try {
            val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
            val seasonStart = season.start
            val seasonEnd = season.end

            val startIso = seasonStart.toString() + "T00:00:00Z"
            val endIso = seasonEnd.toString() + "T23:59:59Z"

            // ── Fetch events ──────────────────────────────────────────────────
            val allInstances: List<EventInstance> = try {
                withContext(Dispatchers.Default) {
                    eventService.fetchEventsGroupedByDay(
                        startRangeIso = startIso,
                        endRangeIso = endIso,
                        onlyMine = true,
                        first = 500,
                        cacheNamespace = "stats_"
                    ).values.flatten()
                }
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                Logger.d("StatsViewModel", "fetchEventsGroupedByDay failed: ${ex.message}")
                emptyList()
            }

            val instances = allInstances.filter { !it.isCancelled }

            // ── Aggregations ──────────────────────────────────────────────────
            val totalSessions = instances.size
            val totalMinutes = instances.sumOf { inst -> durationMin(inst.since, inst.until) }

            // Weeks in season (at most seasonEnd-seasonStart in weeks, but cap shown to 16 most recent)
            val weeklyData = buildWeeklyData(instances, today, seasonStart, seasonEnd)

            // Monthly breakdown
            val monthlyData = buildMonthlyData(instances, seasonStart, seasonEnd)

            // avgPerWeek: total sessions / elapsed full weeks (min 1)
            val elapsedWeeks = maxOf(1, (today.toEpochDays() - seasonStart.toEpochDays()) / 7)
            val avgPerWeek = totalSessions.toDouble() / elapsedWeeks

            // Streak: consecutive weeks ending at current week with ≥1 training
            val currentStreak = computeStreak(weeklyData)

            // Debug: log weekly buckets for easier inspection during testing
            try { Logger.d("StatsViewModel", "weeklyData=${weeklyData.map { it.weekStartIso + ':' + it.count }}") } catch (_: Exception) {}

            // Type breakdown
            val typeData = buildTypeData(instances)

            // Trainer breakdown (top 5)
            val trainerData = buildTrainerData(instances)

            // ── Scoreboard ────────────────────────────────────────────────────
            val myPersonId = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            val scoreEntry: ScoreboardEntry? = if (myPersonId != null) {
                try {
                    withContext(Dispatchers.Default) {
                        peopleService.fetchScoreboard(null, seasonStart.toString(), seasonEnd.toString())
                    }.find { it.personId == myPersonId }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            } else null

            _state.value = _state.value.copy(
                totalSessions = totalSessions,
                totalMinutes = totalMinutes,
                avgSessionsPerWeek = avgPerWeek,
                currentStreak = currentStreak,
                weeklyData = weeklyData,
                monthlyData = monthlyData,
                typeData = typeData,
                trainerData = trainerData,
                scoreEntry = scoreEntry,
                selectedSeason = season,
                isLoading = false,
                error = null
            )
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání statistik")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Fetches summary stats for the last [count] seasons for cross-season comparison. */
    suspend fun loadComparison(count: Int = 5) {
        if (_state.value.isLoadingComparison) return
        _state.value = _state.value.copy(isLoadingComparison = true)
        try {
            val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
            val seasons = SeasonSelection.recent(count, today)
            val summaries = seasons.map { season ->
                val startIso = season.start.toString() + "T00:00:00Z"
                val endIso = season.end.toString() + "T23:59:59Z"
                val instances = try {
                    withContext(Dispatchers.Default) {
                        eventService.fetchEventsGroupedByDay(
                            startRangeIso = startIso,
                            endRangeIso = endIso,
                            onlyMine = true,
                            first = 500,
                            cacheNamespace = "stats_"
                        ).values.flatten()
                    }.filter { !it.isCancelled }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }

                val totalSessions = instances.size
                val totalMinutes = instances.sumOf { durationMin(it.since, it.until) }
                val capDate = if (today <= season.end) today else season.end
                val elapsedWeeks = maxOf(1L, (capDate.toEpochDays() - season.start.toEpochDays()) / 7L)
                val avgPerWeek = totalSessions.toDouble() / elapsedWeeks
                SeasonSummary(season, totalSessions, totalMinutes, avgPerWeek)
            }
            // Auto-assign slots A (0) and B (1) to the two most recent seasons
            val newSeasons = _state.value.compareSeasons.toMutableList()
            if (newSeasons[0] == null) newSeasons[0] = summaries.getOrNull(0)?.season
            if (newSeasons[1] == null) newSeasons[1] = summaries.getOrNull(1)?.season
            _state.value = _state.value.copy(
                comparisonData = summaries,
                isLoadingComparison = false,
                compareSeasons = newSeasons
            )
            // Auto-load detail for pre-filled slots in parallel
            kotlinx.coroutines.coroutineScope {
                newSeasons.forEachIndexed { idx, season ->
                    if (season != null && _state.value.compareData[idx] == null) {
                        launch { loadSeasonDetail(season, idx) }
                    }
                }
            }
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoadingComparison = false)
        }
    }

    /** Loads full season detail (monthly/type/trainer breakdown) for slot [slotIndex] (0=A … 4=E). */
    suspend fun loadSeasonDetail(season: SeasonSelection, slotIndex: Int) {
        val newSeasons = _state.value.compareSeasons.toMutableList().also { it[slotIndex] = season }
        val newLoading = _state.value.isLoadingCompare.toMutableList().also { it[slotIndex] = true }
        _state.value = _state.value.copy(compareSeasons = newSeasons, isLoadingCompare = newLoading)
        try {
            val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
            val startIso = season.start.toString() + "T00:00:00Z"
            val endIso = season.end.toString() + "T23:59:59Z"
            val instances = try {
                withContext(Dispatchers.Default) {
                    eventService.fetchEventsGroupedByDay(
                        startRangeIso = startIso,
                        endRangeIso = endIso,
                        onlyMine = true,
                        first = 500,
                        cacheNamespace = "stats_"
                    ).values.flatten()
                }.filter { !it.isCancelled }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }

            val totalSessions = instances.size
            val totalMinutes = instances.sumOf { durationMin(it.since, it.until) }
            val capDate = if (today <= season.end) today else season.end
            val elapsedWeeks = maxOf(1L, (capDate.toEpochDays() - season.start.toEpochDays()) / 7L)
            val avgPerWeek = totalSessions.toDouble() / elapsedWeeks
            val monthlyData = buildMonthlyData(instances, season.start, season.end)
            val typeData = buildTypeData(instances)
            val trainerData = buildTrainerData(instances)
            val detail = SeasonDetailStats(season, totalSessions, totalMinutes, avgPerWeek, monthlyData, typeData, trainerData)
            val newData = _state.value.compareData.toMutableList().also { it[slotIndex] = detail }
            val doneLoading = _state.value.isLoadingCompare.toMutableList().also { it[slotIndex] = false }
            _state.value = _state.value.copy(compareData = newData, isLoadingCompare = doneLoading)
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            val doneLoading = _state.value.isLoadingCompare.toMutableList().also { it[slotIndex] = false }
            _state.value = _state.value.copy(isLoadingCompare = doneLoading)
        }
    }

    // seasonBounds removed — SeasonSelection contains start/end directly

    /** Duration of a training in minutes based on since/until ISO strings. */
    private fun durationMin(since: String?, until: String?): Long {
        if (since.isNullOrBlank() || until.isNullOrBlank()) return 0L
        return try {
            val s = Instant.parse(since).epochSeconds
            val u = Instant.parse(until).epochSeconds
            maxOf(0L, (u - s) / 60L)
        } catch (_: Exception) {
            try {
                val s = parseToLocal(since) ?: return 0L
                val u = parseToLocal(until) ?: return 0L
                val sEpoch = s.date.toEpochDays().toLong() * 86400L + s.hour * 3600L + s.minute * 60L
                val uEpoch = u.date.toEpochDays().toLong() * 86400L + u.hour * 3600L + u.minute * 60L
                maxOf(0L, (uEpoch - sEpoch) / 60L)
            } catch (_: Exception) { 0L }
        }
    }

    /** Returns the ISO date of the Monday of the week containing [date]. */
    private fun mondayOf(date: LocalDate): LocalDate {
        // Map DayOfWeek to ISO numbers (Mon=1..Sun=7) explicitly to avoid
        // relying on library internals that can change between kotlinx versions.
        val iso = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
            DayOfWeek.SUNDAY -> 7
        }
        val offset = iso - 1 // Monday -> 0
        return date.minus(offset, DateTimeUnit.DAY)
    }

    /** Builds weekly stats for the 16 most recent weeks within the season. */
    private fun buildWeeklyData(
        instances: List<EventInstance>,
        today: LocalDate,
        seasonStart: LocalDate,
        seasonEnd: LocalDate
    ): List<WeekStats> {
        // Index each instance by the Monday of its week.
        // Use local date (converted from instant) when possible so bucketing matches UI timezone.
        val byWeekMonday = mutableMapOf<LocalDate, MutableList<EventInstance>>()
        instances.forEach { inst ->
            val localDate = try {
                com.tkolymp.shared.utils.parseToLocal(inst.since)?.date
            } catch (_: Exception) { null }

            val date = when {
                localDate != null -> localDate
                !inst.since.isNullOrBlank() -> {
                    val dateStr = inst.since.substringBefore('T')
                    try { LocalDate.parse(dateStr) } catch (_: Exception) { null }
                }
                else -> null
            } ?: return@forEach

            val monday = mondayOf(date)
            byWeekMonday.getOrPut(monday) { mutableListOf() }.add(inst)
        }

        // Generate 16 week buckets ending at the week that contains today (or seasonEnd)
        val capDate = if (today <= seasonEnd) today else seasonEnd
        val currentMonday = mondayOf(capDate)
        val result = mutableListOf<WeekStats>()
            for (i in 15 downTo 0) {
            val monday = currentMonday.minus(i * 7, DateTimeUnit.DAY)
            if (monday < seasonStart.minus(7, DateTimeUnit.DAY)) continue // skip weeks before season
            val list = byWeekMonday[monday] ?: emptyList()
            // show week range (Mon–Sun) to make ordering and scope explicit
            val sunday = monday.plus(6, DateTimeUnit.DAY)
            val label = "${monday.dayOfMonth}.${monday.monthNumber}.–${sunday.dayOfMonth}.${sunday.monthNumber}."
            result.add(
                WeekStats(
                    weekLabel = label,
                    weekStartIso = monday.toString(),
                    count = list.size,
                    minutes = list.sumOf { durationMin(it.since, it.until) },
                    isCurrent = (i == 0)
                )
            )
        }
        // Ensure chronological order (oldest -> newest) in case bucketing produced out-of-order entries
        return result.sortedBy { it.weekStartIso }
    }

    /** Groups instances by calendar month (yyyy-MM) within the season. */
    private fun buildMonthlyData(
        instances: List<EventInstance>,
        seasonStart: LocalDate,
        seasonEnd: LocalDate
    ): List<MonthStats> {
        val byMonth = mutableMapOf<String, MutableList<EventInstance>>()
        instances.forEach { inst ->
            val dateStr = inst.since?.substringBefore('T') ?: return@forEach
            val ym = dateStr.take(7) // "yyyy-MM"
            byMonth.getOrPut(ym) { mutableListOf() }.add(inst)
        }

        // Build all months in the season so months with 0 are still present
        val result = mutableListOf<MonthStats>()
        var cursor = LocalDate(seasonStart.year, seasonStart.monthNumber, 1)
        val endMonth = LocalDate(seasonEnd.year, seasonEnd.monthNumber, 1)
        while (cursor <= endMonth) {
            val ym = "${cursor.year}-${cursor.monthNumber.toString().padStart(2, '0')}"
            val list = byMonth[ym] ?: emptyList()
            val label = "${cursor.monthNumber.toString().padStart(2, '0')}/${cursor.year}"
            result.add(
                MonthStats(
                    monthLabel = label,
                    yearMonth = ym,
                    count = list.size,
                    minutes = list.sumOf { durationMin(it.since, it.until) }
                )
            )
            cursor = cursor.plus(1, DateTimeUnit.MONTH)
        }
        // Only keep months that have data OR are within [seasonStart, today]
        return result.filter { it.count > 0 }
    }

    /** Groups by event type. Unknown types kept as-is. */
    private fun buildTypeData(instances: List<EventInstance>): List<TypeStat> {
        val byType = mutableMapOf<String, Int>()
        instances.forEach { inst ->
            val type = inst.event?.type?.lowercase()?.trim() ?: "unknown"
            byType[type] = (byType[type] ?: 0) + 1
        }
        return byType.entries
            .sortedByDescending { it.value }
            .map { (type, count) ->
                TypeStat(
                    type = type,
                    displayName = translateType(type),
                    count = count
                )
            }
    }

    /** Groups by trainer name, returns top-5 sorted by count desc. */
    private fun buildTrainerData(instances: List<EventInstance>): List<TrainerStat> {
        val byTrainer = mutableMapOf<String, Pair<Int, Long>>() // name → (count, minutes)
        instances.forEach { inst ->
            val raw = inst.event?.eventTrainersList ?: emptyList()
            val dur = durationMin(inst.since, inst.until)
            // Normalize: trim, drop blank, dedupe so a duplicated name in the list
            // doesn't inflate counts for the same trainer on one instance.
            val trainers = raw.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

            if (trainers.isEmpty()) {
                val key = "(unknown)"
                val prev = byTrainer[key] ?: Pair(0, 0L)
                byTrainer[key] = Pair(prev.first + 1, prev.second + dur)
            } else {
                trainers.forEach { trainerName ->
                    val key = trainerName.ifBlank { "(unknown)" }
                    val prev = byTrainer[key] ?: Pair(0, 0L)
                    byTrainer[key] = Pair(prev.first + 1, prev.second + dur)
                }
            }
        }
        return byTrainer.entries
            .sortedByDescending { it.value.first }
            .take(5)
            .map { (name, pair) -> TrainerStat(name, pair.first, pair.second) }
    }

    /** Consecutive weeks ending at current week (the last isCurrent=true week) with count > 0. */
    private fun computeStreak(weeklyData: List<WeekStats>): Int {
        // Walk from last to first and count consecutive non-zero weeks
        var streak = 0
        // Skip the current week (partially done) when computing strict streak
        val reversedData = weeklyData.reversed()
        // Start from the current week index; if it has count > 0 it extends the streak
        for (week in reversedData) {
            if (week.count > 0) streak++ else break
        }
        return streak
    }

    private fun translateType(type: String): String {
        return try {
            com.tkolymp.shared.utils.translateEventType(type) ?: type
        } catch (_: Exception) { type }
    }

    /** Clears a comparison slot (0=A … 4=E). */
    fun clearCompare(slotIndex: Int) {
        val newSeasons = _state.value.compareSeasons.toMutableList().also { it[slotIndex] = null }
        val newData = _state.value.compareData.toMutableList().also { it[slotIndex] = null }
        _state.value = _state.value.copy(compareSeasons = newSeasons, compareData = newData)
    }
}
