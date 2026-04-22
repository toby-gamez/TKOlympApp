package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.personalevents.PersonalEvent
import com.tkolymp.shared.personalevents.PersonalEventService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PersonalEventsState(
    val events: List<PersonalEvent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PersonalEventsViewModel(
    private val service: PersonalEventService = ServiceLocator.personalEventService
) : ViewModel() {
    private val _state = MutableStateFlow(PersonalEventsState())
    val state: StateFlow<PersonalEventsState> = _state.asStateFlow()

    suspend fun loadAll() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val list = service.getAll()
            _state.value = _state.value.copy(events = list, isLoading = false)
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = e.message ?: "")
        }
    }

    suspend fun delete(id: String) {
        _state.value = _state.value.copy(isLoading = true)
        try {
            service.delete(id)
            loadAll()
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = e.message)
        }
    }
}
