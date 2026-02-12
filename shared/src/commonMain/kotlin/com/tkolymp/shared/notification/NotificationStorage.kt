package com.tkolymp.shared.notification

expect class NotificationStorage(platformContext: Any) {
    suspend fun saveSettings(settings: NotificationSettings)
    suspend fun getSettings(): NotificationSettings?
    suspend fun saveScheduledNotifications(list: List<ScheduledNotification>)
    suspend fun getScheduledNotifications(): List<ScheduledNotification>
}
