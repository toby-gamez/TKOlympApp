package com.tkolymp.shared.storage

import android.content.Context

actual class CalendarPreferenceStorage actual constructor(platformContext: Any) {
    private val prefs = (platformContext as Context)
        .getSharedPreferences("tkolymp_prefs", Context.MODE_PRIVATE)

    actual suspend fun getPreferTimeline(): Boolean =
        prefs.getBoolean("calendar_prefer_timeline", false)

    actual suspend fun setPreferTimeline(value: Boolean) {
        prefs.edit().putBoolean("calendar_prefer_timeline", value).apply()
    }

    actual suspend fun getThemeMode(): String =
        prefs.getString("app_theme_mode", "system") ?: "system"

    actual suspend fun setThemeMode(value: String) {
        prefs.edit().putString("app_theme_mode", value).apply()
    }

    actual suspend fun isEventInCalendar(eventId: Long): Boolean {
        val stored = prefs.getString("calendar_event_ids", "") ?: ""
        return stored.split(",").contains(eventId.toString())
    }

    actual suspend fun setEventInCalendar(eventId: Long) {
        val stored = prefs.getString("calendar_event_ids", "") ?: ""
        val ids = stored.split(",").filter { it.isNotBlank() }.toMutableSet()
        ids.add(eventId.toString())
        prefs.edit().putString("calendar_event_ids", ids.joinToString(",")).apply()
    }
}
