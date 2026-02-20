package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PeopleState(
    val people: List<Any> = emptyList(),
    val filteredPeople: List<Any> = emptyList(),
    val searchQuery: String = "",
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class PeopleViewModel(
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService
) {
    private val _state = MutableStateFlow(PeopleState())
    val state: StateFlow<PeopleState> = _state.asStateFlow()

    suspend fun loadPeople() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val list = try { peopleService.fetchPeople() } catch (_: Throwable) { emptyList<com.tkolymp.shared.people.Person>() }
            _state.value = _state.value.copy(people = list as? List<Any> ?: emptyList(), filteredPeople = list as? List<Any> ?: emptyList(), isLoading = false)
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání lidí")
        }
    }

    fun updateSearch(query: String) {
        val q = query.trim()
        val filtered = if (q.isBlank()) _state.value.people else _state.value.people.filter { it.toString().trim().contains(q, true) }
        _state.value = _state.value.copy(searchQuery = q, filteredPeople = filtered)
    }
}
