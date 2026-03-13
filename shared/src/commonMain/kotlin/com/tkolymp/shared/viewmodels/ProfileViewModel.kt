package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.people.PersonDetails
import com.tkolymp.shared.user.CurrentUser
import com.tkolymp.shared.user.UserService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileState(
    val person: PersonDetails? = null,
    val currentUser: CurrentUser? = null,
    val coupleIds: List<String> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class ProfileViewModel(
    private val userService: UserService = ServiceLocator.userService
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            withContext(Dispatchers.Default) {
                val pid = try { userService.getCachedPersonId() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("ProfileViewModel", "getCachedPersonId failed: ${e.message}"); null }

                if (!pid.isNullOrBlank()) {
                    val cachedPersonJson = try { userService.getCachedPersonDetailsJson() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("ProfileViewModel", "getCachedPersonDetailsJson failed: ${e.message}"); null }
                    val needsRefetch = cachedPersonJson.isNullOrBlank() || !(
                        cachedPersonJson.contains("activeCouplesList") &&
                            cachedPersonJson.contains("cohortMembershipsList") &&
                            (cachedPersonJson.contains("email") || cachedPersonJson.contains("uEmail"))
                        )
                    if (needsRefetch) {
                        try { userService.fetchAndStorePersonDetails(pid) } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("ProfileViewModel", "fetchAndStorePersonDetails failed: ${e.message}") }
                    }
                }

                val person = try { userService.getCachedPersonDetails() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("ProfileViewModel", "getCachedPersonDetails failed: ${e.message}"); null }
                val currentUser = try { userService.getCachedCurrentUser() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("ProfileViewModel", "getCachedCurrentUser failed: ${e.message}"); null }
                val coupleIds = try { userService.getCachedCoupleIds() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("ProfileViewModel", "getCachedCoupleIds failed: ${e.message}"); emptyList() }

                _state.value = _state.value.copy(person = person, currentUser = currentUser, coupleIds = coupleIds, isLoading = false)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání profilu")
        }
    }
}
