package com.tkolymp.tkolympapp.widget

import android.content.Context
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.calendar.CalendarUtils
import com.tkolymp.shared.competitions.Competition
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.event.EventType
import com.tkolymp.shared.event.firstTrainerOrEmpty
import com.tkolymp.shared.event.toEventType
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.language.AppLanguage
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.people.Person
import com.tkolymp.shared.utils.daysUntilNextBirthday
import com.tkolymp.shared.utils.turningAgeOnNextBirthday
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant

data class BirthdayEntry(val person: Person, val daysUntil: Int, val age: Int)

data class GroupedWidgetEvent(
    val eventId: Long?,
    val instanceId: Long?,   // first instance id — for deep link "event/{eventId}?instanceId={instanceId}"
    val title: String,
    val colorRgb: String?,   // null = lesson (use surfaceVariant bar like LessonView)
    val location: String?,
    val slots: List<LocalDateTime>  // start times, sorted ascending
)

data class NearestDayResult(
    val dateString: String,
    val events: List<GroupedWidgetEvent>
)

object WidgetDataProvider {

    private const val INIT_TIMEOUT_MS = 10_000L

    suspend fun ensureInitialized(context: Context) {
        // TKOlympApplication.onCreate() starts initNetworking() on a background coroutine, so
        // when the system cold-starts the process just to update a widget, this code can run
        // before ServiceLocator is ready. Without waiting, every ServiceLocator access throws,
        // which the callers below swallow as "not logged in" / "nothing upcoming".
        withTimeoutOrNull(INIT_TIMEOUT_MS) {
            while (!ServiceLocator.isInitialized) delay(20)
        }
        // Apply saved language so widget strings match the app language preference.
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
                        instanceId = firstInst.id,
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
                        instanceId = firstInst.id,
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
            // fetchPeople() returns an empty list on any network failure; fall back to the
            // copy OfflineSyncManager saved so a flaky background refresh doesn't blank the widget.
            val people = ServiceLocator.peopleService.fetchPeople().ifEmpty { loadOfflinePeople() }
            // Use the same helpers as the app. Building LocalDate(thisYear, month, day) by hand
            // threw for anyone born on 29 February in a non-leap year, and that exception
            // emptied the whole list ("no upcoming birthdays").
            people
                .mapNotNull { person ->
                    val daysUntil = daysUntilNextBirthday(person.birthDate)
                    if (daysUntil > 30) return@mapNotNull null
                    val age = turningAgeOnNextBirthday(person.birthDate) ?: return@mapNotNull null
                    BirthdayEntry(person, daysUntil, age)
                }
                .sortedBy { it.daysUntil }
                .take(limit)
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun loadOfflinePeople(): List<Person> {
        val raw = try { ServiceLocator.offlineSyncManager.loadPeople() } catch (_: Exception) { null } ?: return emptyList()
        return try {
            AppJson.parseToJsonElement(raw).jsonArray.mapNotNull { el ->
                val jo = el as? JsonObject ?: return@mapNotNull null
                fun str(key: String) = jo[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                val id = str("id") ?: return@mapNotNull null
                Person(id, str("firstName"), str("lastName"), str("prefixTitle"), str("suffixTitle"), str("birthDate"), emptyList())
            }
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

    // Mirrors OverviewViewModel.isLesson — requires EventType.LESSON + non-blank trainer
    private fun isLessonOverview(inst: EventInstance): Boolean {
        val ev = inst.event ?: return false
        return ev.type?.toEventType() == EventType.LESSON &&
            ev.eventTrainersList.orEmpty().isNotEmpty() &&
            !ev.eventTrainersList.firstOrNull().isNullOrBlank()
    }

    suspend fun fetchNearestTrainingDay(context: Context): NearestDayResult? {
        return try {
            ensureInitialized(context)
            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.todayIn(tz)
            val todayString = today.toString()
            val todayIso = "${today}T00:00:00Z"
            val endDate = today.plus(365, DateTimeUnit.DAY)
            val endIso = "${endDate}T23:59:59Z"
            val nowInstant = Clock.System.now()

            val personId = ServiceLocator.userService.getCachedPersonId()
            val coupleIds = ServiceLocator.userService.getCachedCoupleIds()

            val grouped = ServiceLocator.eventService.fetchEventsGroupedByDay(
                startRangeIso = todayIso,
                endRangeIso = endIso,
                onlyMine = true,
                cacheNamespace = "widget_nearest"
            )

            // Mirror OverviewViewModel exactly: do NOT filter isCancelled here
            val allInstances = grouped.values.flatten()
                .sortedBy { it.since ?: it.updatedAt ?: "" }

            val byDay = allInstances
                .groupBy { inst ->
                    val s = inst.since ?: inst.until ?: inst.updatedAt ?: ""
                    s.substringBefore('T').ifEmpty { s }
                }
                .entries.sortedBy { it.key }
                .associate { it.key to it.value }

            val sortedKeys = byDay.keys.sorted()
            val selectedKey = when {
                sortedKeys.isEmpty() -> null
                sortedKeys.contains(todayString) -> {
                    val todayList = byDay[todayString] ?: emptyList()
                    val hasFutureToday = todayList.any { inst ->
                        // Same three-fallback check as OverviewViewModel
                        val timeStr = inst.until ?: inst.since ?: inst.updatedAt ?: ""
                        val instInstant = try { Instant.parse(timeStr) } catch (_: Exception) { null }
                        instInstant != null && instInstant > nowInstant
                    }
                    if (hasFutureToday) todayString
                    else sortedKeys.find { it > todayString } ?: sortedKeys.firstOrNull()
                }
                else -> sortedKeys.find { it > todayString } ?: sortedKeys.firstOrNull()
            } ?: return null

            val selectedDayList = byDay[selectedKey] ?: emptyList()
            val lessons = selectedDayList.filter { isLessonOverview(it) }
            val others = (selectedDayList - lessons.toSet()).sortedBy { it.since }

            val lessonGroups = lessons
                .groupBy { it.event?.firstTrainerOrEmpty() ?: "" }
                .map { (trainer, insts) ->
                    val sorted = insts.sortedBy { it.since }
                    val first = sorted.first()
                    GroupedWidgetEvent(
                        eventId = first.event?.id,
                        instanceId = first.id,
                        title = trainer,
                        colorRgb = null,
                        location = first.event?.locationText?.takeIf { it.isNotBlank() },
                        slots = sorted.mapNotNull { inst ->
                            val s = inst.since ?: return@mapNotNull null
                            try { Instant.parse(s).toLocalDateTime(tz) } catch (_: Exception) { null }
                        }
                    )
                }

            val otherGroups = others.map { inst ->
                val te = CalendarUtils.eventInstanceToTimelineEvent(inst, personId, coupleIds)
                GroupedWidgetEvent(
                    eventId = te?.eventId ?: inst.event?.id,
                    instanceId = inst.id,
                    title = te?.title ?: inst.event?.name ?: "",
                    colorRgb = te?.colorRgb,
                    location = inst.event?.locationText?.takeIf { it.isNotBlank() },
                    slots = listOfNotNull(
                        inst.since?.let { s ->
                            try { Instant.parse(s).toLocalDateTime(tz) } catch (_: Exception) { null }
                        }
                    )
                )
            }

            val allGroups = lessonGroups + otherGroups
            if (allGroups.isEmpty()) null else NearestDayResult(selectedKey, allGroups)
        } catch (_: Exception) { null }
    }
}
