package com.tkolymp.shared.systemcalendar

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

actual class SystemCalendarService actual constructor(platformContext: Any) {
    private val context: Context = (platformContext as Context).applicationContext

    actual suspend fun addEvent(
        title: String,
        description: String?,
        location: String?,
        startMs: Long,
        endMs: Long,
        weeklyRepeatCount: Int?
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
                if (!description.isNullOrBlank()) {
                    putExtra(CalendarContract.Events.DESCRIPTION, description)
                }
                if (!location.isNullOrBlank()) {
                    putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                }
                if (weeklyRepeatCount != null && weeklyRepeatCount > 1) {
                    putExtra(CalendarContract.Events.RRULE, "FREQ=WEEKLY;COUNT=$weeklyRepeatCount")
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
