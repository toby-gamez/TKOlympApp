package com.tkolymp.shared.errorreporting

import android.content.Context

actual class CrashReportStorage actual constructor(platformContext: Any) {
    private val prefs = (platformContext as Context)
        .getSharedPreferences("tkolymp_crash", Context.MODE_PRIVATE)

    actual fun savePendingCrash(report: String) {
        // commit() (synchronous) rather than apply(): the process may die right after this call.
        prefs.edit().putString(KEY, report).commit()
    }

    actual fun getPendingCrash(): String? = prefs.getString(KEY, null)

    actual fun clearPendingCrash() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "pending_crash_report"
    }
}
