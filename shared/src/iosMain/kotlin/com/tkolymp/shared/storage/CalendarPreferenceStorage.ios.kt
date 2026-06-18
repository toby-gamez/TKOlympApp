package com.tkolymp.shared.storage

import platform.Foundation.NSUserDefaults

actual class CalendarPreferenceStorage actual constructor(platformContext: Any) : ICalendarPreferenceStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual override suspend fun getPreferTimeline(): Boolean =
        defaults.boolForKey("calendar_prefer_timeline")

    actual override suspend fun setPreferTimeline(value: Boolean) {
        defaults.setBool(value, "calendar_prefer_timeline")
        defaults.synchronize()
    }

    actual override suspend fun getThemeMode(): String =
        defaults.stringForKey("app_theme_mode") ?: "system"

    actual override suspend fun setThemeMode(value: String) {
        defaults.setObject(value, "app_theme_mode")
        defaults.synchronize()
    }

    actual override suspend fun isEventInCalendar(eventId: Long): Boolean {
        val stored = defaults.stringForKey("calendar_event_ids") ?: ""
        return stored.split(",").contains(eventId.toString())
    }

    actual override suspend fun setEventInCalendar(eventId: Long) {
        val stored = defaults.stringForKey("calendar_event_ids") ?: ""
        val ids = stored.split(",").filter { it.isNotBlank() }.toMutableSet()
        ids.add(eventId.toString())
        defaults.setObject(ids.joinToString(","), "calendar_event_ids")
        defaults.synchronize()
    }

    actual override suspend fun getWeeklyGoal(): Int =
        defaults.integerForKey("weekly_goal").toInt()

    actual override suspend fun setWeeklyGoal(value: Int) {
        defaults.setInteger(value.toLong(), "weekly_goal")
        defaults.synchronize()
    }
}
