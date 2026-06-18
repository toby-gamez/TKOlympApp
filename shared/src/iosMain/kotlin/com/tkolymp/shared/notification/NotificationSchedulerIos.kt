package com.tkolymp.shared.notification

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSinceReferenceDate
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
class NotificationSchedulerIos : INotificationScheduler {

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun scheduleNotificationAt(
        notificationId: String,
        title: String?,
        text: String?,
        isoDateTime: String,
        minutesBefore: Int
    ): Long? {
        val instant = try {
            Instant.parse(isoDateTime)
        } catch (_: Exception) { return null }

        val triggerEpochMillis = instant.toEpochMilliseconds() - minutesBefore * 60_000L
        val nowMillis = System.currentTimeMillis()
        if (triggerEpochMillis <= nowMillis) return null

        val delaySeconds = (triggerEpochMillis - nowMillis) / 1000.0

        val content = UNMutableNotificationContent().apply {
            if (title != null) setTitle(title)
            if (text != null) setBody(text)
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound())
        }

        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
            timeInterval = delaySeconds,
            repeats = false
        )

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = notificationId,
            content = content,
            trigger = trigger
        )

        return suspendCancellableCoroutine { cont ->
            center.addNotificationRequest(request) { error ->
                if (error != null) {
                    cont.resume(null)
                } else {
                    cont.resume(triggerEpochMillis)
                }
            }
        }
    }

    override suspend fun cancelNotification(notificationId: String) {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(notificationId))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(notificationId))
    }

    override suspend fun cancelAllNotifications() {
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }

    override suspend fun requestPermissions(): Boolean {
        return suspendCancellableCoroutine { cont ->
            center.requestAuthorizationWithOptions(
                options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
            ) { granted, _ ->
                cont.resume(granted)
            }
        }
    }
}
