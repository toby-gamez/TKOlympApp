package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

data class EventState(
    val eventJson: JsonObject? = null,
    val myPersonId: String? = null,
    val myCoupleIds: List<String> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class EventViewModel(
    private val eventService: com.tkolymp.shared.event.IEventService = ServiceLocator.eventService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService
) {
    private val _state = MutableStateFlow(EventState())
    val state: StateFlow<EventState> = _state.asStateFlow()

    suspend fun loadEvent(eventId: Long) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            try { withContext(Dispatchers.IO) { ServiceLocator.authService.initialize() } } catch (_: Throwable) {}

            val ev = try { withContext(Dispatchers.IO) { eventService.fetchEventById(eventId) } } catch (ex: Throwable) { null }
            var myPerson: String? = null
            var myCouples: List<String> = emptyList()
            try {
                withContext(Dispatchers.IO) {
                    myPerson = try { userService.getCachedPersonId() } catch (_: Throwable) { null }
                    myCouples = try { userService.getCachedCoupleIds() } catch (_: Throwable) { emptyList() }
                }
            } catch (_: Throwable) {}

            _state.value = _state.value.copy(eventJson = ev, myPersonId = myPerson, myCoupleIds = myCouples, isLoading = false)
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání události")
        }
    }
}
