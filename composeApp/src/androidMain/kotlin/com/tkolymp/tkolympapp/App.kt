package com.tkolymp.tkolympapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.integrity.IntegrityServiceAndroid

@Composable
fun App() {
    val ctx = LocalContext.current
    var integrityFailed by remember { mutableStateOf(false) }
    // In debug builds skip the integrity check immediately (mutableStateOf(true) = already checked)
    var integrityChecked by remember { mutableStateOf(BuildConfig.DEBUG) }

    if (!integrityChecked) {
        LaunchedEffect(Unit) {
            integrityFailed = !IntegrityServiceAndroid(ctx).isValid()
            integrityChecked = true
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    AppContent(
        integrityFailed = integrityFailed,
        platformInit = {
            com.tkolymp.shared.initNetworking(ctx, BuildConfig.API_BASE_URL, BuildConfig.TENANT_ID)
            try { ServiceLocator.topicManager = AndroidTopicManager() } catch (_: Exception) {}
        }
    )
}
