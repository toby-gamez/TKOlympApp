package com.tkolymp.shared.viewmodels

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppLanguage
import com.tkolymp.shared.storage.LanguageStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LanguageState(
    val selectedLanguage: AppLanguage = AppLanguage.CS,
    override val isLoading: Boolean = false,
    override val error: String? = null
) : ViewModelState

class LanguageViewModel(
    private val languageStorage: LanguageStorage = ServiceLocator.languageStorage
) {
    private val _state = MutableStateFlow(LanguageState())
    val state: StateFlow<LanguageState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun load() {
        scope.launch {
            val code = try { languageStorage.getLanguageCode() } catch (_: Throwable) { null }
            val language = if (code != null) AppLanguage.fromCode(code) else AppLanguage.CS
            _state.value = _state.value.copy(selectedLanguage = language)
        }
    }

    fun selectLanguage(language: AppLanguage) {
        scope.launch {
            try {
                languageStorage.saveLanguageCode(language.code)
                _state.value = _state.value.copy(selectedLanguage = language)
            } catch (e: Throwable) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }
}
