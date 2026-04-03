package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.event.EventInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
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

        val paramsChanged = weekOffset != lastWeekOffset || onlyMine != lastOnlyMine
        val shouldInvalidate = forceRefresh || paramsChanged

        _state.value = _state.value.copy(isLoading = true, error = null)
        if (shouldInvalidate) {
            try { cache.invalidatePrefix("calendar_") } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("CalendarViewModel", "cache invalidation failed: ${e.message}") }
        }
        try {
            val map = try {
                withContext(Dispatchers.Default) {
                    eventService.fetchEventsGroupedByDay(startIso, endIso, onlyMine, 200, 0, null, cacheNamespace = "calendar_")
                }
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání akcí")
                return
            }

            val pid = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("CalendarViewModel", "getCachedPersonId failed: ${e.message}"); null }
            val cids = try { userService.getCachedCoupleIds() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("CalendarViewModel", "getCachedCoupleIds failed: ${e.message}"); emptyList() }

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

    private fun isLesson(inst: EventInstance): Boolean =
        inst.event?.type?.equals("lesson", ignoreCase = true) == true &&
            !inst.event?.eventTrainersList.isNullOrEmpty() &&
            !inst.event?.eventTrainersList?.firstOrNull().isNullOrBlank()

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
