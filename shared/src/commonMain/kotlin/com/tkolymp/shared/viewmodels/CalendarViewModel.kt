package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.event.EventInstance
import com.tkolymp.shared.storage.OfflineDataStorage
import com.tkolymp.shared.personalevents.TrainingType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
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
import kotlin.time.Instant
import com.tkolymp.shared.event.firstTrainerOrEmpty
import com.tkolymp.shared.event.parseRegistrationsFromJson
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.sync.OfflineKeys
import com.tkolymp.shared.utils.AppConstants
import com.tkolymp.shared.event.EventType
import com.tkolymp.shared.event.toEventType
import com.tkolymp.shared.calendar.parseCalendarJson
import com.tkolymp.shared.competitions.Competition
import androidx.compose.runtime.Immutable
import com.tkolymp.shared.competitions.ICompetitionService

@Immutable
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
    val hasCancelledMineToShow: Boolean = false,
    val competitionsByDay: Map<String, List<Competition>> = emptyMap(),
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

class CalendarViewModel(
    private val eventService: com.tkolymp.shared.event.IEventService = ServiceLocator.eventService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService,
    private val cache: CacheService = ServiceLocator.cacheService,
    private val offlineDataStorage: OfflineDataStorage = ServiceLocator.offlineDataStorage,
    private val competitionService: ICompetitionService = ServiceLocator.competitionService
) : ViewModel() {
    private val _state = MutableStateFlow(CalendarState())
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    private var lastWeekOffset: Int? = null
    private var lastWeekStart: kotlinx.datetime.LocalDate? = null
    private var lastOnlyMine: Boolean? = null

    suspend fun load(weekOffset: Int, onlyMine: Boolean, forceRefresh: Boolean = false, weekStartOverride: kotlinx.datetime.LocalDate? = null) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val weekStart = weekStartOverride ?: today.plus(weekOffset * 7, DateTimeUnit.DAY)
        val endDay = weekStart.plus(6, DateTimeUnit.DAY)
        val startIso = weekStart.toString() + "T00:00:00Z"
        val endIso = endDay.toString() + "T23:59:59Z"

        val visibleDates = buildList {
            var dd = weekStart
            while (dd <= endDay) { add(dd.toString()); dd = dd.plus(1, DateTimeUnit.DAY) }
        }
        val todayString = today.toString()
        val tomorrowString = today.plus(1, DateTimeUnit.DAY).toString()

        val isFirstLoad = lastWeekStart == null
        val weekChanged = weekStart != lastWeekStart
        val onlyMineChanged = onlyMine != lastOnlyMine
        // only invalidate cache when explicitly forced or when the week changed
        val shouldInvalidate = forceRefresh || weekChanged
        // Show loading indicator only for initial loads, week changes, and explicit refreshes.
        // Tab filter switches and resume-triggered reloads run silently (current data stays visible).
        val showLoadingIndicator = forceRefresh || isFirstLoad || weekChanged

        // Determine online status before touching state so we don't incorrectly clear isOffline.
        val isCurrentlyOnline = try { ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { true }

        // preload cached user ids (used later in state)
        val pid = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("CalendarViewModel", "getCachedPersonId failed: ${e.message}"); null }
        val cids = try { userService.getCachedCoupleIds() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("CalendarViewModel", "getCachedCoupleIds failed: ${e.message}"); emptyList() }

        _state.value = _state.value.copy(
            isLoading = showLoadingIndicator,
            error = null,
            // Only reset isOffline when we're about to do a fresh online load; preserve it otherwise
            // to avoid the screen-level offline cache flickering away and back.
            isOffline = if (showLoadingIndicator && isCurrentlyOnline) false else _state.value.isOffline
        )

        // If only the filter changed (My/All) and we're not forcing refresh, try offline bucket first —
        // but only when offline. When online the bucket summary omits eventRegistrationsList, which
        // makes all individual-lesson slots appear as "VOLNO" until the user manually refreshes.
        if (onlyMineChanged && !forceRefresh && !isCurrentlyOnline) {
            try {
                val bucket = loadOfflineBucket(onlyMine, weekStart, startIso, endIso)
                if (bucket != null) {
                    val parsed = try { mergePersonalEventsIntoMap(bucket, startIso, endIso) } catch (_: Exception) { bucket }
                    val (lessons, other) = splitEventMaps(parsed)
                    lastWeekStart = weekStart
                    lastOnlyMine = onlyMine
                    _state.value = _state.value.copy(
                        eventsByDay = parsed,
                        lessonsByTrainerByDay = lessons,
                        otherEventsByDay = other,
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
                if (isCurrentlyOnline) {
                    cache.invalidatePrefix("calendar_")
                } else {
                    Logger.d("CalendarViewModel", "skipping cache invalidation: offline")
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("CalendarViewModel", "cache invalidation failed: ${e.message}") }
        }

        try {
            val map: Map<String, List<EventInstance>> = try {
                withContext(Dispatchers.Default) {
                    eventService.fetchEventsGroupedByDay(startIso, endIso, onlyMine, AppConstants.FETCH_LIMIT_WEEK, 0, null, cacheNamespace = "calendar_")
                }
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                // offline fallback — personal events will be merged in the main flow below
                val bucket = loadOfflineBucket(onlyMine, weekStart, startIso, endIso)
                if (bucket != null) {
                    _state.value = _state.value.copy(isOffline = true)
                    bucket
                } else throw ex
            }

            // If the server returned an empty map (possible when offline but no exception thrown),
            // try to load the offline week summary saved by OfflineSyncManager.
            if (map.isEmpty()) {
                try {
                    val bucket = loadOfflineBucket(onlyMine, weekStart, startIso, endIso)
                    if (bucket != null) {
                        val parsed = try { mergePersonalEventsIntoMap(bucket, startIso, endIso) } catch (_: Exception) { bucket }
                        val (lessons, other) = splitEventMaps(parsed)
                        lastWeekStart = weekStart
                        lastOnlyMine = onlyMine
                        _state.value = _state.value.copy(
                            eventsByDay = parsed,
                            lessonsByTrainerByDay = lessons,
                            otherEventsByDay = other,
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
                } catch (_: Exception) {}
                // If we reach here and the server map is empty and we have existing data,
                // avoid overwriting the current non-empty state with an empty result.
                // But still re-merge personal events so newly saved ones appear.
                if (map.isEmpty() && _state.value.eventsByDay.isNotEmpty() && !onlyMineChanged && !forceRefresh) {
                    val currentMap = _state.value.eventsByDay
                    val remerged = try { mergePersonalEventsIntoMap(currentMap, startIso, endIso) } catch (_: Exception) { currentMap }
                    val (lessons, other) = splitEventMaps(remerged)
                    _state.value = _state.value.copy(
                        eventsByDay = remerged,
                        lessonsByTrainerByDay = lessons,
                        otherEventsByDay = other,
                        isLoading = false
                    )
                    return
                }
            }

            // merge personal events into server map (or offline bucket from exception fallback)
            val mergedMap = try { mergePersonalEventsIntoMap(map, startIso, endIso) } catch (_: Exception) { map }
            val (lessons, other) = splitEventMaps(mergedMap)

            lastWeekStart = weekStart
            lastOnlyMine = onlyMine

            val hasCancelledMineToShow = if (onlyMine) {
                val dismissedIds = loadDismissedIds()
                mergedMap.values.flatten().any { it.isCancelled && it.id.toString() !in dismissedIds }
            } else {
                _state.value.hasCancelledMineToShow
            }

            val competitionPersonIds = if (onlyMine) pid?.toLongOrNull()?.let { listOf(it) } else null
            val competitionsByDay = try {
                competitionService.getUpcomingCompetitions(
                    pSince = weekStart.toString(),
                    pUntil = endDay.plus(1, DateTimeUnit.DAY).toString(),
                    pPersonIds = competitionPersonIds
                ).groupBy { it.competitionDate }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyMap() }

            _state.value = _state.value.copy(
                eventsByDay = mergedMap,
                lessonsByTrainerByDay = lessons,
                otherEventsByDay = other,
                visibleDates = visibleDates,
                todayString = todayString,
                tomorrowString = tomorrowString,
                myPersonId = pid,
                myCoupleIds = cids,
                hasCancelledMineToShow = hasCancelledMineToShow,
                competitionsByDay = competitionsByDay,
                isLoading = false
            )
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoading))
        }
    }

    private fun splitEventMaps(events: Map<String, List<EventInstance>>): Pair<
        Map<String, Map<String, List<EventInstance>>>,
        Map<String, List<EventInstance>>
    > {
        val lessons = events.mapValues { (_, list) ->
            list.filter { isLesson(it) }
                .groupBy { it.event.firstTrainerOrEmpty() }
                .mapValues { (_, instances) -> instances.sortedBy { it.since } }
        }
        val other = events.mapValues { (_, list) ->
            val lessonSet = list.filter { isLesson(it) }.toSet()
            (list - lessonSet).sortedBy { it.since }
        }
        return lessons to other
    }

    private suspend fun loadOfflineBucket(
        onlyMine: Boolean,
        weekStart: kotlinx.datetime.LocalDate,
        startIso: String,
        endIso: String
    ): Map<String, List<EventInstance>>? {
        val bucketName = if (onlyMine) "MINE" else "ALL"
        val weekKey = OfflineKeys.CAL_PREFIX + "${bucketName}_$weekStart"
        val raw = try { ServiceLocator.offlineSyncManager.loadCalendarWeek(weekKey) } catch (_: Exception) { null } ?: return null
        var parsed = parseCalendarJson(raw)
        parsed = try { enrichParsedWithEventDetails(parsed) } catch (_: Exception) { parsed }
        return parsed
    }

    private suspend fun mergePersonalEventsIntoMap(
        original: Map<String, List<EventInstance>>,
        startIso: String,
        endIso: String
    ): Map<String, List<EventInstance>> {
        return try {
            val result = original.mapValues { (_, v) -> v.toMutableList() }.toMutableMap()
            val personal = try { ServiceLocator.personalEventService.getAll() } catch (_: Exception) { emptyList() }
            val tz = TimeZone.currentSystemDefault()
            val rangeStartDate = try { Instant.parse(startIso).toLocalDateTime(tz).date } catch (_: Exception) { null }
            val rangeEndDate = try { Instant.parse(endIso).toLocalDateTime(tz).date } catch (_: Exception) { null }

            for (ev in personal) {
                try {
                    val startInstant = try { Instant.parse(ev.startIso) } catch (_: Exception) { null }
                    val endInstant = try { Instant.parse(ev.endIso) } catch (_: Exception) { null }
                    val duration = if (startInstant != null && endInstant != null) (endInstant - startInstant) else null
                    val startLdt = startInstant?.toLocalDateTime(tz)

                    // Helper to add an occurrence for a given local date
                    fun addOccurrenceFor(date: kotlinx.datetime.LocalDate, occStartInstant: Instant) {
                        val occEndInstant = if (duration != null) occStartInstant + duration else try { Instant.parse(ev.endIso) } catch (_: Exception) { occStartInstant }
                        val dateKey = date.toString()
                        val trainingLabel = when (ev.type) {
                            TrainingType.GENERAL -> null
                            TrainingType.STT -> "STT"
                            TrainingType.LAT -> "LAT"
                        }
                        val desc = listOfNotNull(trainingLabel, ev.description).joinToString("\n")

                        val event = com.tkolymp.shared.event.Event(
                            id = null,
                            name = ev.title,
                            description = if (desc.isBlank()) null else desc,
                            type = EventType.PERSONAL.rawValue,
                            locationText = ev.location,
                            isRegistrationOpen = false,
                            isVisible = true,
                            isPublic = false,
                            eventTrainersList = emptyList(),
                            eventTargetCohortsList = emptyList(),
                            eventRegistrationsList = emptyList(),
                            location = null
                        )
                        // make id per-occurrence to avoid collisions using 64-bit FNV-1a
                        fun fnv1a64(s: String): Long {
                            var hash = 0xcbf29ce484222325UL
                            val prime = 0x100000001b3UL
                            val bytes = s.encodeToByteArray()
                            for (b in bytes) {
                                hash = hash xor (b.toULong() and 0xffUL)
                                hash *= prime
                            }
                            return hash.toLong()
                        }
                        val occId = fnv1a64("${ev.id}_${date}")
                        val inst = com.tkolymp.shared.event.EventInstance(occId, false, occStartInstant.toString(), occEndInstant.toString(), null, event)
                        val list = result.getOrPut(dateKey) { mutableListOf() }
                        list += inst
                    }

                    if (ev.recurrenceDayOfWeek != null && startLdt != null) {
                        // Expand weekly recurrences across the requested range
                        rangeStartDate?.let { rs ->
                            rangeEndDate?.let { re ->
                                startLdt.let { sldt ->
                                    var current = rs
                                    while (current <= re) {
                                        val dow = current.dayOfWeek.isoDayNumber // 1=Mon..7=Sun
                                        if (dow == ev.recurrenceDayOfWeek) {
                                            // Check recurrence boundaries if present
                                            val occLdt = kotlinx.datetime.LocalDateTime(current.year, current.month.number, current.day, sldt.hour, sldt.minute, sldt.second, sldt.nanosecond)
                                            val occInstant = occLdt.toInstant(tz)
                                            val withinStart = try { if (ev.recurrenceStartIso.isNullOrBlank()) true else Instant.parse(ev.recurrenceStartIso) <= occInstant } catch (_: Exception) { true }
                                            val withinEnd = try { if (ev.recurrenceEndIso.isNullOrBlank()) true else occInstant <= Instant.parse(ev.recurrenceEndIso) } catch (_: Exception) { true }
                                            if (withinStart && withinEnd) addOccurrenceFor(current, occInstant)
                                        }
                                        current = current.plus(1, DateTimeUnit.DAY)
                                    }
                                }
                            }
                        }
                    } else {
                        // Single occurrence (non-recurring)
                        if (startInstant != null) {
                            // Only add if within requested range (safety)
                            val inRange = try {
                                val rs = rangeStartDate
                                val re = rangeEndDate
                                if (rs != null && re != null) {
                                    val d = startInstant.toLocalDateTime(tz).date
                                    d >= rs && d <= re
                                } else true
                            } catch (_: Exception) { true }
                            if (inRange) {
                                val date = startInstant.toLocalDateTime(tz).date
                                addOccurrenceFor(date, startInstant)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // ignore malformed personal event
                }
            }

            // Ensure lists are immutable and sorted by 'since'
            result.mapValues { (_, v) -> v.sortedBy { it.since } }
        } catch (_: Exception) { original }
    }


    private suspend fun enrichParsedWithEventDetails(parsed: Map<String, List<EventInstance>>): Map<String, List<EventInstance>> {
        val json = kotlinx.serialization.json.Json
        val result = mutableMapOf<String, MutableList<EventInstance>>()
        for ((date, list) in parsed) {
            val newList = mutableListOf<EventInstance>()
            for (inst in list) {
                val ev = inst.event
                if (ev?.id != null) {
                    try {
                        val raw = ServiceLocator.offlineSyncManager.loadEventDetail(ev.id)
                        if (!raw.isNullOrBlank()) {
                            val obj = json.parseToJsonElement(raw).jsonObject
                            // build trainers
                            val trainers = (obj["eventTrainersList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull }
                                ?: (obj["eventTrainersList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                            // target cohorts
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

                            // registrations (handle a couple of possible shapes)
                            val regArr = when {
                                obj["eventRegistrationsList"] is kotlinx.serialization.json.JsonArray -> obj["eventRegistrationsList"] as kotlinx.serialization.json.JsonArray
                                obj["eventRegistrations"] is kotlinx.serialization.json.JsonObject -> (obj["eventRegistrations"] as kotlinx.serialization.json.JsonObject)["nodes"] as? kotlinx.serialization.json.JsonArray
                                else -> null
                            }
                            val registrations = regArr?.let { parseRegistrationsFromJson(it) } ?: emptyList()

                            val locationObj = obj["location"] as? kotlinx.serialization.json.JsonObject
                            val location = locationObj?.let { l ->
                                val lid = l["id"]?.jsonPrimitive?.longOrNull ?: l["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                com.tkolymp.shared.event.Location(lid, l["name"]?.jsonPrimitive?.contentOrNull)
                            }

                            val enrichedEvent = com.tkolymp.shared.event.Event(ev.id, obj["name"]?.jsonPrimitive?.contentOrNull ?: ev.name, obj["description"]?.jsonPrimitive?.contentOrNull, obj["type"]?.jsonPrimitive?.contentOrNull ?: ev.type, obj["locationText"]?.jsonPrimitive?.contentOrNull ?: ev.locationText, obj["isRegistrationOpen"]?.jsonPrimitive?.booleanOrNull ?: ev.isRegistrationOpen, obj["isVisible"]?.jsonPrimitive?.booleanOrNull ?: ev.isVisible, obj["isPublic"]?.jsonPrimitive?.booleanOrNull ?: ev.isPublic, trainers, targetCohorts.ifEmpty { ev.eventTargetCohortsList }, registrations.ifEmpty { ev.eventRegistrationsList }, location ?: ev.location)
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

    private fun isLesson(inst: EventInstance): Boolean =
        inst.event?.type?.toEventType() == EventType.LESSON == true &&
            inst.event.eventTrainersList.isNotEmpty() &&
            !inst.event.eventTrainersList.firstOrNull().isNullOrBlank()

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private suspend fun loadDismissedIds(): Set<String> {
        return try {
            val raw = withContext(Dispatchers.Default) { offlineDataStorage.load(OfflineKeys.DISMISSED_CANCELLED) }
            raw?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptySet() }
    }
}
