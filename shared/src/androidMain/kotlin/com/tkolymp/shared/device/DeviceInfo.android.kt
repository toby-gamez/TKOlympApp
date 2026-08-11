package com.tkolymp.shared.device

import android.content.Context
import android.os.Build

actual object DeviceInfo {
    actual val platformName: String = "Android"

    actual val osVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    actual val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"

    private var _appVersion: String = "unknown"
    actual val appVersion: String get() = _appVersion

    /** Populated once from the app's own PackageInfo; call as early as possible (e.g. Application.onCreate / initNetworking). */
    fun init(context: Context) {
        _appVersion = try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
            "${packageInfo.versionName} ($versionCode)"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
