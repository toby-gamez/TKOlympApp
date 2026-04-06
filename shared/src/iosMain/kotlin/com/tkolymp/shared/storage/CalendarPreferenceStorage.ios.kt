package com.tkolymp.shared.storage

import platform.Foundation.NSUserDefaults

actual class CalendarPreferenceStorage actual constructor(platformContext: Any) {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual suspend fun getPreferTimeline(): Boolean =
        defaults.boolForKey("calendar_prefer_timeline")

    actual suspend fun setPreferTimeline(value: Boolean) {
        defaults.setBool(value, "calendar_prefer_timeline")
        defaults.synchronize()
    }

    actual suspend fun getThemeMode(): String =
        defaults.stringForKey("app_theme_mode") ?: "system"

    actual suspend fun setThemeMode(value: String) {
        defaults.setObject(value, "app_theme_mode")
        defaults.synchronize()
    }
}
