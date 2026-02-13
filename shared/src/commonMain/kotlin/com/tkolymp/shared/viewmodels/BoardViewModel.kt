package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BoardState(
    val selectedTab: Int = 0,
    val currentAnnouncements: List<Any> = emptyList(),
    val permanentAnnouncements: List<Any> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class BoardViewModel(
    private val announcementService: com.tkolymp.shared.announcements.IAnnouncementService = ServiceLocator.announcementService
) {
    private val _state = MutableStateFlow(BoardState())
    val state: StateFlow<BoardState> = _state.asStateFlow()

    suspend fun loadAnnouncements() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val sticky = _state.value.selectedTab == 1
            val list = try { announcementService.getAnnouncements(sticky) } catch (_: Throwable) { emptyList<com.tkolymp.shared.announcements.Announcement>() }
            _state.value = _state.value.copy(currentAnnouncements = list as? List<Any> ?: emptyList(), isLoading = false)
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání")
        }
    }

    fun selectTab(index: Int) {
        _state.value = _state.value.copy(selectedTab = index)
    }
}
