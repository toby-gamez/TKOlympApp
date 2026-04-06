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

    actual suspend fun isEventInCalendar(eventId: Long): Boolean {
        val stored = defaults.stringForKey("calendar_event_ids") ?: ""
        return stored.split(",").contains(eventId.toString())
    }

    actual suspend fun setEventInCalendar(eventId: Long) {
        val stored = defaults.stringForKey("calendar_event_ids") ?: ""
        val ids = stored.split(",").filter { it.isNotBlank() }.toMutableSet()
        ids.add(eventId.toString())
        defaults.setObject(ids.joinToString(","), "calendar_event_ids")
        defaults.synchronize()
    }
}
