package com.tkolymp.shared.notification

interface INotificationScheduler {
    /**
     * Schedule a notification at ISO datetime minus minutesBefore. Returns trigger epoch millis or null if scheduling skipped.
     */
    suspend fun scheduleNotificationAt(notificationId: String, title: String?, text: String?, isoDateTime: String, minutesBefore: Int, eventId: Long? = null, tab: Int = 0): Long?
    suspend fun cancelNotification(notificationId: String)
    suspend fun cancelAllNotifications()
    /** Request runtime permissions where applicable (Activity-level request must be performed elsewhere). */
    suspend fun requestPermissions(): Boolean
}
