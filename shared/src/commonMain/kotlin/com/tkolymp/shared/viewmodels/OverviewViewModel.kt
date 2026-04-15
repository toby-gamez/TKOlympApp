package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.announcements.Announcement
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.people.Person
import com.tkolymp.shared.utils.daysUntilNextBirthday
import com.tkolymp.shared.utils.formatBirthDateString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Instant

data class BirthdayEntry(
    val personId: String,
    val name: String,
    val formattedBirthDate: String?,
    val days: Int
)

data class OverviewState(
    val upcomingEvents: List<EventInstance> = emptyList(),
    val recentAnnouncements: List<Announcement> = emptyList(),
    // trainings derived state (for the selected day)
    val trainingLessonsByTrainer: Map<String, List<EventInstance>> = emptyMap(),
    val trainingOtherEvents: List<EventInstance> = emptyList(),
    val trainingSelectedDate: String? = null,
    val todayString: String = "",
    val tomorrowString: String = "",
    // camps derived state (up to 2, grouped by day)
    val campsMapByDay: Map<String, List<EventInstance>> = emptyMap(),
    // birthdays derived state
    val upcomingBirthdays: List<BirthdayEntry> = emptyList(),
    val myPersonId: String? = null,
    val myCoupleIds: List<String> = emptyList(),
    val isOffline: Boolean = false,
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class OverviewViewModel(
    private val eventService: com.tkolymp.shared.event.IEventService = ServiceLocator.eventService,
    private val announcementService: com.tkolymp.shared.announcements.IAnnouncementService = ServiceLocator.announcementService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService,
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService,
    private val cache: CacheService = ServiceLocator.cacheService
) : ViewModel() {
    private val _state = MutableStateFlow(OverviewState())
    val state: StateFlow<OverviewState> = _state.asStateFlow()

    suspend fun loadOverview(forceRefresh: Boolean = false) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val startIso = today.toString() + "T00:00:00Z"
        val endIso = today.plus(365, DateTimeUnit.DAY).toString() + "T23:59:59Z"
        val todayString = today.toString()
        val tomorrowString = today.plus(1, DateTimeUnit.DAY).toString()

        _state.value = _state.value.copy(isLoading = true, error = null, isOffline = false)
        if (forceRefresh) {
            try {
                val online = try { ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { true }
                if (online) {
                    cache.invalidatePrefix("overview_")
                    cache.invalidatePrefix("announcements_")
                } else {
                    Logger.d("OverviewViewModel", "skipping cache invalidation: offline")
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("OverviewViewModel", "cache invalidation failed: ${e.message}") }
        }
        try {
            var events = try {
                val grouped = withContext(Dispatchers.Default) {
                    eventService.fetchEventsGroupedByDay(startIso, endIso, onlyMine = true, first = 200, cacheNamespace = "overview_")
                }
                grouped.values.flatten()
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Logger.d("OverviewViewModel", "fetchEvents failed: ${e.message}")
                // Try offline fallback: collect available offline_cal_MINE_* keys
                try {
                    val keys = ServiceLocator.offlineDataStorage.allKeys().filter { it.startsWith("offline_cal_MINE_") }
                    val parsed = mutableListOf<EventInstance>()
                    for (k in keys) {
                        try {
                            val raw = ServiceLocator.offlineDataStorage.load(k) ?: continue
                            val map = parseCalendarJson(raw)
                            parsed += map.values.flatten()
                        } catch (_: Exception) {}
                    }
                    if (parsed.isNotEmpty()) {
                        _state.value = _state.value.copy(isOffline = true)
                        parsed
                    } else emptyList()
                } catch (_: Exception) { emptyList<EventInstance>() }
            }

            val announcements = try {
                withContext(Dispatchers.Default) { announcementService.getAnnouncements(false) }
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }
                    .take(3)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("OverviewViewModel", "getAnnouncements failed: ${e.message}"); emptyList<Announcement>() }

            val pid = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("OverviewViewModel", "getCachedPersonId failed: ${e.message}"); null }
            val cids = try { userService.getCachedCoupleIds() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("OverviewViewModel", "getCachedCoupleIds failed: ${e.message}"); emptyList<String>() }

            // Derive camps: events with type containing "CAMP", capped at 2
            val camps = events.filter { it.event?.type?.contains("CAMP", ignoreCase = true) == true }
            val campsMapByDay = camps
                .sortedBy { it.since ?: it.updatedAt ?: "" }
                .take(2)
                .groupBy { inst ->
                    val s = inst.since ?: inst.until ?: inst.updatedAt ?: ""
                    s.substringBefore('T').ifEmpty { s }
                }
                .entries.sortedBy { it.key }
                .associate { it.key to it.value }

            // Derive trainings: all events, grouped by day, show one selected day
            val trainings = events.sortedBy { it.since ?: it.updatedAt ?: "" }
            val trainingsMapByDay = trainings
                .groupBy { inst ->
                    val s = inst.since ?: inst.until ?: inst.updatedAt ?: ""
                    s.substringBefore('T').ifEmpty { s }
                }
                .entries.sortedBy { it.key }
                .associate { it.key to it.value }

            val nowInstant = Clock.System.now()
            val sortedKeys = trainingsMapByDay.keys.sorted()
            val selectedKey = if (sortedKeys.isEmpty()) null
            else if (sortedKeys.contains(todayString)) {
                val todayList = trainingsMapByDay[todayString] ?: emptyList()
                val hasFutureToday = todayList.any { inst ->
                    val timeStr = inst.until ?: inst.since ?: inst.updatedAt ?: ""
                    val instInstant = try { Instant.parse(timeStr) } catch (_: Exception) { null }
                    instInstant != null && instInstant > nowInstant
                }
                if (hasFutureToday) todayString else sortedKeys.find { it > todayString } ?: sortedKeys.firstOrNull()
            } else {
                sortedKeys.find { it > todayString } ?: sortedKeys.firstOrNull()
            }

            val selectedDayList = if (selectedKey != null) trainingsMapByDay[selectedKey] ?: emptyList() else emptyList()
            val lessons = selectedDayList.filter { isLesson(it) }
            val otherEvents = (selectedDayList - lessons.toSet()).sortedBy { it.since }
            val lessonsByTrainer = lessons
                .groupBy { it.event?.eventTrainersList?.firstOrNull()!!.trim() }
                .mapValues { (_, insts) -> insts.sortedBy { it.since } }

            // Birthdays via PeopleService
            val upcomingBirthdays = try {
                val people = withContext(Dispatchers.Default) { peopleService.fetchPeople() }
                    .filterIsInstance<Person>()
                people
                    .mapNotNull { p ->
                        val days = daysUntilNextBirthday(p.birthDate)
                        if (days == Int.MAX_VALUE) null else {
                            val name = buildList {
                                p.prefixTitle?.takeIf { it.isNotBlank() }?.let { add(it) }
                                p.firstName?.takeIf { it.isNotBlank() }?.let { add(it) }
                                p.lastName?.takeIf { it.isNotBlank() }?.let { add(it) }
                            }.joinToString(" ").let { base ->
                                if (!p.suffixTitle.isNullOrBlank()) "$base, ${p.suffixTitle}" else base.ifBlank { p.id }
                            }
                            BirthdayEntry(
                                personId = p.id,
                                name = name,
                                formattedBirthDate = formatBirthDateString(p.birthDate),
                                days = days
                            )
                        }
                    }
                    .sortedBy { it.days }
                    .take(3)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("OverviewViewModel", "fetchPeople failed: ${e.message}"); emptyList() }

            _state.value = _state.value.copy(
                upcomingEvents = events,
                recentAnnouncements = announcements,
                trainingLessonsByTrainer = lessonsByTrainer,
                trainingOtherEvents = otherEvents,
                trainingSelectedDate = selectedKey,
                todayString = todayString,
                tomorrowString = tomorrowString,
                campsMapByDay = campsMapByDay,
                upcomingBirthdays = upcomingBirthdays,
                myPersonId = pid,
                myCoupleIds = cids,
                isLoading = false
            )
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání přehledu")
        }
    }

    private fun isLesson(inst: EventInstance): Boolean =
        inst.event?.type?.equals("lesson", ignoreCase = true) == true &&
            !inst.event?.eventTrainersList.isNullOrEmpty() &&
            !inst.event?.eventTrainersList?.firstOrNull().isNullOrBlank()

    private fun parseCalendarJson(raw: String): Map<String, List<EventInstance>> {
        return try {
            val json = Json.parseToJsonElement(raw).jsonObject
            val result = mutableMapOf<String, MutableList<EventInstance>>()
            json.entries.forEach { (date, elem) ->
                val arr = elem.jsonArray
                val list = mutableListOf<EventInstance>()
                arr.forEach { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.longOrNull ?: obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                    val isCancelled = obj["isCancelled"]?.jsonPrimitive?.booleanOrNull ?: false
                    val since = obj["since"]?.jsonPrimitive?.contentOrNull
                    val until = obj["until"]?.jsonPrimitive?.contentOrNull
                    val updatedAt = obj["updatedAt"]?.jsonPrimitive?.contentOrNull
                    val eventId = obj["eventId"]?.jsonPrimitive?.longOrNull
                    val eventName = obj["eventName"]?.jsonPrimitive?.contentOrNull
                    val eventType = obj["eventType"]?.jsonPrimitive?.contentOrNull
                    val locationText = obj["locationText"]?.jsonPrimitive?.contentOrNull
                    val trainers = (obj["trainers"]?.jsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
                    val event = com.tkolymp.shared.event.Event(eventId, eventName, null, eventType, locationText, false, false, false, trainers, emptyList(), emptyList(), null)
                    list += com.tkolymp.shared.event.EventInstance(id, isCancelled, since, until, updatedAt, event)
                }
                result[date] = list
            }
            result
        } catch (e: Exception) { emptyMap() }
    }
}
