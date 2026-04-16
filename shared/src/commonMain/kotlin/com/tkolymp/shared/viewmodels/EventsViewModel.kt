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

data class EventsState(
    val eventsByDay: Map<String, List<EventInstance>> = emptyMap(),
    override val isLoading: Boolean = false,
    override val error: String? = null
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
            val map = try { withContext(Dispatchers.IO) { eventService.fetchEventsGroupedByDay(startIso, endIso, false, 500, 0, "CAMP", cacheNamespace = "camps_") } } catch (e: CancellationException) { throw e } catch (ex: Exception) { emptyMap<String, List<EventInstance>>() }
            val filtered = map.mapValues { entry -> entry.value.filter { it.event?.isVisible != false } }.filterValues { it.isNotEmpty() }

            if (filtered.isNotEmpty()) {
                _state.value = _state.value.copy(eventsByDay = filtered, isLoading = false)
            } else {
                // Try offline fallback when server returns no data
                val offlineGrouped = try {
                    val keys = ServiceLocator.offlineDataStorage.allKeys().filter { it.startsWith("offline_cal_") }
                    val parsed = mutableListOf<EventInstance>()
                    for (k in keys) {
                        try {
                            val raw = ServiceLocator.offlineDataStorage.load(k) ?: continue
                            val json = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
                            json.entries.forEach { (_, elem) ->
                                val arr = elem.jsonArray
                                arr.forEach { item ->
                                    val obj = item.jsonObject
                                    val eventType = obj["eventType"]?.jsonPrimitive?.contentOrNull
                                    if (eventType == null || !eventType.contains("CAMP", ignoreCase = true)) return@forEach
                                    val id = obj["id"]?.jsonPrimitive?.longOrNull ?: obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                                    val isCancelled = obj["isCancelled"]?.jsonPrimitive?.booleanOrNull ?: false
                                    val since = obj["since"]?.jsonPrimitive?.contentOrNull
                                    val until = obj["until"]?.jsonPrimitive?.contentOrNull
                                    val eventId = obj["eventId"]?.jsonPrimitive?.longOrNull
                                    val eventName = obj["eventName"]?.jsonPrimitive?.contentOrNull
                                    val locationText = obj["locationText"]?.jsonPrimitive?.contentOrNull
                                    val ev = com.tkolymp.shared.event.Event(eventId, eventName, null, eventType, locationText, false, false, false, emptyList(), emptyList(), emptyList(), null)
                                    // validate date key and skip invalid entries
                                    val updatedAt = obj["updatedAt"]?.jsonPrimitive?.contentOrNull
                                    val inst = com.tkolymp.shared.event.EventInstance(id, isCancelled, since, until, updatedAt, ev)
                                    val dateKey = inst.since?.substringBefore('T') ?: inst.updatedAt?.substringBefore('T') ?: inst.until?.substringBefore('T')
                                    if (dateKey.isNullOrBlank()) return@forEach
                                    try { kotlinx.datetime.LocalDate.parse(dateKey); parsed += inst } catch (_: Exception) {}
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    parsed.groupBy { inst -> inst.since?.substringBefore('T') ?: inst.updatedAt?.substringBefore('T') ?: inst.until?.substringBefore('T') ?: "" }.filterValues { it.isNotEmpty() }
                } catch (_: Exception) { emptyMap() }

                if (offlineGrouped.isNotEmpty()) {
                    _state.value = _state.value.copy(eventsByDay = offlineGrouped, isLoading = false)
                } else {
                    _state.value = _state.value.copy(eventsByDay = filtered, isLoading = false)
                }
            }
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            // Try offline fallback: scan offline calendar data saved by OfflineSyncManager
            try {
                val keys = ServiceLocator.offlineDataStorage.allKeys().filter { it.startsWith("offline_cal_") }
                val parsed = mutableListOf<EventInstance>()
                for (k in keys) {
                    try {
                        val raw = ServiceLocator.offlineDataStorage.load(k) ?: continue
                        val json = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
                        json.entries.forEach { (_, elem) ->
                            val arr = elem.jsonArray
                            arr.forEach { item ->
                                val obj = item.jsonObject
                                val eventType = obj["eventType"]?.jsonPrimitive?.contentOrNull
                                if (eventType == null || !eventType.contains("CAMP", ignoreCase = true)) return@forEach
                                // reuse CalendarViewViewModel parser would be nicer; construct minimal EventInstance
                                val id = obj["id"]?.jsonPrimitive?.longOrNull ?: obj["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                                val isCancelled = obj["isCancelled"]?.jsonPrimitive?.booleanOrNull ?: false
                                val since = obj["since"]?.jsonPrimitive?.contentOrNull
                                val until = obj["until"]?.jsonPrimitive?.contentOrNull
                                val eventId = obj["eventId"]?.jsonPrimitive?.longOrNull
                                val eventName = obj["eventName"]?.jsonPrimitive?.contentOrNull
                                val locationText = obj["locationText"]?.jsonPrimitive?.contentOrNull
                                val ev = com.tkolymp.shared.event.Event(eventId, eventName, null, eventType, locationText, false, false, false, emptyList(), emptyList(), emptyList(), null)
                                parsed += com.tkolymp.shared.event.EventInstance(id, isCancelled, since, until, obj["updatedAt"]?.jsonPrimitive?.contentOrNull, ev)
                            }
                        }
                    } catch (_: Exception) {}
                }
                val grouped = parsed.groupBy { inst -> inst.since?.substringBefore('T') ?: inst.updatedAt?.substringBefore('T') ?: "" }.filterValues { it.isNotEmpty() }
                _state.value = _state.value.copy(eventsByDay = grouped, isLoading = false)
            } catch (_: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání akcí")
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
