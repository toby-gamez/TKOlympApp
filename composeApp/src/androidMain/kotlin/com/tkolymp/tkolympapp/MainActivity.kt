package com.tkolymp.tkolympapp

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.messaging.FirebaseMessaging
import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.language.AppLanguage
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.language.getDeviceLanguageCode
import com.tkolymp.shared.storage.LanguageStorage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {
    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // permission result handled; we don't strictly need to do anything here
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Load language asynchronously before first composition.
        lifecycleScope.launch {
            val savedCode = try { LanguageStorage(this@MainActivity).getLanguageCode() } catch (_: Exception) { null }
            val language = if (savedCode != null) {
                AppLanguage.fromCode(savedCode)
            } else {
                AppLanguage.fromCode(getDeviceLanguageCode())
            }
            AppStrings.setLanguage(language)

            setContent {
                App()
            }

            // Získání a zalogování FCM tokenu
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    Logger.d("FCM", "Current token obtained")
                        try {
                            val prefs = this@MainActivity.getSharedPreferences("tkolymp_fcm", Context.MODE_PRIVATE)
                            val last = prefs.getString("last_uploaded_fcm_token", null)
                            if (token != null && token != last) {
                                lifecycleScope.launch {
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
                                            Logger.d("FCM", "FCM token uploaded from MainActivity")
                                        }
                                    } catch (t: Throwable) {
                                        Logger.d("FCM", "Failed uploading FCM token: ${t.message}")
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            Logger.d("FCM", "Preparing token upload failed: ${e.message}")
                        }
                } else {
                    Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                }
            }

            // Přihlášení k topicu 'all' pro broadcast notifikace
            FirebaseMessaging.getInstance().subscribeToTopic("all")
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Logger.d("FCM", "Subscribed to topic 'all'")
                    } else {
                        Log.w("FCM", "Topic subscription failed", task.exception)
                    }
                }
        }

        // Create notification channels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelEvents = NotificationChannel(
                "tkolymp_events",
                "Události",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channelEvents)

            val channelCoach = NotificationChannel(
                "coach",
                AppStrings.current.notifications.fromCoach,
                NotificationManager.IMPORTANCE_HIGH
            )
            channelCoach.description = AppStrings.current.notifications.fromCoach
            nm.createNotificationChannel(channelCoach)
        }

        // Request runtime notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Note: UI composition and FCM setup are performed in lifecycleScope.launch above.
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}