package com.tkolymp.tkolympapp

import com.tkolymp.shared.notification.EventReminder
import com.tkolymp.shared.notification.NotificationService
import com.tkolymp.tkolympapp.fakes.FakeEventService
import com.tkolymp.tkolympapp.fakes.FakeNotificationScheduler
import com.tkolymp.tkolympapp.fakes.FakeNotificationStorage
import com.tkolymp.tkolympapp.fakes.minimalEventJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationServiceTest {

    private fun makeService(storage: FakeNotificationStorage) = NotificationService(
        storage = storage,
        scheduler = FakeNotificationScheduler(),
        eventService = FakeEventService(minimalEventJson())
    )

    @Test
    fun `getReminders drops reminders whose notification already fired`() = runTest {
        val now = Clock.System.now()
        val storage = FakeNotificationStorage()
        storage.saveEventReminders(
            listOf(
                // Event already started an hour ago, reminder was for 30 min before -> long fired.
                EventReminder(
                    id = "reminder_evt_1",
                    eventId = 1L,
                    eventName = "Past event",
                    eventStartIso = (now - 1.hours).toString(),
                    minutesBefore = 30
                ),
                // Event starts in an hour, reminder fires 30 min before -> still upcoming.
                EventReminder(
                    id = "reminder_evt_2",
                    eventId = 2L,
                    eventName = "Future event",
                    eventStartIso = (now + 1.hours).toString(),
                    minutesBefore = 30
                )
            )
        )

        val service = makeService(storage)
        val reminders = service.getReminders()

        assertEquals(listOf(2L), reminders.map { it.eventId })
        // Pruning must also persist back to storage, not just filter the return value.
        assertEquals(listOf(2L), storage.getEventReminders().map { it.eventId })
    }

    @Test
    fun `getReminders keeps a reminder that has not fired yet even if it is close`() = runTest {
        val now = Clock.System.now()
        val storage = FakeNotificationStorage()
        storage.saveEventReminders(
            listOf(
                EventReminder(
                    id = "reminder_evt_3",
                    eventId = 3L,
                    eventName = "Soon event",
                    // Trigger time (start - minutesBefore) is 1 minute in the future: not fired yet.
                    eventStartIso = (now + 31.minutes).toString(),
                    minutesBefore = 30
                )
            )
        )

        val reminders = makeService(storage).getReminders()

        assertTrue(reminders.any { it.eventId == 3L })
    }

    @Test
    fun `getReminderForEvent does not return an already-fired reminder`() = runTest {
        val now = Clock.System.now()
        val storage = FakeNotificationStorage()
        storage.saveEventReminders(
            listOf(
                EventReminder(
                    id = "reminder_evt_4",
                    eventId = 4L,
                    eventName = "Past event",
                    eventStartIso = (now - 5.hours).toString(),
                    minutesBefore = 30
                )
            )
        )

        val result = makeService(storage).getReminderForEvent(4L)

        assertEquals(null, result)
    }
}
