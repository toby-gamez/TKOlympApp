package com.tkolymp.shared.systemcalendar

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.EventKit.EKEntityTypeEvent
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKRecurrenceFrequencyWeekly
import platform.EventKit.EKRecurrenceRule
import platform.EventKit.EKSpanThisEvent
import platform.Foundation.NSDate
import platform.Foundation.NSNumber
import platform.Foundation.dateWithTimeIntervalSince1970
import kotlin.coroutines.resume

actual class SystemCalendarService actual constructor(platformContext: Any) {

    actual suspend fun addEvent(
        title: String,
        description: String?,
        location: String?,
        startMs: Long,
        endMs: Long,
        weeklyRepeatCount: Int?
    ): Boolean = suspendCancellableCoroutine { cont ->
        val store = EKEventStore()
        store.requestAccessToEntityType(EKEntityTypeEvent) { granted, _ ->
            if (!granted) {
                cont.resume(false)
                return@requestAccessToEntityType
            }
            val ev = EKEvent.eventWithEventStore(store)
            ev.title = title
            ev.notes = description
            ev.location = location
            ev.startDate = NSDate.dateWithTimeIntervalSince1970(startMs / 1000.0)
            ev.endDate = NSDate.dateWithTimeIntervalSince1970(endMs / 1000.0)
            ev.calendar = store.defaultCalendarForNewEvents
            if (weeklyRepeatCount != null && weeklyRepeatCount > 1) {
                val rule = EKRecurrenceRule(
                    recurrenceWithFrequency = EKRecurrenceFrequencyWeekly,
                    interval = 1,
                    end = platform.EventKit.EKRecurrenceEnd.recurrenceEndWithOccurrenceCount(
                        weeklyRepeatCount.toLong()
                    )
                )
                ev.addRecurrenceRule(rule)
            }
            val saved = try {
                store.saveEvent(ev, EKSpanThisEvent, null)
            } catch (_: Exception) { false }
            cont.resume(saved)
        }
    }
}

