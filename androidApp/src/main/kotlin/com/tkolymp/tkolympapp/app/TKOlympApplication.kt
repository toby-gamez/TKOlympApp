package com.tkolymp.tkolympapp.app

import android.app.Application
import com.tkolymp.shared.Logger
import com.tkolymp.shared.initNetworking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TKOlympApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Logger.isDebug = BuildConfig.DEBUG
        scope.launch {
            initNetworking(this@TKOlympApplication, BuildConfig.API_BASE_URL, BuildConfig.TENANT_ID)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        scope.cancel()
    }
}
