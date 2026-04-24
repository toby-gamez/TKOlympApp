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
                val flattened = grouped.values.flatten()

                // If server returned empty result while we're offline, try offline storage fallback
                if (flattened.isEmpty()) {
                    val online = try { ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { true }
                    if (!online) {
                        try {
                            val startDay = try { kotlinx.datetime.LocalDate.parse(startIso.substringBefore('T')) } catch (_: Exception) { null }
                            val endDay = try { kotlinx.datetime.LocalDate.parse(endIso.substringBefore('T')) } catch (_: Exception) { null }
                            val keys = ServiceLocator.offlineDataStorage.allKeys().filter { it.startsWith("offline_cal_MINE_") }
                                .filter { k ->
                                    try {
                                        val suffix = k.removePrefix("offline_cal_MINE_")
                                        val wk = kotlinx.datetime.LocalDate.parse(suffix)
                                        if (startDay != null && endDay != null) {
                                            // include weeks that start within the requested range
                                            wk <= endDay && wk >= startDay
                                        } else true
                                    } catch (_: Exception) { false }
                                }
                            val parsed = mutableListOf<EventInstance>()
                            for (k in keys) {
                                try {
                                    val raw = ServiceLocator.offlineDataStorage.load(k) ?: continue
                                    val map = parseCalendarJson(raw)
                                    parsed += map.values.flatten()
                                } catch (_: Exception) {}
                            }
                            // Deduplicate by instance id, preferring the most-recent updatedAt/since
                            val deduped = parsed.groupBy { it.id }.mapNotNull { (_, list) ->
                                list.maxByOrNull { it.updatedAt ?: it.since ?: "" }
                            }
                            if (deduped.isNotEmpty()) {
                                _state.value = _state.value.copy(isOffline = true)
                                deduped
                            } else flattened
                        } catch (_: Exception) { flattened }
                    } else flattened
                } else flattened
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Logger.d("OverviewViewModel", "fetchEvents failed: ${e.message}")
                // Try offline fallback: collect available offline_cal_MINE_* keys
                try {
                    val startDay = try { kotlinx.datetime.LocalDate.parse(startIso.substringBefore('T')) } catch (_: Exception) { null }
                    val endDay = try { kotlinx.datetime.LocalDate.parse(endIso.substringBefore('T')) } catch (_: Exception) { null }
                    val keys = ServiceLocator.offlineDataStorage.allKeys().filter { it.startsWith("offline_cal_MINE_") }
                        .filter { k ->
                            try {
                                val suffix = k.removePrefix("offline_cal_MINE_")
                                val wk = kotlinx.datetime.LocalDate.parse(suffix)
                                if (startDay != null && endDay != null) {
                                    wk <= endDay && wk >= startDay
                                } else true
                            } catch (_: Exception) { false }
                        }
                    val parsed = mutableListOf<EventInstance>()
                    for (k in keys) {
                        try {
                            val raw = ServiceLocator.offlineDataStorage.load(k) ?: continue
                            val map = parseCalendarJson(raw)
                            parsed += map.values.flatten()
                        } catch (_: Exception) {}
                    }
                    val deduped = parsed.groupBy { it.id }.mapNotNull { (_, list) -> list.maxByOrNull { it.updatedAt ?: it.since ?: "" } }
                    if (deduped.isNotEmpty()) {
                        _state.value = _state.value.copy(isOffline = true)
                        deduped
                    } else emptyList()
                } catch (_: Exception) { emptyList<EventInstance>() }
            }
            // If some events came from offline minimal JSON (no registrations), try to fetch full
            // event details for those event ids (from cache or offline storage) so we can show
            // participant names like in CalendarScreen.
            events = events.map { inst ->
                val ev = inst.event ?: return@map inst
                if (!ev.eventRegistrationsList.isNullOrEmpty()) return@map inst
                val evId = ev.id ?: return@map inst

                var regs: List<com.tkolymp.shared.event.Registration> = emptyList()
                try {
                    val fullJson = try { eventService.fetchEventById(evId, forceRefresh = false) } catch (_: Exception) { null }
                    val regArr = when {
                        fullJson?.get("eventRegistrationsList") is kotlinx.serialization.json.JsonArray -> fullJson!!.get("eventRegistrationsList") as kotlinx.serialization.json.JsonArray
                        fullJson?.get("eventRegistrations") is kotlinx.serialization.json.JsonObject -> (fullJson!!.get("eventRegistrations") as kotlinx.serialization.json.JsonObject)["nodes"] as? kotlinx.serialization.json.JsonArray
                        else -> null
                    }
                    if (regArr != null) {
                        regs = regArr.mapNotNull { item ->
                            val o = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            val ridPrim = o["id"]?.jsonPrimitive
                            val rid = ridPrim?.longOrNull ?: ridPrim?.contentOrNull?.toLongOrNull()

                            val personObj = o["person"] as? kotlinx.serialization.json.JsonObject
                            val person = personObj?.let { p ->
                                val pidPrim = p["id"]?.jsonPrimitive
                                val pid = pidPrim?.longOrNull ?: pidPrim?.contentOrNull?.toLongOrNull()
                                com.tkolymp.shared.event.Person(pid, p["name"]?.jsonPrimitive?.contentOrNull, p["firstName"]?.jsonPrimitive?.contentOrNull, p["lastName"]?.jsonPrimitive?.contentOrNull)
                            }

                            val coupleObj = o["couple"] as? kotlinx.serialization.json.JsonObject
                            val couple = coupleObj?.let { c ->
                                val cidPrim = c["id"]?.jsonPrimitive
                                val cid = cidPrim?.longOrNull ?: cidPrim?.contentOrNull?.toLongOrNull()
                                val manObj = c["man"] as? kotlinx.serialization.json.JsonObject
                                val womanObj = c["woman"] as? kotlinx.serialization.json.JsonObject
                                val man = manObj?.let { m -> com.tkolymp.shared.event.SimpleName(m["firstName"]?.jsonPrimitive?.contentOrNull, m["lastName"]?.jsonPrimitive?.contentOrNull) }
                                val woman = womanObj?.let { w -> com.tkolymp.shared.event.SimpleName(w["firstName"]?.jsonPrimitive?.contentOrNull, w["lastName"]?.jsonPrimitive?.contentOrNull) }
                                com.tkolymp.shared.event.Couple(cid, man, woman)
                            }

                            com.tkolymp.shared.event.Registration(rid, person, couple)
                        }
                    }
                } catch (_: Exception) {}

                if (regs.isEmpty()) {
                    try {
                        val raw = try { ServiceLocator.offlineSyncManager.loadEventDetail(evId) } catch (_: Exception) { null }
                        if (!raw.isNullOrBlank()) {
                            val parsed = try { Json.parseToJsonElement(raw).jsonObject } catch (_: Exception) { null }
                            val regArr2 = when {
                                parsed?.get("eventRegistrationsList") is kotlinx.serialization.json.JsonArray -> parsed!!.get("eventRegistrationsList") as kotlinx.serialization.json.JsonArray
                                parsed?.get("eventRegistrations") is kotlinx.serialization.json.JsonObject -> (parsed!!.get("eventRegistrations") as kotlinx.serialization.json.JsonObject)["nodes"] as? kotlinx.serialization.json.JsonArray
                                else -> null
                            }
                            if (regArr2 != null) {
                                regs = regArr2.mapNotNull { item ->
                                    val o = item as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                                    val ridPrim = o["id"]?.jsonPrimitive
                                    val rid = ridPrim?.longOrNull ?: ridPrim?.contentOrNull?.toLongOrNull()

                                    val personObj = o["person"] as? kotlinx.serialization.json.JsonObject
                                    val person = personObj?.let { p ->
                                        val pidPrim = p["id"]?.jsonPrimitive
                                        val pid = pidPrim?.longOrNull ?: pidPrim?.contentOrNull?.toLongOrNull()
                                        com.tkolymp.shared.event.Person(pid, p["name"]?.jsonPrimitive?.contentOrNull, p["firstName"]?.jsonPrimitive?.contentOrNull, p["lastName"]?.jsonPrimitive?.contentOrNull)
                                    }

                                    val coupleObj = o["couple"] as? kotlinx.serialization.json.JsonObject
                                    val couple = coupleObj?.let { c ->
                                        val cidPrim = c["id"]?.jsonPrimitive
                                        val cid = cidPrim?.longOrNull ?: cidPrim?.contentOrNull?.toLongOrNull()
                                        val manObj = c["man"] as? kotlinx.serialization.json.JsonObject
                                        val womanObj = c["woman"] as? kotlinx.serialization.json.JsonObject
                                        val man = manObj?.let { m -> com.tkolymp.shared.event.SimpleName(m["firstName"]?.jsonPrimitive?.contentOrNull, m["lastName"]?.jsonPrimitive?.contentOrNull) }
                                        val woman = womanObj?.let { w -> com.tkolymp.shared.event.SimpleName(w["firstName"]?.jsonPrimitive?.contentOrNull, w["lastName"]?.jsonPrimitive?.contentOrNull) }
                                        com.tkolymp.shared.event.Couple(cid, man, woman)
                                    }

                                    com.tkolymp.shared.event.Registration(rid, person, couple)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (regs.isEmpty()) inst else inst.copy(event = ev.copy(eventRegistrationsList = regs))
            }
            val announcements = try {
                val fetched = withContext(Dispatchers.Default) { announcementService.getAnnouncements(false) }
                if (fetched.isNotEmpty()) {
                    fetched.sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }.take(3)
                } else {
                    // If server returned empty result while we're offline, try offline storage fallback
                    val online = try { ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { true }
                    if (!online) {
                        try {
                            val raw = try { ServiceLocator.offlineSyncManager.loadAnnouncements(false) } catch (_: Exception) { null }
                            if (!raw.isNullOrBlank()) {
                                val parsed = try { kotlinx.serialization.json.Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), raw) } catch (_: Exception) { null }
                                if (!parsed.isNullOrEmpty()) {
                                    _state.value = _state.value.copy(isOffline = true)
                                    parsed.sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }.take(3)
                                } else emptyList()
                            } else emptyList()
                        } catch (_: Exception) { emptyList<Announcement>() }
                    } else emptyList()
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Logger.d("OverviewViewModel", "getAnnouncements failed: ${e.message}")
                // As a secondary fallback, try loading offline announcements
                try {
                    val raw = try { ServiceLocator.offlineSyncManager.loadAnnouncements(false) } catch (_: Exception) { null }
                    if (!raw.isNullOrBlank()) {
                        val parsed = try { kotlinx.serialization.json.Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), raw) } catch (_: Exception) { null }
                        if (!parsed.isNullOrEmpty()) {
                            _state.value = _state.value.copy(isOffline = true)
                            parsed.sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }.take(3)
                        } else emptyList()
                    } else emptyList()
                } catch (_: Exception) { emptyList<Announcement>() }
            }

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

            // Birthdays via PeopleService with offline fallback
            val upcomingBirthdays = try {
                val people = withContext(Dispatchers.Default) { peopleService.fetchPeople() }
                if (people.isNotEmpty()) {
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
                } else {
                    // try offline fallback when server returned empty
                    val online = try { ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { true }
                    if (!online) {
                        try {
                            val raw = try { ServiceLocator.offlineSyncManager.loadPeople() } catch (_: Exception) { null }
                            if (!raw.isNullOrBlank()) {
                                val arr = try { Json.parseToJsonElement(raw).jsonArray } catch (_: Exception) { null }
                                if (arr != null) {
                                    val parsedPeople = arr.mapNotNull { node ->
                                        try {
                                            val obj = node.jsonObject
                                            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                                            val first = obj["firstName"]?.jsonPrimitive?.contentOrNull
                                            val last = obj["lastName"]?.jsonPrimitive?.contentOrNull
                                            val prefix = obj["prefixTitle"]?.jsonPrimitive?.contentOrNull
                                            val suffix = obj["suffixTitle"]?.jsonPrimitive?.contentOrNull
                                            val birth = obj["birthDate"]?.jsonPrimitive?.contentOrNull
                                            val memberships = (obj["cohortMembershipsList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { mEl ->
                                                val mObj = mEl as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                                                val cohortObj = mObj["cohort"] as? kotlinx.serialization.json.JsonObject
                                                val cId = cohortObj?.get("id")?.jsonPrimitive?.contentOrNull
                                                val cName = cohortObj?.get("name")?.jsonPrimitive?.contentOrNull
                                                val cColor = cohortObj?.get("colorRgb")?.jsonPrimitive?.contentOrNull
                                                val cVis = cohortObj?.get("isVisible")?.jsonPrimitive?.contentOrNull?.let { it == "true" }
                                                com.tkolymp.shared.people.CohortMembership(com.tkolymp.shared.people.Cohort(cId, cName, cColor, cVis), mObj["since"]?.jsonPrimitive?.contentOrNull, mObj["until"]?.jsonPrimitive?.contentOrNull)
                                            } ?: emptyList()
                                            com.tkolymp.shared.people.Person(id, first, last, prefix, suffix, birth, memberships)
                                        } catch (_: Exception) { null }
                                    }
                                    _state.value = _state.value.copy(isOffline = true)
                                    parsedPeople
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
                                } else emptyList()
                            } else emptyList()
                        } catch (_: Exception) { emptyList<BirthdayEntry>() }
                    } else emptyList()
                }
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
            !inst.event.eventTrainersList.isNullOrEmpty() &&
            !inst.event.eventTrainersList.firstOrNull().isNullOrBlank()

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
