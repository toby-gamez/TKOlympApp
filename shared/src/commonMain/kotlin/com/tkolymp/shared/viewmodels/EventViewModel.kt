package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration.Companion.days
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.utils.asJsonObjectOrNull
import com.tkolymp.shared.utils.asJsonArrayOrNull
import com.tkolymp.shared.utils.str
import com.tkolymp.shared.utils.int
import com.tkolymp.shared.utils.bool
import com.tkolymp.shared.utils.formatTimesWithDateAlways

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
    val myPersonId: String? = null,
    val myCoupleIds: List<String> = emptyList(),
    val isPast: Boolean = false,
    val userRegistered: Boolean = false,
    val registerButtonVisible: Boolean = false,
    val registrationActionsRowVisible: Boolean = false,
    val editRegistrationButtonVisible: Boolean = false,
    val isAddedToCalendar: Boolean = false,
    val calendarResult: Boolean? = null,  // null = no attempt, true = success, false = fail
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class EventViewModel(
    private val eventService: com.tkolymp.shared.event.IEventService = ServiceLocator.eventService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService,
    private val calendarStorage: com.tkolymp.shared.storage.CalendarPreferenceStorage = ServiceLocator.calendarPreferenceStorage,
    private val systemCalendarService: com.tkolymp.shared.systemcalendar.SystemCalendarService = ServiceLocator.systemCalendarService
) : ViewModel() {
    private val _state = MutableStateFlow(EventState())
    val state: StateFlow<EventState> = _state.asStateFlow()

    suspend fun loadEvent(eventId: Long, forceRefresh: Boolean = false) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            // Eagerly check if already added to calendar
            val alreadyAdded = try { calendarStorage.isEventInCalendar(eventId) } catch (_: Exception) { false }
            val ev = try { withContext(Dispatchers.IO) { eventService.fetchEventById(eventId, forceRefresh) } } catch (e: CancellationException) { throw e } catch (ex: Exception) { Logger.d("EventViewModel", "fetchEventById($eventId) failed: ${ex.message}"); null }
            var myPerson: String? = null
            var myCouples: List<String> = emptyList()
            try {
                withContext(Dispatchers.IO) {
                    myPerson = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("EventViewModel", "getCachedPersonId failed: ${e.message}"); null }
                    myCouples = try { userService.getCachedCoupleIds() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("EventViewModel", "getCachedCoupleIds failed: ${e.message}"); emptyList() }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("EventViewModel", "failed to read user cache: ${e.message}") }

            // Derive business-logic fields from loaded JSON
            val instances = ev?.get("eventInstancesList")?.asJsonArrayOrNull() ?: JsonArray(emptyList())
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

            val registrations = ev?.get("eventRegistrationsList")?.asJsonArrayOrNull() ?: JsonArray(emptyList())
            val userRegistered = registrations.any { regEl ->
                val reg = regEl.asJsonObjectOrNull() ?: return@any false
                val personId = reg["person"].asJsonObjectOrNull()?.str("id")
                val coupleId = reg["couple"].asJsonObjectOrNull()?.str("id")
                (personId != null && personId == myPerson) || (coupleId != null && coupleId in myCouples)
            }

            val type = ev?.str("type") ?: ""
            val isLocked = ev?.bool("isLocked") ?: false
            val isRegistrationOpen = if (isLocked) false else (ev?.bool("isRegistrationOpen") ?: true)
            val registerButtonVisible = !isPast && !userRegistered && isRegistrationOpen
            val registrationActionsRowVisible = !isPast && userRegistered && isRegistrationOpen
            val editRegistrationButtonVisible = !type.equals("lesson", ignoreCase = true) && !type.equals("group", ignoreCase = true)

            val trainers = ev?.get("eventTrainersList")?.asJsonArrayOrNull() ?: JsonArray(emptyList())
            val cohorts = ev?.get("eventTargetCohortsList")?.asJsonArrayOrNull() ?: JsonArray(emptyList())
            val externalRegistrations = ev?.get("eventExternalRegistrationsList")?.asJsonArrayOrNull() ?: JsonArray(emptyList())

            val rawName = ev?.str("name")
            val eventName = when {
                !rawName.isNullOrBlank() -> rawName
                type.equals("lesson", ignoreCase = true) ->
                    trainers.firstOrNull()?.asJsonObjectOrNull()?.str("name")?.takeIf { it.isNotBlank() }
                        ?: AppStrings.current.dialogs.noName
                else -> if (ev != null) AppStrings.current.dialogs.noName else ""
            }
            val eventDescription = ev?.str("description") ?: ""
            val summary = ev?.str("summary") ?: ""
            val locationName = ev?.get("location")?.asJsonObjectOrNull()?.str("name") ?: ev?.str("locationText")
            val isVisible = ev?.bool("isVisible") ?: false
            val isPublic = ev?.bool("isPublic") ?: false
            val enableNotes = ev?.bool("enableNotes") ?: false
            val capacity = ev?.int("capacity")
            val remainingPersonSpots = ev?.int("remainingPersonSpots")
            val remainingLessons = ev?.int("remainingLessons")

            val trainerDisplayNames = trainers.mapNotNull { trainerEl ->
                val trainer = trainerEl.asJsonObjectOrNull() ?: return@mapNotNull null
                val trainerName = trainer.str("name") ?: AppStrings.current.dialogs.noName
                val lessonsOffered = trainer.int("lessonsOffered")
                val lessonsRemaining = trainer.int("lessonsRemaining")
                when {
                    lessonsOffered != null && lessonsRemaining != null ->
                        "$trainerName (nabízí: $lessonsOffered, zbývá: $lessonsRemaining)"
                    lessonsOffered != null -> "$trainerName (nabízí: $lessonsOffered)"
                    else -> trainerName
                }
            }.joinToString(", ")

            val cohortDisplayNames = cohorts.mapNotNull { cohortEl ->
                cohortEl.asJsonObjectOrNull()
                    ?.get("cohort")
                    ?.asJsonObjectOrNull()
                    ?.str("name")
            }.joinToString(", ")

            val eventDateText = when {
                firstDate != null && lastDate != null && firstDate != lastDate ->
                    "${AppStrings.current.events.term}: ${formatTimesWithDateAlways(firstDate, null)} - ${formatTimesWithDateAlways(lastDate, null)}"
                firstDate != null -> "${AppStrings.current.events.term}: ${formatTimesWithDateAlways(firstDate, lastDate)}"
                else -> ""
            }

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
                myPersonId = myPerson,
                myCoupleIds = myCouples,
                isPast = isPast,
                userRegistered = userRegistered,
                registerButtonVisible = registerButtonVisible,
                registrationActionsRowVisible = registrationActionsRowVisible,
                editRegistrationButtonVisible = editRegistrationButtonVisible,
                isAddedToCalendar = alreadyAdded,
                isLoading = false
            )
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání události")
        }
    }

    suspend fun addToCalendar(eventId: Long) {
        val s = _state.value
        // Build event data from state
        val title = s.eventName.ifBlank { "Událost" }
        val description = s.summary.ifBlank { s.eventDescription }.ifBlank { null }
        val location = s.locationName

        // Use first instance for time; fall back to 1-hour block starting now
        val firstInstance = s.instances.firstOrNull()?.asJsonObjectOrNull()
        val sinceStr = firstInstance?.str("since")
        val untilStr = firstInstance?.str("until")
        val startMs = sinceStr?.let {
            try { kotlinx.datetime.Instant.parse(it).toEpochMilliseconds() } catch (_: Exception) { null }
        } ?: kotlin.time.Clock.System.now().toEpochMilliseconds()
        val endMs = untilStr?.let {
            try { kotlinx.datetime.Instant.parse(it).toEpochMilliseconds() } catch (_: Exception) { null }
        } ?: (startMs + 3_600_000L)

        // Detect weekly recurrence: instances are weekly if the gap between the
        // first two instances is between 6.5 and 7.5 days.
        val weeklyRepeatCount: Int? = run {
            if (s.instances.size < 2) return@run null
            val first = s.instances.getOrNull(0)?.asJsonObjectOrNull()?.str("since") ?: return@run null
            val second = s.instances.getOrNull(1)?.asJsonObjectOrNull()?.str("since") ?: return@run null
            val t0 = try { kotlinx.datetime.Instant.parse(first).toEpochMilliseconds() } catch (_: Exception) { return@run null }
            val t1 = try { kotlinx.datetime.Instant.parse(second).toEpochMilliseconds() } catch (_: Exception) { return@run null }
            val diffDays = (t1 - t0) / 86_400_000.0
            if (diffDays in 6.5..7.5) s.instances.size else null
        }

        val success = try {
            withContext(Dispatchers.IO) {
                systemCalendarService.addEvent(title, description, location, startMs, endMs, weeklyRepeatCount)
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

        if (success) {
            try { calendarStorage.setEventInCalendar(eventId) } catch (_: Exception) {}
        }
        _state.value = _state.value.copy(
            isAddedToCalendar = if (success) true else _state.value.isAddedToCalendar,
            calendarResult = success
        )
    }

    fun clearCalendarResult() {
        _state.value = _state.value.copy(calendarResult = null)
    }
}
