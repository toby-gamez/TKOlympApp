package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.auth.IAuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LoginState(
    val username: String = "",
    val password: String = "",
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class LoginViewModel(
    private val authService: IAuthService = ServiceLocator.authService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService
) {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun updateUsername(value: String) {
        _state.value = _state.value.copy(username = value, error = null)
    }

    fun updatePassword(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    suspend fun login(): Boolean {
        if (_state.value.isLoading) return false
        _state.value = _state.value.copy(isLoading = true, error = null)
        return try {
            val ok = authService.login(_state.value.username, _state.value.password)
            if (!ok) {
                _state.value = _state.value.copy(isLoading = false, error = "Přihlášení selhalo")
                return false
            }

            val personId = try { userService.fetchAndStorePersonId() } catch (_: Throwable) { null }
            if (personId == null) {
                _state.value = _state.value.copy(isLoading = false, error = "Nelze získat personId po přihlášení")
                return false
            }

            try { userService.fetchAndStoreActiveCouples() } catch (_: Throwable) {}
            try { userService.fetchAndStorePersonDetails(personId) } catch (_: Throwable) {}

            _state.value = _state.value.copy(isLoading = false, error = null)
            true
        } catch (ex: Throwable) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při přihlášení")
            false
        }
    }
}
