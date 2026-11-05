package com.tkolymp.shared.viewmodels

import androidx.lifecycle.ViewModel
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.appearance.AppearanceSettings
import com.tkolymp.shared.appearance.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import com.tkolymp.shared.errorreporting.ErrorReporter

@Immutable
data class SettingsState(
    val preferTimeline: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val errorReportingEnabled: Boolean = true,
    val isLoading: Boolean = false,
)

class SettingsViewModel(
    private val storage: com.tkolymp.shared.storage.ICalendarPreferenceStorage = ServiceLocator.calendarPreferenceStorage,
    private val onboardingStorage: com.tkolymp.shared.storage.OnboardingStorage = ServiceLocator.onboardingStorage,
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
        val errorReporting = try { onboardingStorage.isErrorReportingEnabled() } catch (e: CancellationException) { throw e } catch (_: Exception) { true }
        _state.update { it.copy(preferTimeline = preferTimeline, themeMode = theme, errorReportingEnabled = errorReporting, isLoading = false) }
    }

    /**
     * Lets the user change the error-report choice made on the privacy screen at any time
     * (GDPR art. 7(3) / art. 21). Settings is only reachable after the privacy screen was accepted,
     * so the switch alone decides whether reports (and their device info) are sent.
     */
    suspend fun setErrorReportingEnabled(enabled: Boolean) {
        onboardingStorage.setErrorReportingEnabled(enabled)
        ErrorReporter.setEnabled(enabled)
        ErrorReporter.setDeviceInfoConsented(enabled)
        _state.update { it.copy(errorReportingEnabled = enabled) }
    }

    suspend fun setPreferTimeline(value: Boolean) {
        storage.setPreferTimeline(value)
        _state.update { it.copy(preferTimeline = value) }
        AppearanceSettings.setPreferTimeline(value)
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
