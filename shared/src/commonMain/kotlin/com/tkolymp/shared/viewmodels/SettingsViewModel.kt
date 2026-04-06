package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.appearance.AppearanceSettings
import com.tkolymp.shared.appearance.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class SettingsState(
    val preferTimeline: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isLoading: Boolean = false,
)

class SettingsViewModel(
    private val storage: com.tkolymp.shared.storage.CalendarPreferenceStorage = ServiceLocator.calendarPreferenceStorage
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    suspend fun load() {
        _state.update { it.copy(isLoading = true) }
        val preferTimeline = storage.getPreferTimeline()
        val themeRaw = storage.getThemeMode()
        val theme = when (themeRaw) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
        _state.update { it.copy(preferTimeline = preferTimeline, themeMode = theme, isLoading = false) }
    }

    suspend fun setPreferTimeline(value: Boolean) {
        storage.setPreferTimeline(value)
        _state.update { it.copy(preferTimeline = value) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        val raw = when (mode) {
            ThemeMode.LIGHT -> "light"
            ThemeMode.DARK -> "dark"
            ThemeMode.SYSTEM -> "system"
        }
        storage.setThemeMode(raw)
        _state.update { it.copy(themeMode = mode) }
        AppearanceSettings.setThemeMode(mode)
    }
}
