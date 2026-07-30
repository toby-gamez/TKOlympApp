package com.tkolymp.shared.achievements

import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.event.AttendanceRepository
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.EventType
import com.tkolymp.shared.event.IEventService
import com.tkolymp.shared.people.PeopleService
import com.tkolymp.shared.user.UserService
import com.tkolymp.shared.utils.AppConstants
import com.tkolymp.shared.viewmodels.SeasonSelection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.todayIn

/**
 * Fetches and derives the raw signals [AchievementEngine] evaluates badges against.
 *
 * Camp history and attendance are cheap single queries (camps are infrequent, and
 * attendance is one query for the whole person), so they cover the user's full
 * lifetime. Regular lesson streak/volume would require an unbounded historical scan
 * to do the same, so it's bounded to the last few seasons — the same trade-off
 * [com.tkolymp.shared.viewmodels.StatsViewModel.loadComparison] already makes.
 */
class AchievementService(
    private val eventService: IEventService = ServiceLocator.eventService,
    private val peopleService: PeopleService = ServiceLocator.peopleService,
    private val userService: UserService = ServiceLocator.userService,
    private val attendanceRepository: AttendanceRepository = AttendanceRepository(),
    private val storage: AchievementStorage = AchievementStorage(),
) {
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

        val memberSinceDate = if (!personId.isNullOrBlank()) {
            try {
                peopleService.fetchPerson(personId)?.cohortMembershipsList
                    ?.mapNotNull { it.since?.take(10) }
                    ?.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
                    ?.minOrNull()
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                Logger.d("AchievementService", "member-since fetch failed: ${ex.message}")
                null
            }
        } else null
        Logger.d("AchievementService", "memberSinceDate=$memberSinceDate")

        val recentSeasonInstances = try {
            coroutineScope {
                SeasonSelection.recent(4, today).map { season ->
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

        val attendedLessonDates = recentSeasonInstances
            .filter { !it.isCancelled && attendance[it.id] == "ATTENDED" }
            .mapNotNull { it.since?.take(10) }
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
        Logger.d(
            "AchievementService",
            "recentSeasonInstances=${recentSeasonInstances.size} attendedLessonDates=${attendedLessonDates.size} " +
                "longestStreak=${longestWeeklyStreak(attendedLessonDates)}"
        )

        return AchievementContext(
            completedCamps = completedCamps,
            memberSinceDate = memberSinceDate,
            today = today,
            longestStreakWeeks = longestWeeklyStreak(attendedLessonDates),
            totalLessonsAttended = attendedLessonDates.size,
        )
    }

    /** Evaluates badges from fresh data, keeps original earned dates for already-known badges, and persists the result. */
    suspend fun evaluateAndPersist(): AchievementUpdateResult {
        val context = loadContext()
        val computed = AchievementEngine.evaluate(context)
        val previouslyEarned = storage.loadEarnedBadges()
        val previouslyEarnedIds = previouslyEarned.map { it.id }.toSet()
        val merged = computed.map { badge -> previouslyEarned.firstOrNull { it.id == badge.id } ?: badge }
        storage.saveEarnedBadges(merged)
        val newlyEarnedIds = merged.map { it.id }.toSet() - previouslyEarnedIds
        return AchievementUpdateResult(context, merged, newlyEarnedIds)
    }
}

data class AchievementUpdateResult(
    val context: AchievementContext,
    val earnedBadges: List<EarnedBadge>,
    val newlyEarnedIds: Set<String>,
)

private fun buildCampOccurrences(
    instances: List<EventInstance>,
    attendance: Map<Long, String>,
    today: LocalDate,
): List<CampOccurrence> = instances
    .filter { !it.isCancelled }
    .groupBy { it.event?.id }
    .mapNotNull { (eventId, dayInstances) ->
        if (eventId == null) return@mapNotNull null
        val start = dayInstances.mapNotNull { it.since?.take(10) }
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .minOrNull() ?: return@mapNotNull null
        val end = dayInstances.mapNotNull { it.until?.take(10) }
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .maxOrNull() ?: start
        if (end >= today) return@mapNotNull null
        val statuses = dayInstances.map { attendance[it.id] }
        if (statuses.none { it == "ATTENDED" }) return@mapNotNull null
        CampOccurrence(
            eventId = eventId,
            name = dayInstances.firstOrNull()?.event?.name,
            startDate = start,
            endDate = end,
            seasonStartYear = if (start.month.number >= 9) start.year else start.year - 1,
            attendedAllDays = statuses.all { it == "ATTENDED" },
        )
    }
    .sortedBy { it.startDate }

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
