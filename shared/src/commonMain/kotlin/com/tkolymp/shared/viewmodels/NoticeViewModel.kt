package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.CancellationException
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
) : ViewModel() {
    private val _state = MutableStateFlow(NoticeState())
    val state: StateFlow<NoticeState> = _state.asStateFlow()

    fun load(announcementId: Long, forceRefresh: Boolean = false) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val a = try { announcementService.getAnnouncementById(announcementId, forceRefresh) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                if (a == null) {
                    _state.value = _state.value.copy(isLoading = false, error = "Oznámení nenalezeno")
                } else {
                    _state.value = _state.value.copy(announcement = a, isLoading = false)
                }
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání")
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
