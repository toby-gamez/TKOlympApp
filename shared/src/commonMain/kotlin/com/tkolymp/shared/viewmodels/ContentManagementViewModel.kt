package com.tkolymp.shared.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.management.IManagementService
import com.tkolymp.shared.management.UserPermissions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Immutable
data class ContentManagementState(
    val permissions: UserPermissions = UserPermissions.NONE,
    val isWorking: Boolean = false,
    override val isLoading: Boolean = false,
    override val error: AppError? = null,
) : ViewModelState

sealed class ContentManagementEffect {
    data object EventDeleted : ContentManagementEffect()
    data class EventCancelledChanged(val cancelled: Boolean) : ContentManagementEffect()
    data object AnnouncementDeleted : ContentManagementEffect()
}

/**
 * Trainer / administrator actions offered on list and detail screens: decides which
 * management actions are visible and performs the quick ones (delete, cancel/restore).
 */
class ContentManagementViewModel(
    private val service: IManagementService = ServiceLocator.managementService,
) : ViewModel() {
    private val _state = MutableStateFlow(ContentManagementState())
    val state: StateFlow<ContentManagementState> = _state.asStateFlow()

    private val _effects = Channel<ContentManagementEffect>(Channel.BUFFERED)
    val effects: Flow<ContentManagementEffect> = _effects.receiveAsFlow()

    fun refreshPermissions(force: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val permissions = try { service.getPermissions(force) } catch (e: CancellationException) { throw e } catch (_: Exception) { UserPermissions.NONE }
            _state.value = _state.value.copy(permissions = permissions, isLoading = false)
        }
    }

    /** [eventJson] is the `eventInstance` object loaded by [com.tkolymp.shared.event.EventService.fetchEventById]. */
    fun canEditEvent(eventJson: JsonObject?): Boolean {
        if (eventJson == null) return false
        val trainerIds = (eventJson["eventTrainersList"] as? JsonArray)
            ?.mapNotNull { ((it as? JsonObject)?.get("personId") as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        val managerIds = (eventJson["managerPersonIds"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .orEmpty()
        return _state.value.permissions.canEditEvent(trainerIds, managerIds)
    }

    fun canEditAnnouncement(authorId: String?): Boolean = _state.value.permissions.canEditAnnouncement(authorId)

    fun deleteEvent(instanceId: Long) = runAction(ContentManagementEffect.EventDeleted) { service.deleteEvent(instanceId) }

    fun setEventCancelled(instanceId: Long, cancelled: Boolean) =
        runAction(ContentManagementEffect.EventCancelledChanged(cancelled)) { service.setEventCancelled(instanceId, cancelled) }

    fun deleteAnnouncement(id: Long) = runAction(ContentManagementEffect.AnnouncementDeleted) { service.deleteAnnouncement(id) }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun runAction(effect: ContentManagementEffect, action: suspend () -> Unit) {
        if (_state.value.isWorking) return
        _state.value = _state.value.copy(isWorking = true, error = null)
        viewModelScope.launch {
            try {
                action()
                _state.value = _state.value.copy(isWorking = false)
                _effects.send(effect)
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                _state.value = _state.value.copy(
                    isWorking = false,
                    error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorUpdating, ex),
                )
            }
        }
    }
}
