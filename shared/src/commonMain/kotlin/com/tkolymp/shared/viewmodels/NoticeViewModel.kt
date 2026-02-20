package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NoticeState(
    val announcement: com.tkolymp.shared.announcements.Announcement? = null,
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class NoticeViewModel(
    private val announcementService: com.tkolymp.shared.announcements.IAnnouncementService = ServiceLocator.announcementService
) {
    private val _state = MutableStateFlow(NoticeState())
    val state: StateFlow<NoticeState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun load(announcementId: Long) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        scope.launch {
            try {
                val a = try { announcementService.getAnnouncementById(announcementId) } catch (_: Throwable) { null }
                if (a == null) {
                    _state.value = _state.value.copy(isLoading = false, error = "Oznámení nenalezeno")
                } else {
                    _state.value = _state.value.copy(announcement = a, isLoading = false)
                }
            } catch (ex: Throwable) {
                _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání")
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
