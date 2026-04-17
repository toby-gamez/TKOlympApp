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

data class LeaderboardState(
    val rankings: List<Any> = emptyList(),
    val peopleById: Map<String, com.tkolymp.shared.people.Person> = emptyMap(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class LeaderboardViewModel(
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService
) : ViewModel() {
    private val _state = MutableStateFlow(LeaderboardState())
    val state: StateFlow<LeaderboardState> = _state.asStateFlow()

    suspend fun loadLeaderboard() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            // Load people first (with offline fallback)
            var people: List<com.tkolymp.shared.people.Person> = emptyList()
            try {
                people = peopleService.fetchPeople()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                try {
                    val raw = ServiceLocator.offlineDataStorage.load("offline_people") ?: ""
                    if (raw.isNotBlank()) {
                        val arr = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonArray
                        people = arr.mapNotNull { el ->
                            val obj = el.jsonObject
                            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            val first = obj["firstName"]?.jsonPrimitive?.contentOrNull
                            val last = obj["lastName"]?.jsonPrimitive?.contentOrNull
                            val prefix = obj["prefixTitle"]?.jsonPrimitive?.contentOrNull
                            val suffix = obj["suffixTitle"]?.jsonPrimitive?.contentOrNull
                            val birth = obj["birthDate"]?.jsonPrimitive?.contentOrNull
                            val memberships = obj["cohortMembershipsList"]?.jsonArray?.mapNotNull { mEl ->
                                val mObj = mEl.jsonObject
                                val cohortObj = mObj["cohort"]?.jsonObject
                                val cId = cohortObj?.get("id")?.jsonPrimitive?.contentOrNull
                                val cName = cohortObj?.get("name")?.jsonPrimitive?.contentOrNull
                                val cColor = cohortObj?.get("colorRgb")?.jsonPrimitive?.contentOrNull
                                val cVis = cohortObj?.get("isVisible")?.jsonPrimitive?.contentOrNull?.let { it == "true" }
                                com.tkolymp.shared.people.CohortMembership(
                                    com.tkolymp.shared.people.Cohort(cId, cName, cColor, cVis),
                                    mObj["since"]?.jsonPrimitive?.contentOrNull,
                                    mObj["until"]?.jsonPrimitive?.contentOrNull
                                )
                            } ?: emptyList()
                            com.tkolymp.shared.people.Person(id, first, last, prefix, suffix, birth, memberships)
                        }
                    }
                } catch (_: Exception) {
                    // leave people as empty list
                }
            }

            // If service returned an empty list (network failure but no exception), try offline fallback as well
            if (people.isEmpty()) {
                try {
                    val raw = ServiceLocator.offlineDataStorage.load("offline_people") ?: ""
                    if (raw.isNotBlank()) {
                        val arr = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonArray
                        people = arr.mapNotNull { el ->
                            val obj = el.jsonObject
                            val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                            val first = obj["firstName"]?.jsonPrimitive?.contentOrNull
                            val last = obj["lastName"]?.jsonPrimitive?.contentOrNull
                            val prefix = obj["prefixTitle"]?.jsonPrimitive?.contentOrNull
                            val suffix = obj["suffixTitle"]?.jsonPrimitive?.contentOrNull
                            val birth = obj["birthDate"]?.jsonPrimitive?.contentOrNull
                            val memberships = obj["cohortMembershipsList"]?.jsonArray?.mapNotNull { mEl ->
                                val mObj = mEl.jsonObject
                                val cohortObj = mObj["cohort"]?.jsonObject
                                val cId = cohortObj?.get("id")?.jsonPrimitive?.contentOrNull
                                val cName = cohortObj?.get("name")?.jsonPrimitive?.contentOrNull
                                val cColor = cohortObj?.get("colorRgb")?.jsonPrimitive?.contentOrNull
                                val cVis = cohortObj?.get("isVisible")?.jsonPrimitive?.contentOrNull?.let { it == "true" }
                                com.tkolymp.shared.people.CohortMembership(
                                    com.tkolymp.shared.people.Cohort(cId, cName, cColor, cVis),
                                    mObj["since"]?.jsonPrimitive?.contentOrNull,
                                    mObj["until"]?.jsonPrimitive?.contentOrNull
                                )
                            } ?: emptyList()
                            com.tkolymp.shared.people.Person(id, first, last, prefix, suffix, birth, memberships)
                        }
                    }
                } catch (_: Exception) {
                    // ignore
                }
            }

            val peopleById = people.associateBy { it.id }

            // Load scoreboard (with offline fallback)
            val until = "2100-01-01"
            val list = try {
                val fetched = peopleService.fetchScoreboard(null, "2025-09-01", until)
                if (fetched.isEmpty()) {
                    // try offline fallback when service returned empty list
                    var fallback: List<com.tkolymp.shared.people.ScoreboardEntry> = emptyList()
                    try {
                        val keys = ServiceLocator.offlineDataStorage.allKeys().filter { it.startsWith("offline_scoreboard_") }
                        for (k in keys) {
                            try {
                                val raw = ServiceLocator.offlineDataStorage.load(k) ?: continue
                                val arr = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonArray
                                val res = arr.mapNotNull { el ->
                                    val obj = el.jsonObject
                                    val ranking = obj["ranking"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                    val personId = obj["personId"]?.jsonPrimitive?.contentOrNull
                                    val first = obj["personFirstName"]?.jsonPrimitive?.contentOrNull
                                    val last = obj["personLastName"]?.jsonPrimitive?.contentOrNull
                                    val total = obj["totalScore"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                                    com.tkolymp.shared.people.ScoreboardEntry(ranking, personId, first, last, total)
                                }
                                if (res.isNotEmpty()) { fallback = res; break }
                            } catch (_e: Exception) {
                            }
                        }
                    } catch (_e: Exception) {
                    }
                    fallback
                } else fetched
            } catch (e: CancellationException) {
                throw e
            } catch (_e: Exception) {
                var fallback: List<com.tkolymp.shared.people.ScoreboardEntry> = emptyList()
                try {
                    val keys = ServiceLocator.offlineDataStorage.allKeys().filter { it.startsWith("offline_scoreboard_") }
                    for (k in keys) {
                        try {
                            val raw = ServiceLocator.offlineDataStorage.load(k) ?: continue
                            val arr = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonArray
                            val res = arr.mapNotNull { el ->
                                val obj = el.jsonObject
                                val ranking = obj["ranking"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                val personId = obj["personId"]?.jsonPrimitive?.contentOrNull
                                val first = obj["personFirstName"]?.jsonPrimitive?.contentOrNull
                                val last = obj["personLastName"]?.jsonPrimitive?.contentOrNull
                                val total = obj["totalScore"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                                com.tkolymp.shared.people.ScoreboardEntry(ranking, personId, first, last, total)
                            }
                            if (res.isNotEmpty()) { fallback = res; break }
                        } catch (_e: Exception) {
                        }
                    }
                } catch (_e: Exception) {
                }
                fallback
            }

            _state.value = _state.value.copy(
                rankings = list as? List<Any> ?: emptyList(),
                peopleById = peopleById,
                isLoading = false
            )
        } catch (ex: Exception) {
            _state.value = _state.value.copy(
                isLoading = false,
                error = ex.message ?: "Chyba při načítání žebříčku"
            )
        }
    }
}