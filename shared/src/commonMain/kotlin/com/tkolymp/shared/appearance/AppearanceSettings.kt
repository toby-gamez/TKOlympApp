package com.tkolymp.shared.appearance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Singleton that holds the currently active theme mode so the UI layer can
 * observe and react to changes without restarting the app.
 */
object AppearanceSettings {
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _preferTimeline = MutableStateFlow(false)
    val preferTimeline: StateFlow<Boolean> = _preferTimeline

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun setPreferTimeline(value: Boolean) {
        _preferTimeline.value = value
    }
}
