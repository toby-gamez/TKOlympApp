package com.tkolymp.shared.errorreporting

import platform.Foundation.NSUserDefaults

actual class CrashReportStorage actual constructor(platformContext: Any) {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun savePendingCrash(report: String) {
        defaults.setObject(report, KEY)
        defaults.synchronize()
    }

    actual fun getPendingCrash(): String? = defaults.stringForKey(KEY)

    actual fun clearPendingCrash() {
        defaults.removeObjectForKey(KEY)
        defaults.synchronize()
    }

    private companion object {
        const val KEY = "pending_crash_report"
    }
}
