package com.tkolymp.shared.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.management.EventDraft
import com.tkolymp.shared.management.IManagementService
import com.tkolymp.shared.management.ManagementOptions
import com.tkolymp.shared.management.RichTextBody
import com.tkolymp.shared.management.UserPermissions
import com.tkolymp.shared.utils.parseToLocal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Immutable
data class EventEditState(
    val instanceId: Long? = null,
    val draft: EventDraft = EventDraft(),
    val descriptionText: String = "",
    val descriptionIsRawHtml: Boolean = false,
    val capacityText: String = "",
    val options: ManagementOptions = ManagementOptions(),
    val permissions: UserPermissions = UserPermissions.NONE,
    val isSaving: Boolean = false,
    val validationError: String? = null,
    /** Set once a save succeeded; the screen navigates away. */
    val savedInstanceId: Long? = null,
    override val isLoading: Boolean = false,
    override val error: AppError? = null,
) : ViewModelState {
    val isEditing: Boolean get() = instanceId != null
    val start: LocalDateTime? get() = parseToLocal(draft.sinceIso)
    val end: LocalDateTime? get() = parseToLocal(draft.untilIso)
}

class EventEditViewModel(
    private val service: IManagementService = ServiceLocator.managementService,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val _state = MutableStateFlow(EventEditState())
    val state: StateFlow<EventEditState> = _state.asStateFlow()

    private var loadedFor: Long? = null
    private var loaded = false

    /** Loads the form; [instanceId] null means creating a new event. Repeated calls are no-ops. */
    fun load(instanceId: Long?) {
        if (loaded && loadedFor == instanceId) return
        loaded = true
        loadedFor = instanceId
        _state.value = EventEditState(instanceId = instanceId, isLoading = true)
        viewModelScope.launch {
            try {
                val permissions = service.getPermissions()
                val options = try { service.getOptions() } catch (e: CancellationException) { throw e } catch (_: Exception) { ManagementOptions() }
                val draft = if (instanceId != null) {
                    service.loadEventDraft(instanceId)
                        ?: throw IllegalStateException(AppStrings.current.errorMessages.errorLoadingEvent)
                } else {
                    newDraft(permissions, options)
                }
                val description = RichTextBody.forEditing(draft.description)
                _state.value = _state.value.copy(
                    draft = draft,
                    descriptionText = description.text,
                    descriptionIsRawHtml = description.isRawHtml,
                    capacityText = draft.capacity?.toString().orEmpty(),
                    options = options,
                    permissions = permissions,
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

    fun updateDraft(transform: (EventDraft) -> EventDraft) {
        _state.value = _state.value.copy(draft = transform(_state.value.draft), validationError = null)
    }

    fun setDescription(text: String) {
        _state.value = _state.value.copy(descriptionText = text)
    }

    fun setCapacityText(text: String) {
        _state.value = _state.value.copy(capacityText = text.filter { it.isDigit() }.take(5), validationError = null)
    }

    fun toggleTrainer(personId: String) = updateDraft { d ->
        d.copy(trainerPersonIds = if (personId in d.trainerPersonIds) d.trainerPersonIds - personId else d.trainerPersonIds + personId)
    }

    fun toggleCohort(cohortId: String) = updateDraft { d ->
        d.copy(cohortIds = if (cohortId in d.cohortIds) d.cohortIds - cohortId else d.cohortIds + cohortId)
    }

    /** Moves the start to [date], keeping its time and the event's duration. */
    fun setStartDate(date: LocalDate) {
        val s = _state.value
        val start = s.start ?: return
        setStart(LocalDateTime(date, start.time))
    }

    fun setStartTime(hour: Int, minute: Int) {
        val start = _state.value.start ?: return
        setStart(LocalDateTime(start.date, LocalTime(hour, minute)))
    }

    fun setEndDate(date: LocalDate) {
        val end = _state.value.end ?: return
        setEnd(LocalDateTime(date, end.time))
    }

    fun setEndTime(hour: Int, minute: Int) {
        val end = _state.value.end ?: return
        setEnd(LocalDateTime(end.date, LocalTime(hour, minute)))
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun save() {
        val s = _state.value
        if (s.isSaving || s.isLoading) return
        val strings = AppStrings.current.management
        val capacity = s.capacityText.takeIf { it.isNotBlank() }?.toIntOrNull()
        val validation = when {
            s.draft.name.isBlank() -> strings.nameRequired
            !isEndAfterStart(s.draft.sinceIso, s.draft.untilIso) -> strings.endBeforeStart
            s.capacityText.isNotBlank() && (capacity == null || capacity <= 0) -> strings.invalidCapacity
            else -> null
        }
        if (validation != null) {
            _state.value = s.copy(validationError = validation)
            return
        }
        val draft = s.draft.copy(
            capacity = capacity,
            description = RichTextBody.forSaving(s.descriptionText, s.descriptionIsRawHtml),
        )
        _state.value = s.copy(isSaving = true, validationError = null, error = null)
        viewModelScope.launch {
            try {
                val id = s.instanceId?.also { service.updateEvent(it, draft) } ?: service.createEvent(draft)
                _state.value = _state.value.copy(isSaving = false, savedInstanceId = id)
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorUpdating, ex),
                )
            }
        }
    }

    private fun setStart(newStart: LocalDateTime) {
        val s = _state.value
        val oldStart = instantOf(s.draft.sinceIso)
        val oldEnd = instantOf(s.draft.untilIso)
        val duration = if (oldStart != null && oldEnd != null && oldEnd > oldStart) oldEnd - oldStart else DEFAULT_DURATION
        val startInstant = newStart.toInstant(timeZone)
        updateDraft { it.copy(sinceIso = startInstant.toString(), untilIso = (startInstant + duration).toString()) }
    }

    private fun setEnd(newEnd: LocalDateTime) {
        updateDraft { it.copy(untilIso = newEnd.toInstant(timeZone).toString()) }
    }

    private fun newDraft(permissions: UserPermissions, options: ManagementOptions): EventDraft {
        // Next full hour, one hour long.
        val now = clock.now().toLocalDateTime(timeZone)
        val startLocal = LocalDateTime(now.date, LocalTime(now.hour, 0))
        val start = startLocal.toInstant(timeZone) + 1.hours
        val myTrainerIds = options.trainers.map { it.id }.filter { it in permissions.personIds }
        return EventDraft(
            sinceIso = start.toString(),
            untilIso = (start + DEFAULT_DURATION).toString(),
            trainerPersonIds = myTrainerIds,
        )
    }

    private fun instantOf(iso: String): Instant? = try { Instant.parse(iso) } catch (_: Exception) { null }

    private fun isEndAfterStart(sinceIso: String, untilIso: String): Boolean {
        val start = instantOf(sinceIso) ?: return false
        val end = instantOf(untilIso) ?: return false
        return end > start
    }

    private companion object {
        val DEFAULT_DURATION: Duration = 60.minutes
    }
}
