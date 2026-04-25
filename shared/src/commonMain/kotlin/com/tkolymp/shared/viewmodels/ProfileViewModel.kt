package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.people.PersonDetails
import com.tkolymp.shared.user.CohortDisplay
import com.tkolymp.shared.user.CurrentUser
import com.tkolymp.shared.user.ProfileDerivedState
import com.tkolymp.shared.user.UserService
import com.tkolymp.shared.user.deriveProfileState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.jsonObject

data class ProfileState(
    val person: PersonDetails? = null,
    val currentUser: CurrentUser? = null,
    val coupleIds: List<String> = emptyList(),
    val derived: ProfileDerivedState = ProfileDerivedState(
        titleText = null, bioText = null, addrText = null, emailText = null,
        activeCoupleNames = emptyList(), cohortItems = emptyList(),
        personFields = emptyList(), currentUserFields = emptyList(), addressFields = emptyList(),
        personalList = emptyList(), contactList = emptyList(), externalList = emptyList(), otherList = emptyList()
    ),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class ProfileViewModel(
    private val userService: UserService = ServiceLocator.userService,
    private val tokenStorage: com.tkolymp.shared.storage.TokenStorage = ServiceLocator.tokenStorage
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            withContext(Dispatchers.Default) {
                val pid = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("ProfileViewModel", "getCachedPersonId failed: ${e.message}"); null }

                if (!pid.isNullOrBlank()) {
                    val cachedPerson = try { userService.getCachedPersonDetails() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("ProfileViewModel", "getCachedPersonDetails (pre-check) failed: ${e.message}"); null }
                    val cachedPersonJson = try { userService.getCachedPersonDetailsJson() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("ProfileViewModel", "getCachedPersonDetailsJson failed: ${e.message}"); null }
                    val needsRefetch = try {
                        if (cachedPerson == null || cachedPersonJson.isNullOrBlank()) true
                        else {
                            val parsed = try { kotlinx.serialization.json.Json.parseToJsonElement(cachedPersonJson).jsonObject } catch (_: Exception) { null }
                            val hasActiveCouples = parsed?.get("activeCouplesList") != null
                            val hasCohorts = parsed?.get("cohortMembershipsList") != null
                            val hasEmail = parsed?.get("email") != null || parsed?.get("uEmail") != null
                            !(hasActiveCouples && hasCohorts && hasEmail)
                        }
                    } catch (_: Exception) { true }
                    if (needsRefetch) {
                        try { userService.fetchAndStorePersonDetails(pid) } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("ProfileViewModel", "fetchAndStorePersonDetails failed: ${e.message}") }
                    }
                }

                val person = try { userService.getCachedPersonDetails() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("ProfileViewModel", "getCachedPersonDetails failed: ${e.message}"); null }
                val currentUser = try { userService.getCachedCurrentUser() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("ProfileViewModel", "getCachedCurrentUser failed: ${e.message}"); null }
                val coupleIds = try { userService.getCachedCoupleIds() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("ProfileViewModel", "getCachedCoupleIds failed: ${e.message}"); emptyList() }
                val derived = deriveProfileState(person, currentUser, coupleIds)

                _state.value = _state.value.copy(person = person, currentUser = currentUser, coupleIds = coupleIds, derived = derived, isLoading = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: com.tkolymp.shared.language.AppStrings.current.errorMessages.errorLoadingProfile)
        }
    }

    suspend fun logout() {
        try { tokenStorage.clear() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        try { userService.clear() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }
}
