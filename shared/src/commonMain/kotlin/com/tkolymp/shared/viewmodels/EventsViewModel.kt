package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.utils.DateRangeConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.sync.OfflineKeys
import com.tkolymp.shared.event.EventType
import com.tkolymp.shared.event.toEventType
import androidx.compose.runtime.Immutable
import com.tkolymp.shared.utils.AppConstants

@Immutable
data class EventsState(
    val eventsByDay: Map<String, List<EventInstance>> = emptyMap(),
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

class EventsViewModel(
    private val eventService: com.tkolymp.shared.event.IEventService = ServiceLocator.eventService
) : ViewModel() {
    private val _state = MutableStateFlow(EventsState())
    val state: StateFlow<EventsState> = _state.asStateFlow()

    suspend fun loadCampsNextYear(forceRefresh: Boolean = false) {
        Logger.d("EventsViewModel", "loadCampsNextYear: forceRefresh=$forceRefresh")
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            // Invalidate events cache only when explicitly requested to avoid unnecessary network calls
            if (forceRefresh) {
                try {
                    Logger.d("EventsViewModel", "invalidating cache prefix camps_")
                    ServiceLocator.cacheService.invalidatePrefix("camps_")
                } catch (e: CancellationException) { throw e } catch (t: Exception) {
                    Logger.d("EventsViewModel", "failed to invalidate cache: ${t.message}")
                }
            }

            // Use a broad fixed range (multiplatform-safe) to fetch upcoming camps
            val startIso = DateRangeConstants.FAR_PAST
            val endIso = DateRangeConstants.FAR_FUTURE
            val map = try { withContext(Dispatchers.Default) { eventService.fetchEventsGroupedByDay(startIso, endIso, false, AppConstants.FETCH_LIMIT_PERIOD, 0, "CAMP", cacheNamespace = "camps_") } } catch (e: CancellationException) { throw e } catch (ex: Exception) { emptyMap<String, List<EventInstance>>() }
            val filtered = map.mapValues { entry -> entry.value.filter { it.event?.isVisible != false } }.filterValues { it.isNotEmpty() }

            if (filtered.isNotEmpty()) {
                _state.value = _state.value.copy(eventsByDay = filtered, isLoading = false)
            } else {
                val offlineGrouped = try { loadOfflineCamps() } catch (_: Exception) { emptyMap() }
                if (offlineGrouped.isNotEmpty()) {
                    _state.value = _state.value.copy(eventsByDay = offlineGrouped, isLoading = false)
                } else {
                    _state.value = _state.value.copy(eventsByDay = filtered, isLoading = false)
                }
            }
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            try {
                val grouped = loadOfflineCamps()
                if (grouped.isNotEmpty()) {
                    _state.value = _state.value.copy(eventsByDay = grouped, isLoading = false)
                } else {
                    _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoadingEvents))
                }
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoadingEvents))
            }
        }
    }

    private suspend fun loadOfflineCamps(): Map<String, List<EventInstance>> {
        val keys = ServiceLocator.offlineDataStorage.allKeys()
            .filter { it.startsWith(OfflineKeys.CAL_PREFIX + "CAMPS_") }
        val parsed = mutableListOf<EventInstance>()
        for (k in keys) {
            try {
                val raw = ServiceLocator.offlineDataStorage.load(k) ?: continue
                val json = AppJson.parseToJsonElement(raw).jsonObject
                json.entries.forEach { (_, elem) ->
                    val arr = elem.jsonArray
                    arr.forEach { item ->
                        val obj = item.jsonObject
                        val eventType = obj["eventType"]?.jsonPrimitive?.contentOrNull
                        if (eventType == null || eventType.toEventType() != EventType.CAMP) return@forEach
                        val id = obj["id"]?.jsonPrimitive?.longOrNull ?: obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                        val isCancelled = obj["isCancelled"]?.jsonPrimitive?.booleanOrNull ?: false
                        val since = obj["since"]?.jsonPrimitive?.contentOrNull
                        val until = obj["until"]?.jsonPrimitive?.contentOrNull
                        val eventId = obj["eventId"]?.jsonPrimitive?.longOrNull
                        val eventName = obj["eventName"]?.jsonPrimitive?.contentOrNull
                        val locationText = obj["locationText"]?.jsonPrimitive?.contentOrNull
                        val updatedAt = obj["updatedAt"]?.jsonPrimitive?.contentOrNull
                        val ev = com.tkolymp.shared.event.Event(eventId, eventName, null, eventType, locationText, false, false, false, emptyList(), emptyList(), emptyList(), null)
                        val inst = com.tkolymp.shared.event.EventInstance(id, isCancelled, since, until, updatedAt, ev)
                        val dateKey = inst.since?.substringBefore('T') ?: inst.updatedAt?.substringBefore('T') ?: inst.until?.substringBefore('T')
                        if (dateKey.isNullOrBlank()) return@forEach
                        try { kotlinx.datetime.LocalDate.parse(dateKey); parsed += inst } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
        return parsed
            .distinctBy { Triple(it.event?.id, it.since, it.until) }
            .groupBy { inst ->
                inst.since?.substringBefore('T') ?: inst.updatedAt?.substringBefore('T') ?: inst.until?.substringBefore('T') ?: ""
            }
            .filterValues { it.isNotEmpty() }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
