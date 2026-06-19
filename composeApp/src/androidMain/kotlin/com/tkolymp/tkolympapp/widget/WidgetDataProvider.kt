package com.tkolymp.tkolympapp.widget

import android.content.Context
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.calendar.CalendarUtils
import com.tkolymp.shared.competitions.Competition
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.firstTrainerOrEmpty
import com.tkolymp.shared.initNetworking
import com.tkolymp.shared.language.AppLanguage
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.people.Person
import com.tkolymp.tkolympapp.BuildConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class BirthdayEntry(val person: Person, val daysUntil: Int, val age: Int)

data class GroupedWidgetEvent(
    val eventId: Long?,
    val title: String,
    val colorRgb: String?,   // null = lesson (use surfaceVariant bar like LessonView)
    val location: String?,
    val slots: List<LocalDateTime>  // start times, sorted ascending
)

object WidgetDataProvider {

    private val mutex = Mutex()

    suspend fun ensureInitialized(context: Context) {
        if (ServiceLocator.isInitialized) return
        mutex.withLock {
            if (ServiceLocator.isInitialized) return
            initNetworking(context.applicationContext, BuildConfig.API_BASE_URL, BuildConfig.TENANT_ID)
        }
        // Apply saved language so widget strings match app language preference
        try {
            val code = ServiceLocator.languageStorage.getLanguageCode()
            if (code != null) AppStrings.setLanguage(AppLanguage.fromCode(code))
        } catch (_: Exception) {}
    }

    suspend fun isLoggedIn(context: Context): Boolean {
        return try {
            ensureInitialized(context)
            ServiceLocator.authService.hasToken()
        } catch (_: Exception) { false }
    }

    suspend fun fetchMyUpcomingEvents(context: Context, limit: Int = 10): List<GroupedWidgetEvent> {
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

            // Convert all instances to TimelineEvents, keeping the original EventInstance alongside
            val all = grouped.values.flatten()
                .mapNotNull { inst ->
                    val te = CalendarUtils.eventInstanceToTimelineEvent(inst, personId, coupleIds)
                        ?: return@mapNotNull null
                    if (te.isCancelled || !te.isMyEvent || te.startTime < nowLocal) return@mapNotNull null
                    Pair(inst, te)
                }

            val lessonPairs = all.filter { (inst, _) -> isLesson(inst) }
            val otherPairs  = all.filter { (inst, _) -> !isLesson(inst) }

            // Group lessons by trainer name — matches CalendarViewModel.splitEventMaps exactly
            val lessonGroups = lessonPairs
                .groupBy { (inst, _) -> inst.event.firstTrainerOrEmpty() }
                .map { (trainer, pairs) ->
                    val sorted = pairs.sortedBy { (_, te) -> te.startTime }
                    val firstInst = sorted.first().first
                    GroupedWidgetEvent(
                        eventId = sorted.first().second.eventId,
                        title = trainer,
                        colorRgb = null,   // surfaceVariant bar, same as LessonView
                        location = firstInst.event?.locationText?.takeIf { it.isNotBlank() },
                        slots = sorted.map { (_, te) -> te.startTime }
                    )
                }

            // Group other events by eventId (same event may appear once, but keep the groupBy for safety)
            val otherGroups = otherPairs
                .groupBy { (_, te) -> te.eventId ?: te.id }
                .map { (_, pairs) ->
                    val sorted = pairs.sortedBy { (_, te) -> te.startTime }
                    val firstTe = sorted.first().second
                    val firstInst = sorted.first().first
                    GroupedWidgetEvent(
                        eventId = firstTe.eventId,
                        title = firstTe.title,
                        colorRgb = firstTe.colorRgb,
                        location = firstInst.event?.locationText?.takeIf { it.isNotBlank() },
                        slots = sorted.map { (_, te) -> te.startTime }
                    )
                }

            (lessonGroups + otherGroups)
                .sortedBy { it.slots.first() }
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

    // Mirrors CalendarViewModel.isLesson — event has a non-blank trainer name
    private fun isLesson(inst: EventInstance): Boolean =
        inst.event?.eventTrainersList.orEmpty().isNotEmpty() &&
        !inst.event?.eventTrainersList?.firstOrNull().isNullOrBlank()
}
