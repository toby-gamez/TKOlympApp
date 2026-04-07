package com.tkolymp.shared.notification

expect class NotificationStorage(platformContext: Any) {
    suspend fun saveSettings(settings: NotificationSettings)
    suspend fun getSettings(): NotificationSettings?
    suspend fun saveScheduledNotifications(list: List<ScheduledNotification>)
    suspend fun getScheduledNotifications(): List<ScheduledNotification>
    suspend fun saveReceivedNotifications(list: List<ReceivedMessage>)
    suspend fun getReceivedNotifications(): List<ReceivedMessage>
    suspend fun saveEventReminders(list: List<EventReminder>)
    suspend fun getEventReminders(): List<EventReminder>
}
