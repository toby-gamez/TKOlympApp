package com.tkolymp.shared.storage

expect class CalendarPreferenceStorage(platformContext: Any) {
    suspend fun getPreferTimeline(): Boolean
    suspend fun setPreferTimeline(value: Boolean)
    suspend fun getThemeMode(): String
    suspend fun setThemeMode(value: String)
    suspend fun isEventInCalendar(eventId: Long): Boolean
    suspend fun setEventInCalendar(eventId: Long)
}
