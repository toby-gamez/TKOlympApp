package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

data class OtherState(
    val name: String? = null,
    val subtitle: String? = null,
    val personId: String? = null,
    val coupleIds: List<String> = emptyList(),
    val rawJson: String? = null,
    val personDetailsRaw: String? = null,
    val personDob: String? = null,
    val personFirstName: String? = null,
    val personLastName: String? = null,
    val personPrefix: String? = null,
    val personSuffix: String? = null,
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class OtherViewModel(
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService
) {
    private val _state = MutableStateFlow(OtherState())
    val state: StateFlow<OtherState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        scope.launch {
            try {
                val raw = try { userService.getCachedCurrentUserJson() } catch (_: Throwable) { null }
                val pid = try { userService.getCachedPersonId() } catch (_: Throwable) { null }
                val cids = try { userService.getCachedCoupleIds() } catch (_: Throwable) { emptyList<String>() }
                var personDetails = try { userService.getCachedPersonDetailsJson() } catch (_: Throwable) { null }

                if (personDetails.isNullOrBlank() && !pid.isNullOrBlank()) {
                    try { userService.fetchAndStorePersonDetails(pid) } catch (_: Throwable) { }
                    personDetails = try { userService.getCachedPersonDetailsJson() } catch (_: Throwable) { null }
                }

                // parse simple fields
                var name: String? = null
                var subtitle: String? = null
                var personFirstName: String? = null
                var personLastName: String? = null
                var personDob: String? = null
                var personPrefix: String? = null
                var personSuffix: String? = null

                try {
                    raw?.let {
                        val obj = Json.parseToJsonElement(it).jsonObject
                        val jmeno = obj["uJmeno"]?.toString()?.replace("\"", "")
                        val prijmeni = obj["uPrijmeni"]?.toString()?.replace("\"", "")
                        name = listOfNotNull(jmeno, prijmeni).joinToString(" ")
                        subtitle = obj["uLogin"]?.toString()?.replace("\"", "")
                    }
                } catch (_: Throwable) { }

                try {
                    personDetails?.let {
                        val p = Json.parseToJsonElement(it).jsonObject
                        personFirstName = p["firstName"]?.toString()?.replace("\"", "")
                        personLastName = p["lastName"]?.toString()?.replace("\"", "")
                        personDob = p["birthDate"]?.toString()?.replace("\"", "")
                            ?: p["dateOfBirth"]?.toString()?.replace("\"", "")
                            ?: p["dob"]?.toString()?.replace("\"", "")
                            ?: p["bio"]?.toString()?.replace("\"", "")
                        personPrefix = p["prefixTitle"]?.toString()?.replace("\"", "")
                        personSuffix = p["suffixTitle"]?.toString()?.replace("\"", "")
                            ?: p["postfixTitle"]?.toString()?.replace("\"", "")
                            ?: p["suffix"]?.toString()?.replace("\"", "")
                        if (!personFirstName.isNullOrBlank() || !personLastName.isNullOrBlank()) {
                            val base = listOfNotNull(personPrefix, personFirstName, personLastName).joinToString(" ")
                            name = if (!personSuffix.isNullOrBlank()) "$base, ${personSuffix}" else base
                        }
                    }
                } catch (_: Throwable) { }

                _state.value = _state.value.copy(
                    name = name,
                    subtitle = subtitle,
                    personId = pid,
                    coupleIds = cids,
                    rawJson = raw,
                    personDetailsRaw = personDetails,
                    personDob = personDob,
                    personFirstName = personFirstName,
                    personLastName = personLastName,
                    personPrefix = personPrefix,
                    personSuffix = personSuffix,
                    isLoading = false
                )
            } catch (ex: Throwable) {
                _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání")
            }
        }
    }
}
