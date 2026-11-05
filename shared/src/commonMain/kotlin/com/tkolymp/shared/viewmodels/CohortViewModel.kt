package com.tkolymp.shared.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.club.ClubService
import com.tkolymp.shared.club.CohortDetail
import com.tkolymp.shared.language.AppStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Immutable
data class CohortState(
    val cohort: CohortDetail? = null,
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

class CohortViewModel(
    private val clubService: ClubService = ServiceLocator.clubService
) : ViewModel() {
    private val _state = MutableStateFlow(CohortState())
    val state: StateFlow<CohortState> = _state.asStateFlow()

    suspend fun load(cohortId: String) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val detail = withContext(Dispatchers.Default) { clubService.fetchCohort(cohortId) }
            _state.value = if (detail != null) _state.value.copy(cohort = detail, isLoading = false)
            // keep what we already show when a refresh fails
            else _state.value.copy(isLoading = false, error = AppError.generic(AppStrings.current.errorMessages.errorLoadingData))
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoading))
        }
    }
}
