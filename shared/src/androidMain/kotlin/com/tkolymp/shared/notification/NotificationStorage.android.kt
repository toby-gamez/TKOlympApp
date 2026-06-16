package com.tkolymp.shared.notification

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual class NotificationStorage actual constructor(platformContext: Any) : INotificationStorage {
    private val context = platformContext as Context
    private val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private inline fun <reified T> save(key: String, value: T) {
        prefs.edit().putString(key, json.encodeToString(value)).apply()
    }

    private inline fun <reified T> load(key: String): T? {
        val s = prefs.getString(key, null) ?: return null
        return try {
            json.decodeFromString<T>(s)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    actual override suspend fun saveSettings(settings: NotificationSettings) = save("notification_settings", settings)
    actual override suspend fun getSettings(): NotificationSettings? = load("notification_settings")

    actual override suspend fun saveScheduledNotifications(list: List<ScheduledNotification>) = save("scheduled_notifications", list)
    actual override suspend fun getScheduledNotifications(): List<ScheduledNotification> = load("scheduled_notifications") ?: emptyList()

    actual override suspend fun saveReceivedNotifications(list: List<ReceivedMessage>) = save("received_notifications", list)
    actual override suspend fun getReceivedNotifications(): List<ReceivedMessage> = load("received_notifications") ?: emptyList()

    actual override suspend fun saveEventReminders(list: List<EventReminder>) = save("event_reminders", list)
    actual override suspend fun getEventReminders(): List<EventReminder> = load("event_reminders") ?: emptyList()

    actual override suspend fun saveBirthdaySettings(settings: BirthdayNotificationSettings) = save("birthday_notification_settings", settings)
    actual override suspend fun getBirthdaySettings(): BirthdayNotificationSettings? = load("birthday_notification_settings")

    actual override suspend fun saveScheduledBirthdayNotificationIds(ids: List<String>) = save("scheduled_birthday_ids", ids)
    actual override suspend fun getScheduledBirthdayNotificationIds(): List<String> = load("scheduled_birthday_ids") ?: emptyList()
}
