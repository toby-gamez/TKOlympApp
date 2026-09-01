package com.tkolymp.tkolympapp.app

import android.app.Application
import com.tkolymp.shared.Logger
import com.tkolymp.shared.initNetworking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TKOlympApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Logger.isDebug = BuildConfig.DEBUG
        // initNetworking is pure object construction (no I/O). Running it on a background
        // coroutine avoids blocking the main thread; AppContent's isInitialized guard
        // waits for it before rendering, and WidgetUpdateWorker/WorkManager tasks check
        // ServiceLocator.isInitialized before use.
        appScope.launch {
            initNetworking(this@TKOlympApplication, BuildConfig.API_BASE_URL, BuildConfig.TENANT_ID)
        }
    }
}
