package com.tkolymp.tkolympapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tkolymp.tkolympapp.R
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.tkolymp.shared.notification.ReceivedMessage
import com.tkolymp.shared.language.AppStrings
import kotlin.time.Clock

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        // TODO: Odeslat token na server
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "Message received: ${remoteMessage.data}")
        val title = remoteMessage.notification?.title ?: "TKOlympApp"
        val body = remoteMessage.notification?.body ?: "Máte novou zprávu."
        showNotification(title, body)
        // persist received message locally so it appears in "Od trenéra" tab
        try {
            val storage = ServiceLocator.notificationStorage
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val now = Clock.System.now().toEpochMilliseconds()
                    val id = now.toString()
                    val existing = try { storage.getReceivedNotifications() } catch (_: Exception) { emptyList() }
                    val newList = listOf(ReceivedMessage(id = id, title = title, body = body, sender = "coach", epochMs = now)) + existing
                    storage.saveReceivedNotifications(newList)
                } catch (t: Throwable) {
                    Log.d("FCM", "Failed saving received message: ${t.message}")
                }
            }
        } catch (e: Throwable) {
            Log.d("FCM", "ServiceLocator not ready: ${e.message}")
        }
    }

    private fun showNotification(title: String, message: String) {
        // Default: posílat do kanálu "coach" (Od trenéra)
        val channelId = "coach"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelCoach = NotificationChannel(
                channelId,
                AppStrings.current.notifications.fromCoach,
                NotificationManager.IMPORTANCE_HIGH
            )
            channelCoach.description = AppStrings.current.notifications.fromCoach
            nm.createNotificationChannel(channelCoach)
        }
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_coach) // Přidej ic_coach.xml nebo ic_coach.png do drawable/
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        with(NotificationManagerCompat.from(this)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}
