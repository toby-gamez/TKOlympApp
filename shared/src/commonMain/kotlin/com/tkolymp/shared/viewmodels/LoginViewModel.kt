package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.auth.IAuthService
import com.tkolymp.shared.Logger
import com.tkolymp.shared.language.AppStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.sync.withLock

@Immutable
data class LoginState(
    val username: String = "",
    val password: String = "",
    override val isLoading: Boolean = false,
    override val error: AppError? = null
) : ViewModelState

sealed class LoginSideEffect {
    data object LoginSuccess : LoginSideEffect()
    data class LoginError(val message: String) : LoginSideEffect()
}

class LoginViewModel(
    private val authService: IAuthService = ServiceLocator.authService,
    private val userService: com.tkolymp.shared.user.UserService = ServiceLocator.userService
) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()
    private val _sideEffect = Channel<LoginSideEffect>(Channel.BUFFERED)
    val sideEffect: Flow<LoginSideEffect> = _sideEffect.receiveAsFlow()
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
                val msg = AppStrings.current.errorMessages.errorLogin
                _state.value = _state.value.copy(isLoading = false, error = AppError.generic(msg))
                _sideEffect.trySend(LoginSideEffect.LoginError(msg))
                return false
            }

            val personId = try { userService.fetchAndStorePersonId() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("LoginViewModel", "fetchAndStorePersonId failed: ${e.message}"); null }
            if (personId == null) {
                Logger.d("LoginViewModel", "Login OK but no personId available — proceeding without it")
            } else {
                try { userService.fetchAndStoreActiveCouples() } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("LoginViewModel", "fetchAndStoreActiveCouples failed: ${e.message}") }
                try { userService.fetchAndStorePersonDetails(personId) } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("LoginViewModel", "fetchAndStorePersonDetails failed: ${e.message}") }
            }
            try { userService.fetchAndStoreCurrentUser("1.0") } catch (e: CancellationException) { throw e } catch (e: Exception) { Logger.d("LoginViewModel", "fetchAndStoreCurrentUser failed: ${e.message}") }

            _state.value = _state.value.copy(isLoading = false, error = null)
            _sideEffect.trySend(LoginSideEffect.LoginSuccess)
            true
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            val msg = ex.message ?: AppStrings.current.errorMessages.errorLoadingLogin
            _state.value = _state.value.copy(isLoading = false, error = AppError.generic(msg))
            _sideEffect.trySend(LoginSideEffect.LoginError(msg))
            false
        }
    }
}
