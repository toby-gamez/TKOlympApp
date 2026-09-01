package com.tkolymp.shared.changelog

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.viewmodels.AppError
import com.tkolymp.shared.viewmodels.ViewModelState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ChangelogState(
    val releases: List<ChangelogRelease> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: AppError? = null,
) : ViewModelState

class ChangelogViewModel(
    private val changelogService: IChangelogService = ServiceLocator.changelogService,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangelogState())
    val state: StateFlow<ChangelogState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val releases = withContext(Dispatchers.IO) { changelogService.fetchReleases() }
                _state.value = _state.value.copy(releases = releases, isLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = AppError.generic(e.message ?: "Failed to load changelog")
                )
            }
        }
    }
}
