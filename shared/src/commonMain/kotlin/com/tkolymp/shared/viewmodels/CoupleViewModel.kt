package com.tkolymp.shared.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.people.CoupleDetail
import com.tkolymp.shared.people.PeopleService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Immutable
data class CoupleState(
    val couple: CoupleDetail? = null,
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

class CoupleViewModel(
    private val peopleService: PeopleService = ServiceLocator.peopleService
) : ViewModel() {
    private val _state = MutableStateFlow(CoupleState())
    val state: StateFlow<CoupleState> = _state.asStateFlow()

    suspend fun load(coupleId: String) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val detail = withContext(Dispatchers.Default) {
                peopleService.fetchCouple(coupleId) ?: try { ServiceLocator.offlineSyncManager.loadCouple(coupleId) } catch (_: Exception) { null }
            }
            _state.value = if (detail != null) _state.value.copy(couple = detail, isLoading = false)
            // keep what we already show when a refresh fails
            else _state.value.copy(isLoading = false, error = AppError.generic(AppStrings.current.errorMessages.errorLoadingData))
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoading, ex))
        }
    }
}
