package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tkolymp.shared.json.AppJson
import androidx.compose.runtime.Immutable
import com.tkolymp.shared.language.AppStrings

@Immutable
data class NoticeState(
    val announcement: com.tkolymp.shared.announcements.Announcement? = null,
    val isOffline: Boolean = false,
    override val isLoading: Boolean = false,
    override val error: AppError? = null
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
                data class FetchResult(val announcement: com.tkolymp.shared.announcements.Announcement?, val usedOffline: Boolean)
                val result = withContext(Dispatchers.Default) {
                    var a = when (val r = announcementService.getAnnouncementById(announcementId, forceRefresh)) {
                        is DataResult.Success -> r.data
                        is DataResult.Error -> null
                    }
                    var usedOffline = false
                    if (a == null) {
                        try {
                            val raw = ServiceLocator.offlineSyncManager.loadAnnouncementDetail(announcementId)
                            if (raw != null) {
                                a = AppJson.decodeFromString(com.tkolymp.shared.announcements.Announcement.serializer(), raw)
                                usedOffline = true
                            }
                        } catch (_: Exception) { }
                    }
                    FetchResult(a, usedOffline)
                }
                if (result.announcement == null) {
                    _state.value = _state.value.copy(isLoading = false, error = AppError.generic("Oznámení nenalezeno"))
                } else {
                    _state.value = _state.value.copy(announcement = result.announcement, isLoading = false, isOffline = result.usedOffline)
                }
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoading))
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
