package com.tkolymp.tkolympapp

import androidx.compose.runtime.Composable
import com.tkolymp.shared.ServiceLocator

@Composable
fun App(initialRoute: String? = null) {
    AppContent(
        platformInit = {
            try { ServiceLocator.topicManager = AndroidTopicManager() } catch (_: Exception) {}
        },
        initialRoute = initialRoute
    )
}
