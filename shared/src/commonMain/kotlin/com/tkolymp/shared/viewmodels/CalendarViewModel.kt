package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.event.EventInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CalendarState(
    val eventsByDay: Map<String, List<EventInstance>> = emptyMap(),
    val myPersonId: String? = null,
    val myCoupleIds: List<String> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class CalendarViewModel(
    private val eventService: com.tkolymp.shared.event.IEventService = ServiceLocator.eventService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService,
    private val cache: CacheService = ServiceLocator.cacheService
) {
    private val _state = MutableStateFlow(CalendarState())
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    suspend fun load(startIso: String, endIso: String, onlyMine: Boolean, forceRefresh: Boolean = false) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        if (forceRefresh) {
            try { cache.invalidatePrefix("events_") } catch (_: Throwable) {}
        }
        try {
            val map = try {
                withContext(Dispatchers.Default) {
                    eventService.fetchEventsGroupedByDay(startIso, endIso, onlyMine, 200, 0, null)
                }
            } catch (ex: Throwable) {
                _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání akcí")
                return
            }

            val pid = try { userService.getCachedPersonId() } catch (_: Throwable) { null }
            val cids = try { userService.getCachedCoupleIds() } catch (_: Throwable) { emptyList() }

            _state.value = _state.value.copy(eventsByDay = map, myPersonId = pid, myCoupleIds = cids, isLoading = false)
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání")
        }
    }
}
