package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration.Companion.days
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.asJsonObjectOrNull
import com.tkolymp.shared.utils.asJsonArrayOrNull
import com.tkolymp.shared.utils.str
import com.tkolymp.shared.utils.int
import com.tkolymp.shared.utils.bool
import com.tkolymp.shared.utils.AppConstants
import com.tkolymp.shared.utils.formatTimesWithDateAlways
import com.tkolymp.shared.utils.describeSchedule
import kotlinx.serialization.json.jsonObject
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.event.EventType
import androidx.compose.runtime.Immutable
import com.tkolymp.shared.event.toEventType

@Immutable
data class EventState(
    val eventJson: JsonObject? = null,
    // Parsed display fields
    val eventName: String = "",
    val eventType: String = "",
    val eventDescription: String = "",
    val summary: String = "",
    val locationName: String? = null,
    val isVisible: Boolean = false,
    val isPublic: Boolean = false,
    val isLocked: Boolean = false,
    val enableNotes: Boolean = false,
    val capacity: Int? = null,
    val remainingPersonSpots: Int? = null,
    val remainingLessons: Int? = null,
    val instances: JsonArray = JsonArray(emptyList()),
    val trainers: JsonArray = JsonArray(emptyList()),
    val cohorts: JsonArray = JsonArray(emptyList()),
    val registrations: JsonArray = JsonArray(emptyList()),
    val externalRegistrations: JsonArray = JsonArray(emptyList()),
    val trainerDisplayNames: String = "",
    val cohortDisplayNames: String = "",
    val eventDateText: String = "",
    val scheduleText: String? = null,
    val myPersonId: String? = null,
    val myCoupleIds: List<String> = emptyList(),
    val isCancelled: Boolean = false,
    val isPast: Boolean = false,
    val userRegistered: Boolean = false,
    val registerButtonVisible: Boolean = false,
    val registrationActionsRowVisible: Boolean = false,
    val editRegistrationButtonVisible: Boolean = false,
    val isAddedToCalendar: Boolean = false,
    val firstInstanceIso: String? = null,
    val reminderMinutesBefore: Int? = null,
    val reminderId: String? = null,
    val isOffline: Boolean = false,
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

sealed class EventSideEffect {
    data class CalendarResult(val success: Boolean) : EventSideEffect()
    data class ReminderResult(val set: Boolean) : EventSideEffect()
}

class EventViewModel(
    private val eventService: com.tkolymp.shared.event.IEventService = ServiceLocator.eventService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService,
    private val calendarStorage: com.tkolymp.shared.storage.ICalendarPreferenceStorage = ServiceLocator.calendarPreferenceStorage,
    private val systemCalendarService: com.tkolymp.shared.systemcalendar.ISystemCalendarService = ServiceLocator.systemCalendarService,
    private val notificationService: com.tkolymp.shared.notification.NotificationService = ServiceLocator.notificationService
) : ViewModel() {
    private val _state = MutableStateFlow(EventState())
    val state: StateFlow<EventState> = _state.asStateFlow()

    private val _sideEffect = Channel<EventSideEffect>(Channel.BUFFERED)
    val sideEffect: Flow<EventSideEffect> = _sideEffect.receiveAsFlow()

    suspend fun loadEvent(eventId: Long, instanceId: Long? = null, forceRefresh: Boolean = false) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            // Eagerly check if already added to calendar
            val alreadyAdded = try { calendarStorage.isEventInCalendar(eventId) } catch (_: Exception) { false }
            // When offline, don't bypass the CacheService — use whatever was cached recently.
            // forceRefresh=true only makes sense when the network is actually available.
            val isCurrentlyOnline = try { ServiceLocator.networkMonitor.isConnected() } catch (_: Exception) { true }
            Logger.d("EventViewModel", "loadEvent($eventId): online=$isCurrentlyOnline forceRefresh=$forceRefresh")
            val effectiveForceRefresh = forceRefresh && isCurrentlyOnline
            var ev = try { withContext(Dispatchers.Default) { eventService.fetchEventById(eventId, effectiveForceRefresh) } } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                Logger.d("EventViewModel", "fetchEventById($eventId) exception: ${ex.message}"); null }
            Logger.d("EventViewModel", "fetchEventById($eventId) result: ${if (ev == null) "null" else "JsonObject(keys=${ev.keys})"} ")
            var isOfflineUsed = false
            if (ev == null) {
                try {
                    val raw = ServiceLocator.offlineSyncManager.loadEventDetail(eventId)
                    Logger.d("EventViewModel", "offlineSyncManager.loadEventDetail($eventId): ${if (raw == null) "null" else "found (${raw.length} chars)"}")
                    if (raw != null) {
                        ev = AppJson.parseToJsonElement(raw).jsonObject
                        isOfflineUsed = true
                    }
                } catch (ex: Exception) { Logger.d("EventViewModel", "offlineSyncManager.loadEventDetail($eventId) exception: ${ex.message}") }
            }
            // After all fallbacks, if ev is still null there's nothing to show — set an error
            // so the UI can display a retry button instead of a permanent blank state.
            if (ev == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isOffline = !isCurrentlyOnline,
                    error = AppError.notFound(AppStrings.current.events.noEventToShow)
                )
                return
            }
            val eventObj = ev
            var myPerson: String? = null
            var myCouples: List<String> = emptyList()
            try {
                withContext(Dispatchers.Default) {
                    myPerson = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("EventViewModel", "getCachedPersonId failed: ${e.message}"); null }
                    myCouples = try { userService.getCachedCoupleIds() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("EventViewModel", "getCachedCoupleIds failed: ${e.message}"); emptyList() }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("EventViewModel", "failed to read user cache: ${e.message}") }

            // Derive business-logic fields from loaded JSON
            val instances = eventObj.get("eventInstancesList")?.asJsonArrayOrNull() ?: JsonArray(emptyList())
            val firstDate = instances.firstOrNull()?.asJsonObjectOrNull()?.str("since")
            val lastDate = instances.lastOrNull()?.asJsonObjectOrNull()?.str("until")
            val isPast = try {
                val now = kotlin.time.Clock.System.now()
                when {
                    lastDate != null -> Instant.parse(lastDate) < now
                    firstDate != null -> (Instant.parse(firstDate) + 1.days) < now
                    else -> false
                }
            } catch (_: Exception) { false }

            val registrations = eventObj.get("eventRegistrationsList")?.asJsonArrayOrNull() ?: JsonArray(emptyList())
            val userRegistered = registrations.any { regEl ->
                val reg = regEl.asJsonObjectOrNull() ?: return@any false
                val personId = reg["person"].asJsonObjectOrNull()?.str("id")
                val coupleId = reg["couple"].asJsonObjectOrNull()?.str("id")
                (personId != null && personId == myPerson) || (coupleId != null && coupleId in myCouples)
            }

            val type = eventObj.str("type") ?: ""
            val isLocked = eventObj.bool("isLocked") ?: false
            val isRegistrationOpen = if (isLocked) false else (eventObj.bool("isRegistrationOpen") ?: true)
            val registerButtonVisible = !isPast && !userRegistered && isRegistrationOpen
            val registrationActionsRowVisible = !isPast && userRegistered && isRegistrationOpen
            val editRegistrationButtonVisible = type.toEventType() != EventType.LESSON && type.toEventType() != EventType.GROUP

            val trainers = eventObj.get("eventTrainersList")?.asJsonArrayOrNull() ?: JsonArray(emptyList())
            val cohorts = eventObj.get("eventTargetCohortsList")?.asJsonArrayOrNull() ?: JsonArray(emptyList())
            val externalRegistrations = eventObj.get("eventExternalRegistrationsList")?.asJsonArrayOrNull() ?: JsonArray(emptyList())

            val rawName = eventObj.str("name")
            val eventName = when {
                !rawName.isNullOrBlank() -> rawName
                type.toEventType() == EventType.LESSON ->
                    trainers.firstOrNull()?.asJsonObjectOrNull()?.str("name")?.takeIf { it.isNotBlank() }
                        ?: AppStrings.current.dialogs.noName
                else -> AppStrings.current.dialogs.noName
            }
            val eventDescription = eventObj.str("description") ?: ""
            val summary = eventObj.str("summary") ?: ""
            val locationName = eventObj.get("location")?.asJsonObjectOrNull()?.str("name") ?: eventObj.str("locationText")
            val isVisible = eventObj.bool("isVisible") ?: false
            val isPublic = eventObj.bool("isPublic") ?: false
            val enableNotes = eventObj.bool("enableNotes") ?: false
            val capacity = eventObj.int("capacity")
            val remainingPersonSpots = eventObj.int("remainingPersonSpots")
            val remainingLessons = eventObj.int("remainingLessons")

            val trainerDisplayNames = trainers.mapNotNull { trainerEl ->
                val trainer = trainerEl.asJsonObjectOrNull() ?: return@mapNotNull null
                val trainerName = trainer.str("name") ?: AppStrings.current.dialogs.noName
                val lessonsOffered = trainer.int("lessonsOffered")?.takeIf { it > 0 }
                val lessonsRemaining = trainer.int("lessonsRemaining")?.takeIf { it > 0 }
                when {
                    lessonsOffered != null && lessonsRemaining != null ->
                        "$trainerName (${AppStrings.current.events.trainerOffersLabel}: $lessonsOffered, ${AppStrings.current.events.trainerRemainingLabel}: $lessonsRemaining)"
                    else -> trainerName
                }
            }.joinToString(", ")

            val cohortDisplayNames = cohorts.mapNotNull { cohortEl ->
                cohortEl.asJsonObjectOrNull()
                    ?.get("cohort")
                    ?.asJsonObjectOrNull()
                    ?.str("name")
            }.joinToString(", ")

            val isCancelled = if (instanceId != null) {
                instances.any { inst ->
                    val obj = inst.asJsonObjectOrNull() ?: return@any false
                    obj["id"]?.jsonPrimitive?.longOrNull == instanceId && obj.bool("isCancelled") == true
                }
            } else {
                instances.isNotEmpty() && instances.all { it.asJsonObjectOrNull()?.bool("isCancelled") == true }
            }

            val selectedInstance = if (instanceId != null) {
                instances.firstOrNull { inst ->
                    inst.asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.longOrNull == instanceId
                }?.asJsonObjectOrNull()
            } else null

            val eventDateText = when {
                selectedInstance != null -> {
                    val since = selectedInstance.str("since")
                    val until = selectedInstance.str("until")
                    formatTimesWithDateAlways(since, until)
                }
                firstDate != null && lastDate != null && firstDate != lastDate ->
                    "${formatTimesWithDateAlways(firstDate, null)} - ${formatTimesWithDateAlways(lastDate, null)}"
                firstDate != null -> formatTimesWithDateAlways(firstDate, lastDate)
                else -> ""
            }
            val scheduleText = if (selectedInstance != null) null
                               else describeSchedule(instances, AppStrings.currentLanguage.code)

            _state.value = _state.value.copy(
                eventJson = ev,
                eventName = eventName,
                eventType = type,
                eventDescription = eventDescription,
                summary = summary,
                locationName = locationName,
                isVisible = isVisible,
                isPublic = isPublic,
                isLocked = isLocked,
                enableNotes = enableNotes,
                capacity = capacity,
                remainingPersonSpots = remainingPersonSpots,
                remainingLessons = remainingLessons,
                instances = instances,
                trainers = trainers,
                cohorts = cohorts,
                registrations = registrations,
                externalRegistrations = externalRegistrations,
                trainerDisplayNames = trainerDisplayNames,
                cohortDisplayNames = cohortDisplayNames,
                eventDateText = eventDateText,
                scheduleText = scheduleText,
                myPersonId = myPerson,
                myCoupleIds = myCouples,
                isCancelled = isCancelled,
                isPast = isPast,
                userRegistered = userRegistered,
                registerButtonVisible = registerButtonVisible,
                registrationActionsRowVisible = registrationActionsRowVisible,
                editRegistrationButtonVisible = editRegistrationButtonVisible,
                isAddedToCalendar = alreadyAdded,
                firstInstanceIso = firstDate,
                reminderMinutesBefore = try { notificationService.getReminderForEvent(eventId)?.minutesBefore } catch (_: Exception) { null },
                reminderId = try { notificationService.getReminderForEvent(eventId)?.id } catch (_: Exception) { null },
                isLoading = false,
                isOffline = isOfflineUsed
            )
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoadingEvent))
        }
    }

    suspend fun addToCalendar(eventId: Long) {
        val s = _state.value
        // Build event data from state
        val title = s.eventName.ifBlank { AppStrings.current.events.event }
        val description = s.summary.ifBlank { s.eventDescription }.ifBlank { null }
        val location = s.locationName

        // Use first instance for time; fall back to 1-hour block starting now
        val firstInstance = s.instances.firstOrNull()?.asJsonObjectOrNull()
        val sinceStr = firstInstance?.str("since")
        val untilStr = firstInstance?.str("until")
        val startMs = sinceStr?.let {
            try { Instant.parse(it).toEpochMilliseconds() } catch (_: Exception) { null }
        } ?: kotlin.time.Clock.System.now().toEpochMilliseconds()
        val endMs = untilStr?.let {
            try { Instant.parse(it).toEpochMilliseconds() } catch (_: Exception) { null }
        } ?: (startMs + AppConstants.DEFAULT_EVENT_DURATION_MS)

        // Detect weekly recurrence: instances are weekly if the gap between the
        // first two instances is between 6.5 and 7.5 days.
        val weeklyRepeatCount: Int? = run {
            if (s.instances.size < 2) return@run null
            val first = s.instances.getOrNull(0)?.asJsonObjectOrNull()?.str("since") ?: return@run null
            val second = s.instances.getOrNull(1)?.asJsonObjectOrNull()?.str("since") ?: return@run null
            val t0 = try { Instant.parse(first).toEpochMilliseconds() } catch (_: Exception) { return@run null }
            val t1 = try { Instant.parse(second).toEpochMilliseconds() } catch (_: Exception) { return@run null }
            val diffDays = (t1 - t0) / 86_400_000.0
            if (diffDays in AppConstants.WEEKLY_RECURRENCE_MIN_DAYS..AppConstants.WEEKLY_RECURRENCE_MAX_DAYS) s.instances.size else null
        }

        val success = try {
            withContext(Dispatchers.Default) {
                systemCalendarService.addEvent(title, description, location, startMs, endMs, weeklyRepeatCount)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

        if (success) {
            try { calendarStorage.setEventInCalendar(eventId) } catch (_: Exception) {}
        }
        _state.value = _state.value.copy(isAddedToCalendar = if (success) true else _state.value.isAddedToCalendar)
        if (_sideEffect.trySend(EventSideEffect.CalendarResult(success)).isFailure) {
            Logger.w("EventViewModel", "calendar result side-effect channel full, event dropped")
        }
    }

    suspend fun setReminder(eventId: Long, minutesBefore: Int) {
        val s = _state.value
        val startIso = s.firstInstanceIso ?: return
        try {
            val reminder = com.tkolymp.shared.notification.EventReminder(
                id = "reminder_evt_$eventId",
                eventId = eventId,
                eventName = s.eventName,
                eventStartIso = startIso,
                minutesBefore = minutesBefore
            )
            val saved = notificationService.addOrUpdateReminder(reminder)
            _state.value = _state.value.copy(reminderMinutesBefore = saved.minutesBefore, reminderId = saved.id)
            if (_sideEffect.trySend(EventSideEffect.ReminderResult(set = true)).isFailure) {
                Logger.w("EventViewModel", "reminder result side-effect channel full, event dropped")
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            if (_sideEffect.trySend(EventSideEffect.ReminderResult(set = false)).isFailure) {
                Logger.w("EventViewModel", "reminder result side-effect channel full, event dropped")
            }
        }
    }

    suspend fun removeReminder(eventId: Long) {
        val id = _state.value.reminderId ?: "reminder_evt_$eventId"
        try {
            notificationService.deleteReminder(id)
            _state.value = _state.value.copy(reminderMinutesBefore = null, reminderId = null)
            if (_sideEffect.trySend(EventSideEffect.ReminderResult(set = false)).isFailure) {
                Logger.w("EventViewModel", "reminder result side-effect channel full, event dropped")
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }
}
