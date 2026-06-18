package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.competitions.Competition
import com.tkolymp.shared.competitions.ICompetitionService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import androidx.compose.runtime.Immutable
import kotlin.time.Clock

@Immutable
data class CompetitionState(
    val upcomingCompetitions: List<Competition> = emptyList(),
    val pastCompetitions: List<Competition> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

class CompetitionViewModel(
    private val competitionService: ICompetitionService = ServiceLocator.competitionService
) : ViewModel() {
    private val _state = MutableStateFlow(CompetitionState())
    val state: StateFlow<CompetitionState> = _state.asStateFlow()

    suspend fun load(forceRefresh: Boolean = false) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        if (forceRefresh) {
            try {
                ServiceLocator.cacheService.invalidatePrefix("competitions_")
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        try {
            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.todayIn(tz)
            val upcoming = competitionService.getUpcomingCompetitions(
                pSince = today.toString(),
                pUntil = today.plus(365, DateTimeUnit.DAY).toString(),
                first = 500
            )
            _state.value = _state.value.copy(
                upcomingCompetitions = upcoming,
                isLoading = false
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = AppError.generic("Failed to load competitions: ${e.message}")
            )
        }
    }

    suspend fun loadPast(forceRefresh: Boolean = false) {
        if (_state.value.pastCompetitions.isNotEmpty() && !forceRefresh) return
        _state.value = _state.value.copy(isLoading = true, error = null)
        if (forceRefresh) {
            try {
                ServiceLocator.cacheService.invalidatePrefix("competitions_past")
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        try {
            val tz = TimeZone.currentSystemDefault()
            val today = Clock.System.todayIn(tz)
            val past = competitionService.getPastCompetitions(
                pSince = today.minus(365, DateTimeUnit.DAY).toString(),
                pUntil = today.toString(),
                first = 5000
            )
            _state.value = _state.value.copy(pastCompetitions = past, isLoading = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = AppError.generic("Failed to load past competitions: ${e.message}")
            )
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
