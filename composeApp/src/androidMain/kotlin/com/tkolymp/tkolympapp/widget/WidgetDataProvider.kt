package com.tkolymp.tkolympapp.widget

import android.content.Context
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.calendar.CalendarUtils
import com.tkolymp.shared.calendar.TimelineEvent
import com.tkolymp.shared.competitions.Competition
import com.tkolymp.shared.initNetworking
import com.tkolymp.shared.people.Person
import com.tkolymp.tkolympapp.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class BirthdayEntry(val person: Person, val daysUntil: Int, val age: Int)

object WidgetDataProvider {

    private val mutex = Mutex()

    suspend fun ensureInitialized(context: Context) {
        if (ServiceLocator.isInitialized) return
        mutex.withLock {
            if (ServiceLocator.isInitialized) return
            initNetworking(context.applicationContext, BuildConfig.API_BASE_URL, BuildConfig.TENANT_ID)
        }
    }

    suspend fun isLoggedIn(context: Context): Boolean {
        return try {
            ensureInitialized(context)
            ServiceLocator.authService.hasToken()
        } catch (_: Exception) { false }
    }

    suspend fun fetchMyUpcomingEvents(context: Context, limit: Int = 3): List<TimelineEvent> {
        return try {
            ensureInitialized(context)
            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.todayIn(tz)
            val todayIso = "${today}T00:00:00Z"
            val endDate = today.plus(14, DateTimeUnit.DAY)
            val endIso = "${endDate}T23:59:59Z"

            val personId = ServiceLocator.userService.getCachedPersonId()
            val coupleIds = ServiceLocator.userService.getCachedCoupleIds()

            val grouped = ServiceLocator.eventService.fetchEventsGroupedByDay(
                startRangeIso = todayIso,
                endRangeIso = endIso,
                cacheNamespace = "widget"
            )

            val nowLocal = Clock.System.now().toLocalDateTime(tz)

            grouped.values.flatten()
                .mapNotNull { CalendarUtils.eventInstanceToTimelineEvent(it, personId, coupleIds) }
                .filter { !it.isCancelled && it.isMyEvent && it.startTime >= nowLocal }
                .sortedBy { it.startTime }
                .take(limit)
        } catch (_: Exception) { emptyList() }
    }

    suspend fun fetchUpcomingBirthdays(context: Context, limit: Int = 3): List<BirthdayEntry> {
        return try {
            ensureInitialized(context)
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            val todayEpoch = today.toEpochDays()
            val people = ServiceLocator.peopleService.fetchPeople()
            people
                .mapNotNull { person ->
                    val bd = person.birthDate ?: return@mapNotNull null
                    val parsed = runCatching { LocalDate.parse(bd) }.getOrNull() ?: return@mapNotNull null
                    val thisYearBd = LocalDate(today.year, parsed.month, parsed.dayOfMonth)
                    val nextBd = if (thisYearBd.toEpochDays() >= todayEpoch) thisYearBd
                                 else LocalDate(today.year + 1, parsed.month, parsed.dayOfMonth)
                    val daysUntil = (nextBd.toEpochDays() - todayEpoch).toInt()
                    if (daysUntil > 30) return@mapNotNull null
                    val age = nextBd.year - parsed.year
                    BirthdayEntry(person, daysUntil, age)
                }
                .sortedBy { it.daysUntil }
                .take(limit)
        } catch (_: Exception) { emptyList() }
    }

    suspend fun fetchNextCompetition(context: Context): Competition? {
        return try {
            ensureInitialized(context)
            ServiceLocator.competitionService.getNearestUpcoming(pPersonIds = null)
        } catch (_: Exception) { null }
    }
}
