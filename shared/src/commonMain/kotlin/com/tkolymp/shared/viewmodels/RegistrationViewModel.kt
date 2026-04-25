package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.registration.filterOwnedRegistrations
import com.tkolymp.shared.utils.asJsonObjectOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

data class RegistrationState(
    val trainerNames: Map<String, String> = emptyMap(),
    val myPersonName: String? = null,
    val myCoupleNames: Map<String, String> = emptyMap(),
    val registrationDisplayNames: Map<String, String> = emptyMap(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class RegistrationViewModel(
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService
) : ViewModel() {
    private val _state = MutableStateFlow(RegistrationState())
    val state: StateFlow<RegistrationState> = _state.asStateFlow()

    suspend fun invalidateAndRefresh(trainers: JsonArray, registrations: JsonArray, myPersonId: String?, myCoupleIds: List<String>) {
        try { ServiceLocator.cacheService.invalidatePrefix("person_") } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        try { ServiceLocator.cacheService.invalidate("people_all") } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        loadNames(trainers, registrations, myPersonId, myCoupleIds, null, emptyMap())
    }

    suspend fun loadNames(trainers: JsonArray, registrations: JsonArray, myPersonId: String?, myCoupleIds: List<String>, myPersonNameHint: String?, myCoupleNamesHint: Map<String, String>) {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            val trainerMap = mutableMapOf<String, String>()
                try {
                withContext(Dispatchers.Default) {
                    trainers.forEachIndexed { idx, tEl ->
                        try {
                            val tObj = tEl as? kotlinx.serialization.json.JsonObject
                            val tIdStr = tObj?.get("id")?.jsonPrimitive?.contentOrNull ?: idx.toString()
                            val personRef = tObj?.get("person")?.asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull
                                ?: tObj?.get("personId")?.jsonPrimitive?.contentOrNull
                            val fetched = if (!personRef.isNullOrBlank()) try { peopleService.fetchPersonDisplayName(personRef, false) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } else null
                            if (!fetched.isNullOrBlank()) trainerMap[tIdStr] = fetched
                        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                    }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}

            var personName = myPersonNameHint
            val coupleNames = myCoupleNamesHint.toMutableMap()
                try {
                withContext(Dispatchers.Default) {
                    if (personName.isNullOrBlank() && myPersonId != null) {
                        personName = try { peopleService.fetchPersonDisplayName(myPersonId, true) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                    }
                    myCoupleIds.forEach { cid ->
                        if (coupleNames[cid].isNullOrBlank()) {
                            try {
                                val fetched = peopleService.fetchCoupleDisplayName(cid)
                                if (!fetched.isNullOrBlank()) coupleNames[cid] = fetched
                            } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}

            val regDisplayNames = mutableMapOf<String, String>()
            try {
                withContext(Dispatchers.Default) {
                    filterOwnedRegistrations(registrations, myPersonId, myCoupleIds).forEach { rEl ->
                        val r = rEl as? JsonObject
                        val rid = r?.get("id")?.jsonPrimitive?.contentOrNull ?: return@forEach
                        val personId = r.get("person").asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull
                        val coupleId = r.get("couple").asJsonObjectOrNull()?.get("id")?.jsonPrimitive?.contentOrNull
                        val fetched = when {
                            !personId.isNullOrBlank() -> try { peopleService.fetchPersonDisplayName(personId, false) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                            !coupleId.isNullOrBlank() -> try { peopleService.fetchCoupleDisplayName(coupleId) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                            else -> null
                        }
                        if (!fetched.isNullOrBlank()) regDisplayNames[rid] = fetched
                    }
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) {}

            _state.value = _state.value.copy(trainerNames = trainerMap, myPersonName = personName, myCoupleNames = coupleNames, registrationDisplayNames = regDisplayNames, isLoading = false)
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: com.tkolymp.shared.language.AppStrings.current.errorMessages.errorLoadingNames)
        }
    }
}
