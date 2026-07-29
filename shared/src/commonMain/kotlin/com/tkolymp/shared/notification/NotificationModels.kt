package com.tkolymp.shared.notification

import com.tkolymp.shared.event.Event
import kotlinx.serialization.Serializable

@Serializable
data class NotificationRule(
    val id: String,
    val name: String = "",
    val enabled: Boolean = true,
    val filterType: FilterType = FilterType.ALL,
    val locations: List<String> = emptyList(),
    val trainers: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val timesBeforeMinutes: List<Int> = listOf(60, 5)
)

@Serializable
data class NotificationSettings(
    val globalEnabled: Boolean = true,
    val rules: List<NotificationRule> = emptyList()
)

@Serializable
data class ScheduledNotification(
    val notificationId: String,
    val eventId: Long?,
    val eventName: String?,
    val triggerEpochMs: Long
)

@kotlinx.serialization.Serializable
data class ReceivedMessage(
    val id: String,
    val title: String? = null,
    val body: String? = null,
    val sender: String? = null,
    val topic: String? = null,
    val epochMs: Long
)

enum class FilterType { ALL, BY_LOCATION, BY_TRAINER, BY_TYPE }

/**
 * Settings for birthday notifications.
 * Filters are additive (OR logic): a person is included when any enabled criterion matches.
 * - notifyAll: ignore other criteria, notify for everyone
 * - notifyTrainers: include club trainers
 * - selectedCohortIds: include members of these groups
 * - selectedPersonIds: always include these specific people
 */
@Serializable
data class BirthdayNotificationSettings(
    val enabled: Boolean = false,
    val notifyAll: Boolean = true,
    val notifyTrainers: Boolean = false,
    val selectedCohortIds: List<String> = emptyList(),
    val selectedPersonIds: List<String> = emptyList(),
    val notificationHour: Int = 8,
    val daysBefore: Int = 0
)

@Serializable
data class EventReminder(
    val id: String,
    val eventId: Long,
    val eventName: String,
    val eventStartIso: String,
    val minutesBefore: Int = 30,
    val scheduledNotificationId: String? = null,
    // Non-null marks this as a camp schedule day's reminder (one entry summarizing all of
    // that day's per-lesson notifications, see CampScheduleReminderService) rather than a
    // regular single-notification event reminder — editing/deleting it must go through
    // CampScheduleReminderService instead of NotificationService's single-id path.
    val campDayIndex: Int? = null,
    // The specific sub-item's own label (e.g. a lesson's block "STT 2" or a note's text
    // "Oběd") shown alongside its time under the shared [eventName] header, when several
    // reminders share one event id — null for a regular single-notification reminder,
    // where the event name alone is enough.
    val subLabel: String? = null
)
