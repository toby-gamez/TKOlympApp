package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppLanguage
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.language.getDeviceLanguageCode
import com.tkolymp.shared.storage.LanguageStorage
import kotlinx.coroutines.CancellationException
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
) : ViewModel() {
    private val _state = MutableStateFlow(LanguageState())
    val state: StateFlow<LanguageState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            val savedCode = try { languageStorage.getLanguageCode() } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            val language = if (savedCode != null) {
                AppLanguage.fromCode(savedCode)
            } else {
                // No saved preference – use the device language, fall back to CS if unsupported
                AppLanguage.fromCode(getDeviceLanguageCode())
            }
            AppStrings.setLanguage(language)
            _state.value = _state.value.copy(selectedLanguage = language)
        }
    }

    fun selectLanguage(language: AppLanguage) {
        viewModelScope.launch {
            try {
                languageStorage.saveLanguageCode(language.code)
                AppStrings.setLanguage(language)
                _state.value = _state.value.copy(selectedLanguage = language)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }
}
