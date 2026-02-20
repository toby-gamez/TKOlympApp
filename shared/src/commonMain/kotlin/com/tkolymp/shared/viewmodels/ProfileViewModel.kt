package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileState(
    val userJson: String? = null,
    val personJson: String? = null,
    val coupleIds: List<String> = emptyList(),
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class ProfileViewModel(
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService
) {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = _state.value.copy(isLoading = true, error = null)
        try {
            withContext(Dispatchers.Default) {
                val userJson = try { userService.getCachedCurrentUserJson() } catch (_: Throwable) { null }
                val cachedPerson = try { userService.getCachedPersonDetailsJson() } catch (_: Throwable) { null }
                val pid = try { userService.getCachedPersonId() } catch (_: Throwable) { null }

                if (!pid.isNullOrBlank()) {
                    val needsRefetch = cachedPerson.isNullOrBlank() || !(
                        cachedPerson.contains("activeCouplesList") &&
                            cachedPerson.contains("cohortMembershipsList") &&
                            (cachedPerson.contains("email") || cachedPerson.contains("uEmail"))
                        )
                    if (needsRefetch) {
                        try { userService.fetchAndStorePersonDetails(pid) } catch (_: Throwable) {}
                    }
                }

                val personJson = try { userService.getCachedPersonDetailsJson() } catch (_: Throwable) { null }
                val coupleIds = try { userService.getCachedCoupleIds() } catch (_: Throwable) { emptyList() }

                _state.value = _state.value.copy(userJson = userJson, personJson = personJson, coupleIds = coupleIds, isLoading = false)
            }
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při načítání profilu")
        }
    }
}
