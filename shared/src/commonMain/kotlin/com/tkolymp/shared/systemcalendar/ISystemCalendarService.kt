package com.tkolymp.shared.systemcalendar

interface ISystemCalendarService {
    suspend fun addEvent(
        title: String,
        description: String?,
        location: String?,
        startMs: Long,
        endMs: Long,
        weeklyRepeatCount: Int?
    ): Boolean
}
