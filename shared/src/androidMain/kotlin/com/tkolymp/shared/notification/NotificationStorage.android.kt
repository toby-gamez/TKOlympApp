package com.tkolymp.shared.notification

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

actual class NotificationStorage actual constructor(platformContext: Any) {
    private val context = platformContext as Context
    private val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    actual suspend fun saveSettings(settings: NotificationSettings) {
        val s = json.encodeToString(settings)
        prefs.edit().putString("notification_settings", s).apply()
    }

    actual suspend fun getSettings(): NotificationSettings? {
        val s = prefs.getString("notification_settings", null) ?: return null
        return try {
            json.decodeFromString<NotificationSettings>(s)
        } catch (_: Throwable) { null }
    }

    actual suspend fun saveScheduledNotifications(list: List<ScheduledNotification>) {
        val s = json.encodeToString(list)
        prefs.edit().putString("scheduled_notifications", s).apply()
    }

    actual suspend fun getScheduledNotifications(): List<ScheduledNotification> {
        val s = prefs.getString("scheduled_notifications", null) ?: return emptyList()
        return try {
            json.decodeFromString(s)
        } catch (_: Throwable) { emptyList() }
    }
}
