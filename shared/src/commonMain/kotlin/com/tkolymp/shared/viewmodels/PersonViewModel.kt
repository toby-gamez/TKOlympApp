package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PersonState(
    val personId: String? = null,
    val person: Any? = null,
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class PersonViewModel(
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService
) {
    private val _state = MutableStateFlow(PersonState())
    val state: StateFlow<PersonState> = _state.asStateFlow()

    suspend fun loadPerson(personId: String) {
        _state.value = _state.value.copy(isLoading = true, error = null, personId = personId)
        try {
            val p = try { peopleService.fetchPerson(personId) } catch (_: Throwable) { null }
            _state.value = _state.value.copy(person = p, isLoading = false)
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání osoby")
        }
    }
}
