package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.club.Cohort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class GroupsState(
    val cohorts: List<Cohort> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class GroupsViewModel(
    private val clubService: com.tkolymp.shared.club.ClubService = ServiceLocator.clubService
) {
    private val _state = MutableStateFlow(GroupsState())
    val state: StateFlow<GroupsState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val d = try { withContext(Dispatchers.Default) { clubService.fetchClubData() } } catch (ex: Throwable) { null }
            if (d == null) {
                _state.value = _state.value.copy(isLoading = false, error = "Chyba při načítání dat")
            } else {
                _state.value = _state.value.copy(cohorts = d.cohorts, isLoading = false)
            }
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání")
        }
    }
}
