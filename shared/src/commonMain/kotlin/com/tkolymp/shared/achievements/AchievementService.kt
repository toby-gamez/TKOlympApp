package com.tkolymp.shared.achievements

import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.competitions.Competition
import com.tkolymp.shared.competitions.ICompetitionService
import com.tkolymp.shared.event.AttendanceRepository
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.EventType
import com.tkolymp.shared.event.IEventService
import com.tkolymp.shared.people.CouplePeriod
import com.tkolymp.shared.people.PeopleService
import com.tkolymp.shared.user.UserService
import com.tkolymp.shared.utils.AppConstants
import com.tkolymp.shared.utils.DateRangeConstants
import com.tkolymp.shared.utils.parseToLocal
import com.tkolymp.shared.viewmodels.SeasonSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.todayIn

/**
 * Fetches and derives the raw signals [AchievementEngine] evaluates badges against.
 *
 * Camp history covers the user's full lifetime (camps are infrequent, so a single wide
 * date-range query is cheap). Regular lesson streak/volume is bounded to
 * [LESSON_HISTORY_SEASONS] seasons rather than truly unbounded, but wide enough to cover
 * long-standing members.
 */
class AchievementService(
    private val eventService: IEventService = ServiceLocator.eventService,
    private val peopleService: PeopleService = ServiceLocator.peopleService,
    private val userService: UserService = ServiceLocator.userService,
    private val competitionService: ICompetitionService = ServiceLocator.competitionService,
    private val attendanceRepository: AttendanceRepository = AttendanceRepository(),
    private val storage: AchievementStorage = AchievementStorage(),
) {
    companion object {
        private const val LESSON_HISTORY_SEASONS = 15
        private const val COMPETITION_HISTORY_YEARS = 20
        private const val COMPETITION_HISTORY_LIMIT = 2000
    }

    suspend fun loadContext(): AchievementContext {
        val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
        val personId = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        Logger.d("AchievementService", "loadContext: personId=$personId today=$today")

        val attendance = if (!personId.isNullOrBlank()) {
            try { attendanceRepository.fetchAttendanceStatuses(personId) } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                Logger.d("AchievementService", "attendance fetch failed: ${ex.message}")
                emptyMap()
            }
        } else emptyMap()
        Logger.d("AchievementService", "attendance entries=${attendance.size} sampleStatuses=${attendance.values.toList().take(10)}")

        val campInstances = try {
            withContext(Dispatchers.Default) {
                val start = today.minus(DatePeriod(years = 15))
                eventService.fetchEventsGroupedByDay(
                    startRangeIso = "${start}T00:00:00Z",
                    endRangeIso = "${today}T23:59:59Z",
                    onlyMine = true,
                    first = AppConstants.FETCH_LIMIT_PERIOD,
                    onlyType = EventType.CAMP.rawValue,
                    cacheNamespace = "achievements_"
                ).values.flatten()
            }
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            Logger.d("AchievementService", "camp fetch failed: ${ex.message}")
            emptyList()
        }
        Logger.d(
            "AchievementService",
            "campInstances=${campInstances.size} " +
                campInstances.take(10).joinToString(prefix = "[", postfix = "]") {
                    "(eventId=${it.event?.id}, name=${it.event?.name}, since=${it.since}, until=${it.until}, instId=${it.id}, cancelled=${it.isCancelled}, attendance=${attendance[it.id]})"
                }
        )

        val completedCamps = buildCampOccurrences(campInstances, attendance, today)
        Logger.d(
            "AchievementService",
            "completedCamps=${completedCamps.size} " +
                completedCamps.joinToString(prefix = "[", postfix = "]") { "(${it.name}, ${it.startDate}..${it.endDate}, season=${it.seasonStartYear}, perfect=${it.attendedAllDays})" }
        )

        val person = if (!personId.isNullOrBlank()) {
            try {
                peopleService.fetchPerson(personId)
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                Logger.d("AchievementService", "person fetch failed: ${ex.message}")
                null
            }
        } else null

        val memberSinceDate = person?.cohortMembershipsList
            ?.mapNotNull { it.since?.take(10) }
            ?.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.minOrNull()
        Logger.d("AchievementService", "memberSinceDate=$memberSinceDate")

        val longestPartnershipSeasons = if (!personId.isNullOrBlank()) {
            derivePartnershipSeasons(person?.allCouplesList.orEmpty(), personId, today)
        } else 0
        Logger.d("AchievementService", "longestPartnershipSeasons=$longestPartnershipSeasons")

        val recentSeasonInstances = try {
            coroutineScope {
                SeasonSelection.recent(LESSON_HISTORY_SEASONS, today).map { season ->
                    async(Dispatchers.Default) {
                        try {
                            eventService.fetchEventsGroupedByDay(
                                startRangeIso = "${season.start}T00:00:00Z",
                                endRangeIso = "${season.end}T23:59:59Z",
                                onlyMine = true,
                                first = AppConstants.FETCH_LIMIT_PERIOD,
                                cacheNamespace = "stats_"
                            ).values.flatten()
                        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
                    }
                }.awaitAll().flatten()
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }

        // Mirrors StatsViewModel's totalSessions: count every scheduled, non-cancelled lesson
        // regardless of attendance-marking, since trainers rarely mark individual lessons ATTENDED
        // (attendance is really only tracked for camps) — requiring it left this permanently at 0.
        val attendedLessonDates = recentSeasonInstances
            .filter { !it.isCancelled }
            .mapNotNull { it.since?.take(10) }
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        Logger.d(
            "AchievementService",
            "recentSeasonInstances=${recentSeasonInstances.size} attendedLessonDates=${attendedLessonDates.size} " +
                "longestStreak=${longestWeeklyStreak(attendedLessonDates)}"
        )

        val nonCancelledInstances = recentSeasonInstances.filter { !it.isCancelled }

        val distinctTrainers = nonCancelledInstances
            .flatMap { it.event?.eventTrainersList ?: emptyList() }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .size

        val distinctEventTypes = nonCancelledInstances
            .mapNotNull { it.event?.type?.lowercase()?.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .size

        val instanceTimestamps = nonCancelledInstances.mapNotNull { parseToLocal(it.since) }
        val earlyBirdCount = instanceTimestamps.count { it.hour < 8 }
        val nightOwlCount = instanceTimestamps.count { it.hour >= 20 }
        val weekendLessonsCount = instanceTimestamps.count {
            it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY
        }

        val returnedAfterBreak = attendedLessonDates.distinct().sorted()
            .zipWithNext()
            .any { (a, b) -> b.toEpochDays() - a.toEpochDays() >= 90 }

        val pastCompetitions = fetchPastCompetitions(personId)
        val competitionStats = deriveCompetitionStats(pastCompetitions)

        Logger.d(
            "AchievementService",
            "competitionsCompleted=${competitionStats.completed} finals=${competitionStats.finalsReached} bestRanking=${competitionStats.bestRanking} " +
                "danceStyles=${competitionStats.distinctDanceStyles} trainers=$distinctTrainers eventTypes=$distinctEventTypes earlyBird=$earlyBirdCount " +
                "nightOwl=$nightOwlCount weekend=$weekendLessonsCount comeback=$returnedAfterBreak longestPartnershipSeasons=$longestPartnershipSeasons"
        )

        return AchievementContext(
            completedCamps = completedCamps,
            memberSinceDate = memberSinceDate,
            today = today,
            longestStreakWeeks = longestWeeklyStreak(attendedLessonDates),
            totalLessonsAttended = attendedLessonDates.size,
            competitionsCompleted = competitionStats.completed,
            competitionFinalsReached = competitionStats.finalsReached,
            bestRanking = competitionStats.bestRanking,
            distinctDanceStyles = competitionStats.distinctDanceStyles,
            longestPartnershipSeasons = longestPartnershipSeasons,
            distinctTrainers = distinctTrainers,
            distinctEventTypes = distinctEventTypes,
            earlyBirdCount = earlyBirdCount,
            nightOwlCount = nightOwlCount,
            weekendLessonsCount = weekendLessonsCount,
            returnedAfterBreak = returnedAfterBreak,
        )
    }

    /**
     * Read-only achievement context for an arbitrary club member (e.g. viewed from their profile
     * screen) — never touches [AchievementStorage], which holds only the *current device user's*
     * own earned-badge/seen-diploma state. Only categories backed by data sources that already
     * accept an explicit personId are computed (Camp, Membership, Competitions); lesson-based
     * fields (streaks, trainer/type variety, time-of-day counters) are left at their defaults
     * because the only way to get another person's lesson history is an unbounded, unpaginated
     * club-wide query — the UI filters those categories out for other people rather than risk
     * silently-truncated data.
     */
    suspend fun loadContextForPerson(personId: String): AchievementContext {
        val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())

        val attendance = try {
            attendanceRepository.fetchAttendanceStatuses(personId)
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            Logger.d("AchievementService", "loadContextForPerson: attendance fetch failed: ${ex.message}")
            emptyMap()
        }

        // Club-wide (not onlyMine) — camps are infrequent enough that this is the same safe
        // pattern OfflineSyncManager already uses, then filtered down to this person's registrations.
        val clubCampInstances = try {
            withContext(Dispatchers.Default) {
                eventService.fetchEventsGroupedByDay(
                    startRangeIso = DateRangeConstants.FAR_PAST,
                    endRangeIso = DateRangeConstants.FAR_FUTURE,
                    onlyMine = false,
                    first = AppConstants.FETCH_LIMIT_FULL,
                    onlyType = EventType.CAMP.rawValue,
                    cacheNamespace = null,
                ).values.flatten()
            }
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            Logger.d("AchievementService", "loadContextForPerson: camp fetch failed: ${ex.message}")
            emptyList()
        }
        val personCampInstances = clubCampInstances.filter { inst ->
            inst.event?.eventRegistrationsList?.any { it.person?.id?.toString() == personId } == true
        }
        val completedCamps = buildCampOccurrences(personCampInstances, attendance, today)

        val person = try {
            peopleService.fetchPerson(personId)
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            Logger.d("AchievementService", "loadContextForPerson: person fetch failed: ${ex.message}")
            null
        }
        val memberSinceDate = person?.cohortMembershipsList
            ?.mapNotNull { it.since?.take(10) }
            ?.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.minOrNull()
        val longestPartnershipSeasons = derivePartnershipSeasons(person?.allCouplesList.orEmpty(), personId, today)

        val competitionStats = deriveCompetitionStats(fetchPastCompetitions(personId))

        return AchievementContext(
            completedCamps = completedCamps,
            memberSinceDate = memberSinceDate,
            today = today,
            longestStreakWeeks = 0,
            totalLessonsAttended = 0,
            competitionsCompleted = competitionStats.completed,
            competitionFinalsReached = competitionStats.finalsReached,
            bestRanking = competitionStats.bestRanking,
            distinctDanceStyles = competitionStats.distinctDanceStyles,
            longestPartnershipSeasons = longestPartnershipSeasons,
        )
    }

    // getPastCompetitions() defaults to just the last year when pSince/pUntil aren't given —
    // fine for the competitions screen, but it silently capped every competition-based badge
    // (counts, dance styles, partnership seasons) to whatever fits in a single year.
    private suspend fun fetchPastCompetitions(personId: String?): List<Competition> {
        if (personId.isNullOrBlank()) return emptyList()
        return try {
            val today = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault())
            competitionService.getPastCompetitions(
                pSince = today.minus(DatePeriod(years = COMPETITION_HISTORY_YEARS)).toString(),
                pUntil = today.toString(),
                pPersonIds = listOfNotNull(personId.toLongOrNull()),
                first = COMPETITION_HISTORY_LIMIT,
            )
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            Logger.d("AchievementService", "competitions fetch failed: ${ex.message}")
            emptyList()
        }
    }

    /** Evaluates badges from fresh data, keeps original earned dates for already-known badges, and persists the result. */
    suspend fun evaluateAndPersist(): AchievementUpdateResult {
        val context = loadContext()
        val computed = AchievementEngine.evaluate(context)
        val hadPriorBadgeRecord = storage.hasStoredBadges()
        val previouslyEarned = storage.loadEarnedBadges()
        val previouslyEarnedIds = previouslyEarned.map { it.id }.toSet()
        val merged = computed.map { badge -> previouslyEarned.firstOrNull { it.id == badge.id } ?: badge }
        storage.saveEarnedBadges(merged)
        // On the very first run there's no real "since last visit" baseline — e.g. a long-time
        // member's first-ever evaluate() can retroactively earn many historical badges at once,
        // none of which are actually "new". Only flag newly-earned badges once a baseline exists.
        val newlyEarnedIds = if (hadPriorBadgeRecord) merged.map { it.id }.toSet() - previouslyEarnedIds else emptySet()

        val currentDiplomaIds = context.completedCamps.map { it.eventId }.toSet()
        val hadPriorDiplomaRecord = storage.hasStoredSeenDiplomaIds()
        val previouslySeenDiplomaIds = storage.loadSeenDiplomaIds()
        val newlyEarnedDiplomaEventIds =
            if (hadPriorDiplomaRecord) currentDiplomaIds - previouslySeenDiplomaIds else emptySet()
        storage.saveSeenDiplomaIds(previouslySeenDiplomaIds + currentDiplomaIds)

        return AchievementUpdateResult(context, merged, newlyEarnedIds, newlyEarnedDiplomaEventIds)
    }
}

data class AchievementUpdateResult(
    val context: AchievementContext,
    val earnedBadges: List<EarnedBadge>,
    val newlyEarnedIds: Set<String>,
    val newlyEarnedDiplomaEventIds: Set<Long> = emptySet(),
)

private data class CampDay(val instance: EventInstance, val start: LocalDate, val end: LocalDate)

/**
 * Groups flat per-day camp instances into multi-day "occurrences" (one soustředění = several
 * consecutive days). [EventInstance.event] is synthesized per-day by the API client with
 * `event.id == instance.id` (there's no real parent/series id in `eventInstancesForRangeList`),
 * so grouping by `event.id` would put every single day in its own group. Instead, consecutive
 * (gap <= 1 day) same-name instances are clustered together, each contributing its own
 * since..until span so a single instance that already spans several days (or several single-day
 * instances back to back) both produce the correct overall start/end date.
 */
private fun buildCampOccurrences(
    instances: List<EventInstance>,
    attendance: Map<Long, String>,
    today: LocalDate,
): List<CampOccurrence> {
    val days = instances
        .filter { !it.isCancelled }
        .mapNotNull { inst ->
            val start = inst.since?.take(10)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val end = inst.until?.take(10)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: start
            CampDay(inst, start, end)
        }

    val clusters = days
        .groupBy { it.instance.event?.name }
        .values
        .flatMap { sameName ->
            val sorted = sameName.sortedBy { it.start }
            val result = mutableListOf<MutableList<CampDay>>()
            for (day in sorted) {
                val current = result.lastOrNull()
                if (current != null && day.start.toEpochDays() - current.last().end.toEpochDays() <= 1) {
                    current += day
                } else {
                    result += mutableListOf(day)
                }
            }
            result
        }

    return clusters.mapNotNull { cluster ->
        val eventId = cluster.first().instance.event?.id ?: return@mapNotNull null
        val start = cluster.minOf { it.start }
        val end = cluster.maxOf { it.end }
        if (end >= today) return@mapNotNull null
        // `instances` is already fetched with onlyMine=true, so being registered/scheduled for a
        // past camp is treated as completion — eventAttendancesList is empty for many clubs (never
        // populated by the backend), so requiring an explicit ATTENDED status here left this at 0
        // for everyone, same root cause as the lessons badge.
        val statuses = cluster.map { attendance[it.instance.id] }
        CampOccurrence(
            eventId = eventId,
            name = cluster.first().instance.event?.name,
            startDate = start,
            endDate = end,
            seasonStartYear = if (start.month.number >= 9) start.year else start.year - 1,
            attendedAllDays = statuses.all { it == "ATTENDED" },
        )
    }.sortedBy { it.startDate }
}

/** Sep 1 -> Aug 31 season boundary, matching [CampOccurrence.seasonStartYear] and [SeasonSelection]. */
private fun seasonYearOf(date: LocalDate): Int = if (date.month.number >= 9) date.year else date.year - 1

private data class CompetitionStats(
    val completed: Int,
    val finalsReached: Int,
    val bestRanking: Int?,
    val distinctDanceStyles: Int,
)

/** Shared by [AchievementService.loadContext] and [AchievementService.loadContextForPerson]. */
private fun deriveCompetitionStats(pastCompetitions: List<Competition>): CompetitionStats {
    val competitionsWithResult = pastCompetitions.filter { it.hasResult }

    // `Competition.dances` comes back an empty array for every real entry observed (the backend
    // doesn't populate it) — `category.discipline` (e.g. "Standard"/"Latin") is what's actually
    // populated, and is the right granularity for a "competes in multiple disciplines" badge.
    val distinctDisciplines = pastCompetitions
        .mapNotNull { it.category?.discipline?.trim()?.lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()
        .size

    return CompetitionStats(
        completed = competitionsWithResult.size,
        finalsReached = competitionsWithResult.count { it.isFinal },
        bestRanking = competitionsWithResult.mapNotNull { it.ranking }.minOrNull(),
        distinctDanceStyles = distinctDisciplines,
    )
}

/**
 * Longest span of distinct seasons spent with the same dance partner, from the club's own
 * man/woman couple records (real `since`/`until` dates) rather than the CSTS competition feed —
 * that federation feed only has ~2 seasons of digitized results for some members, badly
 * undercounting long-standing partnerships that predate it.
 */
private fun derivePartnershipSeasons(couples: List<CouplePeriod>, personId: String, today: LocalDate): Int {
    val myId = personId.trim()
    return couples
        .filter { it.status?.uppercase() == "ACTIVE" || it.status?.uppercase() == "EXPIRED" }
        .mapNotNull { couple ->
            val partnerId = when (myId) {
                couple.manId?.trim() -> couple.womanId
                couple.womanId?.trim() -> couple.manId
                else -> null
            } ?: return@mapNotNull null
            val since = couple.since?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val until = couple.until?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
            partnerId to (since to until)
        }
        .groupBy({ it.first }, { it.second })
        .maxOfOrNull { (_, periods) ->
            periods.flatMap { (since, until) -> (seasonYearOf(since)..seasonYearOf(until)) }.distinct().size
        } ?: 0
}

private fun longestWeeklyStreak(dates: List<LocalDate>): Int {
    if (dates.isEmpty()) return 0
    val weeks = dates.map { it.toEpochDays() / 7 }.toSortedSet().toList()
    var longest = 1
    var current = 1
    for (i in 1 until weeks.size) {
        current = if (weeks[i] == weeks[i - 1] + 1) current + 1 else 1
        longest = maxOf(longest, current)
    }
    return longest
}
