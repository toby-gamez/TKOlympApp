package com.tkolymp.shared.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class NotificationSchedulerAndroid(private val platformContext: Any) : INotificationScheduler {
    private val context = platformContext as Context

    private fun makeIntent(notificationId: String, title: String?, text: String?): Intent {
        return Intent("com.tkolymp.tkolympapp.SHOW_NOTIFICATION").apply {
            `package` = context.packageName
            putExtra("notificationId", notificationId)
            putExtra("title", title)
            putExtra("text", text)
        }
    }

    override suspend fun cancelNotification(notificationId: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = makeIntent(notificationId, null, null)
        val flags = PendingIntent.FLAG_NO_CREATE or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getBroadcast(context, notificationId.hashCode(), intent, flags)
        if (pi != null) am.cancel(pi)
    }

    override suspend fun cancelAllNotifications() {
        // Not easily enumerable; cancel using stored scheduled list in storage.
    }

    override suspend fun requestPermissions(): Boolean {
        // Runtime POST_NOTIFICATIONS permission must be requested from an Activity; return true for now.
        return true
    }

    override suspend fun scheduleNotificationAt(notificationId: String, title: String?, text: String?, isoDateTime: String, minutesBefore: Int): Long? {
        val instant = try {
            java.time.OffsetDateTime.parse(isoDateTime).toInstant()
        } catch (ex: Throwable) {
            try {
                java.time.Instant.parse(isoDateTime)
            } catch (ex2: Throwable) {
                return null
            }
        }

        val triggerAt = instant.toEpochMilli() - minutesBefore * 60_000L
        val now = System.currentTimeMillis()
        if (triggerAt <= now) return null

        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = makeIntent(notificationId, title, text)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pi = PendingIntent.getBroadcast(context, notificationId.hashCode(), intent, flags)
        // Use a regular (inexact) alarm instead of exact scheduling.
        am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        return triggerAt
    }
}
