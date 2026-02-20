package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LeaderboardState(
    val rankings: List<Any> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class LeaderboardViewModel(
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService
) {
    private val _state = MutableStateFlow(LeaderboardState())
    val state: StateFlow<LeaderboardState> = _state.asStateFlow()

    suspend fun loadLeaderboard() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val until = "2100-01-01"
            val list = try { peopleService.fetchScoreboard(null, "2025-09-01", until) } catch (_: Throwable) { emptyList<com.tkolymp.shared.people.ScoreboardEntry>() }
            _state.value = _state.value.copy(rankings = list as? List<Any> ?: emptyList(), isLoading = false)
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání žebříčku")
        }
    }
}
