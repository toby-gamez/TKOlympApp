package com.tkolymp.tkolympapp

import androidx.compose.runtime.Composable
import platform.Foundation.NSBundle

@Composable
fun App() {
    AppContent(
        platformInit = {
            val baseUrl = NSBundle.mainBundle.objectForInfoDictionaryKey("API_BASE_URL") as? String ?: ""
            val tenantId = NSBundle.mainBundle.objectForInfoDictionaryKey("TENANT_ID") as? String ?: "1"
            com.tkolymp.shared.initNetworking(baseUrl, tenantId)
        }
    )
}
