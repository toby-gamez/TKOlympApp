package com.tkolymp.shared.campschedule

import com.tkolymp.shared.Logger
import com.tkolymp.shared.json.AppJson
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.notification.INotificationScheduler
import com.tkolymp.shared.storage.OfflineDataStorage
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Schedules one notification per "my" lesson for a camp day. Kept separate from
 * [com.tkolymp.shared.notification.NotificationService.addOrUpdateReminder], which is
 * hard-keyed to a single reminder per event id and can't hold N per-day lesson reminders.
 */
class CampScheduleReminderService(
    private val offlineDataStorage: OfflineDataStorage,
    private val scheduler: INotificationScheduler,
    private val campScheduleService: CampScheduleService
) {
    private companion object {
        const val TAG = "CampScheduleReminderService"
    }

    private val timeRegex = Regex("""(\d{1,2}):(\d{2})""")

    suspend fun rescheduleForDay(eventId: Long, dayIndex: Int, dayDate: LocalDate, myLessons: List<ScheduleEntry.Lesson>, minutesBefore: Int) {
        cancelForDay(eventId, dayIndex)
        if (minutesBefore <= 0) return

        val tz = TimeZone.currentSystemDefault()
        val newIds = myLessons.mapIndexedNotNull { idx, lesson ->
            val isoDateTime = combineDateAndTime(dayDate, lesson.time, tz) ?: return@mapIndexedNotNull null
            val nid = "camp_${eventId}_day${dayIndex}_lesson$idx"
            val title = lesson.block ?: AppStrings.current.events.eventTypeLesson
            val trigger = try {
                scheduler.scheduleNotificationAt(
                    notificationId = nid,
                    title = title,
                    text = lesson.time,
                    isoDateTime = isoDateTime,
                    minutesBefore = minutesBefore
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.w(TAG, "scheduleNotificationAt failed for $nid: ${e.message}")
                null
            }
            nid.takeIf { trigger != null }
        }
        offlineDataStorage.save(campScheduleService.reminderIdsKey(eventId, dayIndex), AppJson.encodeToString(ListSerializer(String.serializer()), newIds))
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
