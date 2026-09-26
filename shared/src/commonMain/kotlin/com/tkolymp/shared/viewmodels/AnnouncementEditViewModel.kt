package com.tkolymp.shared.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.management.AnnouncementDraft
import com.tkolymp.shared.management.IManagementService
import com.tkolymp.shared.management.ManagementOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class AnnouncementEditState(
    val announcementId: Long? = null,
    /** [AnnouncementDraft.body] holds the stored HTML used to seed the rich-text editor. */
    val draft: AnnouncementDraft = AnnouncementDraft(),
    val options: ManagementOptions = ManagementOptions(),
    val isSaving: Boolean = false,
    val validationError: String? = null,
    /** True once the form data (and the stored HTML for the editor) has been loaded. */
    val isLoaded: Boolean = false,
    /** Set once a save succeeded; the screen navigates away. */
    val savedAnnouncementId: Long? = null,
    override val isLoading: Boolean = false,
    override val error: AppError? = null,
) : ViewModelState {
    val isEditing: Boolean get() = announcementId != null
}

class AnnouncementEditViewModel(
    private val service: IManagementService = ServiceLocator.managementService,
) : ViewModel() {
    private val _state = MutableStateFlow(AnnouncementEditState())
    val state: StateFlow<AnnouncementEditState> = _state.asStateFlow()

    private var loadedFor: Long? = null
    private var loaded = false

    /** Loads the form; [announcementId] null means creating a new announcement. */
    fun load(announcementId: Long?, sticky: Boolean = false) {
        if (loaded && loadedFor == announcementId) return
        loaded = true
        loadedFor = announcementId
        _state.value = AnnouncementEditState(announcementId = announcementId, isLoading = true)
        viewModelScope.launch {
            try {
                val options = try { service.getOptions() } catch (e: CancellationException) { throw e } catch (_: Exception) { ManagementOptions() }
                val draft = if (announcementId != null) {
                    service.loadAnnouncementDraft(announcementId)
                        ?: throw IllegalStateException(AppStrings.current.announcements.noAnnouncementToShow)
                } else {
                    AnnouncementDraft(isSticky = sticky)
                }
                _state.value = _state.value.copy(
                    draft = draft,
                    options = options,
                    isLoaded = true,
                    isLoading = false,
                )
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoading, ex),
                )
            }
        }
    }

    fun updateDraft(transform: (AnnouncementDraft) -> AnnouncementDraft) {
        _state.value = _state.value.copy(draft = transform(_state.value.draft), validationError = null)
    }

    fun toggleCohort(cohortId: String) = updateDraft { d ->
        d.copy(cohortIds = if (cohortId in d.cohortIds) d.cohortIds - cohortId else d.cohortIds + cohortId)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** [bodyHtml] is the current content of the rich-text body editor. */
    fun save(bodyHtml: String) {
        val s = _state.value
        if (s.isSaving || s.isLoading || !s.isLoaded) return
        if (s.draft.title.isBlank()) {
            _state.value = s.copy(validationError = AppStrings.current.management.titleRequired)
            return
        }
        val draft = s.draft.copy(body = bodyHtml)
        _state.value = s.copy(isSaving = true, validationError = null, error = null)
        viewModelScope.launch {
            try {
                val id = s.announcementId?.also { service.updateAnnouncement(it, draft) } ?: service.createAnnouncement(draft)
                _state.value = _state.value.copy(isSaving = false, savedAnnouncementId = id)
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorUpdating, ex),
                )
            }
        }
    }
}
