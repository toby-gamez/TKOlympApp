package com.tkolymp.shared.systemcalendar

actual class SystemCalendarService actual constructor(platformContext: Any) : ISystemCalendarService {

    actual override suspend fun addEvent(
        title: String,
        description: String?,
        location: String?,
        startMs: Long,
        endMs: Long,
        weeklyRepeatCount: Int?
    ): Boolean = false
}
