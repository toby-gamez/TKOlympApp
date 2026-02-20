package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.club.ClubData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrainersLocationsState(
    val clubData: ClubData? = null,
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class TrainersLocationsViewModel(
    private val clubService: com.tkolymp.shared.club.ClubService = ServiceLocator.clubService
) {
    private val _state = MutableStateFlow(TrainersLocationsState())
    val state: StateFlow<TrainersLocationsState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val d = try {
                withContext(Dispatchers.Default) { clubService.fetchClubData() }
            } catch (ex: Throwable) {
                _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání klubu")
                return
            }
            _state.value = _state.value.copy(clubData = d, isLoading = false)
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání")
        }
    }
}
