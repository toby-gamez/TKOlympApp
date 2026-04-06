package com.tkolymp.shared.systemcalendar

/**
 * Platform-specific service for adding events to the device system calendar.
 *
 * @param platformContext On Android: Application Context. On iOS: not used (pass Unit).
 */
expect class SystemCalendarService(platformContext: Any) {
    /**
     * Adds an event to the system calendar.
     *
     * @param title              Event title
     * @param description        Optional description
     * @param location           Optional location name
     * @param startMs            Start time in epoch milliseconds
     * @param endMs              End time in epoch milliseconds
     * @param weeklyRepeatCount  If non-null and > 1, creates a weekly recurring event with this many occurrences
     * @return true if the event was successfully added / intent was launched
     */
    suspend fun addEvent(
        title: String,
        description: String?,
        location: String?,
        startMs: Long,
        endMs: Long,
        weeklyRepeatCount: Int? = null
    ): Boolean
}
