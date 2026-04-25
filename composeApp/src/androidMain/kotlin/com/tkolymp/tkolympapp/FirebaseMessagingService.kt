package com.tkolymp.tkolympapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.tkolymp.shared.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tkolymp.tkolympapp.R
import com.tkolymp.shared.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.tkolymp.shared.notification.ReceivedMessage
import com.tkolymp.shared.language.AppStrings
import kotlin.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import android.content.Context

class MyFirebaseMessagingService : FirebaseMessagingService() {
    private val svcScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Logger.d("FCM", "New token received")
        // Send token to server (best-effort). Avoid duplicate uploads.
        try {
            val prefs = getSharedPreferences("tkolymp_fcm", Context.MODE_PRIVATE)
            val last = prefs.getString("last_uploaded_fcm_token", null)
            if (token == last) {
                Logger.d("FCM", "Token unchanged, skipping upload")
                return
            }
            svcScope.launch {
                try {
                    val mutation = """mutation RegisterFcm(${'$'}input: RegisterFcmInput!) { registerFcm(input: ${'$'}input) { success } }"""
                    val variables = buildJsonObject {
                        put("input", buildJsonObject {
                            put("token", JsonPrimitive(token))
                            put("platform", JsonPrimitive("ANDROID"))
                        })
                    }
                    val resp = ServiceLocator.graphQlClient.post(mutation, variables)
                    val errors = resp.jsonObject["errors"]
                    if (errors != null) {
                        Logger.d("FCM", "GraphQL errors registering token: $errors")
                    } else {
                        prefs.edit().putString("last_uploaded_fcm_token", token).apply()
                        Logger.d("FCM", "FCM token uploaded")
                    }
                } catch (t: Throwable) {
                    Logger.d("FCM", "Failed uploading FCM token: ${t.message}")
                }
            }
        } catch (e: Throwable) {
            Logger.d("FCM", "Failed preparing token upload: ${e.message}")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
            Logger.d("FCM", "Message received: ${remoteMessage.data}")
        val title = remoteMessage.notification?.title ?: "TKOlympApp"
        val body = remoteMessage.notification?.body ?: "Máte novou zprávu."
        showNotification(title, body)
        // persist received message locally so it appears in "Od trenéra" tab
        try {
            val storage = ServiceLocator.notificationStorage
                svcScope.launch {
                try {
                        val now = Clock.System.now().toEpochMilliseconds()
                        val id = now.toString()
                        // try to determine topic: RemoteMessage.from may be like "/topics/<id>"
                        val from = remoteMessage.from
                        val dataMap = remoteMessage.data
                        val candidateKeys = listOf("topic", "channel", "group", "group_id", "groupId", "cohort", "cohortId", "topic_id", "topicName", "channelId")
                        var topic: String? = null
                        if (from != null && from.startsWith("/topics/")) topic = from.substringAfterLast("/")
                        if (topic.isNullOrBlank()) {
                            for (k in candidateKeys) {
                                val v = dataMap[k]
                                if (!v.isNullOrBlank()) { topic = v; break }
                            }
                        }
                        if (topic != null) topic = topic.trim()
                        Logger.d("FCM", "Detected topic=$topic from=$from dataKeys=${dataMap.keys}")
                        val existing = try { storage.getReceivedNotifications() } catch (_: Exception) { emptyList() }
                        val newList = listOf(ReceivedMessage(id = id, title = title, body = body, sender = "coach", topic = topic, epochMs = now)) + existing
                        Logger.d("FCM", "Saving received message id=$id topic=$topic data=${dataMap.keys}")
                    storage.saveReceivedNotifications(newList)
                } catch (t: Throwable) {
                    Logger.d("FCM", "Failed saving received message: ${t.message}")
                }
            }
        } catch (e: Throwable) {
            Logger.d("FCM", "ServiceLocator not ready: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        svcScope.cancel()
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
