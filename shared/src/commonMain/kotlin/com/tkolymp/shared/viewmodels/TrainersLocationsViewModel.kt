package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.club.ClubData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class TrainersLocationsState(
    val clubData: ClubData? = null,
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class TrainersLocationsViewModel(
    private val clubService: com.tkolymp.shared.club.ClubService = ServiceLocator.clubService
) : ViewModel() {
    private val _state = MutableStateFlow(TrainersLocationsState())
    val state: StateFlow<TrainersLocationsState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val d = try {
                withContext(Dispatchers.Default) { clubService.fetchClubData() }
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                // Try offline fallback: load saved club JSON
                try {
                    val raw = ServiceLocator.offlineDataStorage.load("offline_club") ?: run {
                        _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání klubu")
                        return
                    }
                    val obj = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonObject
                    val locations = (obj["tenantLocationsList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }?.map { l ->
                        com.tkolymp.shared.club.Location(l["name"]?.jsonPrimitive?.contentOrNull)
                    } ?: emptyList()
                    val trainers = (obj["tenantTrainersList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }?.map { t ->
                        val personObj = t["person"] as? kotlinx.serialization.json.JsonObject
                        val person = com.tkolymp.shared.club.TrainerPerson(
                            id = personObj?.get("id")?.jsonPrimitive?.contentOrNull,
                            firstName = personObj?.get("firstName")?.jsonPrimitive?.contentOrNull,
                            lastName = personObj?.get("lastName")?.jsonPrimitive?.contentOrNull,
                            prefixTitle = personObj?.get("prefixTitle")?.jsonPrimitive?.contentOrNull,
                            suffixTitle = personObj?.get("suffixTitle")?.jsonPrimitive?.contentOrNull
                        )
                        val guestPrice = t["guestPrice45Min"] as? kotlinx.serialization.json.JsonObject
                        val gp = guestPrice?.let { com.tkolymp.shared.club.Money(it["amount"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(), it["currency"]?.jsonPrimitive?.contentOrNull) }
                        val guestPayout = t["guestPayout45Min"] as? kotlinx.serialization.json.JsonObject
                        val gpayout = guestPayout?.let { com.tkolymp.shared.club.Money(it["amount"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull(), it["currency"]?.jsonPrimitive?.contentOrNull) }
                        val visible = t["isVisible"]?.jsonPrimitive?.contentOrNull?.let { it == "true" }
                        com.tkolymp.shared.club.Trainer(person, gp, gpayout, visible)
                    } ?: emptyList()
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
                    _state.value = _state.value.copy(clubData = com.tkolymp.shared.club.ClubData(locations, trainers, cohorts, null), isLoading = false)
                    return
                } catch (_: Exception) {
                    _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání klubu")
                    return
                }
            }
            _state.value = _state.value.copy(clubData = d, isLoading = false)
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání")
        }
    }
}
