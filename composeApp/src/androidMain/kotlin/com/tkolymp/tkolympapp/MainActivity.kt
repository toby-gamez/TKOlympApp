package com.tkolymp.tkolympapp

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.tkolymp.shared.language.AppLanguage
import com.tkolymp.shared.language.AppStrings
import com.tkolymp.shared.language.getDeviceLanguageCode
import com.tkolymp.shared.storage.LanguageStorage
import kotlinx.coroutines.runBlocking

@SuppressLint("InvalidFragmentVersionForActivityResult")
class MainActivity : ComponentActivity() {
    private val requestNotificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // permission result handled; we don't strictly need to do anything here
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Apply language synchronously before first composition so every screen
        // (including OnboardingScreen) already sees the correct language.
        val savedCode = runBlocking { LanguageStorage(this@MainActivity).getLanguageCode() }
        val language = if (savedCode != null) {
            AppLanguage.fromCode(savedCode)
        } else {
            AppLanguage.fromCode(getDeviceLanguageCode())
        }
        AppStrings.setLanguage(language)

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
                "Od trenéra",
                NotificationManager.IMPORTANCE_HIGH
            )
            channelCoach.description = "Důležitá oznámení od trenéra"
            nm.createNotificationChannel(channelCoach)
        }

        // Request runtime notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            App()
        }
        // Získání a zalogování FCM tokenu
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "Current token: $token")
                // TODO: Odeslat token na server
            } else {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
            }
        }

        // Přihlášení k topicu 'all' pro broadcast notifikace
        FirebaseMessaging.getInstance().subscribeToTopic("all")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("FCM", "Subscribed to topic 'all'")
                } else {
                    Log.w("FCM", "Topic subscription failed", task.exception)
                }
            }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}