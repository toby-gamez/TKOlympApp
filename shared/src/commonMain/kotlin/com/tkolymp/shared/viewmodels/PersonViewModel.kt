package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class PersonState(
    val personId: String? = null,
    val person: Any? = null,
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class PersonViewModel(
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService
) : ViewModel() {
    private val _state = MutableStateFlow(PersonState())
    val state: StateFlow<PersonState> = _state.asStateFlow()

    suspend fun loadPerson(personId: String) {
        _state.value = _state.value.copy(isLoading = true, error = null, personId = personId)
        try {
            val p = try { peopleService.fetchPerson(personId) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

            if (p != null) {
                _state.value = _state.value.copy(person = p, isLoading = false)
                return
            }

            // Offline fallback: try offline_people saved by OfflineSyncManager
            try {
                val raw = try { ServiceLocator.offlineSyncManager.loadPeople() } catch (_: Exception) { null }
                if (!raw.isNullOrBlank()) {
                    val arr = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonArray
                    val found = arr.mapNotNull { el ->
                        val obj = el.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull
                        if (id == null || id != personId) return@mapNotNull null
                        val first = obj["firstName"]?.jsonPrimitive?.contentOrNull
                        val last = obj["lastName"]?.jsonPrimitive?.contentOrNull
                        val prefix = obj["prefixTitle"]?.jsonPrimitive?.contentOrNull
                        val suffix = obj["suffixTitle"]?.jsonPrimitive?.contentOrNull
                        val birth = obj["birthDate"]?.jsonPrimitive?.contentOrNull
                        val isTrainer = obj["isTrainer"]?.jsonPrimitive?.contentOrNull?.let { it == "true" }
                        // parse cohort memberships
                        val memberships = (obj["cohortMembershipsList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { mEl ->
                            val mObj = mEl as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            val cohortObj = mObj["cohort"] as? kotlinx.serialization.json.JsonObject
                            val cId = cohortObj?.get("id")?.jsonPrimitive?.contentOrNull
                            val cName = cohortObj?.get("name")?.jsonPrimitive?.contentOrNull
                            val cColor = cohortObj?.get("colorRgb")?.jsonPrimitive?.contentOrNull
                            val cVis = cohortObj?.get("isVisible")?.jsonPrimitive?.contentOrNull?.let { it == "true" }
                            com.tkolymp.shared.people.CohortMembership(com.tkolymp.shared.people.Cohort(cId, cName, cColor, cVis), mObj["since"]?.jsonPrimitive?.contentOrNull, mObj["until"]?.jsonPrimitive?.contentOrNull)
                        } ?: emptyList()

                        com.tkolymp.shared.people.PersonDetails(
                            id = id,
                            firstName = first,
                            lastName = last,
                            prefixTitle = prefix,
                            suffixTitle = suffix,
                            birthDate = birth,
                            bio = null,
                            cstsId = null,
                            email = null,
                            gender = null,
                            isTrainer = isTrainer,
                            phone = null,
                            wdsfId = null,
                            activeCouplesList = emptyList(),
                            cohortMembershipsList = memberships,
                            rawResponse = null
                        )
                    }.firstOrNull()

                    if (found != null) {
                        _state.value = _state.value.copy(person = found, isLoading = false)
                        return
                    }
                }
            } catch (_: Exception) {}

            // nothing found
            _state.value = _state.value.copy(person = null, isLoading = false)
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání osoby")
        }
    }
}
