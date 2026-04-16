package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.club.Cohort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class GroupsState(
    val cohorts: List<Cohort> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class GroupsViewModel(
    private val clubService: com.tkolymp.shared.club.ClubService = ServiceLocator.clubService
) : ViewModel() {
    private val _state = MutableStateFlow(GroupsState())
    val state: StateFlow<GroupsState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val d = try { withContext(Dispatchers.Default) { clubService.fetchClubData() } } catch (e: CancellationException) { throw e } catch (ex: Exception) { null }
            if (d == null) {
                // Try offline fallback (offline_club saved by OfflineSyncManager)
                try {
                    val raw = ServiceLocator.offlineDataStorage.load("offline_club") ?: run {
                        _state.value = _state.value.copy(isLoading = false, error = "Chyba při načítání dat")
                        return
                    }
                    val obj = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
                    val tenantObj = obj["getCurrentTenant"] as? kotlinx.serialization.json.JsonObject
                    val cohortsArr = (tenantObj?.get("cohortsList") as? kotlinx.serialization.json.JsonArray) ?: kotlinx.serialization.json.JsonArray(emptyList())
                    val cohorts = cohortsArr.mapNotNull { it as? kotlinx.serialization.json.JsonObject }.map { c ->
                        com.tkolymp.shared.club.Cohort(
                            colorRgb = c["colorRgb"]?.jsonPrimitive?.contentOrNull,
                            name = c["name"]?.jsonPrimitive?.contentOrNull,
                            description = c["description"]?.jsonPrimitive?.contentOrNull,
                            location = c["location"]?.jsonPrimitive?.contentOrNull
                        )
                    }
                    _state.value = _state.value.copy(cohorts = cohorts, isLoading = false)
                } catch (_: Exception) {
                    _state.value = _state.value.copy(isLoading = false, error = "Chyba při načítání dat")
                }
            } else {
                _state.value = _state.value.copy(cohorts = d.cohorts, isLoading = false)
            }
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání")
        }
    }
}
