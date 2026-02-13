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

    suspend fun loadCampsNextYear() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            // Use a broad fixed range (multiplatform-safe) to fetch upcoming camps
            val startIso = "2023-01-01T00:00:00Z"
            val endIso = "2100-01-01T23:59:59Z"
            val map = try { withContext(Dispatchers.IO) { eventService.fetchEventsGroupedByDay(startIso, endIso, false, 500, 0, "CAMP") } } catch (ex: Throwable) { emptyMap<String, List<EventInstance>>() }
            val filtered = map.mapValues { entry -> entry.value.filter { it.event?.isVisible != false } }.filterValues { it.isNotEmpty() }
            _state.value = _state.value.copy(eventsByDay = filtered, isLoading = false)
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání akcí")
        }
    }
}
