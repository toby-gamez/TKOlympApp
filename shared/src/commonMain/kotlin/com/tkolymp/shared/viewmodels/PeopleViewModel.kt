package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.tkolymp.shared.people.Person

data class PeopleState(
    val people: List<Any> = emptyList(),
    val filteredPeople: List<Any> = emptyList(),
    val searchQuery: String = "",
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class PeopleViewModel(
    private val peopleService: com.tkolymp.shared.people.PeopleService = ServiceLocator.peopleService
) : ViewModel() {
    private val _state = MutableStateFlow(PeopleState())
    val state: StateFlow<PeopleState> = _state.asStateFlow()

    suspend fun loadPeople() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            // preserve existing people if fetch fails or returns empty while offline
            val current: List<Person> = (_state.value.people as? List<Person>) ?: emptyList()
            var fetched: List<Person>? = null
            try { fetched = peopleService.fetchPeople() } catch (e: CancellationException) { throw e } catch (_: Exception) { fetched = null }

            if (fetched != null && fetched.isNotEmpty()) {
                _state.value = _state.value.copy(people = fetched as? List<Any> ?: emptyList(), filteredPeople = fetched as? List<Any> ?: emptyList(), isLoading = false)
            } else {
                // try offline fallback saved by OfflineSyncManager
                try {
                    val raw = ServiceLocator.offlineSyncManager.loadPeople()
                    if (raw != null) {
                        val parsed = Json.parseToJsonElement(raw).jsonArray.mapNotNull { el ->
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
                            _state.value = _state.value.copy(people = parsed as? List<Any> ?: emptyList(), filteredPeople = parsed as? List<Any> ?: emptyList(), isLoading = false)
                        } else {
                            // keep current data if available
                            _state.value = _state.value.copy(people = current as? List<Any> ?: emptyList(), filteredPeople = current as? List<Any> ?: emptyList(), isLoading = false)
                        }
                    } else {
                        _state.value = _state.value.copy(people = current as? List<Any> ?: emptyList(), filteredPeople = current as? List<Any> ?: emptyList(), isLoading = false)
                    }
                } catch (_: Exception) {
                    _state.value = _state.value.copy(people = current as? List<Any> ?: emptyList(), filteredPeople = current as? List<Any> ?: emptyList(), isLoading = false)
                }
            }
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání lidí")
        }
    }

    fun updateSearch(query: String) {
        val q = query.trim()
        val filtered = if (q.isBlank()) _state.value.people else _state.value.people.filter { it.toString().trim().contains(q, true) }
        _state.value = _state.value.copy(searchQuery = q, filteredPeople = filtered)
    }
}
