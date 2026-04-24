package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.Logger
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.calendar.CalendarUtils
import com.tkolymp.shared.calendar.CalendarViewState
import com.tkolymp.shared.calendar.CollisionDetectionAlgorithm
import com.tkolymp.shared.calendar.EventLayoutData
import com.tkolymp.shared.calendar.ViewMode
import com.tkolymp.shared.event.IEventService
import com.tkolymp.shared.user.UserService
import com.tkolymp.shared.personalevents.TrainingType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

/**
 * ViewModel for CalendarView screen
 * Manages state, loads events, and calculates layouts
 */
class CalendarViewViewModel(
    private val eventService: IEventService = ServiceLocator.eventService,
    private val userService: UserService = ServiceLocator.userService,
    private val cache: CacheService = ServiceLocator.cacheService
) : ViewModel() {
    private val _state = MutableStateFlow(
        CalendarViewState(
            selectedDate = kotlin.time.Clock.System.todayIn(TimeZone.currentSystemDefault()),
            viewMode = ViewMode.DAY
        )
    )
    val state: StateFlow<CalendarViewState> = _state.asStateFlow()
    
    private var myPersonId: String? = null
    private var myCoupleIds: List<String> = emptyList()
    private var userInfoLoaded = false
    // Track last requested range start and filter to avoid unnecessary cache invalidation
    private var lastRangeStartIso: String? = null
    private var lastShowOnlyMine: Boolean? = null
    
    /**
     * Load cached user information (lazy loading)
     */
    private suspend fun loadUserInfo() {
        if (userInfoLoaded) return
        
        try {
            myPersonId = userService.getCachedPersonId()
        } catch (e: Exception) {
            myPersonId = null
        }
        
        try {
            myCoupleIds = userService.getCachedCoupleIds()
        } catch (e: Exception) {
            myCoupleIds = emptyList()
        }
        
        userInfoLoaded = true
    }
    
    /**
     * Load events for the current date range and view mode
     */
    suspend fun loadEvents() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        var isOfflineUsed = false
        
        // Ensure user info is loaded first
        loadUserInfo()
        
        try {
            val currentState = _state.value
            val timeRange = CalendarUtils.calculateTimeRange(
                currentState.selectedDate,
                currentState.viewMode
            )

            // Convert to ISO strings for API
            val startIso = timeRange.start.toInstant(TimeZone.currentSystemDefault()).toString()
            val endIso = timeRange.end.toInstant(TimeZone.currentSystemDefault()).toString()

            // Decide whether filter changed or visible range changed since last load
            val onlyMineChanged = currentState.showOnlyMine != lastShowOnlyMine
            val rangeChanged = startIso != lastRangeStartIso

            Logger.d("CalendarViewViewModel", "loadEvents: selectedDate=${currentState.selectedDate} viewMode=${currentState.viewMode} startIso=${startIso} lastRangeStartIso=${lastRangeStartIso} onlyMine=${currentState.showOnlyMine} onlyMineChanged=${onlyMineChanged} rangeChanged=${rangeChanged}")

            // If only the filter changed but the range is the same, try offline-week summary first
            if (onlyMineChanged && !rangeChanged) {
                try {
                    val bucketName = if (currentState.showOnlyMine) "MINE" else "ALL"
                    val startDate = timeRange.start.date
                    val weekKey = "offline_cal_${'$'}{bucketName}_${'$'}startDate"
                    var raw = try { ServiceLocator.offlineSyncManager.loadCalendarWeek(weekKey) } catch (_: Exception) { null }
                    if (raw == null) {
                        // try to find any compatible offline_cal_* key for this bucket (some buckets use different suffixes)
                        try {
                            val keys = try { ServiceLocator.offlineSyncManager.listOfflineKeys() } catch (_: Exception) { emptySet() }
                            val candidates = keys.filter { it.startsWith("offline_cal_${bucketName}_") }
                            // prefer exact weekStart match
                            val exact = candidates.firstOrNull { it.endsWith(startDate.toString()) }
                            val anyKey = exact ?: candidates.firstOrNull()
                            if (anyKey != null) Logger.d("CalendarViewViewModel", "found offline key for bucket=${bucketName}: ${anyKey}")
                            if (anyKey != null) raw = try { ServiceLocator.offlineSyncManager.loadCalendarWeek(anyKey) } catch (_: Exception) { null }
                        } catch (_: Exception) {}
                    }
                    if (raw != null) {
                        Logger.d("CalendarViewViewModel", "using offline data (key present)")
                        var parsed = parseCalendarJson(raw)
                        parsed = try { enrichParsedWithEventDetails(parsed) } catch (_: Exception) { parsed }

                        // Convert parsed offline data to timeline events
                        val serverTimeline = parsed.values.flatten()
                            .mapNotNull { instance ->
                                CalendarUtils.eventInstanceToTimelineEvent(instance, myPersonId, myCoupleIds)
                            }
                            .filter { event ->
                                event.startTime.date >= timeRange.start.date && event.startTime.date <= timeRange.end.date
                            }

                        val personalTimeline = try { expandPersonalEventsToTimeline(startIso, endIso) } catch (_: Exception) { emptyList() }
                        val allTimelineEvents = (serverTimeline + personalTimeline).sortedBy { it.startTime }

                        val layoutData = if (currentState.viewMode == ViewMode.DAY) {
                            CollisionDetectionAlgorithm.calculateLayout(allTimelineEvents)
                        } else {
                            allTimelineEvents.groupBy { it.startTime.date }
                                .flatMap { (_, dayEvents) -> CollisionDetectionAlgorithm.calculateLayout(dayEvents).entries }
                                .associate { it.key to it.value }
                        }

                        _state.value = currentState.copy(
                            events = allTimelineEvents,
                            layoutData = layoutData,
                            isLoading = false,
                            isOffline = true
                        )

                        lastRangeStartIso = startIso
                        lastShowOnlyMine = currentState.showOnlyMine
                        return
                    }
                } catch (_: Exception) {}
            }

            // If the visible range changed (or first load), invalidate related cache entries when online
            if (rangeChanged) {
                try {
                    val online = try { ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { true }
                    if (online) {
                        try { cache.invalidatePrefix("calendar_") } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }

            // Try fetching from API; on failure, fall back to offline cached week data
            var eventsGrouped: Map<String, List<com.tkolymp.shared.event.EventInstance>> = try {
                eventService.fetchEventsGroupedByDay(
                    startRangeIso = startIso,
                    endRangeIso = endIso,
                    onlyMine = currentState.showOnlyMine,
                    first = 500,
                    offset = 0,
                    onlyType = null,
                    cacheNamespace = "calendar_"
                )
            } catch (ex: Exception) {
                // Attempt offline fallback using offline sync manager
                try {
                    val bucketName = if (currentState.showOnlyMine) "MINE" else "ALL"
                    val startDate = timeRange.start.date
                    val weekKey = "offline_cal_${'$'}{bucketName}_${'$'}startDate"
                    var raw = try { ServiceLocator.offlineSyncManager.loadCalendarWeek(weekKey) } catch (_: Exception) { null }
                    if (raw == null) {
                        try {
                            val keys = try { ServiceLocator.offlineSyncManager.listOfflineKeys() } catch (_: Exception) { emptySet() }
                            val candidates = keys.filter { it.startsWith("offline_cal_${bucketName}_") }
                            val exact = candidates.firstOrNull { it.endsWith(startDate.toString()) }
                            val anyKey = exact ?: candidates.firstOrNull()
                            if (anyKey != null) raw = try { ServiceLocator.offlineSyncManager.loadCalendarWeek(anyKey) } catch (_: Exception) { null }
                        } catch (_: Exception) {}
                    }
                    if (raw != null) {
                        var parsed = parseCalendarJson(raw)
                        parsed = try { enrichParsedWithEventDetails(parsed) } catch (_: Exception) { parsed }
                        isOfflineUsed = true
                        // parsed is Map<String, List<EventInstance>>
                        parsed
                    } else throw ex
                } catch (_: Exception) {
                    throw ex
                }
            }

            // If server returned empty map, try offline week summary before deciding to keep previous state
            if (eventsGrouped.isEmpty()) {
                try {
                    val bucketName = if (currentState.showOnlyMine) "MINE" else "ALL"
                    val startDate = timeRange.start.date
                    val weekKey = "offline_cal_${'$'}{bucketName}_${'$'}startDate"
                    var raw = try { ServiceLocator.offlineSyncManager.loadCalendarWeek(weekKey) } catch (_: Exception) { null }
                    if (raw == null) {
                        // try to find any compatible offline_cal_* key for this bucket
                        try {
                            val keys = try { ServiceLocator.offlineSyncManager.listOfflineKeys() } catch (_: Exception) { emptySet() }
                            val candidates = keys.filter { it.startsWith("offline_cal_${bucketName}_") }
                            val exact = candidates.firstOrNull { it.endsWith(startDate.toString()) }
                            val anyKey = exact ?: candidates.firstOrNull()
                            if (anyKey != null) raw = try { ServiceLocator.offlineSyncManager.loadCalendarWeek(anyKey) } catch (_: Exception) { null }
                        } catch (_: Exception) {}
                    }
                    if (raw != null) {
                        var parsed = parseCalendarJson(raw)
                        parsed = try { enrichParsedWithEventDetails(parsed) } catch (_: Exception) { parsed }
                        isOfflineUsed = true
                        // replace eventsGrouped with parsed offline data
                        eventsGrouped = parsed
                    }
                } catch (_: Exception) {}
                // continue — if still empty, we'll render empty events (so date change reflects)
            }

            // Convert server EventInstances to TimelineEvents
            val serverTimeline = eventsGrouped.values.flatten()
                .mapNotNull { instance ->
                    CalendarUtils.eventInstanceToTimelineEvent(
                        instance,
                        myPersonId,
                        myCoupleIds
                    )
                }
                .filter { event ->
                    // Filter by date range
                    event.startTime.date >= timeRange.start.date &&
                    event.startTime.date <= timeRange.end.date
                }

            // Load personal (local) events and expand recurrences into TimelineEvent
            val personalTimeline = try {
                expandPersonalEventsToTimeline(startIso, endIso)
            } catch (_: Exception) { emptyList() }

            val allTimelineEvents = (serverTimeline + personalTimeline).sortedBy { it.startTime }

            // Calculate layout for all events
            val layoutData = if (currentState.viewMode == ViewMode.DAY) {
                // For single day, calculate layout for all events
                CollisionDetectionAlgorithm.calculateLayout(allTimelineEvents)
            } else {
                // For multi-day, calculate layout per day
                allTimelineEvents.groupBy { it.startTime.date }
                    .flatMap { (_, dayEvents) ->
                        CollisionDetectionAlgorithm.calculateLayout(dayEvents).entries
                    }
                    .associate { it.key to it.value }
            }

            _state.value = currentState.copy(
                events = allTimelineEvents,
                layoutData = layoutData,
                isLoading = false,
                isOffline = isOfflineUsed
            )

            lastRangeStartIso = startIso
            lastShowOnlyMine = currentState.showOnlyMine

        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = e.message ?: "Neznámá chyba při načítání událostí"
            )
        }
    }

    // Helpers: parse offline calendar JSON into EventInstance maps and enrich details
    private fun parseCalendarJson(raw: String): Map<String, List<com.tkolymp.shared.event.EventInstance>> {
        return try {
            val json = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
            val result = mutableMapOf<String, MutableList<com.tkolymp.shared.event.EventInstance>>()
            json.entries.forEach { (date, elem) ->
                val arr = elem.jsonArray
                val list = mutableListOf<com.tkolymp.shared.event.EventInstance>()
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

                    val targetCohorts = (obj["targetCohorts"]?.jsonArray)?.mapNotNull { it2 ->
                        val o = it2.jsonObject
                        val cohortId = o["cohortId"]?.jsonPrimitive?.longOrNull ?: o["cohortId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        val cohortObj = o["cohort"]?.jsonObject
                        val cohort = cohortObj?.let { c ->
                            val cid = c["id"]?.jsonPrimitive?.longOrNull ?: c["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                            com.tkolymp.shared.event.Cohort(cid, c["name"]?.jsonPrimitive?.contentOrNull, c["colorRgb"]?.jsonPrimitive?.contentOrNull)
                        }
                        com.tkolymp.shared.event.TargetCohort(cohortId, cohort)
                    } ?: emptyList()

                    val registrations = (obj["eventRegistrationsList"]?.jsonArray)?.mapNotNull { regEl ->
                        val o = regEl.jsonObject
                        val rid = o["id"]?.jsonPrimitive?.longOrNull ?: o["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()

                        val personObj = o["person"]?.jsonObject
                        val person = personObj?.let { p ->
                            val pid = p["id"]?.jsonPrimitive?.longOrNull ?: p["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                            com.tkolymp.shared.event.Person(pid, p["name"]?.jsonPrimitive?.contentOrNull, p["firstName"]?.jsonPrimitive?.contentOrNull, p["lastName"]?.jsonPrimitive?.contentOrNull)
                        }

                        val coupleObj = o["couple"]?.jsonObject
                        val couple = coupleObj?.let { c ->
                            val cid = c["id"]?.jsonPrimitive?.longOrNull ?: c["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                            val manObj = c["man"]?.jsonObject
                            val womanObj = c["woman"]?.jsonObject
                            val man = manObj?.let { m -> com.tkolymp.shared.event.SimpleName(m["firstName"]?.jsonPrimitive?.contentOrNull, m["lastName"]?.jsonPrimitive?.contentOrNull) }
                            val woman = womanObj?.let { w -> com.tkolymp.shared.event.SimpleName(w["firstName"]?.jsonPrimitive?.contentOrNull, w["lastName"]?.jsonPrimitive?.contentOrNull) }
                            com.tkolymp.shared.event.Couple(cid, man, woman)
                        }
                        com.tkolymp.shared.event.Registration(rid, person, couple)
                    } ?: emptyList()

                    val locationObj = obj["location"]?.jsonObject
                    val location = locationObj?.let { l ->
                        val lid = l["id"]?.jsonPrimitive?.longOrNull ?: l["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        com.tkolymp.shared.event.Location(lid, l["name"]?.jsonPrimitive?.contentOrNull)
                    }

                    val event = com.tkolymp.shared.event.Event(eventId, eventName, null, eventType, locationText, false, false, false, trainers, targetCohorts, registrations, location)
                    list += com.tkolymp.shared.event.EventInstance(id, isCancelled, since, until, updatedAt, event)
                }
                result[date] = list
            }
            result
        } catch (e: Exception) { emptyMap() }
    }

    private suspend fun enrichParsedWithEventDetails(parsed: Map<String, List<com.tkolymp.shared.event.EventInstance>>): Map<String, List<com.tkolymp.shared.event.EventInstance>> {
        val json = kotlinx.serialization.json.Json
        val result = mutableMapOf<String, MutableList<com.tkolymp.shared.event.EventInstance>>()
        for ((date, list) in parsed) {
            val newList = mutableListOf<com.tkolymp.shared.event.EventInstance>()
            for (inst in list) {
                val ev = inst.event
                if (ev?.id != null) {
                    try {
                        val raw = ServiceLocator.offlineSyncManager.loadEventDetail(ev.id)
                        if (!raw.isNullOrBlank()) {
                            val obj = json.parseToJsonElement(raw).jsonObject
                            val trainers = (obj["eventTrainersList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
                                ?: (obj["eventTrainersList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                            val targetCohorts = (obj["eventTargetCohortsList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { item ->
                                val o = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                                val cohortId = o["cohortId"]?.jsonPrimitive?.longOrNull ?: o["cohortId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                val cohortObj = o["cohort"] as? kotlinx.serialization.json.JsonObject
                                val cohort = cohortObj?.let { c ->
                                    val cid = c["id"]?.jsonPrimitive?.longOrNull ?: c["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                    com.tkolymp.shared.event.Cohort(cid, c["name"]?.jsonPrimitive?.contentOrNull, c["colorRgb"]?.jsonPrimitive?.contentOrNull)
                                }
                                com.tkolymp.shared.event.TargetCohort(cohortId, cohort)
                            } ?: emptyList()

                            val regArr = when {
                                obj["eventRegistrationsList"] is kotlinx.serialization.json.JsonArray -> obj["eventRegistrationsList"] as kotlinx.serialization.json.JsonArray
                                obj["eventRegistrations"] is kotlinx.serialization.json.JsonObject -> (obj["eventRegistrations"] as kotlinx.serialization.json.JsonObject)["nodes"] as? kotlinx.serialization.json.JsonArray
                                else -> null
                            }
                            val registrations = regArr?.mapNotNull { item ->
                                val o = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                                val rid = o["id"]?.jsonPrimitive?.longOrNull ?: o["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                val personObj = o["person"] as? kotlinx.serialization.json.JsonObject
                                val person = personObj?.let { p ->
                                    val pid = p["id"]?.jsonPrimitive?.longOrNull ?: p["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                    com.tkolymp.shared.event.Person(pid, p["name"]?.jsonPrimitive?.contentOrNull, p["firstName"]?.jsonPrimitive?.contentOrNull, p["lastName"]?.jsonPrimitive?.contentOrNull)
                                }
                                val coupleObj = o["couple"] as? kotlinx.serialization.json.JsonObject
                                val couple = coupleObj?.let { c ->
                                    val cid = c["id"]?.jsonPrimitive?.longOrNull ?: c["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                    val manObj = c["man"] as? kotlinx.serialization.json.JsonObject
                                    val womanObj = c["woman"] as? kotlinx.serialization.json.JsonObject
                                    val man = manObj?.let { m -> com.tkolymp.shared.event.SimpleName(m["firstName"]?.jsonPrimitive?.contentOrNull, m["lastName"]?.jsonPrimitive?.contentOrNull) }
                                    val woman = womanObj?.let { w -> com.tkolymp.shared.event.SimpleName(w["firstName"]?.jsonPrimitive?.contentOrNull, w["lastName"]?.jsonPrimitive?.contentOrNull) }
                                    com.tkolymp.shared.event.Couple(cid, man, woman)
                                }
                                com.tkolymp.shared.event.Registration(rid, person, couple)
                            } ?: emptyList()

                            val locationObj = obj["location"] as? kotlinx.serialization.json.JsonObject
                            val location = locationObj?.let { l ->
                                val lid = l["id"]?.jsonPrimitive?.longOrNull ?: l["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                com.tkolymp.shared.event.Location(lid, l["name"]?.jsonPrimitive?.contentOrNull)
                            }

                            val enrichedEvent = com.tkolymp.shared.event.Event(ev.id, obj["name"]?.jsonPrimitive?.contentOrNull ?: ev.name, obj["description"]?.jsonPrimitive?.contentOrNull, obj["type"]?.jsonPrimitive?.contentOrNull ?: ev.type, obj["locationText"]?.jsonPrimitive?.contentOrNull ?: ev.locationText, obj["isRegistrationOpen"]?.jsonPrimitive?.booleanOrNull ?: ev.isRegistrationOpen, obj["isVisible"]?.jsonPrimitive?.booleanOrNull ?: ev.isVisible, obj["isPublic"]?.jsonPrimitive?.booleanOrNull ?: ev.isPublic, trainers ?: ev.eventTrainersList, targetCohorts.ifEmpty { ev.eventTargetCohortsList }, registrations.ifEmpty { ev.eventRegistrationsList }, location ?: ev.location)
                            newList += com.tkolymp.shared.event.EventInstance(inst.id, inst.isCancelled, inst.since, inst.until, inst.updatedAt, enrichedEvent)
                            continue
                        }
                    } catch (_: Exception) {}
                }
                newList += inst
            }
            result[date] = newList
        }
        return result
    }

    // Expand personal events (including weekly recurrences) into TimelineEvent list
    private suspend fun expandPersonalEventsToTimeline(startIso: String, endIso: String): List<com.tkolymp.shared.calendar.TimelineEvent> {
        return try {
            val result = mutableListOf<com.tkolymp.shared.calendar.TimelineEvent>()
            val personal = try { ServiceLocator.personalEventService.getAll() } catch (_: Exception) { emptyList() }
            val tz = TimeZone.currentSystemDefault()
            val rangeStartDate = try { kotlinx.datetime.Instant.parse(startIso).toLocalDateTime(tz).date } catch (_: Exception) { null }
            val rangeEndDate = try { kotlinx.datetime.Instant.parse(endIso).toLocalDateTime(tz).date } catch (_: Exception) { null }

            for (ev in personal) {
                try {
                    val startInstant = try { kotlinx.datetime.Instant.parse(ev.startIso) } catch (_: Exception) { null }
                    val endInstant = try { kotlinx.datetime.Instant.parse(ev.endIso) } catch (_: Exception) { null }
                    val duration = if (startInstant != null && endInstant != null) (endInstant - startInstant) else null
                    val startLdt = startInstant?.toLocalDateTime(tz)

                    fun addOccurrence(occStartInstant: kotlinx.datetime.Instant) {
                        val occEndInstant = if (duration != null) occStartInstant + duration else try { kotlinx.datetime.Instant.parse(ev.endIso) } catch (_: Exception) { occStartInstant }
                        val sldt = occStartInstant.toLocalDateTime(tz)
                        val eldt = occEndInstant.toLocalDateTime(tz)
                        val id = (ev.id + sldt.date.toString()).hashCode().toLong()
                        val trainingLabel = when (ev.type) {
                            TrainingType.GENERAL -> null
                            TrainingType.STT -> "STT"
                            TrainingType.LAT -> "LAT"
                        }
                        val desc = listOfNotNull(trainingLabel, ev.description).joinToString("\n")

                        val te = com.tkolymp.shared.calendar.TimelineEvent(
                            id = id,
                            eventId = null,
                            title = ev.title,
                            description = if (desc.isBlank()) null else desc,
                            type = "PERSONAL",
                            startTime = sldt,
                            endTime = eldt,
                            isCancelled = false,
                            isMyEvent = true,
                            colorRgb = ev.colorHex,
                            event = null
                        )
                        result += te
                    }

                    if (ev.recurrenceDayOfWeek != null && startLdt != null) {
                        rangeStartDate?.let { rs ->
                            rangeEndDate?.let { re ->
                                var current = rs
                                while (current <= re) {
                                    val dow = current.dayOfWeek.ordinal + 1
                                    if (dow == ev.recurrenceDayOfWeek) {
                                        val occLdt = kotlinx.datetime.LocalDateTime(current.year, current.monthNumber, current.dayOfMonth, startLdt.hour, startLdt.minute, startLdt.second, startLdt.nanosecond)
                                        val occInstant = occLdt.toInstant(tz)
                                        val withinStart = try { if (ev.recurrenceStartIso.isNullOrBlank()) true else kotlinx.datetime.Instant.parse(ev.recurrenceStartIso) <= occInstant } catch (_: Exception) { true }
                                        val withinEnd = try { if (ev.recurrenceEndIso.isNullOrBlank()) true else occInstant <= kotlinx.datetime.Instant.parse(ev.recurrenceEndIso) } catch (_: Exception) { true }
                                        if (withinStart && withinEnd) addOccurrence(occInstant)
                                    }
                                    current = current.plus(1, DateTimeUnit.DAY)
                                }
                            }
                        }
                    } else {
                        if (startInstant != null) {
                            val inRange = try {
                                val rs = rangeStartDate
                                val re = rangeEndDate
                                if (rs != null && re != null) {
                                    val d = startInstant.toLocalDateTime(tz).date
                                    d >= rs && d <= re
                                } else true
                            } catch (_: Exception) { true }
                            if (inRange) addOccurrence(startInstant)
                        }
                    }
                } catch (_: Exception) {
                    // ignore malformed personal event
                }
            }

            result.sortedBy { it.startTime }
        } catch (_: Exception) { emptyList() }
    }
    
    /**
     * Change view mode (day/3-day/week)
     */
    suspend fun setViewMode(mode: ViewMode) {
        if (_state.value.viewMode != mode) {
            _state.value = _state.value.copy(viewMode = mode)
            loadEvents()
        }
    }
    
    /**
     * Navigate to a specific date
     */
    suspend fun setSelectedDate(date: LocalDate) {
        if (_state.value.selectedDate != date) {
            _state.value = _state.value.copy(selectedDate = date)
            loadEvents()
        }
    }
    
    /**
     * Navigate to previous period (day/3-days/week)
     */
    suspend fun navigatePrevious() {
        val currentDate = _state.value.selectedDate
        val newDate = when (_state.value.viewMode) {
            ViewMode.DAY -> currentDate.minus(1, DateTimeUnit.DAY)
            ViewMode.THREE_DAY -> currentDate.minus(3, DateTimeUnit.DAY)
            ViewMode.WEEK -> currentDate.minus(7, DateTimeUnit.DAY)
        }
        setSelectedDate(newDate)
    }
    
    /**
     * Navigate to next period (day/3-days/week)
     */
    suspend fun navigateNext() {
        val currentDate = _state.value.selectedDate
        val newDate = when (_state.value.viewMode) {
            ViewMode.DAY -> currentDate.plus(1, DateTimeUnit.DAY)
            ViewMode.THREE_DAY -> currentDate.plus(3, DateTimeUnit.DAY)
            ViewMode.WEEK -> currentDate.plus(7, DateTimeUnit.DAY)
        }
        setSelectedDate(newDate)
    }
    
    /**
     * Navigate to today
     */
    suspend fun navigateToday() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        setSelectedDate(today)
    }
    
    /**
     * Toggle between "My events" and "All events"
     */
    suspend fun toggleShowOnlyMine() {
        _state.value = _state.value.copy(
            showOnlyMine = !_state.value.showOnlyMine
        )
        loadEvents()
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
    
    /**
     * Get formatted date label for current view
     */
    fun getDateLabel(): String {
        val currentState = _state.value
        val date = currentState.selectedDate
        
        return when (currentState.viewMode) {
            ViewMode.DAY -> CalendarUtils.formatDate(date)
            ViewMode.THREE_DAY, ViewMode.WEEK -> {
                val timeRange = CalendarUtils.calculateTimeRange(date, currentState.viewMode)
                val startDate = timeRange.start.date
                val endDate = timeRange.end.date
                
                if (startDate.month == endDate.month) {
                    "${startDate.dayOfMonth}–${endDate.dayOfMonth}. ${CalendarUtils.getMonthName(startDate.monthNumber)}"
                } else {
                    "${startDate.dayOfMonth}. ${CalendarUtils.getMonthName(startDate.monthNumber)} – " +
                    "${endDate.dayOfMonth}. ${CalendarUtils.getMonthName(endDate.monthNumber)}"
                }
            }
        }
    }
    
    /**
     * Get list of dates for multi-day views
     */
    fun getDatesInRange(): List<LocalDate> {
        val currentState = _state.value
        val timeRange = CalendarUtils.calculateTimeRange(
            currentState.selectedDate,
            currentState.viewMode
        )
        
        val dates = mutableListOf<LocalDate>()
        var currentDate = timeRange.start.date
        
        while (currentDate <= timeRange.end.date) {
            dates.add(currentDate)
            currentDate = currentDate.plus(1, DateTimeUnit.DAY)
        }
        
        return dates
    }
    
    /**
     * Get events for a specific date (used in multi-day views)
     */
    fun getEventsForDate(date: LocalDate): List<EventLayoutData> {
        return _state.value.layoutData.values.filter { 
            it.event.startTime.date == date 
        }
    }
}
