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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
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
import com.tkolymp.shared.payments.PaymentService
import com.tkolymp.shared.models.UserRole
import com.tkolymp.shared.competitions.Competition
import androidx.compose.runtime.Immutable
import com.tkolymp.shared.competitions.ICompetitionService

@Immutable
data class BirthdayEntry(
    val personId: String,
    val name: String,
    val formattedBirthDate: String?,
    val days: Int,
    val cohortColors: List<String> = emptyList()
)

@Immutable
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
    val weeklyGoal: Int = 0,
    val currentWeekCount: Int = 0,
    val currentWeekMinutes: Long = 0L,
    val paymentDaysUntilDue: Int? = null,
    val isDancer: Boolean = true,
    val nearestCompetition: Competition? = null,
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

class OverviewViewModel(
    private val eventService: com.tkolymp.shared.event.IEventService = ServiceLocator.eventService,
    private val announcementService: com.tkolymp.shared.announcements.IAnnouncementService = ServiceLocator.announcementService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService,
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService,
    private val cache: CacheService = ServiceLocator.cacheService,
    private val calendarPreferenceStorage: com.tkolymp.shared.storage.ICalendarPreferenceStorage = ServiceLocator.calendarPreferenceStorage,
    private val paymentService: PaymentService = ServiceLocator.paymentService,
    private val onboardingStorage: com.tkolymp.shared.storage.OnboardingStorage? = ServiceLocator.onboardingStorage,
    private val competitionService: ICompetitionService = ServiceLocator.competitionService
) : ViewModel() {
    private val _state = MutableStateFlow(OverviewState())
    val state: StateFlow<OverviewState> = _state.asStateFlow()

    suspend fun loadOverview(forceRefresh: Boolean = false) {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val isoDay = when (today.dayOfWeek) {
            kotlinx.datetime.DayOfWeek.MONDAY -> 1
            kotlinx.datetime.DayOfWeek.TUESDAY -> 2
            kotlinx.datetime.DayOfWeek.WEDNESDAY -> 3
            kotlinx.datetime.DayOfWeek.THURSDAY -> 4
            kotlinx.datetime.DayOfWeek.FRIDAY -> 5
            kotlinx.datetime.DayOfWeek.SATURDAY -> 6
            kotlinx.datetime.DayOfWeek.SUNDAY -> 7
        }
        val weekMonday = today.minus(isoDay - 1, DateTimeUnit.DAY)
        val startIso = weekMonday.toString() + "T00:00:00Z"
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
            val pid = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("OverviewViewModel", "getCachedPersonId failed: ${e.message}"); null }
            val cids = try { userService.getCachedCoupleIds() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("OverviewViewModel", "getCachedCoupleIds failed: ${e.message}"); emptyList<String>() }

            var events: List<EventInstance> = emptyList()
            var announcements: List<Announcement> = emptyList()
            var upcomingBirthdays: List<BirthdayEntry> = emptyList()
            var paymentDaysUntilDue: Int? = null
            var nearestCompetition: Competition? = null

            coroutineScope {
                val eventsDef = async {
                    var fetched = try {
                        val grouped = withContext(Dispatchers.Default) {
                            eventService.fetchEventsGroupedByDay(startIso, endIso, onlyMine = true, first = AppConstants.FETCH_LIMIT_WEEK, cacheNamespace = "overview_")
                        }
                        val flattened = grouped.values.flatten()

                        if (flattened.isEmpty()) {
                            val online = try { ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { true }
                            if (!online) {
                                try {
                                    val startDay = try { kotlinx.datetime.LocalDate.parse(startIso.substringBefore('T')) } catch (_: Exception) { null }
                                    val endDay = try { kotlinx.datetime.LocalDate.parse(endIso.substringBefore('T')) } catch (_: Exception) { null }
                                    val keys = ServiceLocator.offlineDataStorage.allKeys().filter { it.startsWith(OfflineKeys.CAL_PREFIX + "MINE_") }
                                        .filter { k ->
                                            try {
                                                val suffix = k.removePrefix(OfflineKeys.CAL_PREFIX + "MINE_")
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
                        try {
                            val startDay = try { kotlinx.datetime.LocalDate.parse(startIso.substringBefore('T')) } catch (_: Exception) { null }
                            val endDay = try { kotlinx.datetime.LocalDate.parse(endIso.substringBefore('T')) } catch (_: Exception) { null }
                            val keys = ServiceLocator.offlineDataStorage.allKeys().filter { it.startsWith(OfflineKeys.CAL_PREFIX + "MINE_") }
                                .filter { k ->
                                    try {
                                        val suffix = k.removePrefix(OfflineKeys.CAL_PREFIX + "MINE_")
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

                    coroutineScope {
                        fetched.map { inst ->
                            async {
                                val ev = inst.event ?: return@async inst
                                if (!ev.eventRegistrationsList.isNullOrEmpty()) return@async inst
                                val evId = ev.id ?: return@async inst

                                var regs: List<com.tkolymp.shared.event.Registration> = emptyList()
                                try {
                                    val fullJson = try { eventService.fetchEventById(evId, forceRefresh = false) } catch (_: Exception) { null }
                                    val regArr = fullJson?.let { fj ->
                                        when {
                                            fj["eventRegistrationsList"] is kotlinx.serialization.json.JsonArray -> fj["eventRegistrationsList"] as kotlinx.serialization.json.JsonArray
                                            fj["eventRegistrations"] is kotlinx.serialization.json.JsonObject -> (fj["eventRegistrations"] as kotlinx.serialization.json.JsonObject)["nodes"] as? kotlinx.serialization.json.JsonArray
                                            else -> null
                                        }
                                    }
                                    if (regArr != null) {
                                        regs = parseRegistrationsFromJson(regArr)
                                    }
                                } catch (_: Exception) {}

                                if (regs.isEmpty()) {
                                    try {
                                        val raw = try { ServiceLocator.offlineSyncManager.loadEventDetail(evId) } catch (_: Exception) { null }
                                        if (!raw.isNullOrBlank()) {
                                            val parsed = try { AppJson.parseToJsonElement(raw).jsonObject } catch (_: Exception) { null }
                                            val regArr2 = parsed?.let { pj ->
                                                when {
                                                    pj["eventRegistrationsList"] is kotlinx.serialization.json.JsonArray -> pj["eventRegistrationsList"] as kotlinx.serialization.json.JsonArray
                                                    pj["eventRegistrations"] is kotlinx.serialization.json.JsonObject -> (pj["eventRegistrations"] as kotlinx.serialization.json.JsonObject)["nodes"] as? kotlinx.serialization.json.JsonArray
                                                    else -> null
                                                }
                                            }
                                            if (regArr2 != null) {
                                                regs = parseRegistrationsFromJson(regArr2)
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }

                                if (regs.isEmpty()) inst else inst.copy(event = ev.copy(eventRegistrationsList = regs))
                            }
                        }.awaitAll()
                    }
                }

                val announcementsDef = async {
                    try {
                        val fetched = when (val r = withContext(Dispatchers.Default) { announcementService.getAnnouncements(false) }) {
                            is DataResult.Success -> r.data
                            is DataResult.Error -> emptyList()
                        }
                        if (fetched.isNotEmpty()) {
                            fetched.sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }.take(3)
                        } else {
                            val online = try { ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { true }
                            if (!online) {
                                try {
                                    val raw = try { ServiceLocator.offlineSyncManager.loadAnnouncements(false) } catch (_: Exception) { null }
                                    if (!raw.isNullOrBlank()) {
                                        val parsed = try { AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), raw) } catch (_: Exception) { null }
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
                        try {
                            val raw = try { ServiceLocator.offlineSyncManager.loadAnnouncements(false) } catch (_: Exception) { null }
                            if (!raw.isNullOrBlank()) {
                                val parsed = try { AppJson.decodeFromString(kotlinx.serialization.builtins.ListSerializer(com.tkolymp.shared.announcements.Announcement.serializer()), raw) } catch (_: Exception) { null }
                                if (!parsed.isNullOrEmpty()) {
                                    _state.value = _state.value.copy(isOffline = true)
                                    parsed.sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }.take(3)
                                } else emptyList()
                            } else emptyList()
                        } catch (_: Exception) { emptyList<Announcement>() }
                    }
                }

                val birthdaysDef = async {
                    try {
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
                                        val cohortColors = p.cohortMembershipsList
                                            .mapNotNull { it.cohort }
                                            .filter { it.isVisible != false }
                                            .mapNotNull { it.colorRgb }
                                            .filter { it.isNotBlank() }
                                        BirthdayEntry(
                                            personId = p.id,
                                            name = name,
                                            formattedBirthDate = formatBirthDateString(p.birthDate),
                                            days = days,
                                            cohortColors = cohortColors
                                        )
                                    }
                                }
                                .sortedBy { it.days }
                                .take(3)
                        } else {
                            val online = try { ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { true }
                            if (!online) {
                                try {
                                    val raw = try { ServiceLocator.offlineSyncManager.loadPeople() } catch (_: Exception) { null }
                                    if (!raw.isNullOrBlank()) {
                                        val arr = try { AppJson.parseToJsonElement(raw).jsonArray } catch (_: Exception) { null }
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
                                                        val cohortColors = p.cohortMembershipsList
                                                            .mapNotNull { it.cohort }
                                                            .filter { it.isVisible != false }
                                                            .mapNotNull { it.colorRgb }
                                                            .filter { it.isNotBlank() }
                                                        BirthdayEntry(
                                                            personId = p.id,
                                                            name = name,
                                                            formattedBirthDate = formatBirthDateString(p.birthDate),
                                                            days = days,
                                                            cohortColors = cohortColors
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
                }

                val paymentsDef = async {
                    try {
                        val debtors = withContext(Dispatchers.Default) { paymentService.fetchDebtorsForPerson(pid) }
                        val soonestDueAt = debtors
                            .filter { it.isUnpaid == true }
                            .mapNotNull { it.payment?.dueAt?.takeIf { s -> s.isNotBlank() } }
                            .minOrNull()
                        if (soonestDueAt != null) {
                            val dueDate = try {
                                kotlinx.datetime.LocalDate.parse(soonestDueAt.substringBefore('T'))
                            } catch (_: Exception) { null }
                            dueDate?.let { (it.toEpochDays() - today.toEpochDays()).toInt() }
                        } else null
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                }

                val competitionDef = async {
                    try {
                        val pidLong = pid?.toLongOrNull()
                        val personFilter = if (pidLong != null) listOf(pidLong) else null
                        competitionService.getNearestUpcoming(pPersonIds = personFilter)
                    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                }

                events = eventsDef.await()
                announcements = announcementsDef.await()
                upcomingBirthdays = birthdaysDef.await()
                paymentDaysUntilDue = paymentsDef.await()
                nearestCompetition = competitionDef.await()
            }

            val isDancer = try { onboardingStorage?.getUserRole() != UserRole.PARENT } catch (_: Exception) { true }

            val weeklyGoal = try { calendarPreferenceStorage.getWeeklyGoal() } catch (_: Exception) { 0 }
            val mondayStr = weekMonday.toString()
            val sundayStr = weekMonday.plus(6, DateTimeUnit.DAY).toString()

            val campsMapByDay = withContext(Dispatchers.Default) {
                events.filter { it.event?.type?.toEventType() == EventType.CAMP == true }
                    .sortedBy { it.since ?: it.updatedAt ?: "" }
                    .take(2)
                    .groupBy { inst ->
                        val s = inst.since ?: inst.until ?: inst.updatedAt ?: ""
                        s.substringBefore('T').ifEmpty { s }
                    }
                    .entries.sortedBy { it.key }
                    .associate { it.key to it.value }
            }

            val trainings = withContext(Dispatchers.Default) {
                events.sortedBy { it.since ?: it.updatedAt ?: "" }
            }
            val trainingsMapByDay = withContext(Dispatchers.Default) {
                trainings
                    .groupBy { inst ->
                        val s = inst.since ?: inst.until ?: inst.updatedAt ?: ""
                        s.substringBefore('T').ifEmpty { s }
                    }
                    .entries.sortedBy { it.key }
                    .associate { it.key to it.value }
            }

            val nowInstant = Clock.System.now()
            val sortedKeys = withContext(Dispatchers.Default) { trainingsMapByDay.keys.sorted() }
            val selectedKey = withContext(Dispatchers.Default) {
                if (sortedKeys.isEmpty()) null
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
            }

            val selectedDayList = if (selectedKey != null) trainingsMapByDay[selectedKey] ?: emptyList() else emptyList()
            val lessons = selectedDayList.filter { isLesson(it) }
            val otherEvents = (selectedDayList - lessons.toSet()).sortedBy { it.since }
            val lessonsByTrainer = withContext(Dispatchers.Default) {
                lessons
                    .groupBy { it.event.firstTrainerOrEmpty() }
                    .mapValues { (_, insts) -> insts.sortedBy { it.since } }
            }

            val thisWeekEvents = withContext(Dispatchers.Default) {
                events.filter { inst ->
                    val ds = (inst.since ?: inst.until ?: "").substringBefore('T')
                    ds in mondayStr..sundayStr && !inst.isCancelled
                }
            }
            val currentWeekCount = thisWeekEvents.size
            val currentWeekMinutes = withContext(Dispatchers.Default) {
                thisWeekEvents.sumOf { inst -> overviewDurationMin(inst.since, inst.until) }
            }

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
                weeklyGoal = weeklyGoal,
                currentWeekCount = currentWeekCount,
                currentWeekMinutes = currentWeekMinutes,
                paymentDaysUntilDue = paymentDaysUntilDue,
                isDancer = isDancer,
                nearestCompetition = nearestCompetition,
                isLoading = false
            )
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoadingOverview))
        }
    }

    private fun isLesson(inst: EventInstance): Boolean =
        inst.event?.type?.toEventType() == EventType.LESSON == true &&
            !inst.event.eventTrainersList.isNullOrEmpty() &&
            !inst.event.eventTrainersList.firstOrNull().isNullOrBlank()

    private fun overviewDurationMin(since: String?, until: String?): Long {
        if (since.isNullOrBlank() || until.isNullOrBlank()) return 0L
        return try {
            val s = kotlin.time.Instant.parse(since).epochSeconds
            val u = kotlin.time.Instant.parse(until).epochSeconds
            maxOf(0L, (u - s) / 60L)
        } catch (_: Exception) { 0L }
    }

}
