package com.tkolymp.shared.storage

import android.content.Context

actual class CalendarPreferenceStorage actual constructor(platformContext: Any) : ICalendarPreferenceStorage {
    private val prefs = (platformContext as Context)
        .getSharedPreferences("tkolymp_prefs", Context.MODE_PRIVATE)

    actual override suspend fun getPreferTimeline(): Boolean =
        prefs.getBoolean("calendar_prefer_timeline", false)

    actual override suspend fun setPreferTimeline(value: Boolean) {
        prefs.edit().putBoolean("calendar_prefer_timeline", value).apply()
    }

    actual override suspend fun getThemeMode(): String =
        prefs.getString("app_theme_mode", "system") ?: "system"

    actual override suspend fun setThemeMode(value: String) {
        prefs.edit().putString("app_theme_mode", value).apply()
    }

    actual override suspend fun isEventInCalendar(eventId: Long): Boolean {
        val stored = prefs.getString("calendar_event_ids", "") ?: ""
        return stored.split(",").contains(eventId.toString())
    }

    actual override suspend fun setEventInCalendar(eventId: Long) {
        val stored = prefs.getString("calendar_event_ids", "") ?: ""
        val ids = stored.split(",").filter { it.isNotBlank() }.toMutableSet()
        ids.add(eventId.toString())
        prefs.edit().putString("calendar_event_ids", ids.joinToString(",")).apply()
    }

    actual override suspend fun getWeeklyGoal(): Int =
        prefs.getInt("weekly_goal", 0)

    actual override suspend fun setWeeklyGoal(value: Int) {
        prefs.edit().putInt("weekly_goal", value).apply()
    }
}
