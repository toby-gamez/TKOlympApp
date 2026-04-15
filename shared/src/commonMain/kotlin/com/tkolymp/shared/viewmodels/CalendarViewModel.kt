package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.event.EventInstance
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

data class CalendarState(
    val eventsByDay: Map<String, List<EventInstance>> = emptyMap(),
    val lessonsByTrainerByDay: Map<String, Map<String, List<EventInstance>>> = emptyMap(),
    val otherEventsByDay: Map<String, List<EventInstance>> = emptyMap(),
    val visibleDates: List<String> = emptyList(),
    val todayString: String = "",
    val tomorrowString: String = "",
    val myPersonId: String? = null,
    val myCoupleIds: List<String> = emptyList(),
    val isOffline: Boolean = false,
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class CalendarViewModel(
    private val eventService: com.tkolymp.shared.event.IEventService = ServiceLocator.eventService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService,
    private val cache: CacheService = ServiceLocator.cacheService
) : ViewModel() {
    private val _state = MutableStateFlow(CalendarState())
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    private var lastWeekOffset: Int? = null
    private var lastOnlyMine: Boolean? = null

    suspend fun load(weekOffset: Int, onlyMine: Boolean, forceRefresh: Boolean = false) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val weekStart = today.plus(weekOffset * 7, DateTimeUnit.DAY)
        val endDay = weekStart.plus(6, DateTimeUnit.DAY)
        val startIso = weekStart.toString() + "T00:00:00Z"
        val endIso = endDay.toString() + "T23:59:59Z"

        val visibleDates = buildList {
            var dd = weekStart
            while (dd <= endDay) { add(dd.toString()); dd = dd.plus(1, DateTimeUnit.DAY) }
        }
        val todayString = today.toString()
        val tomorrowString = today.plus(1, DateTimeUnit.DAY).toString()

        val onlyMineChanged = onlyMine != lastOnlyMine
        // only invalidate cache when explicitly forced or when the week changed —
        // switching the onlyMine filter should not wipe cached data (helps offline)
        val shouldInvalidate = forceRefresh || (weekOffset != lastWeekOffset)

        // preload cached user ids (used later in state)
        val pid = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("CalendarViewModel", "getCachedPersonId failed: ${e.message}"); null }
        val cids = try { userService.getCachedCoupleIds() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("CalendarViewModel", "getCachedCoupleIds failed: ${e.message}"); emptyList() }

        _state.value = _state.value.copy(isLoading = true, error = null, isOffline = false)

        // If only the filter changed (My/All) and we're not forcing refresh, try offline bucket first
        if (onlyMineChanged && !forceRefresh) {
            try {
                val bucketName = if (onlyMine) "MINE" else "ALL"
                val weekKey = "offline_cal_${bucketName}_$weekStart"
                val raw = try { ServiceLocator.offlineSyncManager.loadCalendarWeek(weekKey) } catch (_: Exception) { null }
                if (raw != null) {
                    val parsed = parseCalendarJson(raw)

                    val lessonsByTrainerByDay = parsed.mapValues { (_, list) ->
                        list.filter { isLesson(it) }
                            .groupBy { it.event?.eventTrainersList?.firstOrNull()!!.trim() }
                            .mapValues { (_, instances) -> instances.sortedBy { it.since } }
                    }
                    val otherEventsByDay = parsed.mapValues { (_, list) ->
                        val lessonSet = list.filter { isLesson(it) }.toSet()
                        (list - lessonSet).sortedBy { it.since }
                    }

                    lastWeekOffset = weekOffset
                    lastOnlyMine = onlyMine

                    _state.value = _state.value.copy(
                        eventsByDay = parsed,
                        lessonsByTrainerByDay = lessonsByTrainerByDay,
                        otherEventsByDay = otherEventsByDay,
                        visibleDates = visibleDates,
                        todayString = todayString,
                        tomorrowString = tomorrowString,
                        myPersonId = pid,
                        myCoupleIds = cids,
                        isOffline = true,
                        isLoading = false
                    )
                    return
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }

        if (shouldInvalidate) {
            try {
                val online = try { ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { true }
                if (online) {
                    cache.invalidatePrefix("calendar_")
                } else {
                    Logger.d("CalendarViewModel", "skipping cache invalidation: offline")
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("CalendarViewModel", "cache invalidation failed: ${e.message}") }
        }

        try {
            val map: Map<String, List<EventInstance>> = try {
                withContext(Dispatchers.Default) {
                    eventService.fetchEventsGroupedByDay(startIso, endIso, onlyMine, 200, 0, null, cacheNamespace = "calendar_")
                }
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                // offline fallback
                val bucketName = if (onlyMine) "MINE" else "ALL"
                val weekKey = "offline_cal_${bucketName}_$weekStart"
                val raw = try { ServiceLocator.offlineSyncManager.loadCalendarWeek(weekKey) } catch (_: Exception) { null }
                if (raw != null) {
                    val parsed = parseCalendarJson(raw)
                    _state.value = _state.value.copy(isOffline = true)
                    parsed
                } else throw ex
            }

            val lessonsByTrainerByDay = map.mapValues { (_, list) ->
                list.filter { isLesson(it) }
                    .groupBy { it.event?.eventTrainersList?.firstOrNull()!!.trim() }
                    .mapValues { (_, instances) -> instances.sortedBy { it.since } }
            }
            val otherEventsByDay = map.mapValues { (_, list) ->
                val lessonSet = list.filter { isLesson(it) }.toSet()
                (list - lessonSet).sortedBy { it.since }
            }

            lastWeekOffset = weekOffset
            lastOnlyMine = onlyMine

            _state.value = _state.value.copy(
                eventsByDay = map,
                lessonsByTrainerByDay = lessonsByTrainerByDay,
                otherEventsByDay = otherEventsByDay,
                visibleDates = visibleDates,
                todayString = todayString,
                tomorrowString = tomorrowString,
                myPersonId = pid,
                myCoupleIds = cids,
                isLoading = false
            )
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání")
        }
    }

    private fun parseCalendarJson(raw: String): Map<String, List<EventInstance>> {
        return try {
            val json = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
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

    private fun isLesson(inst: EventInstance): Boolean =
        inst.event?.type?.equals("lesson", ignoreCase = true) == true &&
            inst.event.eventTrainersList.isNotEmpty() &&
            !inst.event.eventTrainersList.firstOrNull().isNullOrBlank()

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
