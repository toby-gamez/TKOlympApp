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
import com.tkolymp.shared.utils.asJsonObjectOrNull
import com.tkolymp.shared.utils.asJsonArrayOrNull
import com.tkolymp.shared.utils.str
import com.tkolymp.shared.utils.bool

data class EventState(
    val eventJson: JsonObject? = null,
    val myPersonId: String? = null,
    val myCoupleIds: List<String> = emptyList(),
    val isPast: Boolean = false,
    val userRegistered: Boolean = false,
    val registerButtonVisible: Boolean = false,
    val registrationActionsRowVisible: Boolean = false,
    val editRegistrationButtonVisible: Boolean = false,
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class EventViewModel(
    private val eventService: com.tkolymp.shared.event.IEventService = ServiceLocator.eventService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService
) : ViewModel() {
    private val _state = MutableStateFlow(EventState())
    val state: StateFlow<EventState> = _state.asStateFlow()

    suspend fun loadEvent(eventId: Long, forceRefresh: Boolean = false) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
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

            _state.value = _state.value.copy(
                eventJson = ev,
                myPersonId = myPerson,
                myCoupleIds = myCouples,
                isPast = isPast,
                userRegistered = userRegistered,
                registerButtonVisible = registerButtonVisible,
                registrationActionsRowVisible = registrationActionsRowVisible,
                editRegistrationButtonVisible = editRegistrationButtonVisible,
                isLoading = false
            )
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání události")
        }
    }
}
