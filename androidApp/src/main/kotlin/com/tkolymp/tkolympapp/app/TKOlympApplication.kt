package com.tkolymp.tkolympapp.app

import android.app.Application
import com.tkolymp.shared.Logger
import com.tkolymp.shared.initNetworking

class TKOlympApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.isDebug = BuildConfig.DEBUG
        initNetworking(this, BuildConfig.API_BASE_URL, BuildConfig.TENANT_ID)
    }
}
