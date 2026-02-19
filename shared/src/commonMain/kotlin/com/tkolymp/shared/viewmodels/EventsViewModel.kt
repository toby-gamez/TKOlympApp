package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.event.EventInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class EventsState(
    val eventsByDay: Map<String, List<EventInstance>> = emptyMap(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class EventsViewModel(
    private val eventService: com.tkolymp.shared.event.IEventService = ServiceLocator.eventService
) {
    private val _state = MutableStateFlow(EventsState())
    val state: StateFlow<EventsState> = _state.asStateFlow()

    suspend fun loadCampsNextYear(forceRefresh: Boolean = false) {
        println("EventsViewModel.loadCampsNextYear: forceRefresh=$forceRefresh")
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            // Invalidate events cache only when explicitly requested to avoid unnecessary network calls
            if (forceRefresh) {
                try {
                    println("EventsViewModel: invalidating cache prefix camps_")
                    ServiceLocator.cacheService.invalidatePrefix("camps_")
                } catch (t: Throwable) {
                    println("EventsViewModel: failed to invalidate cache: ${t.message}")
                }
            }

            // Use a broad fixed range (multiplatform-safe) to fetch upcoming camps
            val startIso = "2023-01-01T00:00:00Z"
            val endIso = "2100-01-01T23:59:59Z"
            val map = try { withContext(Dispatchers.IO) { eventService.fetchEventsGroupedByDay(startIso, endIso, false, 500, 0, "CAMP", cacheNamespace = "camps_") } } catch (ex: Throwable) { emptyMap<String, List<EventInstance>>() }
            val filtered = map.mapValues { entry -> entry.value.filter { it.event?.isVisible != false } }.filterValues { it.isNotEmpty() }
            if (filtered.isEmpty()) {
                _state.value = _state.value.copy(eventsByDay = filtered, isLoading = false, error = "Žádné akce typu CAMP - server nevrátil žádné výsledky")
            } else {
                _state.value = _state.value.copy(eventsByDay = filtered, isLoading = false)
            }
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání akcí")
        }
    }
}
