package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.auth.IAuthService
import com.tkolymp.shared.Logger
import com.tkolymp.shared.language.AppStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LoginState(
    val username: String = "",
    val password: String = "",
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class LoginViewModel() : ViewModel() {
    private val authService: IAuthService
        get() = ServiceLocator.authService

    private val userService: com.tkolymp.shared.user.UserService
        get() = ServiceLocator.userService
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()
    private val loginMutex = Mutex()

    fun updateUsername(value: String) {
        _state.value = _state.value.copy(username = value, error = null)
    }

    fun updatePassword(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    suspend fun login(): Boolean = loginMutex.withLock {
        _state.value = _state.value.copy(isLoading = true, error = null)
        return try {
            val ok = authService.login(_state.value.username, _state.value.password)
            if (!ok) {
                _state.value = _state.value.copy(isLoading = false, error = AppStrings.current.errorLogin)
                return false
            }

            val personId = try { userService.fetchAndStorePersonId() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("LoginViewModel", "fetchAndStorePersonId failed: ${e.message}"); null }
            if (personId == null) {
                _state.value = _state.value.copy(isLoading = false, error = AppStrings.current.errorLoginPersonId)
                return false
            }

            try { userService.fetchAndStoreActiveCouples() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("LoginViewModel", "fetchAndStoreActiveCouples failed: ${e.message}") }
            try { userService.fetchAndStorePersonDetails(personId) } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("LoginViewModel", "fetchAndStorePersonDetails failed: ${e.message}") }

            _state.value = _state.value.copy(isLoading = false, error = null)
            true
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = ex.message ?: "Chyba při přihlášení")
            false
        }
    }
}
