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
import com.tkolymp.shared.json.AppJson
import androidx.compose.runtime.Immutable
import com.tkolymp.shared.language.AppStrings

@Immutable
data class PersonState(
    val personId: String? = null,
    val person: Any? = null,
    override val isLoading: Boolean = false,
    override val error: AppError? = null
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
                    val arr = AppJson.parseToJsonElement(raw).jsonArray
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

                        fun blankToNull(s: String?) = s?.takeIf { it.isNotBlank() }

                        val activeCouples = (obj["activeCouplesList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { cEl ->
                            val cObj = cEl as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            val manObj = cObj["man"] as? kotlinx.serialization.json.JsonObject
                            val womanObj = cObj["woman"] as? kotlinx.serialization.json.JsonObject
                            com.tkolymp.shared.people.ActiveCouple(
                                id = blankToNull(cObj["id"]?.jsonPrimitive?.contentOrNull),
                                man = com.tkolymp.shared.people.CoupleMember(manObj?.get("firstName")?.jsonPrimitive?.contentOrNull, manObj?.get("lastName")?.jsonPrimitive?.contentOrNull),
                                woman = com.tkolymp.shared.people.CoupleMember(womanObj?.get("firstName")?.jsonPrimitive?.contentOrNull, womanObj?.get("lastName")?.jsonPrimitive?.contentOrNull)
                            )
                        } ?: emptyList()

                        val allCouples = (obj["allCouplesList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { cEl ->
                            val cObj = cEl as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            com.tkolymp.shared.people.CouplePeriod(
                                id = blankToNull(cObj["id"]?.jsonPrimitive?.contentOrNull),
                                manId = blankToNull(cObj["manId"]?.jsonPrimitive?.contentOrNull),
                                womanId = blankToNull(cObj["womanId"]?.jsonPrimitive?.contentOrNull),
                                since = blankToNull(cObj["since"]?.jsonPrimitive?.contentOrNull),
                                until = blankToNull(cObj["until"]?.jsonPrimitive?.contentOrNull),
                                status = blankToNull(cObj["status"]?.jsonPrimitive?.contentOrNull)
                            )
                        } ?: emptyList()

                        val cstsProgress = (obj["cstsProgressList"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { pEl ->
                            val pObj = pEl as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                            val catObj = pObj["category"] as? kotlinx.serialization.json.JsonObject
                            val cat = catObj?.let { cat ->
                                com.tkolymp.shared.competitions.CompetitionCategory(
                                    id = cat["id"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                                    name = blankToNull(cat["name"]?.jsonPrimitive?.contentOrNull),
                                    series = blankToNull(cat["series"]?.jsonPrimitive?.contentOrNull),
                                    discipline = blankToNull(cat["discipline"]?.jsonPrimitive?.contentOrNull),
                                    ageGroup = blankToNull(cat["ageGroup"]?.jsonPrimitive?.contentOrNull),
                                    genderGroup = blankToNull(cat["genderGroup"]?.jsonPrimitive?.contentOrNull),
                                    competitorClass = blankToNull(cat["class"]?.jsonPrimitive?.contentOrNull),
                                    competitorType = blankToNull(cat["competitorType"]?.jsonPrimitive?.contentOrNull),
                                    baseDanceProgramId = cat["baseDanceProgramId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                                )
                            }
                            com.tkolymp.shared.competitions.CstsProgress(
                                points = blankToNull(pObj["points"]?.jsonPrimitive?.contentOrNull),
                                finals = pObj["finals"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.takeIf { it >= 0 },
                                competitorName = blankToNull(pObj["competitorName"]?.jsonPrimitive?.contentOrNull),
                                category = cat
                            )
                        } ?: emptyList()

                        com.tkolymp.shared.people.PersonDetails(
                            id = id,
                            firstName = first,
                            lastName = last,
                            prefixTitle = prefix,
                            suffixTitle = suffix,
                            birthDate = birth,
                            cstsId = blankToNull(obj["cstsId"]?.jsonPrimitive?.contentOrNull),
                            email = blankToNull(obj["email"]?.jsonPrimitive?.contentOrNull),
                            gender = blankToNull(obj["gender"]?.jsonPrimitive?.contentOrNull),
                            isTrainer = isTrainer,
                            phone = blankToNull(obj["phone"]?.jsonPrimitive?.contentOrNull),
                            wdsfId = blankToNull(obj["wdsfId"]?.jsonPrimitive?.contentOrNull),
                            activeCouplesList = activeCouples,
                            cohortMembershipsList = memberships,
                            rawResponse = null,
                            cstsProgressList = cstsProgress,
                            instagramUsername = blankToNull(obj["instagramUsername"]?.jsonPrimitive?.contentOrNull),
                            tiktokUsername = blankToNull(obj["tiktokUsername"]?.jsonPrimitive?.contentOrNull),
                            facebookUrl = blankToNull(obj["facebookUrl"]?.jsonPrimitive?.contentOrNull),
                            websiteUrl = blankToNull(obj["websiteUrl"]?.jsonPrimitive?.contentOrNull),
                            note = blankToNull(obj["note"]?.jsonPrimitive?.contentOrNull),
                            allCouplesList = allCouples
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
            _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoadingPerson))
        }
    }
}
