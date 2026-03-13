package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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

            _state.value = _state.value.copy(eventJson = ev, myPersonId = myPerson, myCoupleIds = myCouples, isLoading = false)
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání události")
        }
    }
}
