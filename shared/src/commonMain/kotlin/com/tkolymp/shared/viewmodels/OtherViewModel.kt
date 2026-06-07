package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.language.AppStrings

data class OtherState(
    val name: String? = null,
    val subtitle: String? = null,
    val personId: String? = null,
    val cstsId: String? = null,
    val coupleIds: List<String> = emptyList(),
    val rawJson: String? = null,
    val personDetailsRaw: String? = null,
    val personDob: String? = null,
    val personFirstName: String? = null,
    val personLastName: String? = null,
    val personPrefix: String? = null,
    val personSuffix: String? = null,
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

class OtherViewModel(
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService
) : ViewModel() {
    private val _state = MutableStateFlow(lastKnownState ?: OtherState())
    val state: StateFlow<OtherState> = _state.asStateFlow()
    private var loadStarted = false

    companion object {
        // Survives ViewModel recreation within the same process — eliminates the blank-name flash
        private var lastKnownState: OtherState? = null

        fun clearCache() { lastKnownState = null }
    }

    init {
        fetchFromStorage()
    }

    // Called from the screen — no-op if already started; use force = true to refresh
    fun load(force: Boolean = false) {
        if (loadStarted && !force) return
        fetchFromStorage()
    }

    private fun fetchFromStorage() {
        loadStarted = true
        viewModelScope.launch {
            try {
                val raw = try { userService.getCachedCurrentUserJson() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                val pid = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                val cstsId = try { userService.getCachedCstsId() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                val cids = try { userService.getCachedCoupleIds() } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList<String>() }
                var personDetails = try { userService.getCachedPersonDetailsJson() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

                // Only show spinner + hit the network when person details aren't cached yet
                if (personDetails.isNullOrBlank() && !pid.isNullOrBlank()) {
                    _state.value = _state.value.copy(isLoading = true, error = null)
                    try { userService.fetchAndStorePersonDetails(pid) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
                    personDetails = try { userService.getCachedPersonDetailsJson() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                }

                var name: String? = null
                var subtitle: String? = null
                var personFirstName: String? = null
                var personLastName: String? = null
                var personDob: String? = null
                var personPrefix: String? = null
                var personSuffix: String? = null

                try {
                    raw?.let {
                        val obj = AppJson.parseToJsonElement(it).jsonObject
                        val jmeno = obj["uJmeno"]?.jsonPrimitive?.contentOrNull
                        val prijmeni = obj["uPrijmeni"]?.jsonPrimitive?.contentOrNull
                        name = listOfNotNull(jmeno?.takeIf { it.isNotBlank() }, prijmeni?.takeIf { it.isNotBlank() }).joinToString(" ")
                        subtitle = obj["uLogin"]?.jsonPrimitive?.contentOrNull
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { }

                try {
                    personDetails?.let {
                        val p = AppJson.parseToJsonElement(it).jsonObject
                        personFirstName = p["firstName"]?.jsonPrimitive?.contentOrNull
                        personLastName = p["lastName"]?.jsonPrimitive?.contentOrNull
                        personDob = p["birthDate"]?.jsonPrimitive?.contentOrNull
                            ?: p["dateOfBirth"]?.jsonPrimitive?.contentOrNull
                            ?: p["dob"]?.jsonPrimitive?.contentOrNull
                        personPrefix = p["prefixTitle"]?.jsonPrimitive?.contentOrNull
                        personSuffix = p["suffixTitle"]?.jsonPrimitive?.contentOrNull
                            ?: p["postfixTitle"]?.jsonPrimitive?.contentOrNull
                            ?: p["suffix"]?.jsonPrimitive?.contentOrNull
                        if (!personFirstName.isNullOrBlank() || !personLastName.isNullOrBlank()) {
                            val parts = listOf(personPrefix, personFirstName, personLastName)
                                .filterNotNull()
                                .filter { it.isNotBlank() }
                            val base = parts.joinToString(" ")
                            name = if (!personSuffix.isNullOrBlank()) "$base, $personSuffix" else base
                        }
                    }
                } catch (e: CancellationException) { throw e } catch (_: Exception) { }

                val updated = _state.value.copy(
                    name = name,
                    subtitle = subtitle,
                    personId = pid,
                    cstsId = cstsId,
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
                lastKnownState = updated
                _state.value = updated
            } catch (e: CancellationException) { throw e } catch (ex: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = AppError.generic(ex.message ?: AppStrings.current.errorMessages.errorLoading))
            }
        }
    }
}
