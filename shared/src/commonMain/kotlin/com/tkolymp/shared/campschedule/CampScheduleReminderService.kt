package com.tkolymp.shared.campschedule

import com.tkolymp.shared.Logger
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.notification.EventReminder
import com.tkolymp.shared.notification.INotificationScheduler
import com.tkolymp.shared.notification.INotificationStorage
import com.tkolymp.shared.storage.OfflineDataStorage
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Schedules one notification per "my" lesson for a camp day, kept separate from
 * [com.tkolymp.shared.notification.NotificationService.addOrUpdateReminder] (which is
 * hard-keyed to a single reminder per event id). Each individual lesson also gets its
 * own [EventReminder] row (tagged with [EventReminder.campDayIndex], id equal to its
 * actual scheduled notification id) so every lesson within the schedule shows up as its
 * own entry in the shared Notifications > Reminders list — not one combined entry for
 * the whole camp day.
 */
class CampScheduleReminderService(
    private val offlineDataStorage: OfflineDataStorage,
    private val scheduler: INotificationScheduler,
    private val campScheduleService: CampScheduleService,
    private val notificationStorage: INotificationStorage
) {
    private companion object {
        const val TAG = "CampScheduleReminderService"
        // Index of the Rozpis/Schedule tab within EventScreen's tab row (see EventScreen.kt).
        const val SCHEDULE_TAB_INDEX = 1
    }

    private val timeRegex = Regex("""(\d{1,2}):(\d{2})""")

    suspend fun rescheduleForDay(
        eventId: Long,
        dayIndex: Int,
        dayDate: LocalDate,
        myEntries: List<ScheduleEntry>,
        minutesBefore: Int,
        eventName: String
    ) {
        cancelForDay(eventId, dayIndex)
        if (minutesBefore <= 0) return

        val tz = TimeZone.currentSystemDefault()
        val newIds = mutableListOf<String>()
        val newRows = mutableListOf<EventReminder>()
        myEntries.forEachIndexed { idx, entry ->
            val isoDateTime = combineDateAndTime(dayDate, entry.time, tz) ?: return@forEachIndexed
            val nid = "camp_${eventId}_day${dayIndex}_entry$idx"
            // A lesson's title is its group block (e.g. "STT 2"); a note's title is its
            // own text (e.g. "Oběd", "Večerka...") since that's the only description it has.
            val title = when (entry) {
                is ScheduleEntry.Lesson -> entry.block ?: AppStrings.current.events.eventTypeLesson
                is ScheduleEntry.Note -> entry.text ?: AppStrings.current.events.eventTypeLesson
            }
            val trigger = try {
                scheduler.scheduleNotificationAt(
                    notificationId = nid,
                    title = title,
                    text = entry.time,
                    isoDateTime = isoDateTime,
                    minutesBefore = minutesBefore,
                    eventId = eventId,
                    tab = SCHEDULE_TAB_INDEX
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.w(TAG, "scheduleNotificationAt failed for $nid: ${e.message}")
                null
            }
            if (trigger != null) {
                newIds += nid
                newRows += EventReminder(
                    id = nid,
                    eventId = eventId,
                    eventName = eventName,
                    eventStartIso = isoDateTime,
                    minutesBefore = minutesBefore,
                    scheduledNotificationId = nid,
                    campDayIndex = dayIndex,
                    subLabel = title
                )
            }
        }
        offlineDataStorage.save(campScheduleService.reminderIdsKey(eventId, dayIndex), AppJson.encodeToString(ListSerializer(String.serializer()), newIds))
        replaceRowsForDay(eventId, dayIndex, newRows)
    }

    /** Reschedules a single lesson's notification at a new minutes-before, keeping its own list row. */
    suspend fun updateMinutesForLesson(reminder: EventReminder, minutesBefore: Int) {
        val nid = reminder.scheduledNotificationId ?: reminder.id
        try { scheduler.cancelNotification(nid) } catch (e: Exception) { if (e is CancellationException) throw e }
        if (minutesBefore <= 0) {
            removeRow(reminder.id)
            return
        }
        val trigger = try {
            scheduler.scheduleNotificationAt(
                notificationId = nid,
                title = reminder.eventName,
                text = null,
                isoDateTime = reminder.eventStartIso,
                minutesBefore = minutesBefore,
                eventId = reminder.eventId,
                tab = SCHEDULE_TAB_INDEX
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "updateMinutesForLesson failed for $nid: ${e.message}")
            null
        }
        if (trigger == null) {
            removeRow(reminder.id)
            return
        }
        saveRow(reminder.copy(minutesBefore = minutesBefore, scheduledNotificationId = nid))
    }

    /** Cancels a single lesson's reminder without touching the rest of that day's reminders. */
    suspend fun cancelLessonReminder(reminder: EventReminder) {
        val nid = reminder.scheduledNotificationId ?: reminder.id
        try { scheduler.cancelNotification(nid) } catch (e: Exception) { if (e is CancellationException) throw e }
        removeRow(reminder.id)
    }

    suspend fun cancelForDay(eventId: Long, dayIndex: Int) {
        val key = campScheduleService.reminderIdsKey(eventId, dayIndex)
        val prevIds = try { offlineDataStorage.load(key) } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }?.let { raw -> try { AppJson.decodeFromString(ListSerializer(String.serializer()), raw) } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        } } ?: emptyList()

        prevIds.forEach { id ->
            try { scheduler.cancelNotification(id) } catch (e: Exception) { if (e is CancellationException) throw e }
        }
        offlineDataStorage.save(key, AppJson.encodeToString(ListSerializer(String.serializer()), emptyList<String>()))
        replaceRowsForDay(eventId, dayIndex, emptyList())
    }

    private suspend fun replaceRowsForDay(eventId: Long, dayIndex: Int, rows: List<EventReminder>) {
        val others = try { notificationStorage.getEventReminders() } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }.filter { !(it.eventId == eventId && it.campDayIndex == dayIndex) }
        try { notificationStorage.saveEventReminders(others + rows) } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "replaceRowsForDay failed: ${e.message}")
        }
    }

    private suspend fun saveRow(reminder: EventReminder) {
        val others = try { notificationStorage.getEventReminders() } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }.filter { it.id != reminder.id }
        try { notificationStorage.saveEventReminders(others + reminder) } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "saveRow failed: ${e.message}")
        }
    }

    private suspend fun removeRow(id: String) {
        val remaining = try { notificationStorage.getEventReminders() } catch (e: Exception) {
            if (e is CancellationException) throw e
            return
        }.filter { it.id != id }
        try { notificationStorage.saveEventReminders(remaining) } catch (e: Exception) {
            if (e is CancellationException) throw e
            Logger.w(TAG, "removeRow failed: ${e.message}")
        }
    }

    private fun combineDateAndTime(date: LocalDate, timeText: String, tz: TimeZone): String? {
        val m = timeRegex.find(timeText) ?: return null
        val (h, min) = m.destructured
        val hour = h.toIntOrNull() ?: return null
        val minute = min.toIntOrNull() ?: return null
        return try { date.atTime(hour, minute).toInstant(tz).toString() } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }
}
