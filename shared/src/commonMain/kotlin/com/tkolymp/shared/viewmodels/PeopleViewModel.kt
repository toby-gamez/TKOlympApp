package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.tkolymp.shared.people.Person
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.language.AppStrings

data class PeopleState(
    val people: List<Person> = emptyList(),
    val filteredPeople: List<Person> = emptyList(),
    val searchQuery: String = "",
    val trainerPersonIds: Set<String> = emptySet(),
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

class PeopleViewModel(
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService,
    private val clubService: com.tkolymp.shared.club.ClubService = ServiceLocator.clubService
) : ViewModel() {
    private val _state = MutableStateFlow(PeopleState())
    val state: StateFlow<PeopleState> = _state.asStateFlow()

    suspend fun loadPeople() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            // preserve existing people if fetch fails or returns empty while offline
            val current: List<Person> = _state.value.people
            val trainerIds = try {
                clubService.fetchClubData().trainers.mapNotNull { it.person?.id }.toSet()
            } catch (e: CancellationException) { throw e } catch (_: Exception) { _state.value.trainerPersonIds }
            var fetched: List<Person>? = null
            try { fetched = peopleService.fetchPeople() } catch (e: CancellationException) { throw e } catch (_: Exception) { fetched = null }

            if (fetched != null && fetched.isNotEmpty()) {
                _state.value = _state.value.copy(people = fetched, filteredPeople = fetched, trainerPersonIds = trainerIds, isLoading = false)
            } else {
                // try offline fallback saved by OfflineSyncManager
                try {
                    val raw = ServiceLocator.offlineSyncManager.loadPeople()
                    if (raw != null) {
                        val parsed = AppJson.parseToJsonElement(raw).jsonArray.mapNotNull { el ->
                            try {
                                val jo = el.jsonObject
                                val id = jo["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                val first = jo["firstName"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                val last = jo["lastName"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                val memberships = (jo["cohortMembershipsList"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())).mapNotNull { mEl ->
                                    try {
                                        val mObj = mEl.jsonObject
                                        val cohortObj = mObj["cohort"]?.jsonObject
                                        val cid = cohortObj?.get("id")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                        val cname = cohortObj?.get("name")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                        val ccolor = cohortObj?.get("colorRgb")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                                        val cvis = cohortObj?.get("isVisible")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { it == "true" }
                                        com.tkolymp.shared.people.CohortMembership(com.tkolymp.shared.people.Cohort(cid, cname, ccolor, cvis), mObj["since"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }, mObj["until"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() })
                                    } catch (_: Exception) { null }
                                }
                                Person(id, first, last, null, null, null, memberships)
                            } catch (_: Exception) { null }
                        }
                        if (parsed.isNotEmpty()) {
                            _state.value = _state.value.copy(people = parsed, filteredPeople = parsed, trainerPersonIds = trainerIds, isLoading = false)
                        } else {
                            // keep current data if available
                            _state.value = _state.value.copy(people = current, filteredPeople = current, trainerPersonIds = trainerIds, isLoading = false)
                        }
                    } else {
                        _state.value = _state.value.copy(people = current, filteredPeople = current, trainerPersonIds = trainerIds, isLoading = false)
                    }
                } catch (_: Exception) {
                    _state.value = _state.value.copy(people = current, filteredPeople = current, trainerPersonIds = trainerIds, isLoading = false)
                }
            }
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoadingPeople))
        }
    }

    fun updateSearch(query: String) {
        val q = query.trim()
        val filtered = if (q.isBlank()) _state.value.people else _state.value.people.filter { p -> listOfNotNull(p.firstName, p.lastName, p.prefixTitle, p.suffixTitle).any { it.contains(q, true) } }
        _state.value = _state.value.copy(searchQuery = q, filteredPeople = filtered)
    }
}
